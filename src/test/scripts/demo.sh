#!/bin/sh

# This script quickly sets up a local Search Relevance Workbench from scratch with:
# * User Behavior Insights sample data
# * An "ecommerce" style sample data
# You can now exercise all the capabilities of SRW!  
# 
# It will clear out any existing data except ecommerce index if you pass --skip-ecommerce-step as a parameter.

# Check for --skip-ecommerce-step parameter
SKIP_ECOMMERCE=false
for arg in "$@"; do
  if [ "$arg" = "--skip-ecommerce-step" ]; then
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

  curl -s -X POST "http://localhost:9200/ecommerce/_bulk?pretty" -H 'Content-Type: application/json' --data-binary @transformed_esci_1.json
fi

echo Deleting UBI indexes
(curl -s -X DELETE "http://localhost:9200/ubi_queries" > /dev/null) || true
(curl -s -X DELETE "http://localhost:9200/ubi_events" > /dev/null) || true

echo Creating UBI indexes using mappings
curl -s -X POST http://localhost:9200/_plugins/ubi/initialize

echo Loading sample UBI data
curl  -X POST 'http://localhost:9200/index-name/_bulk?pretty' --data-binary @../data-esci/ubi_queries_events.ndjson -H "Content-Type: application/x-ndjson"

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
echo Indexed UBI data: $NUMBER_OF_QUERIES queries and $NUMBER_OF_EVENTS events

echo

echo Deleting queryset, search config, judgment and experiment indexes
(curl -s -X DELETE "http://localhost:9200/.plugins-search-relevance-search-config" > /dev/null) || true
(curl -s -X DELETE "http://localhost:9200/.plugins-search-relevance-queryset" > /dev/null) || true
(curl -s -X DELETE "http://localhost:9200/.plugins-search-relevance-judgment" > /dev/null) || true
(curl -s -X DELETE "http://localhost:9200/.plugins-search-relevance-experiment" > /dev/null) || true
(curl -s -X DELETE "http://localhost:9200/.plugins-search-relevance-evaluation-result" > /dev/null) || true

sleep 2
echo Create search configs

exe() { (set -x ; "$@") | jq | tee RES; echo; }

exe curl -s -X PUT "http://localhost:9200/_plugins/search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d'{
      "name": "baseline",
      "query": "{\"query\":{\"multi_match\":{\"query\":\"%SearchText%\",\"fields\":[\"id\",\"title\",\"category\",\"bullets\",\"description\",\"attrs.Brand\",\"attrs.Color\"]}}}",
      "index": "ecommerce"
}'

SC_BASELINE=`jq -r '.search_configuration_id' < RES`

exe curl -s -X PUT "http://localhost:9200/_plugins/search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d'{
      "name": "baseline with title weight",
      "query": "{\"query\":{\"multi_match\":{\"query\":\"%SearchText%\",\"fields\":[\"id\",\"title^25\",\"category\",\"bullets\",\"description\",\"attrs.Brand\",\"attrs.Color\"]}}}",
      "index": "ecommerce"
}'

SC_CHALLENGER=`jq -r '.search_configuration_id' < RES`

echo
echo List search configurations
exe curl -s -X GET "http://localhost:9200/_plugins/search_relevance/search_configurations" \
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

echo
echo Create Query Sets
exe curl -s -X POST "localhost:9200/_plugins/search_relevance/query_sets" \
-H "Content-type: application/json" \
-d'{
   	"name": "Top 20",
   	"description": "Top 20 most frequent queries sourced from user searches.",
   	"sampling": "topn",
   	"querySetSize": 20
}'

QS=`jq -r '.query_set_id' < RES`

sleep 2

echo
echo List Query Sets

exe curl -s -X GET "localhost:9200/_plugins/search_relevance/query_sets" \
-H "Content-type: application/json" \
-d'{
     "sort": {
       "sampling": {
         "order": "desc"
       }
     },
     "size": 10
   }'

echo
echo Query Set id: $QS

echo
echo Create Implicit Judgments
exe curl -s -X PUT "localhost:9200/_plugins/search_relevance/judgments" \
-H "Content-type: application/json" \
-d'{
   	"clickModel": "coec",
    "maxRank": 20,
   	"name": "Implicit Judgements",
   	"type": "UBI_JUDGMENT"
   }'
   
JUDGMENT_LIST_ID=`jq -r '.judgment_id' < RES`

# wait for judgments to be created in the background
sleep 5

echo
echo Create PAIRWISE Experiment
exe curl -s -X PUT "localhost:9200/_plugins/search_relevance/experiments" \
-H "Content-type: application/json" \
-d"{
   	\"querySetId\": \"$QS\",
   	\"searchConfigurationList\": [\"$SC_BASELINE\", \"$SC_CHALLENGER\"],
   	\"size\": 10,
   	\"type\": \"PAIRWISE_COMPARISON\"
   }"
   

EX_PAIRWISE=`jq -r '.experiment_id' < RES`

echo
echo Experiment id: $EX_PAIRWISE

echo
echo Show PAIRWISE Experiment
exe curl -s -X GET "localhost:9200/_plugins/search_relevance/experiments/$EX_PAIRWISE"

echo
echo Create POINTWISE Experiment
# TODO the type UBI_EVALUTATION SHOULD BE POINTWISE_EVALUATION
exe curl -s -X PUT "localhost:9200/_plugins/search_relevance/experiments" \
-H "Content-type: application/json" \
-d"{
   	\"querySetId\": \"$QS\",
   	\"searchConfigurationList\": [\"$SC_BASELINE\"],
    \"judgmentList\": [\"$JUDGMENT_LIST_ID\"],
   	\"size\": 8,
   	\"type\": \"UBI_EVALUATION\"
   }"

EX_POINTWISE=`jq -r '.experiment_id' < RES`

echo
echo Experiment id: $EX_POINTWISE

echo
echo Show POINTWISE Experiment
exe curl -s -X GET "localhost:9200/_plugins/search_relevance/experiments/$EX_POINTWISE"

echo
echo List experiments
exe curl -s -X GET "http://localhost:9200/_plugins/search_relevance/experiments" \
-H "Content-type: application/json" \
-d'{
     "sort": {
       "timestamp": {
         "order": "desc"
       }
     },
     "size": 3
   }'
