curl -s -X PUT "http://localhost:9200/_plugins/search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d'{
      "name": "multi_match",
      "queryBody": "{\"multi_match\":{\"query\":\"%SearchText%\",\"fields\":[\"name\",\"description\"]}}",
      "searchPipeline": "n/a"
}' | jq