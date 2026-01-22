# Search for search configurations by ID
curl -X POST "localhost:9200/_plugins/_search_relevance/search_configurations/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "term": { "id": "config-123" }
    },
    "size": 10
  }'

# Search for search configurations by name
curl -X POST "localhost:9200/_plugins/_search_relevance/search_configurations/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "match": { "name": "My Search Config" }
    },
    "size": 10
  }'

# Search for search configurations by index
curl -X POST "localhost:9200/_plugins/_search_relevance/search_configurations/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "term": { "index": "ecommerce" }
    },
    "size": 10
  }'

# Search for search configurations with wildcard on index
curl -X POST "localhost:9200/_plugins/_search_relevance/search_configurations/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "wildcard": { "index": "ecommerce*" }
    },
    "size": 10
  }'

# Search for search configurations that have a search pipeline
curl -X POST "localhost:9200/_plugins/_search_relevance/search_configurations/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "exists": { "field": "searchPipeline" }
    },
    "size": 10
  }'

# Complex search - find configs for specific index with specific query pattern
curl -X POST "localhost:9200/_plugins/_search_relevance/search_configurations/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "bool": {
        "must": [
          { "term": { "index": "products" } },
          { "match": { "query": "match" } }
        ]
      }
    },
    "size": 10
  }'

# Get all search configurations (match_all)
curl -X GET "localhost:9200/_plugins/_search_relevance/search_configurations/_search" \
  -H "Content-Type: application/json"

# Search with date range on timestamp
curl -X POST "localhost:9200/_plugins/_search_relevance/search_configurations/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "range": {
        "timestamp": {
          "gte": "2024-01-01",
          "lte": "2024-12-31"
        }
      }
    },
    "size": 10
  }'

# Search and return only specific fields
curl -X POST "localhost:9200/_plugins/_search_relevance/search_configurations/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "match_all": {}
    },
    "_source": ["id", "name", "index"],
    "size": 10
  }'

# Search with sorting by name
curl -X POST "localhost:9200/_plugins/_search_relevance/search_configurations/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "match_all": {}
    },
    "sort": [
      { "name.keyword": { "order": "asc" } }
    ],
    "size": 10
  }'