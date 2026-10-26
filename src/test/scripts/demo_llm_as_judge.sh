#!/bin/bash -e

# This script demonstrates using LLM as a Judge.
#
# Prerequisites:
# * OpenSearch 3.4 or newer with SRW plugin installed
# * Either an OPENAI_API_KEY, or a local Ollama server (https://ollama.com)
#   with a model already pulled (e.g. `ollama pull llama3`)
# * The "ecommerce" sample index already set up (run demo.sh or
#   demo_hybrid_optimizer.sh first to create and populate it)
#
# Usage:
#   One of the following two is required:
#   --openai_api_key <key>    Use OpenAI (gpt-3.5-turbo) as the judge model
#   --ollama_model <model>    Use a local Ollama model as the judge (e.g. llama3)
#
# Optional arguments:
#   --ollama_url <url>        Ollama base URL (default: http://localhost:11434)
#   --opensearch_url <url>    OpenSearch base URL (default: http://localhost:9200)

# Helper script
exe() { (set -x ; "$@") | jq | tee RES; echo; }

# Ansi color code variables
MAJOR='\033[0;34m[LLM JUDGE DEMO] '
RESET='\033[0m' # No Color

# Parse command line arguments
OPENAI_API_KEY=""
OLLAMA_MODEL=""
OLLAMA_URL="http://localhost:11434"
OPENSEARCH_URL="http://localhost:9200"

while [[ $# -gt 0 ]]; do
  case $1 in
    --openai_api_key)
      OPENAI_API_KEY="$2"
      shift 2
      ;;
    --ollama_model)
      OLLAMA_MODEL="$2"
      shift 2
      ;;
    --ollama_url)
      OLLAMA_URL="$2"
      shift 2
      ;;
    --opensearch_url)
      OPENSEARCH_URL="$2"
      shift 2
      ;;
    --opensearch_url=*)
      OPENSEARCH_URL="${1#--opensearch_url=}"
      shift
      ;;
    *)
      echo "Unknown option: $1"
      echo "Usage: $0 (--openai_api_key YOUR_API_KEY | --ollama_model MODEL_NAME) [--ollama_url <url>] [--opensearch_url <url>]"
      exit 1
      ;;
  esac
done

# Exactly one judge provider must be selected
if [ -z "$OPENAI_API_KEY" ] && [ -z "$OLLAMA_MODEL" ]; then
  echo "Error: either --openai_api_key or --ollama_model is required"
  echo "Usage: $0 (--openai_api_key YOUR_API_KEY | --ollama_model MODEL_NAME) [--ollama_url <url>] [--opensearch_url <url>]"
  exit 1
fi
if [ -n "$OPENAI_API_KEY" ] && [ -n "$OLLAMA_MODEL" ]; then
  echo "Error: pass only one of --openai_api_key or --ollama_model, not both"
  exit 1
fi


if [ -n "$OLLAMA_MODEL" ]; then
  echo -e "${MAJOR}Configure ML Connector to local Ollama ($OLLAMA_MODEL).${RESET}"

  OLLAMA_HOST=$(echo "$OLLAMA_URL" | sed -E 's#^https?://##')
  OLLAMA_REGEX="^${OLLAMA_URL}/.*\$"

  # A local Ollama server is a private-IP endpoint (e.g. localhost); ml-commons
  # blocks those by default as an SSRF guard, so explicitly allow it here.
  curl -s -XPUT "$OPENSEARCH_URL/_cluster/settings" -H 'Content-Type: application/json' -d"{
      \"persistent\": {
          \"plugins.ml_commons.trusted_connector_endpoints_regex\": [
            \"$OLLAMA_REGEX\"
          ],
          \"plugins.ml_commons.connector.private_ip_enabled\": true
      }
  }"

  # ml-commons requires a non-empty "credential" block even when the
  # endpoint (like a local Ollama server) needs no authentication.
  exe curl -s -XPOST "$OPENSEARCH_URL/_plugins/_ml/connectors/_create" -H 'Content-Type: application/json' -d"{
      \"name\": \"Ollama Chat Connector\",
      \"description\": \"The connector to a local Ollama model service\",
      \"version\": 1,
      \"protocol\": \"http\",
      \"parameters\": {
          \"endpoint\": \"$OLLAMA_HOST\",
          \"model\": \"$OLLAMA_MODEL\"
      },
      \"credential\": {
          \"placeholder\": \"not_required_for_local_ollama\"
      },
      \"actions\": [
          {
              \"action_type\": \"predict\",
              \"method\": \"POST\",
              \"url\": \"http://\${parameters.endpoint}/v1/chat/completions\",
              \"request_body\": \"{ \\\"model\\\": \\\"\${parameters.model}\\\", \\\"messages\\\": \${parameters.messages} }\"
          }
      ]
  }"

  MODEL_NAME="ollama-$OLLAMA_MODEL"
