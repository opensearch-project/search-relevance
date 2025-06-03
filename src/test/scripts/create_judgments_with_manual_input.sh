curl -s -X PUT "localhost:9200/_plugins/search_relevance/judgments" \
-H "Content-type: application/json" \
-d'{
   	"name": "Imported Judgments",
   	"description": "Judgments generated outside SRW",
   	"type": "IMPORT_JUDGMENT",
   	"judgmentScores": {
      "red dress": [
        {
          "docId": "B077ZJXCTS",
          "score": "0.000"
        },
        {
          "docId": "B071S6LTJJ",
          "score": "0.000"
        },
        {
          "docId": "B01IDSPDJI",
          "score": "0.000"
        },
        {
          "docId": "B07QRCGL3G",
          "score": "0.000"
        },
        {
          "docId": "B074V6Q1DR",
          "score": "0.000"
        }
      ],
      "blue jeans": [
        {
          "docId": "B07L9V4Y98",
          "score": "0.000"
        },
        {
          "docId": "B01N0DSRJC",
          "score": "0.000"
        },
        {
          "docId": "B001CRAWCQ",
          "score": "0.000"
        },
        {
          "docId": "B075DGJZRM",
          "score": "0.000"
        },
        {
          "docId": "B009ZD297U",
          "score": "0.000"
        }
      ]
    }
}' | jq
