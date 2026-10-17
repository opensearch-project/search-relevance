# Search for judgments by ID
curl -X POST "localhost:9200/_plugins/_search_relevance/judgments/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "term": { "id": "judgment-123" }
    },
    "size": 10
  }'

# Search for judgments by name
curl -X POST "localhost:9200/_plugins/_search_relevance/judgments/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "match": { "name": "ESCI Judgments" }
    },
    "size": 10
  }'

# Search for judgments by status
curl -X POST "localhost:9200/_plugins/_search_relevance/judgments/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "term": { "status": "COMPLETED" }
    },
    "size": 10
  }'

# Search for judgments by type
curl -X POST "localhost:9200/_plugins/_search_relevance/judgments/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "term": { "type": "CALCULATED" }
    },
    "size": 10
  }'

# Search for judgments with specific query in judgmentRatings
curl -X POST "localhost:9200/_plugins/_search_relevance/judgments/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "nested": {
        "path": "judgmentRatings",
        "query": {
          "match": {
            "judgmentRatings.query": "red dress"
          }
        }
      }
    },
    "size": 10
  }'

# Complex search - find completed CALCULATED judgments
curl -X POST "localhost:9200/_plugins/_search_relevance/judgments/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "bool": {
        "must": [
          { "term": { "type": "CALCULATED" } },
          { "term": { "status": "COMPLETED" } }
        ]
      }
    },
    "size": 10
  }'

# Get all judgments (match_all) - excludes judgmentRatings.ratings by default
curl -X GET "localhost:9200/_plugins/_search_relevance/judgments/_search" \
  -H "Content-Type: application/json"

# Explicitly include all fields including judgmentRatings.ratings
curl -X POST "localhost:9200/_plugins/_search_relevance/judgments/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "match_all": {}
    },
    "_source": {
      "includes": ["*"]
    }
  }'

# Include specific fields including full judgmentRatings
curl -X POST "localhost:9200/_plugins/_search_relevance/judgments/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "term": { "status": "COMPLETED" }
    },
    "_source": ["id", "name", "type", "status", "judgmentRatings"]
  }'