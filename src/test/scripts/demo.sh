#!/bin/sh

# This script quickly sets up a local Search Relevance Workbench from scratch with:
# * User Behavior Insights sample data
# * An "ecommerce" style sample data
# You can now exercise the Single Query comparison, Query Set Comparison and Search Evaluation experiments of SRW!  
# 
# This assumes you started OpenSearch from the root via 
# ./gradlew run --preserve-data --debug-jvm which faciliates debugging
# 
# It will clear out any existing indexes, except ecommerce index if you pass --skip-ecommerce as a parameter.
#
# Optional arguments:
#   --skip-ecommerce          Skip deleting and re-loading the ecommerce index
#   --opensearch_url <url>    OpenSearch base URL (default: http://localhost:9200)

# Resolve the directory this script lives in so data paths work from any working directory
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DATA_DIR="$SCRIPT_DIR/../data-esci"

# Helper script
exe() { (set -x ; "$@") | jq | tee RES; echo; }

# Defaults
OPENSEARCH_URL="http://localhost:9200"
SKIP_ECOMMERCE=false

# Parse arguments
while [ $# -gt 0 ]; do
  case "$1" in
    --skip-ecommerce)
      SKIP_ECOMMERCE=true
      ;;
    --opensearch_url)
      OPENSEARCH_URL="$2"
      shift
      ;;
    --opensearch_url=*)
      OPENSEARCH_URL="${1#--opensearch_url=}"
      ;;
  esac
  shift
done


# Once we get remote cluster connection working, we can potentially eliminate this.
if [ "$SKIP_ECOMMERCE" = false ]; then
  echo "Deleting ecommerce sample data"
  (curl -s -X DELETE "$OPENSEARCH_URL/ecommerce" > /dev/null) || true

  ECOMMERCE_ZIP_FILE="$DATA_DIR/esci_us_ecommerce_shrunk.ndjson.zip"

  echo "Creating ecommerce index using schema.json"
  curl -s -X PUT "$OPENSEARCH_URL/ecommerce" \
    -H 'Content-Type: application/json' \
    --data-binary @"$DATA_DIR/schema.json"

  echo ""
  echo "Populating ecommerce index"
  
  # Index all data directly from zip file
  unzip -p "$ECOMMERCE_ZIP_FILE" | curl -s -o /dev/null -w "%{http_code}" -X POST "$OPENSEARCH_URL/ecommerce/_bulk" \
    -H 'Content-Type: application/x-ndjson' --data-binary @-
  
  echo "All data indexed successfully"
fi

echo "Deleting UBI indexes"
(curl -s -X DELETE "$OPENSEARCH_URL/ubi_queries" > /dev/null) || true
(curl -s -X DELETE "$OPENSEARCH_URL/ubi_events" > /dev/null) || true

echo "Creating UBI indexes using mappings"
curl -s -X POST "$OPENSEARCH_URL/_plugins/ubi/initialize"

echo "Loading sample UBI data"
curl -o /dev/null -X POST "$OPENSEARCH_URL/index-name/_bulk?pretty" --data-binary @"$DATA_DIR/ubi_queries_events.ndjson" -H "Content-Type: application/x-ndjson"

echo "Refreshing UBI indexes to make indexed data available for query sampling"
curl -XPOST "$OPENSEARCH_URL/ubi_queries/_refresh"
echo ""
curl -XPOST "$OPENSEARCH_URL/ubi_events/_refresh"

read -r -d '' QUERY_BODY << EOF
{
  "query": {
    "match_all": {}
  },
  "size": 0
}
EOF

NUMBER_OF_QUERIES=$(curl -s -XGET "$OPENSEARCH_URL/ubi_queries/_search" \
  -H "Content-Type: application/json" \
  -d "${QUERY_BODY}" | jq -r '.hits.total.value')

