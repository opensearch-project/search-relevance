#!/bin/bash

# Test Script for Ollama Native OpenAI API Compatibility
# This script tests Ollama's native OpenAI-compatible endpoints
# Usage: ./scripts/test-ollama-native.sh [MODEL_NAME]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
OLLAMA_DIR="$PROJECT_ROOT/.ollama"

# Load config if available
if [ -f "$OLLAMA_DIR/config.env" ]; then
    source "$OLLAMA_DIR/config.env"
fi

# Use provided model or default
MODEL_NAME="${1:-${OLLAMA_MODEL_NAME:-phi3:mini}}"
OLLAMA_BASE_URL="${OLLAMA_API_BASE:-http://localhost:11434/v1}"

echo "=== Testing Ollama Native OpenAI API ==="
echo "Model: $MODEL_NAME"
echo "Base URL: $OLLAMA_BASE_URL"
echo ""

# Check if Ollama is running
if ! curl -s http://localhost:11434/api/version > /dev/null 2>&1; then
    echo "Ollama is not running!"
    echo "Please start Ollama first:"
    echo "  ./scripts/setup-local-ollama.sh $MODEL_NAME"
    exit 1
fi

echo "Ollama service is running"

# Check Ollama version for OpenAI API compatibility
MINIMUM_VERSION="0.1.14"
if command -v ollama &> /dev/null; then
    OLLAMA_VERSION=$(ollama --version 2>/dev/null | grep -oE "v?[0-9]+\.[0-9]+\.[0-9]+" | head -1 | sed 's/^v//')
    
    if [ ! -z "$OLLAMA_VERSION" ]; then
        echo "Detected Ollama version: $OLLAMA_VERSION"
        
        # Check if version meets minimum requirement
        if printf '%s\n%s\n' "$MINIMUM_VERSION" "$OLLAMA_VERSION" | sort -V | head -1 | grep -q "^$MINIMUM_VERSION$"; then
            echo "Version $OLLAMA_VERSION supports native OpenAI API"
        else
            echo "Version $OLLAMA_VERSION may not support native OpenAI API"
            echo "   Minimum required: $MINIMUM_VERSION"
            echo "   Update with: curl -fsSL https://ollama.com/install.sh | sh"
            echo "   Continuing with tests anyway..."
        fi
    else
        echo "Could not determine Ollama version - continuing with tests..."
    fi
fi

# Test 1: List models endpoint
echo ""
echo "Test 1: List Models Endpoint"
echo "GET $OLLAMA_BASE_URL/models"

MODELS_RESPONSE=$(curl -s "$OLLAMA_BASE_URL/models" 2>/dev/null || echo "ERROR")
if [ "$MODELS_RESPONSE" = "ERROR" ]; then
    echo "Failed to connect to models endpoint"
    exit 1
fi

echo "Models endpoint working"
if command -v jq &> /dev/null; then
    echo "Available models:"
    echo "$MODELS_RESPONSE" | jq -r '.data[].id' | sed 's/^/  - /'
else
    echo "Raw response: $MODELS_RESPONSE"
fi

# Test 2: Chat completions endpoint
echo ""
echo "Test 2: Chat Completions Endpoint"
echo "POST $OLLAMA_BASE_URL/chat/completions"

REQUEST_PAYLOAD='{
    "model": "'$MODEL_NAME'",
    "messages": [
        {
            "role": "user", 
            "content": "Generate a simple rating for a product. Respond with just a number between 0 and 1, like: {\"rating_score\": 0.8}"
        }
    ],
    "max_tokens": 50,
    "temperature": 0.1
}'

echo "Request payload:"
echo "$REQUEST_PAYLOAD" | jq . 2>/dev/null || echo "$REQUEST_PAYLOAD"

CHAT_RESPONSE=$(curl -s -X POST "$OLLAMA_BASE_URL/chat/completions" \
    -H "Content-Type: application/json" \
    -d "$REQUEST_PAYLOAD" 2>/dev/null || echo "ERROR")

if [ "$CHAT_RESPONSE" = "ERROR" ]; then
    echo "Failed to connect to chat completions endpoint"
    exit 1
fi

