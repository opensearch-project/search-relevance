import json
import os

querySetId= "0be68e4d-812c-4e93-bb5c-31c4323b59e1"
searchConfigurationId="0be68e4d-812c-4e93-bb5c-31c4323b59e1"
judgmentId="0be68e4d-812c-4e93-bb5c-31c4323b59e1"

ratingsMap = {
  "Willis":0.76,
  "star wars":0.27,
  "Ice Age":0.00,
  "Wizard of Oz":0.00,
  "Titanic":0.37
}

# Load the Movie_Search_Quepid_1.json file
with open("Movie_Search_Quepid_1.json", "r") as f:
    movie_data = json.load(f)

    
# Extract doc_id and rating from movie data
# Note: This part might need adjustment depending on the actual structure of your JSON
evaluationResultList = []
for item in movie_data.get("queries", []):
    
    query_text = item.get("query_text", "")
    documentIds = []
    metrics = []
    for doc in item.get("ratings", []):
        doc_id = doc.get("doc_id", "")
        rating = {
          "docId": doc.get("doc_id", ""),
          "rating": doc.get("rating", 0)
        }
        documentIds.append(doc_id)
    
    metric = {
        "metric": "ndcg",
        "value": ratingsMap.get(query_text)
    }
    metrics.append(metric)

    evaluationResult = {
      "searchText": query_text,
      "judgmentIds": [judgmentId],
      "documentIds": documentIds,
      "metrics": metrics
    }
    
    evaluationResultList.append(evaluationResult)



# Create the final data structure with metadata
output_data = {
 	"querySetId": querySetId,
 	"searchConfigurationList": [searchConfigurationId],
  "judgmentList": [judgmentId],
 	"type": "POINTWISE_EVALUATION",
  "size": 10,
  "evaluationResultList": evaluationResultList
}

# Write the transformed data to the new file
output_path = "./movies_experiment.json"
with open(output_path, "w") as f:
    json.dump(output_data, f, indent=2)

print(f"Created {output_path}")
