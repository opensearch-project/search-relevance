#!/bin/sh

# TODO
#  * Implement list experiments
#  * Implement show a specific experiment
#  * Switch to the field values used in the runbook, we are using those from the other shell scripts. In particular use ecommerce index and use the query bodies from the runbook

echo Deleting queryset, search config and experiment indexes
(curl -s -X DELETE "http://localhost:9200/.plugins-search-relevance-search-config" > /dev/null) || true
(curl -s -X DELETE "http://localhost:9200/.plugins-search-relevance-queryset" > /dev/null) || true
(curl -s -X DELETE "http://localhost:9200/.plugins-search-relevance-experiment" > /dev/null) || true

echo Create search configs

exe() { (set -x ; "$@") | jq | tee RES; echo; }

exe curl -s -X PUT "http://localhost:9200/_plugins/search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d'{
      "name": "baseline",
      "queryBody": "{\"multi_match\":{\"query\":\"%SearchText%\",\"fields\":[\"id\",\"title\",\"category\",\"bullets\",\"description\",\"attrs.Brand\",\"attrs.Color\"]}}",
      "index": "ecommerce"
}' 

SC_BASELINE=`jq -r '.search_configuration_id' < RES`

exe curl -s -X PUT "http://localhost:9200/_plugins/search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d'{
      "name": "multi_match",
      "queryBody": "{\"multi_match\":{\"query\":\"%SearchText%\",\"fields\":[\"id\",\"title^25\",\"category\",\"bullets\",\"description\",\"attrs.Brand\",\"attrs.Color\"]}}",
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
   	"sampling": "random",
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

# echo
# echo List experiments
# exe curl -s -X GET "http://localhost:9200/_plugins/search_relevance/experiments" \
# -H "Content-type: application/json" \
# -d'{
#      "sort": {
#        "timestamp": {
#          "order": "desc"
#        }
#      },
#      "size": 3
#    }'
