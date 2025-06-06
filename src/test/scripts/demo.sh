#!/bin/sh

# This script quickly sets up a local Search Relevance Workbench from scratch with:
# * User Behavior Insights sample data
# * An "ecommerce" style sample data
# You can now exercise the Single Query comparison, Query Set Comparison and Search Evaluation experiments of SRW!  
# 
# There are two ways to start:
# 
# 1) `docker compose build && docker compose up` which enables UBI support and larger amounts of ecommerce data ingestion.
# 2) `./gradlew run --preserve-data --debug-jvm` which faciliates debugging, but pass in --skip-ubi
# 
# It will clear out any existing data except ecommerce index if you pass --skip-ecommerce as a parameter.

# Helper script
exe() { (set -x ; "$@") | jq | tee RES; echo; }

# Check for --skip-ecommerce parameter
# Check for --skip-ubi parameter
SKIP_ECOMMERCE=false
SKIP_UBI=false
for arg in "$@"; do
  if [ "$arg" = "--skip-ubi" ]; then
    SKIP_UBI=true
  fi
  if [ "$arg" = "--skip-ecommerce" ]; then
    SKIP_ECOMMERCE=true
  fi
done


# Once we get remote cluster connection working, we can eliminate this.
if [ "$SKIP_ECOMMERCE" = false ]; then
  echo Deleting ecommerce sample data
  (curl -s -X DELETE "http://localhost:9200/ecommerce" > /dev/null) || true

  ECOMMERCE_DATA_FILE="esci_us_opensearch-2025-06-06.json"
  # Check if data file exists locally, if not download it
  if [ ! -f "$ECOMMERCE_DATA_FILE" ]; then
    echo "Data file not found locally. Downloading from S3..."
    wget https://o19s-public-datasets.s3.amazonaws.com/esci_us_opensearch-2025-06-06.json
  fi

  echo "Creating ecommerce index using default bulk ingestion schema"

  # Create the index by reading in one doc
  head -n 2 "$ECOMMERCE_DATA_FILE" | curl -s -X POST "http://localhost:9200/index-name/_bulk?pretty" \
    -H 'Content-Type: application/x-ndjson' --data-binary @-

  # Increase the mappings
  curl -s -X PUT "http://localhost:9200/ecommerce/_settings" \
  -H "Content-type: application/json" \
  -d'{
    "index.mapping.total_fields.limit": 20000
  }'

  echo
  echo Populating ecommerce index
  # do 250 products
  #head -n 500 ../esci_us/esci_us_opensearch.json | curl -s -X POST "http://localhost:9200/index-name/_bulk" \
  #  -H 'Content-Type: application/x-ndjson' --data-binary @-
  # 
  #  echo Populating ecommerce index
   
  # Get total line count of the file
  TOTAL_LINES=$(wc -l < "$ECOMMERCE_DATA_FILE")
  echo "Total lines in file: $TOTAL_LINES"
  
  # Calculate number of chunks (50000 lines per chunk)
  CHUNK_SIZE=50000
  CHUNKS=$(( (TOTAL_LINES + CHUNK_SIZE - 1) / CHUNK_SIZE ))
  echo "Will process file in $CHUNKS chunks of $CHUNK_SIZE lines each"
  
  # Process file in chunks
  for (( i=0; i<CHUNKS; i++ )); do
    START_LINE=$(( i * CHUNK_SIZE + 1 ))
    END_LINE=$(( (i + 1) * CHUNK_SIZE ))
    
    # Ensure we don't go past the end of the file
    if [ $END_LINE -gt $TOTAL_LINES ]; then
      END_LINE=$TOTAL_LINES
    fi
    
    LINES_TO_PROCESS=$(( END_LINE - START_LINE + 1 ))
    echo "Processing chunk $((i+1))/$CHUNKS: lines $START_LINE-$END_LINE ($LINES_TO_PROCESS lines)"
    
    # Use sed to extract the chunk and pipe to curl for indexing
    sed -n "${START_LINE},${END_LINE}p" "$ECOMMERCE_DATA_FILE" | \
      curl -s -o /dev/null -w "%{http_code}" -X POST "http://localhost:9200/ecommerce/_bulk" \
      -H 'Content-Type: application/x-ndjson' --data-binary @- 
    
    # Give OpenSearch a moment to process the chunk
    sleep 1
  done
  
  echo "All data indexed successfully"