NUMBER_OF_EVENTS=$(curl -s -XGET "$OPENSEARCH_URL/ubi_events/_search" \
  -H "Content-Type: application/json" \
  -d "${QUERY_BODY}" | jq -r '.hits.total.value')
  
echo ""
echo "Indexed UBI data: $NUMBER_OF_QUERIES queries and $NUMBER_OF_EVENTS events"

echo ""

curl -XPUT "$OPENSEARCH_URL/_cluster/settings" -H 'Content-Type: application/json' -d'
{
  "persistent" : {
    "plugins.search_relevance.workbench_enabled" : true
  }
}
'

echo "Deleting queryset, search config, judgment and experiment indexes"
(curl -s -X DELETE "$OPENSEARCH_URL/search-relevance-search-config" > /dev/null) || true
(curl -s -X DELETE "$OPENSEARCH_URL/search-relevance-queryset" > /dev/null) || true
(curl -s -X DELETE "$OPENSEARCH_URL/search-relevance-judgment" > /dev/null) || true
(curl -s -X DELETE "$OPENSEARCH_URL/.plugins-search-relevance-experiment" > /dev/null) || true
(curl -s -X DELETE "$OPENSEARCH_URL/search-relevance-evaluation-result" > /dev/null) || true
(curl -s -X DELETE "$OPENSEARCH_URL/search-relevance-experiment-variant" > /dev/null) || true

sleep 2
echo "Create search configs"



exe curl -s -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d'{
      "name": "baseline",
      "query": "{\"query\":{\"multi_match\":{\"query\":\"%SearchText%\",\"fields\":[\"asin\",\"title\",\"category\",\"bullet_points\",\"description\",\"brand\",\"color\"]}}}",
      "index": "ecommerce"
}'

SC_BASELINE=`jq -r '.search_configuration_id' < RES`

exe curl -s -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d'{
      "name": "baseline with title weight",
      "query": "{\"query\":{\"multi_match\":{\"query\":\"%SearchText%\",\"fields\":[\"asin\",\"title^25\",\"category\",\"bullet_points\",\"description\",\"brand\",\"color\"]}}}",
      "index": "ecommerce"
}'

SC_CHALLENGER=`jq -r '.search_configuration_id' < RES`

echo ""
echo "List search configurations"
exe curl -s -X GET "$OPENSEARCH_URL/_plugins/_search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d'{
     "sort": {
       "timestamp": {
         "order": "desc"
       }
     },
     "size": 10
   }'

echo ""
echo "Baseline search config id: $SC_BASELINE"
echo "Challenger search config id: $SC_CHALLENGER"

echo ""
echo "Create Query Sets by Sampling UBI Data"
exe curl -s -X POST "$OPENSEARCH_URL/_plugins/_search_relevance/query_sets" \
-H "Content-type: application/json" \
-d'{
   	"name": "Top 20",
   	"description": "Top 20 most frequent queries sourced from user searches.",
   	"sampling": "topn",
   	"querySetSize": 20
}'

QUERY_SET_UBI=`jq -r '.query_set_id' < RES`

sleep 2

echo ""
echo "Upload Manually Curated Query Set"

exe curl -s -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/query_sets" \
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

echo ""
echo "Upload ESCI Query Set"

exe curl -s -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/query_sets" \
-H "Content-type: application/json" \
--data-binary @"$DATA_DIR/esci_us_queryset.json"



QUERY_SET_ESCI=`jq -r '.query_set_id' < RES`

echo ""
echo "List Query Sets"

exe curl -s -X GET "$OPENSEARCH_URL/_plugins/_search_relevance/query_sets" \
-H "Content-type: application/json" \
-d'{
     "sort": {
       "sampling": {
         "order": "desc"
       }
     },
     "size": 10
   }'

echo ""
echo "Create Implicit Judgments"
exe curl -s -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/judgments" \
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

