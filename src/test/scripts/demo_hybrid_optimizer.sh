#!/bin/sh

# This script quickly sets up a local Search Relevance Workbench from scratch with:
# * An "ecommerce" style sample data
# You can now exercise all the capabilities of Hybrid Optimizer!  
# 
# There are two ways to start:
# 
# 2) `./gradlew run --preserve-data --debug-jvm` which faciliates debugging.
# 
# It will clear out any existing data except ecommerce index if you pass --skip-ecommerce as a parameter.

# Helper script
exe() { (set -x ; "$@") | jq | tee RES; echo; }

# Check for --skip-ecommerce parameter
SKIP_ECOMMERCE=false
for arg in "$@"; do
  if [ "$arg" = "--skip-ecommerce" ]; then
    SKIP_ECOMMERCE=true
  fi
done


# Once we get remote cluster connection working, we can eliminate this.
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


  echo
  echo Populating ecommerce index
  # do 250 products
  #head -n 500 ../esci_us/esci_us_opensearch.json | curl -s -X POST "http://localhost:9200/index-name/_bulk" \
  #  -H 'Content-Type: application/x-ndjson' --data-binary @-
  # 
   
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
echo Import Judgements
exe curl -s -X PUT "localhost:9200/_plugins/_search_relevance/judgments" \
-H "Content-type: application/json" \
-d'{
   	"name": "Imported Judgments",
   	"description": "Judgments generated outside SRW",
   	"type": "IMPORT_JUDGMENT",
   	"judgmentScores": {
      "red dress": [
        {
          "docId": "B077ZJXCTS",
          "score": "0.000"
        },
        {
          "docId": "B071S6LTJJ",
          "score": "0.000"
        },
        {
          "docId": "B01IDSPDJI",
          "score": "0.000"
        },
        {
          "docId": "B07QRCGL3G",
          "score": "0.000"
        },
        {
          "docId": "B074V6Q1DR",
          "score": "0.000"
        }
      ],
      "blue jeans": [
        {
          "docId": "B07L9V4Y98",
          "score": "0.000"
        },
        {
          "docId": "B01N0DSRJC",
          "score": "0.000"
        },
        {
          "docId": "B001CRAWCQ",
          "score": "0.000"
        },
        {
          "docId": "B075DGJZRM",
          "score": "0.000"
        },
        {
          "docId": "B009ZD297U",
          "score": "0.000"
        }
      ]
    }
}' 

IMPORTED_JUDGMENT_LIST_ID=`jq -r '.judgment_id' < RES`

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

echo
echo Upload ESCI Judgments 

# TODO Fix the bug the we arne't creating the judgement index using our defintion in the api
curl -s -X PUT "http://localhost:9200/search-relevance-judgment/_settings" \
-H "Content-type: application/json" \
-d'{
  "index.mapping.total_fields.limit": 20000
}'

exe curl -s -X PUT "localhost:9200/_plugins/_search_relevance/judgments" \
-H "Content-type: application/json" \
--data-binary @../data-esci/esci_us_judgments.json



ESCI_JUDGMENT_LIST_ID=`jq -r '.judgment_id' < RES`

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

echo
echo Expand total fields limit for SRW indexes 

# TODO Fix the bug the we need to increase the number of fields due to our use of dynamice field
curl -s -X PUT "http://localhost:9200/.plugins-search-relevance-experiment/_settings" \
-H "Content-type: application/json" \
-d'{
  "index.mapping.total_fields.limit": 20000
}'

curl -s -X PUT "http://localhost:9200/search-relevance-judgment/_settings" \
-H "Content-type: application/json" \
-d'{
  "index.mapping.total_fields.limit": 20000
}'

curl -s -X PUT "http://localhost:9200/search-relevance-experiment-variant/_settings" \
-H "Content-type: application/json" \
-d'{
  "index.mapping.total_fields.limit": 20000
}'