fi

if [ "$SKIP_UBI" = false ]; then
  echo Deleting UBI indexes
  (curl -s -X DELETE "http://localhost:9200/ubi_queries" > /dev/null) || true
  (curl -s -X DELETE "http://localhost:9200/ubi_events" > /dev/null) || true
  
  echo Creating UBI indexes using mappings
  curl -s -X POST http://localhost:9200/_plugins/ubi/initialize
  
  echo Loading sample UBI data
  curl -o /dev/null -X POST 'http://localhost:9200/index-name/_bulk?pretty' --data-binary @../data-esci/ubi_queries_events.ndjson -H "Content-Type: application/x-ndjson"
  
  echo Refreshing UBI indexes to make indexed data available for query sampling
  curl -XPOST "http://localhost:9200/ubi_queries/_refresh"
  echo
  curl -XPOST "http://localhost:9200/ubi_events/_refresh"
  
  read -r -d '' QUERY_BODY << EOF
  {
    "query": {
      "match_all": {}
    },
    "size": 0
  }
EOF
  
  NUMBER_OF_QUERIES=$(curl -s -XGET "http://localhost:9200/ubi_queries/_search" \
    -H "Content-Type: application/json" \
    -d "${QUERY_BODY}" | jq -r '.hits.total.value')
  
  NUMBER_OF_EVENTS=$(curl -s -XGET "http://localhost:9200/ubi_events/_search" \
    -H "Content-Type: application/json" \
    -d "${QUERY_BODY}" | jq -r '.hits.total.value')
    
  echo
  echo "Indexed UBI data: $NUMBER_OF_QUERIES queries and $NUMBER_OF_EVENTS events"
  
fi

echo

curl -XPUT "http://localhost:9200/_cluster/settings" -H 'Content-Type: application/json' -d'
{
  "persistent" : {
    "plugins.search_relevance.workbench_enabled" : true
  }
}
'

echo Deleting queryset, search config, judgment and experiment indexes
(curl -s -X DELETE "http://localhost:9200/search-relevance-search-config" > /dev/null) || true
(curl -s -X DELETE "http://localhost:9200/search-relevance-queryset" > /dev/null) || true
(curl -s -X DELETE "http://localhost:9200/search-relevance-judgment" > /dev/null) || true
(curl -s -X DELETE "http://localhost:9200/.plugins-search-relevance-experiment" > /dev/null) || true
(curl -s -X DELETE "http://localhost:9200/search-relevance-evaluation-result" > /dev/null) || true
(curl -s -X DELETE "http://localhost:9200/search-relevance-experiment-variant" > /dev/null) || true

sleep 2
echo Create search configs



exe curl -s -X PUT "http://localhost:9200/_plugins/_search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d'{
      "name": "baseline",
      "query": "{\"query\":{\"multi_match\":{\"query\":\"%SearchText%\",\"fields\":[\"id\",\"title\",\"category\",\"bullets\",\"description\",\"attrs.Brand\",\"attrs.Color\"]}}}",
      "index": "ecommerce"
}'

SC_BASELINE=`jq -r '.search_configuration_id' < RES`

exe curl -s -X PUT "http://localhost:9200/_plugins/_search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d'{
      "name": "baseline with title weight",
      "query": "{\"query\":{\"multi_match\":{\"query\":\"%SearchText%\",\"fields\":[\"id\",\"title^25\",\"category\",\"bullets\",\"description\",\"attrs.Brand\",\"attrs.Color\"]}}}",
      "index": "ecommerce"
}'

