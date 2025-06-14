curl -s -X POST "localhost:9200/_plugins/_search_relevance/experiments" \
-H "Content-type: application/json" \
-d'{
 	"querySetId": "f3ee203a-f560-4369-a132-c9167f55c9ba",
 	"searchConfigurationList": ["ea4b62f6-ff56-47af-b9a4-6f38b6256d1d"],
  "judgmentList": ["8638eaa7-5417-4f79-aa69-1cf3f462d1fb"],
 	"type": "POINTWISE_EVALUATION",
  "size": 10,
  "evaluationResultList": [
    {
      "searchText": "led tv",
      "judgmentIds": [
        "8638eaa7-5417-4f79-aa69-1cf3f462d1fb"
      ],
      "documentIds": [
        "B079VXT54Z",
        "B07MXBCQCF",
        "B07ZFBTFQF",
        "B0915F456C",
        "B07176GBXQ",
        "B07QGQGDRM",
        "B01LY0FCQO",
        "B079NCCK2M",
        "B083GRRW2Z",
        "B076KMND87"
      ],
      "metrics": [
             {
               "metric": "coverage",
               "value": 1.0
             },
             {
               "metric": "precision@10",
               "value": 0.3
             },
             {
               "metric": "ndcg",
               "value": 0.5
             },
             {
               "metric": "precision@5",
               "value": 0.9
             },
             {
               "metric": "MAP",
               "value": 0.8
             }
           ]
    },
    {
      "searchText": "tv",
      "judgmentIds": [
        "8638eaa7-5417-4f79-aa69-1cf3f462d1fb"
      ],
      "documentIds": [
        "B07GPN3MRY",
        "B07176GBXQ",
        "B07W7RP985",
        "B01FH7EQNW",
        "B08718Q168",
        "B086PWXVFW",
        "B06XKFWSJ4",
        "B01N1SSOUC",
        "B07P72ZB37",
        "B01AJJN0DA"
      ],
      "metrics": [
        {
          "metric": "coverage",
          "value": 0.2
        },
        {
          "metric": "precision@10",
          "value": 0.3
        },
        {
          "metric": "ndcg",
          "value": 0.4
        },
        {
          "metric": "precision@5",
          "value": 0.1
        },
        {
          "metric": "MAP",
          "value": 0.1
        }
      ]
    }      
  ]
}'
