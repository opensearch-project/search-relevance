#!/bin/bash -e

# This script demonstrates using LLM as a Judge.
# 
# Prerequisites: 
# * OpenSearch 3.4 or newer with SRW plugins installed 
# * An OPENAI_API_KEY
# * The "ecommerce" sample index setup.
#
# It will clear out any existing data except ecommerce index if you pass --skip-ecommerce as a parameter.

# Helper script
exe() { (set -x ; "$@") | jq | tee RES; echo; }

# Ansi color code variables
MAJOR='\033[0;34m[HO DEMO] '
RESET='\033[0m' # No Color

# Parse command line arguments
OPENAI_API_KEY=""

while [[ $# -gt 0 ]]; do
  case $1 in
    --openai_api_key)
      OPENAI_API_KEY="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      echo "Usage: $0 [--skip-ecommerce] [--openai_api_key YOUR_API_KEY]"
      exit 1
      ;;
  esac
done

# Check if OpenAI key is provided
if [ -z "$OPENAI_API_KEY" ]; then
  echo "Error: --openai_api_key parameter is required"
  echo "Usage: $0 --openai_api_key YOUR_API_KEY"
  exit 1
fi


echo -e "${MAJOR}Configure ML Connector to OpenAI.${RESET}"

curl -XPUT "http://localhost:9200/_cluster/settings" -H 'Content-Type: application/json' -d'
{
    "persistent": {
        "plugins.ml_commons.trusted_connector_endpoints_regex": [
          "^https://api\\.openai\\.com/.*$"
        ]
    }
}
'

exe curl -XPOST "http://localhost:9200/_plugins/_ml/connectors/_create" -H 'Content-Type: application/json' -d"{
    \"name\": \"OpenAI Chat Connector\",
    \"description\": \"The connector to public OpenAI model service for GPT 3.5\",
    \"version\": 1,
    \"protocol\": \"http\",
    \"parameters\": {
        \"endpoint\": \"api.openai.com\",
        \"model\": \"gpt-3.5-turbo\"
    },
    \"credential\": {
        \"openAI_key\": \"$OPENAI_API_KEY\"
    },
    \"actions\": [
        {
            \"action_type\": \"predict\",
            \"method\": \"POST\",
            \"url\": \"https://\${parameters.endpoint}/v1/chat/completions\",
            \"headers\": {
                \"Authorization\": \"Bearer \${credential.openAI_key}\"
            },
            \"request_body\": \"{ \\\"model\\\": \\\"\${parameters.model}\\\", \\\"messages\\\": \${parameters.messages} }\"
        }
    ]
}"

CONNECTOR_ID=`jq -r '.connector_id' < RES`

exe curl -XPOST "http://localhost:9200/_plugins/_ml/model_groups/_register" -H 'Content-Type: application/json' -d'
{
  "name": "remote_model_group",
  "description": "A model group for external models"
}
'

MODEL_GROUP_ID=`jq -r '.model_group_id' < RES`

exe curl -XPOST "http://localhost:9200/_plugins/_ml/models/_register" -H 'Content-Type: application/json' -d"{
    \"name\": \"openAI-gpt-3.5-turbo\",
    \"function_name\": \"remote\",
    \"model_group_id\": \"$MODEL_GROUP_ID\",
    \"description\": \"test model\",
    \"connector_id\": \"$CONNECTOR_ID\"
}"

TASK_ID=`jq -r '.task_id' < RES`
MODEL_ID=`jq -r '.model_id' < RES`


echo -e "${MAJOR}Waiting for the model to be registered.${RESET}"
max_attempts=10
attempts=0

# Wait for task to be COMPLETED
while [[ "$(curl -s localhost:9200/_plugins/_ml/tasks/$TASK_ID | jq -r '.state')" != "COMPLETED" && $attempts -lt $max_attempts ]]; do
    echo "Waiting for task to complete... attempt $((attempts + 1))/$max_attempts"
    sleep 5
    attempts=$((attempts + 1))
done

if [[ $attempts -ge $max_attempts ]]; then
    echo "Limit of attempts reached. Something went wrong with registering the model. Check OpenSearch logs."
    exit 1
else
    response=$(curl -s localhost:9200/_plugins/_ml/tasks/$TASK_ID)
    model_id=$(echo "$response" | jq -r '.model_id')
    echo "Task completed successfully! Model registered with id: $model_id"
fi

curl -XPOST "http://localhost:9200/_plugins/_ml/models/${MODEL_ID}/_predict" -H 'Content-Type: application/json' -d'
{
  "parameters": {
    "messages": [
      {
        "role": "system",
        "content": "You are a helpful assistant."
      },
      {
        "role": "user",
        "content": "Hello!"
      }
    ]
  }
}
'


echo -e "${MAJOR}Configure SRW for LLM Judgements.${RESET}"

exe curl -X PUT "http://localhost:9200/_plugins/_search_relevance/query_sets" -H 'Content-Type: application/json' -d'{
  "name": "Laptop Queries",
  "description": "Query set for testing LLM judgment with custom fields",
  "querySetQueries": [
    {
      "queryText": "laptop for developers",
      "category": "Electronics",
      "targetAudience": "professionals",
      "referenceAnswer": "A portable computer suitable for software development"
    },
    {
      "queryText": "coffee machine",
      "category": "kitchen & dining",
      "targetAudience": "home users",
      "referenceAnswer": "An appliance for brewing coffee at home"
    }
  ]
}'

QUERY_SET_ID=`jq -r '.query_set_id' < RES`


exe curl -X PUT "http://localhost:9200/_plugins/_search_relevance/search_configurations" -H 'Content-Type: application/json' -d'{
  "name": "Products Multi-Field Search",
  "description": "Search both name and description fields",
  "index": "test_products",
  "query": "{\"query\": {\"multi_match\": {\"query\": \"%SearchText%\", \"fields\": [\"name\", \"description\"]}}}"
}'

SEARCH_CONFIGURATION_ID=`jq -r '.search_configuration_id' < RES`


curl -X PUT "http://localhost:9200/_plugins/_search_relevance/judgments" -H 'Content-Type: application/json' -d'{
  "name": "Test Eric: GPT-4 SCORE0_1 Custom Template",
  "type": "LLM_JUDGMENT",
  "querySetId": "'"$QUERY_SET_ID"'",
  "searchConfigurationList": ["'"$SEARCH_CONFIGURATION_ID"'"],
  "modelId": "'"$MODEL_ID"'",
  "size": 5,
  "tokenLimit": 4000,
  "contextFields": ["name", "description"],
  "ignoreFailure": false,
  "llmJudgmentRatingType": "SCORE0_1",
  "promptTemplate": "Given the query: {{queryText}}\nCategory: {{category}}\nTarget audience: {{targetAudience}}\nReference: {{referenceAnswer}}\n\nDocuments: {{hits}}Rate the relevance of this document on a scale of 0.0 to 1.0, where 0.0 is completely irrelevant and 1.0 is perfectly relevant.",
  "overwriteCache": false
}'


https://docs.opensearch.org/latest/data-prepper/