SC_CHALLENGER=`jq -r '.search_configuration_id' < RES`

echo
echo List search configurations
exe curl -s -X GET "http://localhost:9200/_plugins/_search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d'{
     "sort": {
       "timestamp": {
         "order": "desc"
       }
     },
     "size": 10
   }'

echo
echo Baseline search config id: $SC_BASELINE
echo Challenger search config id: $SC_CHALLENGER

if [ "$SKIP_UBI" = false ]; then
  echo
  echo Create Query Sets by Sampling UBI Data
  exe curl -s -X POST "localhost:9200/_plugins/_search_relevance/query_sets" \
  -H "Content-type: application/json" \
  -d'{
     	"name": "Top 20",
     	"description": "Top 20 most frequent queries sourced from user searches.",
     	"sampling": "topn",
     	"querySetSize": 20
  }'
  
  QUERY_SET_UBI=`jq -r '.query_set_id' < RES`
  
  sleep 2
fi

echo
echo Upload Manually Curated Query Set 

exe curl -s -X PUT "localhost:9200/_plugins/_search_relevance/query_sets" \
-H "Content-type: application/json" \
-d'{
   	"name": "TVs",
   	"description": "Some TVs that people might want",
   	"sampling": "manual",
   	"querySetQueries": [
    	{"queryText": "tv"},
    	{"queryText": "led tv"}
    ]
}'

QUERY_SET_MANUAL=`jq -r '.query_set_id' < RES`

echo
echo Upload ESCI Query Set 

exe curl -s -X PUT "localhost:9200/_plugins/_search_relevance/query_sets" \
-H "Content-type: application/json" \
--data-binary @../data-esci/esci_us_queryset.json



QUERY_SET_ESCI=`jq -r '.query_set_id' < RES`

echo
echo List Query Sets

exe curl -s -X GET "localhost:9200/_plugins/_search_relevance/query_sets" \
-H "Content-type: application/json" \
-d'{
     "sort": {
       "sampling": {
         "order": "desc"
       }
     },
     "size": 10
   }'

if [ "$SKIP_UBI" = false ]; then
  echo
  echo Create Implicit Judgments
  exe curl -s -X PUT "localhost:9200/_plugins/_search_relevance/judgments" \
  -H "Content-type: application/json" \
  -d'{
     	"clickModel": "coec",
      "maxRank": 20,
     	"name": "Implicit Judgements",
     	"type": "UBI_JUDGMENT"
    }'
    
  UBI_JUDGMENT_LIST_ID=`jq -r '.judgment_id' < RES`
  
  # wait for judgments to be created in the background
  sleep 2
fi

echo
echo Import Manaully Curated Judgements
exe curl -s -X PUT "localhost:9200/_plugins/_search_relevance/judgments" \
-H "Content-type: application/json" \
-d'{
   	"name": "Imported Judgments",
   	"description": "Judgments generated outside SRW",
   	"type": "IMPORT_JUDGMENT",
   	"judgmentRatings": {
      "red dress": [
        {
          "docId": "B077ZJXCTS",
          "rating": "0.000"
        },
        {
          "docId": "B071S6LTJJ",
          "rating": "0.000"
        },
        {
          "docId": "B01IDSPDJI",
          "rating": "0.000"
        },
        {
          "docId": "B07QRCGL3G",
          "rating": "0.000"
        },
        {
          "docId": "B074V6Q1DR",
          "rating": "0.000"
        }
      ],
      "blue jeans": [
        {
          "docId": "B07L9V4Y98",
          "rating": "0.000"
        },
        {
          "docId": "B01N0DSRJC",
          "rating": "0.000"
        },
        {
          "docId": "B001CRAWCQ",
          "rating": "0.000"
        },
        {
          "docId": "B075DGJZRM",
          "rating": "0.000"
        },
        {
          "docId": "B009ZD297U",
          "rating": "0.000"
        }
      ]
    }
}' 

