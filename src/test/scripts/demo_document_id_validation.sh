#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0
#
# The OpenSearch Contributors require contributions made to
# this file be licensed under the Apache-2.0 license or a
# compatible open source license.

# Examples for judgment document ID validation. Requires curl and python3.
# Creates two imported judgments; invalid requests should not create judgments.
# Usage: BASE_URL=http://localhost:9201 bash src/test/scripts/demo_document_id_validation.sh
# Optional: set LLM_JUDGMENT_ID to a completed demo LLM judgment containing
# sku<red>, catalog\item42, and item"42 under "laptop for developers".
# That example requires jq and updates those documents' ratings in place.
set -euo pipefail

BASE_URL=${BASE_URL:-http://localhost:9200}

echo 'Valid IDs with special characters: expect HTTP 200'
curl -sS -w '\nHTTP %{http_code}\n' -X PUT "$BASE_URL/_plugins/_search_relevance/judgments" \
  -H 'Content-Type: application/json' --data-binary @- <<'JSON'
{
  "name": "Document ID validation demo",
  "type": "IMPORT_JUDGMENT",
  "judgmentRatings": [{
    "query": "red shoes",
    "ratings": [
      {"docId": "sku<red>", "rating": "0.5"},
      {"docId": "catalog\\item42", "rating": "0.5"},
      {"docId": "item\"42", "rating": "0.5"},
      {"docId": " ", "rating": "0.5"}
    ]
  }]
}
JSON

if [[ -n ${LLM_JUDGMENT_ID:-} ]]; then
  echo 'Manual edit of the demo LLM judgment: expect HTTP 200'
  curl -fsS "$BASE_URL/_plugins/_search_relevance/judgments/$LLM_JUDGMENT_ID" \
    | jq '{judgmentRatings: [.hits.hits[0]._source.judgmentRatings[] | select(.query | startswith("laptop for developers#")) | {query, ratings: [{docId:"sku<red>",rating:"1.0"},{docId:"catalog\\item42",rating:"0.8"},{docId:"item\"42",rating:"0.8"}]}]}' \
    | curl -sS -w '\nHTTP %{http_code}\n' -X PUT "$BASE_URL/_plugins/_search_relevance/judgments/$LLM_JUDGMENT_ID" \
        -H 'Content-Type: application/json' --data-binary @-

else
  echo 'Skipping manual edit: set LLM_JUDGMENT_ID to enable it.'
fi

echo '512 UTF-8 bytes: expect HTTP 200'
python3 - <<'PY' | curl -sS -w '\nHTTP %{http_code}\n' -X PUT "$BASE_URL/_plugins/_search_relevance/judgments" -H 'Content-Type: application/json' --data-binary @-
import json
print(json.dumps({"name":"Document ID byte limit demo","type":"IMPORT_JUDGMENT","judgmentRatings":[{"query":"red shoes","ratings":[{"docId":"é"*256,"rating":"0.5"}]}]}))
PY

echo '514 UTF-8 bytes: expect HTTP 400'
python3 - <<'PY' | curl -sS -w '\nHTTP %{http_code}\n' -X PUT "$BASE_URL/_plugins/_search_relevance/judgments" -H 'Content-Type: application/json' --data-binary @-
import json
print(json.dumps({"name":"Oversized document ID demo","type":"IMPORT_JUDGMENT","judgmentRatings":[{"query":"red shoes","ratings":[{"docId":"é"*257,"rating":"0.5"}]}]}))
PY

echo 'Empty document ID: expect HTTP 400'
curl -sS -w '\nHTTP %{http_code}\n' -X PUT "$BASE_URL/_plugins/_search_relevance/judgments" \
  -H 'Content-Type: application/json' --data-binary @- <<'JSON'
{"name":"Empty document ID demo","type":"IMPORT_JUDGMENT","judgmentRatings":[{"query":"red shoes","ratings":[{"docId":"","rating":"0.5"}]}]}
JSON

echo 'Numeric document ID: expect HTTP 400'
curl -sS -w '\nHTTP %{http_code}\n' -X PUT "$BASE_URL/_plugins/_search_relevance/judgments" \
  -H 'Content-Type: application/json' --data-binary @- <<'JSON'
{"name":"Numeric document ID demo","type":"IMPORT_JUDGMENT","judgmentRatings":[{"query":"red shoes","ratings":[{"docId":42,"rating":"0.5"}]}]}
JSON
