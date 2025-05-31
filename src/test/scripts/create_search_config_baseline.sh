curl -s -X PUT "http://localhost:9200/_plugins/search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d'{
      "name": "baseline",
      "index": "sample_index";
      "query": "{\"query\": {\n\"match_all\": {}}}",
      "searchPipeline": ""
}' | jq