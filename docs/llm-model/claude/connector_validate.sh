#!/bin/bash

# Claude 3.5 Haiku Connector Validation Script
set -e

OPENSEARCH_URL="http://localhost:9200"
CONNECTOR_NAME="claude-3-5-haiku-working"
MODEL_NAME="claude-3-5-haiku-working-model"

# Get AWS credentials
export AWS_ACCESS_KEY_ID=$(aws configure get aws_access_key_id)
export AWS_SECRET_ACCESS_KEY=$(aws configure get aws_secret_access_key)
export AWS_SESSION_TOKEN=$(aws configure get aws_session_token)

echo "Creating Claude 3.5 Haiku connector..."

# Create connector
CONNECTOR_RESPONSE=$(curl -s -X POST "${OPENSEARCH_URL}/_plugins/_ml/connectors/_create" \
-H "Content-Type: application/json" \
-d '{
  "name": "'${CONNECTOR_NAME}'",
  "description": "Claude 3.5 Haiku connector for search relevance rating",
  "version": 1,
  "protocol": "aws_sigv4",
  "credential": {
    "access_key": "'$AWS_ACCESS_KEY_ID'",
    "secret_key": "'$AWS_SECRET_ACCESS_KEY'",
    "session_token": "'$AWS_SESSION_TOKEN'"
  },
  "parameters": {
    "region": "us-east-1",
    "service_name": "bedrock",
    "model": "us.anthropic.claude-3-5-haiku-20241022-v1:0"
  },
  "client_config": {
    "max_connection": 1,
    "connection_timeout": 60000,
    "read_timeout": 60000,
    "retry_backoff_millis": 3000,
    "retry_timeout_seconds": 60,
    "max_retry_times": 2
  },
  "actions": [
    {
      "action_type": "predict",
      "method": "POST",
      "headers": {
        "content-type": "application/json"
      },
      "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
      "request_body": "{\"anthropic_version\": \"bedrock-2023-05-31\", \"max_tokens\": 2000, \"messages\": ${parameters.messages}}"
    }
  ]
}')

CONNECTOR_ID=$(echo $CONNECTOR_RESPONSE | jq -r '.connector_id')
echo "Connector created with ID: $CONNECTOR_ID"

# Register model
echo "Registering model..."
MODEL_RESPONSE=$(curl -s -X POST "${OPENSEARCH_URL}/_plugins/_ml/models/_register" \
-H "Content-Type: application/json" \
-d '{
  "name": "'${MODEL_NAME}'",
  "function_name": "remote",
  "description": "Claude 3.5 Haiku model for search relevance rating",
  "connector_id": "'$CONNECTOR_ID'"
}')

MODEL_ID=$(echo $MODEL_RESPONSE | jq -r '.model_id')
echo "Model registered with ID: $MODEL_ID"

# Deploy model
echo "Deploying model..."
curl -s -X POST "${OPENSEARCH_URL}/_plugins/_ml/models/${MODEL_ID}/_deploy" > /dev/null
echo "Model deployed successfully"

# Test prediction
echo "Testing prediction..."
TEST_RESPONSE=$(curl -s -X POST "${OPENSEARCH_URL}/_plugins/_ml/models/${MODEL_ID}/_predict" \
-H "Content-Type: application/json" \
-d '{
  "parameters": {
    "messages": [
      {
        "role": "user",
        "content": [
          {
            "type": "text",
            "text": "Rate search relevance 0.0-1.0. Return JSON only: [{\"id\":\"001\",\"rating_score\":0.9}]. Rate ALL hits.\n\nSearchText - banana; Reference - banana smoothie; Hits - [{\"_index\": \"sample_index03\", \"_source\": {\"name\": \"banana\", \"price\": 1.99, \"description\": \"this is a banana\"}, \"_id\": \"003\"}, {\"_index\": \"sample_index03\", \"_source\": {\"name\": \"apple\", \"price\": 0.99, \"description\": \"fresh apple\"}, \"_id\": \"004\"}, {\"_index\": \"sample_index03\", \"_source\": {\"name\": \"banana smoothie\", \"price\": 3.99, \"description\": \"fresh banana smoothie\"}, \"_id\": \"005\"}]"
          }
        ]
      }
    ]
  }
}')

echo "Test completed successfully!"
echo "Response: $(echo $TEST_RESPONSE | jq -r '.inference_results[0].output[0].dataAsMap.content[0].text')"
echo ""
echo "Connector ID: $CONNECTOR_ID"
echo "Model ID: $MODEL_ID"