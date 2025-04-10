curl -s -X PUT "localhost:9200/_plugins/search_relevance/experiments" \
-H "Content-type: application/json" \
-d'{
   	"index": "sample_index",
   	"querySetId": "test01",
   	"searchConfigurationList": ["baseline", "multi_match"],
   	"k": 10
   }' | jq