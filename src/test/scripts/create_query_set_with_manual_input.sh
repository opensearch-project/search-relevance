curl -s -X PUT "localhost:9200/_plugins/search_relevance/query_sets" \
-H "Content-type: application/json" \
-d'{
   	"name": "TVs",
   	"description": "Some TVs that people might want",
   	"sampling": "manual",
   	"querySetQueries": [
    	{"queryText": "tv"},
    	{"queryText": "led tv"}
    ]
}' | jq