else
  echo -e "${MAJOR}Configure ML Connector to OpenAI.${RESET}"

  curl -s -XPUT "$OPENSEARCH_URL/_cluster/settings" -H 'Content-Type: application/json' -d'
  {
      "persistent": {
          "plugins.ml_commons.trusted_connector_endpoints_regex": [
            "^https://api\\.openai\\.com/.*$"
          ]
      }
  }
  '

  exe curl -s -XPOST "$OPENSEARCH_URL/_plugins/_ml/connectors/_create" -H 'Content-Type: application/json' -d"{
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

  MODEL_NAME="openAI-gpt-3.5-turbo"
fi

CONNECTOR_ID=`jq -r '.connector_id' < RES`

if [ -z "$CONNECTOR_ID" ] || [ "$CONNECTOR_ID" = "null" ]; then
  echo "Error: failed to create ML connector, see response above."
  exit 1
fi

# Register with a fixed custom id so re-running this script is idempotent;
# a prior run's "already exists" (409) is treated as success. This relies on
# https://github.com/opensearch-project/ml-commons/issues/5140, fixed in 3.10.
MODEL_GROUP_ID="search-relevance-llm-judge-model-group"
http_status=$(curl -s -o RES -w '%{http_code}' -XPOST "$OPENSEARCH_URL/_plugins/_ml/model_groups/_register" -H 'Content-Type: application/json' -d"{
  \"model_group_id\": \"$MODEL_GROUP_ID\",
  \"name\": \"remote_model_group\",
  \"description\": \"A model group for external models\"
}")
cat RES | jq

if [ "$http_status" != "200" ] && [ "$http_status" != "409" ]; then
  echo "Error: failed to create model group (HTTP $http_status), see response above."
  exit 1
fi
echo "Using model group with id: $MODEL_GROUP_ID"

exe curl -s -XPOST "$OPENSEARCH_URL/_plugins/_ml/models/_register" -H 'Content-Type: application/json' -d"{
    \"name\": \"$MODEL_NAME\",
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
while [[ "$(curl -s "$OPENSEARCH_URL/_plugins/_ml/tasks/$TASK_ID" | jq -r '.state')" != "COMPLETED" && $attempts -lt $max_attempts ]]; do
    echo "Waiting for task to complete... attempt $((attempts + 1))/$max_attempts"
    sleep 5
    attempts=$((attempts + 1))
done

if [[ $attempts -ge $max_attempts ]]; then
    echo "Limit of attempts reached. Something went wrong with registering the model. Check OpenSearch logs."
    exit 1
else
    response=$(curl -s "$OPENSEARCH_URL/_plugins/_ml/tasks/$TASK_ID")
    model_id=$(echo "$response" | jq -r '.model_id')
    echo "Task completed successfully! Model registered with id: $model_id"
fi

curl -s -XPOST "$OPENSEARCH_URL/_plugins/_ml/models/${MODEL_ID}/_predict" -H 'Content-Type: application/json' -d'
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
echo ""


echo -e "${MAJOR}Configure SRW for LLM Judgments.${RESET}"

curl -s -XPUT "$OPENSEARCH_URL/_cluster/settings" -H 'Content-Type: application/json' -d'
{
  "persistent" : {
    "plugins.search_relevance.workbench_enabled" : true
  }
}
'
echo ""

exe curl -s -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/query_sets" -H 'Content-Type: application/json' -d'{
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


exe curl -s -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/search_configurations" -H 'Content-Type: application/json' -d'{
  "name": "Products Multi-Field Search",
  "description": "Search both title and description fields",
  "index": "ecommerce",
  "query": "{\"query\": {\"multi_match\": {\"query\": \"%queryText%\", \"fields\": [\"title\", \"description\"]}}}"
}'

SEARCH_CONFIGURATION_ID=`jq -r '.search_configuration_id' < RES`


exe curl -s -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/judgments" -H 'Content-Type: application/json' -d'{
  "name": "LLM Judge Demo: SCORE0_1 Custom Template",
  "type": "LLM_JUDGMENT",
  "querySetId": "'"$QUERY_SET_ID"'",
  "searchConfigurationList": ["'"$SEARCH_CONFIGURATION_ID"'"],
  "modelId": "'"$MODEL_ID"'",
  "size": 5,
  "tokenLimit": 4000,
  "contextFields": ["title", "description"],
  "ignoreFailure": false,
  "llmJudgmentRatingType": "SCORE0_1",
  "promptTemplate": "Given the query: {{queryText}}\nCategory: {{category}}\nTarget audience: {{targetAudience}}\nReference: {{referenceAnswer}}\n\nDocuments: {{hits}}Rate the relevance of this document on a scale of 0.0 to 1.0, where 0.0 is completely irrelevant and 1.0 is perfectly relevant."
}'

LLM_JUDGMENT_ID=`jq -r '.judgment_id' < RES`

echo ""
echo "LLM judgment id: $LLM_JUDGMENT_ID"
