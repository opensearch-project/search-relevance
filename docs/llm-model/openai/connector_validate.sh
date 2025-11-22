#!/bin/bash

# OpenAI GPT Connector Validation Script
set -e

OPENSEARCH_URL="http://localhost:9200"
CONNECTOR_NAME="mfenqin-batch-test"
MODEL_NAME="openai-gpt-model"
OPENAI_API_KEY="<your open ai key>"

echo "Creating OpenAI GPT connector..."

# Create connector
CONNECTOR_RESPONSE=$(curl -s -X POST "${OPENSEARCH_URL}/_plugins/_ml/connectors/_create" \
-H "Content-Type: application/json" \
-d '{
  "name": "'${CONNECTOR_NAME}'",
  "description": "OpenAI GPT connector for search relevance rating",
  "version": "1",
  "protocol": "http",
  "parameters": {
    "endpoint": "api.openai.com",
    "model": "gpt-5-nano"
  },
  "credential": {
    "openAI_key": "'${OPENAI_API_KEY}'"
  },
  "actions": [
    {
      "action_type": "batch_predict",
      "method": "POST",
      "url": "https://${parameters.endpoint}/v1/chat/completions",
      "headers": {
        "Authorization": "Bearer ${credential.openAI_key}"
      },
      "request_body": "{ \"model\": \"${parameters.model}\", \"messages\": ${parameters.messages} }"
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
  "description": "OpenAI GPT model for search relevance rating",
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
        "role": "system",
        "content": "You are an expert search relevance rater. Your task is to evaluate the relevance between search query and results with these criteria:\n- Score 1.0: Perfect match, highly relevant\n- Score 0.7-0.9: Very relevant with minor variations\n- Score 0.4-0.6: Moderately relevant\n- Score 0.1-0.3: Slightly relevant\n- Score 0.0: Completely irrelevant\nEvaluate based on: exact matches, semantic relevance, and overall context between the SearchText and content in Hits.\nWhen a reference is provided, evaluate based on the relevance to both SearchText and its reference.\n\nIMPORTANT: Provide your response ONLY as a JSON array of objects, each with \"id\" and \"rating_score\" fields. You MUST include a rating for EVERY hit provided, even if the rating is 0. Do not include any explanation or additional text. Example format: [{\"id\": \"001\", \"rating_score\": 0.9}, {\"id\": \"002\", \"rating_score\": 0.5}, {\"id\": \"003\", \"rating_score\": 0.0}]"
      },
      {
        "role": "user",
        "content": "SearchText - banana; Reference - banana smoothie; Hits - [{\"_index\": \"sample_index03\", \"_source\": {\"name\": \"banana\", \"price\": 1.99, \"description\": \"this is a banana\"}, \"_id\": \"003\"}, {\"_index\": \"sample_index03\", \"_source\": {\"name\": \"apple\", \"price\": 0.99, \"description\": \"fresh apple\"}, \"_id\": \"004\"}, {\"_index\": \"sample_index03\", \"_source\": {\"name\": \"banana smoothie\", \"price\": 3.99, \"description\": \"fresh banana smoothie\"}, \"_id\": \"005\"}]"
      }
    ]
  }
}')

echo "Search relevance rating test completed!"
echo "Response: $(echo $RATING_RESPONSE | jq -r '.inference_results[0].output[0].dataAsMap.choices[0].message.content')"
echo ""
echo "Connector ID: $CONNECTOR_ID"
echo "Model ID: $MODEL_ID"
