#!/bin/sh

# This script quickly sets up a local Search Relevance Workbench from scratch with:
# * User Behavior Insights sample data
# * "ecommerce" sample index
# You can now exercise all the capabilities of SRW!  It will clear out any existing data.

# TODO
#  * Switch to the field values used in the runbook, we are using those from the other shell scripts. In particular use ecommerce index and use the query bodies from the runbook

# Once we get remote cluster connection working, we can eliminate this.
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

echo Deleting UBI indexes
(curl -s -X DELETE "http://localhost:9200/ubi_queries" > /dev/null) || true
(curl -s -X DELETE "http://localhost:9200/ubi_events" > /dev/null) || true

echo Creating UBI indexes using mappings
curl -s -X POST http://localhost:9200/_plugins/ubi/initialize

echo Loading sample UBI data
curl  -X POST 'http://localhost:9200/index-name/_bulk?pretty' --data-binary @../data-esci/ubi_queries_events.ndjson -H "Content-Type: application/x-ndjson"


echo Deleting queryset, search config, judgement and experiment indexes
(curl -s -X DELETE "http://localhost:9200/.plugins-search-relevance-search-config" > /dev/null) || true
(curl -s -X DELETE "http://localhost:9200/.plugins-search-relevance-queryset" > /dev/null) || true
(curl -s -X DELETE "http://localhost:9200/.plugins-search-relevance-judgement" > /dev/null) || true
(curl -s -X DELETE "http://localhost:9200/.plugins-search-relevance-experiment" > /dev/null) || true

echo Create search configs

exe() { (set -x ; "$@") | jq | tee RES; echo; }

exe curl -s -X PUT "http://localhost:9200/_plugins/search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d'{
      "name": "baseline",
      "query": "{\"query\": {\n\"match_all\": {}}}",
      "index": "ecommerce"
}' 

SC_BASELINE=`jq -r '.search_configuration_id' < RES`

exe curl -s -X PUT "http://localhost:9200/_plugins/search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d'{
      "name": "multi_match",
      "query": "{\"query\":{\"multi_match\":{\"query\":\"%SearchText%\",\"fields\":[\"id\",\"title\",\"category\",\"bullets\",\"description\",\"attrs.Brand\",\"attrs.Color\"]}}}",
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
   	"name": "test03",
   	"description": "test03",
   	"sampling": "topn",
   	"querySetSize": 20
}'

QS=`jq -r '.query_set_id' < RES`

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
echo Create Experiment
exe curl -s -X PUT "localhost:9200/_plugins/search_relevance/experiments" \
-H "Content-type: application/json" \
-d"{
   	\"querySetId\": \"$QS\",
   	\"searchConfigurationList\": [\"$SC_BASELINE\", \"$SC_CHALLENGER\"],
   	\"size\": 10,
   	\"type\": \"PAIRWISE_COMPARISON\"
   }"

EX=`jq -r '.experiment_id' < RES`

echo
echo Experiment id: $EX

echo
echo Show Experiment
exe curl -s -X GET "localhost:9200/_plugins/search_relevance/experiments/$EX"

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
