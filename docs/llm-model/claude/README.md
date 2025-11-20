# Claude 3.5 Haiku OpenSearch ML Connector

This directory contains the setup and validation scripts for integrating Claude 3.5 Haiku with OpenSearch ML for search relevance rating.

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
- **Model**: `us.anthropic.claude-3-5-haiku-20241022-v1:0` (inference profile)
- **Region**: `us-east-1`
- **Max Tokens**: 4000
- **Protocol**: AWS SigV4

### Message Format
```json
{
  "parameters": {
    "messages": [
      {
        "role": "user",
        "content": [
          {
            "type": "text",
            "text": "Your prompt here"
          }
        ]
      }
    ]
  }
}
```