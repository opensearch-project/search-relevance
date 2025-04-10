curl -s -X PUT "http://localhost:9200/_plugins/search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d'{
      "name": "baseline",
      "queryBody": "{\"match_all\":{}}",
      "searchPipeline": "n/a"
}' | jq