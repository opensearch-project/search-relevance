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
if [ "$SKIP_ECOMMERCE" = false ]; then
  echo Deleting ecommerce sample data
  (curl -s -X DELETE "http://localhost:9200/ecommerce" > /dev/null) || true

  # Check if data file exists locally, if not download it
  if [ ! -f "transformed_esci_1.json" ]; then
    echo "Data file not found locally. Downloading from S3..."
    wget https://o19s-public-datasets.s3.amazonaws.com/chorus-opensearch-edition/transformed_esci_1.json
  fi

  echo "Creating ecommerce index using default bulk ingestion schema"

  # Create the index by reading in one doc
  head -n 2 transformed_esci_1.json | curl -s -X POST "http://localhost:9200/index-name/_bulk?pretty" \
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
  head -n 500 transformed_esci_1.json | curl -s -X POST "http://localhost:9200/index-name/_bulk?pretty" \
    -H 'Content-Type: application/x-ndjson' --data-binary @-
  # do all, requires extra RAM to be allocated to OS.
  #curl -s -X POST "http://localhost:9200/ecommerce/_bulk?pretty" -H 'Content-Type: application/json' --data-binary @transformed_esci_1.json
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
