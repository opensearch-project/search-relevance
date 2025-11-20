# OpenAI GPT OpenSearch ML Connector

This directory contains the setup and validation scripts for integrating OpenAI GPT models with OpenSearch ML.

## Prerequisites

- OpenSearch running on `http://localhost:9200`
- Valid OpenAI API key
- `jq` installed for JSON parsing

## Quick Start

```bash
# Update API key in connector_validate.sh
chmod +x connector_validate.sh
./connector_validate.sh
```

## Configuration

### Model Details
- **Model**: `gpt-5-nano` (configurable)
- **Endpoint**: `api.openai.com`
- **Protocol**: HTTP
- **Action Type**: `/v1/chat/completion`

### Available Models
- `gpt-4o` - Latest GPT-4 Omni model
- `gpt-4o-mini` - Smaller, faster GPT-4 variant
- `gpt-4-turbo` - GPT-4 Turbo
- `gpt-3.5-turbo` - GPT-3.5 Turbo
- `gpt-5-nano` - GPT-5 Nano (if available)

### Message Format
```json
{
  "parameters": {
    "messages": [
      {
        "role": "system",
        "content": "System prompt here"
      },
      {
        "role": "user",
        "content": "User message here"
      }
    ]
  }
}
```

## Response Format

```json
{
  "inference_results": [{
    "output": [{
      "name": "response",
      "dataAsMap": {
        "id": "chatcmpl-...",
        "object": "chat.completion",
        "created": 1761689000,
        "model": "gpt-5-nano",
        "choices": [{
          "index": 0,
          "message": {
            "role": "assistant",
            "content": "Model response here"
          },
          "finish_reason": "stop"
        }],
        "usage": {
          "prompt_tokens": 100,
          "completion_tokens": 50,
          "total_tokens": 150
        }
      }
    }],
    "status_code": 200
  }]
}
```
