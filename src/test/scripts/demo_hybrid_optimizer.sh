#!/bin/bash -e

# This script quickly sets up a local Search Relevance Workbench from scratch with:
# * An "ecommerce" style sample data
# You can now exercise all the capabilities of Hybrid Optimizer!
#
# Prerequisites: OpenSearch 3.1 or newer with SRW and UBI plugins installed
#
# It will clear out any existing data except ecommerce index if you pass --skip-ecommerce as a parameter.

# Helper script
exe() { (set -x ; "$@") | jq | tee RES; echo; }

# Ansi color code variables
MAJOR='\033[0;34m[HO DEMO] '
RESET='\033[0m' # No Color

# Check for --skip-ecommerce parameter
SKIP_ECOMMERCE=false
for arg in "$@"; do
  if [ "$arg" = "--skip-ecommerce" ]; then
    SKIP_ECOMMERCE=true
  fi
done

echo -e "${MAJOR}Configuring the ML Commons plugin.${RESET}"
curl -s -X PUT "http://localhost:9200/_cluster/settings" -H 'Content-Type: application/json' --data-binary '{
  "persistent": {
        "plugins": {
            "ml_commons": {
                "only_run_on_ml_node": "false",
                "model_access_control_enabled": "true",
                "native_memory_threshold": "99"
            }
        }
    }
}'

echo
echo -e "${MAJOR}Lookup or Register a model group.${RESET}"
response=$(curl -s -X POST "http://localhost:9200/_plugins/_ml/model_groups/_search" \
  -H 'Content-Type: application/json' \
  --data-binary '{
    "query": {
      "bool": {
        "must": [
          {
            "terms": {
              "name": [
                "neural_search_model_group"
              ]
            }
          }
        ]
      }
    }
  }')

# Extract the model_group_id from the JSON response
model_group_id=$(echo "$response" | jq -r '.hits.hits[0]._id')

# Check if model_group_id is blank or "null"
if [ -z "$model_group_id" ] || [ "$model_group_id" = "null" ]; then
  echo "No existing model group found, creating a new one..."
  response=$(curl -s -X POST "http://localhost:9200/_plugins/_ml/model_groups/_register" \
    -H 'Content-Type: application/json' \
    --data-binary '{
      "name": "neural_search_model_group",
      "description": "A model group for neural search models"
    }')

  # Extract the model_group_id from the JSON response
  model_group_id=$(echo "$response" | jq -r '.model_group_id')
  echo "Created Model Group with id: $model_group_id"
else
  echo "Using existing Model Group with id: $model_group_id"
fi

echo -e "${MAJOR}Registering a model in the model group.${RESET}"
response=$(curl -s -X POST "http://localhost:9200/_plugins/_ml/models/_register" \
  -H 'Content-Type: application/json' \
  --data-binary "{
     \"name\": \"huggingface/sentence-transformers/all-MiniLM-L6-v2\",
     \"version\": \"1.0.1\",
     \"model_group_id\": \"$model_group_id\",
     \"model_format\": \"TORCH_SCRIPT\"
  }")

# Extract the task_id from the JSON response
task_id=$(echo "$response" | jq -r '.task_id')

# Use the extracted task_id
echo "Created Model, get status with task id: $task_id"


echo -e "${MAJOR}Waiting for the model to be registered.${RESET}"
max_attempts=10
attempts=0

# Wait for task to be COMPLETED
while [[ "$(curl -s localhost:9200/_plugins/_ml/tasks/$task_id | jq -r '.state')" != "COMPLETED" && $attempts -lt $max_attempts ]]; do
    echo "Waiting for task to complete... attempt $((attempts + 1))/$max_attempts"
    sleep 5
    attempts=$((attempts + 1))
done

if [[ $attempts -ge $max_attempts ]]; then
    echo "Limit of attempts reached. Something went wrong with registering the model. Check OpenSearch logs."
    exit 1
else
    response=$(curl -s localhost:9200/_plugins/_ml/tasks/$task_id)
    model_id=$(echo "$response" | jq -r '.model_id')
    echo "Task completed successfully! Model registered with id: $model_id"
fi

echo -e "${MAJOR}Deploying the model.${RESET}"
response=$(curl -s -X POST "http://localhost:9200/_plugins/_ml/models/$model_id/_deploy")

# Extract the task_id from the JSON response
deploy_task_id=$(echo "$response" | jq -r '.task_id')

echo "Model deployment started, get status with task id: $deploy_task_id"

echo -e "${MAJOR}Waiting for the model to be deployed.${RESET}"
# Reset attempts
attempts=0

while [[ "$(curl -s localhost:9200/_plugins/_ml/tasks/$task_id | jq -r '.state')" != "COMPLETED" && $attempts -lt $max_attempts ]]; do
    echo "Waiting for task to complete... attempt $((attempts + 1))/$max_attempts"
    sleep 5
    attempts=$((attempts + 1))
done

if [[ $attempts -ge $max_attempts ]]; then
    echo "Limit of attempts reached. Something went wrong with deploying the model. Check OpenSearch logs."
