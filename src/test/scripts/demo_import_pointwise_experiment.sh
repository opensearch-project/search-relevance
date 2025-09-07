# Example of importing an externally run POINTWISE evaluation into SRW.
# 
# It populates SRW with a externally created query set, judgments, and search configuration.
# Then it plucks out those GUIDs and updates the import file with the correct values.
#
# USAGE:
#   ./demo_import_pointwise_experiment.sh [OPENSEARCH_URL]
#
# PARAMETERS:
#   OPENSEARCH_URL - Optional. The URL of the OpenSearch instance to use. 
#                    Default: "localhost:9200"
#
# EXAMPLES:
#   # Use default localhost:9200
#   ./demo_import_pointwise_experiment.sh
#
#   # Use a remote OpenSearch instance
#   ./demo_import_pointwise_experiment.sh "http://chorus-opensearch-edition.dev.o19s.com:9200"
#
# Helper script
exe() { (set -x ; "$@") | jq | tee RES; echo; }

# Set default OpenSearch URL or use provided parameter
OPENSEARCH_URL=${1:-"localhost:9200"}
echo "Using OpenSearch URL: $OPENSEARCH_URL"

exe curl -s -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/query_sets" \
-H "Content-type: application/json" \
--data-binary @../data-external-evaluation/movies_queryset.json

QUERY_SET_ID=`jq -r '.query_set_id' < RES`

exe curl -s -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/judgments" \
-H "Content-type: application/json" \
--data-binary @../data-external-evaluation/movies_judgments.json

JUDGMENTS_ID=`jq -r '.judgment_id' < RES`



exe curl -s -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d'{
      "name": "Movie Search from Quepid 1",
      "query": "{}",
      "index": ""
}'

EXTERNAL_SEARCH_CONFIGURATION_ID=`jq -r '.search_configuration_id' < RES`


# Update the movies_experiment_pointwise.json file with the extracted IDs
echo "Updating experiment file with the extracted IDs..."
TEMP_FILE="../data-external-evaluation/movies_experiment_tmp.json"

# Use jq to update the JSON file with our variables
jq --arg qid "$QUERY_SET_ID" \
   --arg sid "$EXTERNAL_SEARCH_CONFIGURATION_ID" \
   --arg jid "$JUDGMENTS_ID" \
   '.querySetId = $qid | .searchConfigurationList = [$sid] | .judgmentList = [$jid] | .evaluationResultList[].judgmentIds = [$jid]' \
   ../data-external-evaluation/movies_experiment_pointwise.json > "$TEMP_FILE"

if [ $? -eq 0 ]; then
    echo "Successfully updated experiment file with new IDs."    
else
    echo "Error: Failed to update the experiment file."
    exit 1
fi

echo "Submitting updated experiment to API..."
exe curl -s -X POST "$OPENSEARCH_URL/_plugins/_search_relevance/experiments" \
-H "Content-type: application/json" \
--data-binary @../data-external-evaluation/movies_experiment_tmp.json
