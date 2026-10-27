# SPDX-License-Identifier: Apache-2.0
#
# The OpenSearch Contributors require contributions made to
# this file be licensed under the Apache-2.0 license or a
# compatible open source license.

# Exact match on a name.
curl -X POST "localhost:9200/_plugins/_search_relevance/query_sets/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "match": { "name": "Top 20" }
    },
    "size": 10
  }'
  
# Example of a partial match of a queryset that has queryText "wall lamp without cord"
curl -X POST "localhost:9200/_plugins/_search_relevance/query_sets/_search" \
  -H "Content-Type: application/json" \
  -d '{
  "query": {
        "nested": {
          "path": "querySetQueries",
          "query": {
            "match_phrase": {
              "querySetQueries.queryText": "lamp without cord"
            }
          }
        }
      }
  }'
