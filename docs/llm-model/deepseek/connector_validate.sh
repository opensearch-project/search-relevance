#!/bin/bash

# DeepSeek Chat Connector Validation Script
set -e

OPENSEARCH_URL="http://localhost:9200"
CONNECTOR_NAME="DeepSeek Chat"
MODEL_NAME="deepseek-chat-model"
DEEPSEEK_API_KEY="<your deep seek key>"

echo "Creating DeepSeek Chat connector..."

# Create connector
CONNECTOR_RESPONSE=$(curl -s -X POST "${OPENSEARCH_URL}/_plugins/_ml/connectors/_create" \
-H "Content-Type: application/json" \
-d "{
  \"name\": \"${CONNECTOR_NAME}\",
  \"description\": \"DeepSeek Chat connector for conversational AI\",
  \"version\": \"1\",
  \"protocol\": \"http\",
  \"parameters\": {
    \"endpoint\": \"api.deepseek.com\",
    \"model\": \"deepseek-chat\"
  },
  \"credential\": {
    \"deepSeek_key\": \"${DEEPSEEK_API_KEY}\"
  },
  \"actions\": [
    {
      \"action_type\": \"predict\",
      \"method\": \"POST\",
      \"url\": \"https://\${parameters.endpoint}/v1/chat/completions\",
      \"headers\": {
        \"Content-Type\": \"application/json\",
        \"Authorization\": \"Bearer \${credential.deepSeek_key}\"
      },
      \"request_body\": \"{ \\\"model\\\": \\\"\${parameters.model}\\\", \\\"messages\\\": \${parameters.messages} }\"
    }
  ]
}")

CONNECTOR_ID=$(echo $CONNECTOR_RESPONSE | jq -r '.connector_id')
echo "Connector created with ID: $CONNECTOR_ID"

# Register model
echo "Registering model..."
MODEL_RESPONSE=$(curl -s -X POST "${OPENSEARCH_URL}/_plugins/_ml/models/_register" \
-H "Content-Type: application/json" \
-d '{
  "name": "'${MODEL_NAME}'",
  "function_name": "remote",
  "description": "DeepSeek chat model for conversational AI",
  "connector_id": "'$CONNECTOR_ID'"
}')

MODEL_ID=$(echo $MODEL_RESPONSE | jq -r '.model_id')
echo "Model registered with ID: $MODEL_ID"

# Deploy model
echo "Deploying model..."
curl -s -X POST "${OPENSEARCH_URL}/_plugins/_ml/models/${MODEL_ID}/_deploy" > /dev/null
echo "Model deployed successfully"

# Test basic chat
echo "Testing basic chat..."
CHAT_RESPONSE=$(curl -s -X POST "${OPENSEARCH_URL}/_plugins/_ml/models/${MODEL_ID}/_predict" \
-H "Content-Type: application/json" \
-d '{
  "parameters": {
    "messages": [
      {
        "role": "user",
        "content": "Hello, respond with just the word success"
      }
    ]
  }
}')

echo "Basic chat test completed!"
echo "Response: $(echo $CHAT_RESPONSE | jq -r '.inference_results[0].output[0].dataAsMap.choices[0].message.content')"

# Test search relevance rating
echo "Testing search relevance rating..."
RATING_RESPONSE=$(curl -s -X POST "${OPENSEARCH_URL}/_plugins/_ml/models/${MODEL_ID}/_predict" \
-H "Content-Type: application/json" \
-d '{
  "parameters": {
    "messages": [
      {
        "role": "user",
        "content": "Rate search relevance 0.0-1.0. Return JSON only: [{\"id\":\"001\",\"rating_score\":0.9}]. Rate ALL hits.\n\nSearchText - banana; Reference - banana smoothie; Hits - [{\"_index\": \"sample_index03\", \"_source\": {\"name\": \"banana\", \"price\": 1.99, \"description\": \"this is a banana\"}, \"_id\": \"003\"}, {\"_index\": \"sample_index03\", \"_source\": {\"name\": \"apple\", \"price\": 0.99, \"description\": \"fresh apple\"}, \"_id\": \"004\"}, {\"_index\": \"sample_index03\", \"_source\": {\"name\": \"banana smoothie\", \"price\": 3.99, \"description\": \"fresh banana smoothie\"}, \"_id\": \"005\"}]"
      }
    ]
  }
}')

echo "Search relevance rating test completed!"
echo "Response: $(echo $RATING_RESPONSE | jq -r '.inference_results[0].output[0].dataAsMap.choices[0].message.content')"
echo ""
echo "Connector ID: $CONNECTOR_ID"
echo "Model ID: $MODEL_ID"