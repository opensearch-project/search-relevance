# Example of importing an externally run evaluation into SRW
# 
# You must update the values below to match entities in SRW already: 
# 
# querySetId should be the one that is named 'TVs'.
# searchConfigurationList should point to one you created, maybe called "External Search Configuration", 
#   use placeholders for any required values.
# judgmentList should be ESCI Judgements.  Do a find and replace as it shows up multiple levels.
# 
# 
# curl -s -X POST "localhost:9200/_plugins/_search_relevance/experiments" \
# -H "Content-type: application/json" \
# -d @../data-esci/esci_us_external_experiment.json
# Helper script
exe() { (set -x ; "$@") | jq | tee RES; echo; }

exe curl -s -X PUT "localhost:9200/_plugins/_search_relevance/query_sets" \
-H "Content-type: application/json" \
--data-binary @../data-external-evaluation/movies_queryset.json

QUERY_SET_ID=`jq -r '.query_set_id' < RES`

exe curl -s -X PUT "localhost:9200/_plugins/_search_relevance/judgments" \
-H "Content-type: application/json" \
--data-binary @../data-external-evaluation/movies_judgments.json

JUDGMENTS_ID=`jq -r '.judgment_id' < RES`



exe curl -s -X PUT "http://localhost:9200/_plugins/_search_relevance/search_configurations" \
-H "Content-type: application/json" \
-d'{
      "name": "Movie Search from Quepid",
      "query": "{}",
      "index": "ecommerce"
}'

EXTERNAL_SEARCH_CONFIGURATION_ID=`jq -r '.search_configuration_id' < RES`


# Update the movies_experiment.json file with the extracted IDs
echo "Updating experiment file with the extracted IDs..."
TEMP_FILE="../data-external-evaluation/movies_experiment_updated.json"

# Use jq to update the JSON file with our variables
jq --arg qid "$QUERY_SET_ID" \
   --arg sid "$EXTERNAL_SEARCH_CONFIGURATION_ID" \
   --arg jid "$JUDGMENTS_ID" \
   '.querySetId = $qid | .searchConfigurationList = [$sid] | .judgmentList = [$jid] | .evaluationResultList[].judgmentIds = [$jid]' \
   ../data-external-evaluation/movies_experiment.json > "$TEMP_FILE"

if [ $? -eq 0 ]; then
    echo "Successfully updated experiment file with new IDs."    
else
    echo "Error: Failed to update the experiment file."
    exit 1
fi

echo "Submitting updated experiment to API..."
exe curl -s -X POST "http://localhost:9200/_plugins/_search_relevance/experiments" \
-H "Content-type: application/json" \
--data-binary @../data-external-evaluation/movies_experiment_updated.json