IMPORTED_JUDGMENT_LIST_ID=`jq -r '.judgment_id' < RES`

echo
echo Upload ESCI Judgments 

exe curl -s -X PUT "localhost:9200/_plugins/_search_relevance/judgments" \
-H "Content-type: application/json" \
--data-binary @../data-esci/esci_us_judgments.json



ESCI_JUDGMENT_LIST_ID=`jq -r '.judgment_id' < RES`

echo
echo Create PAIRWISE Experiment
exe curl -s -X PUT "localhost:9200/_plugins/_search_relevance/experiments" \
-H "Content-type: application/json" \
-d"{
   	\"querySetId\": \"$QUERY_SET_MANUAL\",
   	\"searchConfigurationList\": [\"$SC_BASELINE\", \"$SC_CHALLENGER\"],
   	\"size\": 10,
   	\"type\": \"PAIRWISE_COMPARISON\"
   }"
   

EX_PAIRWISE=`jq -r '.experiment_id' < RES`

echo
echo Experiment id: $EX_PAIRWISE

echo
echo Show PAIRWISE Experiment
exe curl -s -X GET "localhost:9200/_plugins/_search_relevance/experiments/$EX_PAIRWISE"

echo
echo Create POINTWISE Experiment

exe curl -s -X PUT "localhost:9200/_plugins/_search_relevance/experiments" \
-H "Content-type: application/json" \
-d"{
   	\"querySetId\": \"$QUERY_SET_MANUAL\",
   	\"searchConfigurationList\": [\"$SC_BASELINE\"],
    \"judgmentList\": [\"$IMPORTED_JUDGMENT_LIST_ID\"],
   	\"size\": 8,
   	\"type\": \"POINTWISE_EVALUATION\"
   }"

EX_POINTWISE=`jq -r '.experiment_id' < RES`

echo
echo Experiment id: $EX_POINTWISE

echo
echo Show POINTWISE Experiment
exe curl -s -X GET "localhost:9200/_plugins/_search_relevance/experiments/$EX_POINTWISE"

echo
echo List experiments
exe curl -s -X GET "http://localhost:9200/_plugins/_search_relevance/experiments" \
-H "Content-type: application/json" \
-d'{
     "sort": {
       "timestamp": {
         "order": "desc"
       }
     },
     "size": 3
   }'

   
## BEGIN HYBRID OPTIMIZER DEMO ##
echo
echo
echo BEGIN HYBRID OPTIMIZER DEMO
echo
echo Creating Hybrid Query to be Optimized
exe curl -s -X PUT "http://localhost:9200/_plugins/_search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d'{
      "name": "hybrid_query_1",
      "query": "{\"query\":{\"hybrid\":{\"queries\":[{\"match\":{\"title\":\"%SearchText%\"}},{\"match\":{\"category\":\"%SearchText%\"}}]}}}",
      "index": "ecommerce"
}'

SC_HYBRID=`jq -r '.search_configuration_id' < RES`

echo
echo Hybrid search config id: $SC_HYBRID

echo
echo Create HYBRID OPTIMIZER Experiment

exe curl -s -X PUT "localhost:9200/_plugins/_search_relevance/experiments" \
-H "Content-type: application/json" \
-d"{
   	\"querySetId\": \"$QUERY_SET_MANUAL\",
   	\"searchConfigurationList\": [\"$SC_HYBRID\"],
    \"judgmentList\": [\"$IMPORTED_JUDGMENT_LIST_ID\"],
   	\"size\": 10,
   	\"type\": \"HYBRID_OPTIMIZER\"
  }"

EX_HO=`jq -r '.experiment_id' < RES`

echo
echo Experiment id: $EX_HO

echo
echo Show HYBRID OPTIMIZER Experiment
exe curl -s -X GET localhost:9200/_plugins/_search_relevance/experiments/$EX_HO
