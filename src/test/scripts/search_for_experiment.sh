# Search for experiments by ID
curl -X POST "localhost:9200/_plugins/_search_relevance/experiments/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "term": { "id": "experiment-123" }
    },
    "size": 10
  }'

# Search for experiments by type
curl -X POST "localhost:9200/_plugins/_search_relevance/experiments/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "term": { "type": "POINTWISE_EVALUATION" }
    },
    "size": 10
  }'

# Search for experiments by status
curl -X POST "localhost:9200/_plugins/_search_relevance/experiments/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "term": { "status": "COMPLETED" }
    },
    "size": 10
  }'

# Search for experiments by querySetId
curl -X POST "localhost:9200/_plugins/_search_relevance/experiments/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "term": { "querySetId": "my-query-set-id" }
    },
    "size": 10
  }'

# Complex search - find completed HYBRID_OPTIMIZER experiments
curl -X POST "localhost:9200/_plugins/_search_relevance/experiments/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "bool": {
        "must": [
          { "term": { "type": "HYBRID_OPTIMIZER" } },
          { "term": { "status": "COMPLETED" } }
        ]
      }
    },
    "size": 10
  }'

# Get all experiments (match_all)
curl -X GET "localhost:9200/_plugins/_search_relevance/experiments/_search" \
  -H "Content-Type: application/json"