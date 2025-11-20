# Cohere Command R OpenSearch ML Connector

This directory contains the setup and validation scripts for integrating Cohere Command R with OpenSearch ML via AWS Bedrock.

## Prerequisites

- OpenSearch running on `http://localhost:9200`
- AWS credentials configured (`aws configure`)
- `jq` installed for JSON parsing

## Quick Start

```bash
chmod +x connector_validate.sh
./connector_validate.sh
```

## Configuration

### Model Details
- **Model**: `cohere.command-r-v1:0` (via Bedrock)
- **Region**: `us-east-1`
- **Max Tokens**: 1000
- **Protocol**: AWS SigV4
- **Streaming**: Supported

### Message Format
```json
{
  "parameters": {
    "message": "Your message here"
  }
}
```

## Available Cohere Models

### Chat Models (via Bedrock)
- `cohere.command-r-v1:0` - Command R
- `cohere.command-r-plus-v1:0` - Command R+ (more capable)

### Embedding Models
- `cohere.embed-v4:0` - Embed v4 (multimodal)
- `cohere.embed-english-v3` - English embeddings
- `cohere.embed-multilingual-v3` - Multilingual embeddings

### Rerank Model
- `cohere.rerank-v3-5:0` - Rerank 3.5

## Response Format

```json
{
  "inference_results": [{
    "output": [{
      "name": "response",
      "dataAsMap": {
        "response_id": "...",
        "text": "Model response here",
        "generation_id": "...",
        "chat_history": [...],
        "finish_reason": "COMPLETE"
      }
    }],
    "status_code": 200
  }]
}
```