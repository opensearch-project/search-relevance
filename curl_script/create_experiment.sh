curl -s -X PUT "localhost:9200/_plugins/search_relevance/experiments" \
-H "Content-type: application/json" \
-d'{
   	"index": "sample_index",
   	"querySetId": "{query_set_id}",
   	"searchConfigurationList": ["{search_configuration_id01}", "{search_configuration_id02}"],
   	"k": 10
   }' | jq