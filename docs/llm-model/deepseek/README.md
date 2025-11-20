# DeepSeek Chat OpenSearch ML Connector

This directory contains the setup and validation scripts for integrating DeepSeek Chat with OpenSearch ML.

## Prerequisites

- OpenSearch running on `http://localhost:9200`
- DeepSeek API key
- `jq` installed for JSON parsing

## Quick Start

```bash
chmod +x connector_validate.sh
./connector_validate.sh
```

## Configuration

### Model Details
- **Model**: `deepseek-chat`
- **Endpoint**: `api.deepseek.com`
- **Protocol**: HTTP
- **API Version**: v1

### Message Format
```json
{
  "parameters": {
    "messages": [
      {
        "role": "user",
        "content": "Your message here"
      }
    ]
  }
}
```

## Available Models

### Chat Models
- `deepseek-chat` - General purpose chat model
- `deepseek-coder` - Code-focused model (if available)

## Response Format

```json
{
  "inference_results": [{
    "output": [{
      "name": "response",
      "dataAsMap": {
        "id": "...",
        "object": "chat.completion",
        "created": 1761688945,
        "model": "deepseek-chat",
        "choices": [{
          "index": 0,
          "message": {
            "role": "assistant",
            "content": "Model response here"
          },
          "finish_reason": "stop"
        }],
        "usage": {
          "prompt_tokens": 12,
          "completion_tokens": 1,
          "total_tokens": 13
        }
      }
    }],
    "status_code": 200
  }]
}
```