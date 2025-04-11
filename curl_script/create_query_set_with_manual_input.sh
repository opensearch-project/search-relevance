curl -s -X PUT "localhost:9200/_plugins/search_relevance/query_sets" \
-H "Content-type: application/json" \
-d'{
   	"name": "test01",
   	"description": "test01",
   	"sampling": "manual",
   	"querySetQueries": "apple, banana"
}' | jq