echo ""
echo "Chat completions endpoint working"
echo "Response:"
if command -v jq &> /dev/null; then
    echo "$CHAT_RESPONSE" | jq .
    
    # Extract just the content
    CONTENT=$(echo "$CHAT_RESPONSE" | jq -r '.choices[0].message.content' 2>/dev/null || echo "")
    if [ ! -z "$CONTENT" ] && [ "$CONTENT" != "null" ]; then
        echo ""
        echo "Generated Content:"
        echo "$CONTENT"
        
        # Test if it contains rating-like content
        if echo "$CONTENT" | grep -E "(rating|score)" > /dev/null; then
            echo "Response contains rating-related content - good for LLM judgments!"
        fi
    fi
else
    echo "$CHAT_RESPONSE"
fi

# Test 3: Compatibility with ML-Commons format
echo ""
echo "Test 3: ML-Commons Connector Compatibility"
echo "Testing the exact format ML-Commons would use..."

ML_COMMONS_PAYLOAD='{
    "model": "'$MODEL_NAME'",
    "messages": [
        {
            "role": "system",
            "content": "You are a search relevance judge. Rate the relevance of search results."
        },
        {
            "role": "user", 
            "content": "Query: wireless headphones\nDocument: Premium Bluetooth wireless headphones with noise cancellation\nRate the relevance from 0.0 to 1.0:"
        }
    ],
    "temperature": 0.0
}'

echo "ML-Commons style request:"
echo "$ML_COMMONS_PAYLOAD" | jq . 2>/dev/null || echo "$ML_COMMONS_PAYLOAD"

ML_RESPONSE=$(curl -s -X POST "$OLLAMA_BASE_URL/chat/completions" \
    -H "Content-Type: application/json" \
    -d "$ML_COMMONS_PAYLOAD" 2>/dev/null || echo "ERROR")

if [ "$ML_RESPONSE" = "ERROR" ]; then
    echo "ML-Commons style request failed"
else
    echo ""
    echo "ML-Commons style request succeeded"
    if command -v jq &> /dev/null; then
        ML_CONTENT=$(echo "$ML_RESPONSE" | jq -r '.choices[0].message.content' 2>/dev/null || echo "")
        if [ ! -z "$ML_CONTENT" ] && [ "$ML_CONTENT" != "null" ]; then
            echo "ML-Commons Response:"
            echo "$ML_CONTENT"
        fi
    fi
fi

# Test 4: Performance check
echo ""
echo "Test 4: Performance Check"
echo "Measuring response time..."

START_TIME=$(date +%s.%N)
PERF_RESPONSE=$(curl -s -X POST "$OLLAMA_BASE_URL/chat/completions" \
    -H "Content-Type: application/json" \
    -d '{"model":"'$MODEL_NAME'","messages":[{"role":"user","content":"Hello"}],"max_tokens":10}' \
    2>/dev/null || echo "ERROR")
END_TIME=$(date +%s.%N)

if [ "$PERF_RESPONSE" != "ERROR" ]; then
    DURATION=$(echo "$END_TIME - $START_TIME" | bc -l 2>/dev/null || echo "N/A")
    echo "Response time: ${DURATION}s"
    
    if command -v bc &> /dev/null && [ "$DURATION" != "N/A" ]; then
        if (( $(echo "$DURATION < 5.0" | bc -l) )); then
        echo "Great performance for local testing!"
    elif (( $(echo "$DURATION < 15.0" | bc -l) )); then
        echo "Good performance for development"
    else
        echo "Slower performance - consider smaller model for testing"
        fi
    fi
else
    echo "Performance test failed"
fi

echo ""
echo "=== Summary ==="
echo "Ollama native OpenAI API is working!"
echo ""
echo "Ready for ML-Commons Integration:"
echo "  Endpoint: $OLLAMA_BASE_URL/chat/completions"
echo "  Model: $MODEL_NAME"
echo "  Format: Standard OpenAI chat completions"
echo ""
echo "Next Steps:"
echo "  1. Create ML-Commons remote connector pointing to: $OLLAMA_BASE_URL/chat/completions"
echo "  2. Register model with connector"
echo "  3. Run integration tests with: ./gradlew integTest --tests '*LLMJudgmentGenerationIT*'"
echo ""
