# SPDX-License-Identifier: Apache-2.0
#
# The OpenSearch Contributors require contributions made to
# this file be licensed under the Apache-2.0 license or a
# compatible open source license.

curl -s -X POST "localhost:9200/_plugins/_search_relevance/query_sets" \
-H "Content-type: application/json" \
-d'{
   	"name": "test03",
   	"description": "test03",
   	"sampling": "random",
   	"querySetSize": 200
}' | jq