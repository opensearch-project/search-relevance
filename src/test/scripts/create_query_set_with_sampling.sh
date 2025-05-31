curl -s -X POST "localhost:9200/_plugins/search_relevance/query_sets" \
-H "Content-type: application/json" \
-d'{
   	"name": "test03",
   	"description": "test03",
   	"sampling": "random",
   	"querySetSize": 200
}' | jq