else
    response=$(curl -s localhost:9200/_plugins/_ml/tasks/$task_id)
    model_id=$(echo "$response" | jq -r '.model_id')
    echo "Task completed successfully! Model deployed with id: $model_id"
fi

# Check state of deployed model
attempts=0
while [[ "$(curl -s localhost:9200/_plugins/_ml/models/$model_id | jq -r '.model_state')" != "DEPLOYED" && $attempts -lt $max_attempts ]]; do
    echo "Waiting for task to complete... attempt $((attempts + 1))/$max_attempts"
    sleep 5
    attempts=$((attempts + 1))
done

if [[ $attempts -ge $max_attempts ]]; then
    echo "Limit of attempts reached. Something went wrong with deploying the model. Check OpenSearch logs."
else
    echo "Task completed successfully! Model deployment successful with model id: $model_id"
fi

echo -e "${MAJOR}Creating an ingest pipeline for embedding generation during index time.${RESET}"
curl -s -X PUT "http://localhost:9200/_ingest/pipeline/embeddings-pipeline" \
  -H 'Content-Type: application/json' \
  --data-binary "{
     \"description\": \"A text embedding pipeline\",
       \"processors\": [
         {
          \"text_embedding\": {
          \"model_id\": \"$model_id\",
          \"field_map\": {
            \"title\": \"title_embedding\"
          }
        }
      }
    ]
  }"

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

  echo "Creating ecommerce index using predefined schema"

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

QUERY_BODY=$(cat << 'EOF'
{
  "query": {
    "match_all": {}
  },
  "size": 0
}
EOF
)

NUMBER_OF_QUERIES=$(curl -s -XGET "http://localhost:9200/ubi_queries/_search" \
  -H "Content-Type: application/json" \
  -d "${QUERY_BODY}" | jq -r '.hits.total.value')

NUMBER_OF_EVENTS=$(curl -s -XGET "http://localhost:9200/ubi_events/_search" \
  -H "Content-Type: application/json" \
  -d "${QUERY_BODY}" | jq -r '.hits.total.value')

echo
echo "Indexed UBI data: $NUMBER_OF_QUERIES queries and $NUMBER_OF_EVENTS events"

echo
echo Create Query Sets by Sampling UBI Data
exe curl -s -X POST "localhost:9200/_plugins/_search_relevance/query_sets" \
-H "Content-type: application/json" \
-d'{
   	"name": "Top 2 Queries",
   	"description": "The 2 most frequent queries sourced from user searches.",
   	"sampling": "topn",
   	"querySetSize": 2
}'

QUERY_SET_UBI=`jq -r '.query_set_id' < RES`

echo
echo Upload ESCI Query Set 

exe curl -s -X PUT "localhost:9200/_plugins/_search_relevance/query_sets" \
-H "Content-type: application/json" \
--data-binary @../data-esci/esci_us_queryset.json



QUERY_SET_ESCI=`jq -r '.query_set_id' < RES`

echo
echo Create Implicit Judgments
exe curl -s -X PUT "localhost:9200/_plugins/_search_relevance/judgments" \
-H "Content-type: application/json" \
-d'{
   	"clickModel": "coec",
    "maxRank": 50,
   	"name": "Implicit Judgements",
   	"type": "UBI_JUDGMENT"
  }'

UBI_JUDGMENT_LIST_ID=`jq -r '.judgment_id' < RES`

# wait for judgments to be created in the background
sleep 2

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

exe curl -s -X PUT "localhost:9200/_plugins/_search_relevance/judgments" \
-H "Content-type: application/json" \
--data-binary @../data-esci/esci_us_judgments.json



ESCI_JUDGMENT_LIST_ID=`jq -r '.judgment_id' < RES`

echo
echo
echo BEGIN HYBRID OPTIMIZER DEMO
echo
echo Creating Hybrid Query to be Optimized with model $model_id

exe curl -s -X PUT "http://localhost:9200/_plugins/_search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d"{
      \"name\": \"hybrid_query\",
      \"query\": \"{\\\"query\\\":{\\\"hybrid\\\":{\\\"queries\\\":[{\\\"multi_match\\\":{\\\"query\\\":\\\"%SearchText%\\\",\\\"fields\\\":[\\\"id\\\",\\\"title\\\",\\\"category\\\",\\\"bullets\\\",\\\"description\\\",\\\"attrs.Brand\\\",\\\"attrs.Color\\\"]}},{\\\"neural\\\":{\\\"title_embedding\\\":{\\\"query_text\\\":\\\"%SearchText%\\\",\\\"k\\\":100,\\\"model_id\\\":\\\"${model_id}\\\"}}}]}},\\\"size\\\":10}\",
      \"index\": \"ecommerce\"
}"

SC_HYBRID=`jq -r '.search_configuration_id' < RES`

echo
echo Create HYBRID OPTIMIZER Experiment

exe curl -s -X PUT "localhost:9200/_plugins/_search_relevance/experiments" \
-H "Content-type: application/json" \
-d"{
   	\"querySetId\": \"$QUERY_SET_UBI\",
   	\"searchConfigurationList\": [\"$SC_HYBRID\"],
    \"judgmentList\": [\"$UBI_JUDGMENT_LIST_ID\"],
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