echo ""
echo "Import Manually Curated Judgements"
exe curl -s -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/judgments" \
-H "Content-type: application/json" \
-d'{
    "name": "Imported Judgments",
    "description": "Judgments generated outside SRW",
    "type": "IMPORT_JUDGMENT",
    "judgmentRatings": [
        {
            "query": "red dress",
            "ratings": [
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
            ]
        },
        {
            "query": "blue jeans",
            "ratings": [
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
    ]
}'

IMPORTED_JUDGMENT_LIST_ID=`jq -r '.judgment_id' < RES`

echo ""
echo "Upload ESCI Judgments"

exe curl -s -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/judgments" \
-H "Content-type: application/json" \
--data-binary @"$DATA_DIR/esci_us_judgments.json"



ESCI_JUDGMENT_LIST_ID=`jq -r '.judgment_id' < RES`

echo ""
echo "Create PAIRWISE Experiment"
exe curl -s -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/experiments" \
-H "Content-type: application/json" \
-d"{
   	\"querySetId\": \"$QUERY_SET_MANUAL\",
   	\"searchConfigurationList\": [\"$SC_BASELINE\", \"$SC_CHALLENGER\"],
   	\"size\": 10,
   	\"type\": \"PAIRWISE_COMPARISON\"
   }"
   

EX_PAIRWISE=`jq -r '.experiment_id' < RES`

echo ""
echo "Experiment id: $EX_PAIRWISE"

echo ""
echo "Show PAIRWISE Experiment"
exe curl -s -X GET "$OPENSEARCH_URL/_plugins/_search_relevance/experiments/$EX_PAIRWISE"

echo ""
echo "Create POINTWISE Experiment"

exe curl -s -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/experiments" \
-H "Content-type: application/json" \
-d"{
   	\"querySetId\": \"$QUERY_SET_MANUAL\",
   	\"searchConfigurationList\": [\"$SC_BASELINE\"],
    \"judgmentList\": [\"$IMPORTED_JUDGMENT_LIST_ID\"],
   	\"size\": 8,
   	\"type\": \"POINTWISE_EVALUATION\"
   }"

EX_POINTWISE=`jq -r '.experiment_id' < RES`

echo ""
echo "Experiment id: $EX_POINTWISE"

echo ""
echo "Show POINTWISE Experiment"
exe curl -s -X GET "$OPENSEARCH_URL/_plugins/_search_relevance/experiments/$EX_POINTWISE"

echo ""
echo "List experiments"
exe curl -s -X GET "$OPENSEARCH_URL/_plugins/_search_relevance/experiments" \
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
echo ""
echo ""
echo "BEGIN HYBRID OPTIMIZER DEMO"
echo ""
echo "Creating Hybrid Query to be Optimized"
exe curl -s -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d'{
      "name": "hybrid_query_1",
      "query": "{\"query\":{\"hybrid\":{\"queries\":[{\"match\":{\"title\":\"%SearchText%\"}},{\"match\":{\"category\":\"%SearchText%\"}}]}}}",
      "index": "ecommerce"
}'

SC_HYBRID=`jq -r '.search_configuration_id' < RES`

echo ""
echo "Hybrid search config id: $SC_HYBRID"

echo ""
echo "Create HYBRID OPTIMIZER Experiment"

exe curl -s -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/experiments" \
-H "Content-type: application/json" \
-d"{
   	\"querySetId\": \"$QUERY_SET_MANUAL\",
   	\"searchConfigurationList\": [\"$SC_HYBRID\"],
    \"judgmentList\": [\"$IMPORTED_JUDGMENT_LIST_ID\"],
   	\"size\": 10,
   	\"type\": \"HYBRID_OPTIMIZER\"
  }"

EX_HO=`jq -r '.experiment_id' < RES`

echo ""
echo "Experiment id: $EX_HO"

echo ""
echo "Show HYBRID OPTIMIZER Experiment"
exe curl -s -X GET "$OPENSEARCH_URL/_plugins/_search_relevance/experiments/$EX_HO"