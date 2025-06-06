curl -s -X PUT "localhost:9200/_plugins/_search_relevance/judgments" \
-H "Content-type: application/json" \
-d'{
   	"name": "Imported Judgments",
   	"description": "Judgments generated outside SRW",
   	"type": "IMPORT_JUDGMENT",
   	"judgmentRatings": {
      "red dress": [
        {
          "docId": "B077ZJXCTS",
          "rating": "0.000"
        },
        {
          "docId": "B071S6LTJJ",
          "rating": "0.000"
        },
        {
          "docId": "B01IDSPDJI",
          "rating": "0.000"
        },
        {
          "docId": "B07QRCGL3G",
          "rating": "0.000"
        },
        {
          "docId": "B074V6Q1DR",
          "rating": "0.000"
        }
      ],
      "blue jeans": [
        {
          "docId": "B07L9V4Y98",
          "rating": "0.000"
        },
        {
          "docId": "B01N0DSRJC",
          "rating": "0.000"
        },
        {
          "docId": "B001CRAWCQ",
          "rating": "0.000"
        },
        {
          "docId": "B075DGJZRM",
          "rating": "0.000"
        },
        {
          "docId": "B009ZD297U",
          "rating": "0.000"
        }
      ]
    }
}' | jq
