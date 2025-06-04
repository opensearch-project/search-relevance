query_set_id=9ac2d25a-d4df-444d-8008-9aeadfe70927
search_configuration_id=161c9023-bb0e-44c9-b347-3aa5a1bf66af
judgement_list_id=c0343cd1-2dca-43b0-9ebf-7a927d21ef4b
   
curl -s -X PUT "localhost:9200/_plugins/_search_relevance/experiments" \
-H "Content-type: application/json" \
-d"{
   	\"querySetId\": \"${query_set_id}\",
   	\"searchConfigurationList\": [\"${search_configuration_id}\"],
    \"judgmentList\": [\"${judgement_list_id}\"],
   	\"size\": 8,
   	\"type\": \"POINTWISE_EVALUATION\"
  }"
