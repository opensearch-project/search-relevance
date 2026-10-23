# SPDX-License-Identifier: Apache-2.0
#
# The OpenSearch Contributors require contributions made to
# this file be licensed under the Apache-2.0 license or a
# compatible open source license.

curl -s -X PUT "http://localhost:9200/_plugins/_search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d'{
      "name": "baseline",
      "index": "sample_index";
      "query": "{\"query\": {\n\"match_all\": {}}}",
      "searchPipeline": ""
}' | jq