curl -s -X PUT "localhost:9200/_plugins/search_relevance/experiments" \
-H "Content-type: application/json" \
-d'{
   	"querySetId": "{query_set_id}",
   	"searchConfigurationList": ["{search_configuration_id01}", "{search_configuration_id02}"],
   	"size": 10,
   	"type": "PAIRWISE_COMPARISON"
   }' | jq