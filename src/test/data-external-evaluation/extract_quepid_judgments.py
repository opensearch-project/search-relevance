import json
import os


# Load the Movie_Search_Quepid_Demo_case.json file
with open("Movie_Search_Quepid_Demo_case.json", "r") as f:
    movie_data = json.load(f)

    
# Extract doc_id and rating from movie data
# Note: This part might need adjustment depending on the actual structure of your JSON
judgmentRatings = []
for item in movie_data.get("queries", []):
    
    query_text = item.get("query_text", "")
    ratings = []
    for doc in item.get("ratings", []):
        doc_id = doc.get("doc_id", "")
        rating = {
          "docId": doc.get("doc_id", ""),
          "rating": str(doc.get("rating", 0))
        }
        
        ratings.append(rating)

    judgmentRating = {
      "query": query_text,
      "ratings": ratings
    }
    
    judgmentRatings.append(judgmentRating)

# Create the final data structure with metadata
output_data = {
    "name": "Movie Search Judgments",
    "description": "Judgments sourced from Quepid Movie Search Case",
    "type": "IMPORT_JUDGMENT",
    "judgmentRatings": judgmentRatings
}

# Write the transformed data to the new file
output_path = "./movies_judgments.json"
with open(output_path, "w") as f:
    json.dump(output_data, f, indent=2)

print(f"Created {output_path}")
