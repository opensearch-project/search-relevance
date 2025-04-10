curl -s -X POST "http://localhost:9200/sample_index/_doc/1" \
-H "Content-type: application/json" \
-d'{
     "name": "banana",
     "price": 1.99,
     "description": "this is a banana"
   }'

curl -s -X POST "http://localhost:9200/sample_index/_doc/1" \
-H "Content-type: application/json" \
-d'{
     "name": "apple",
     "price": 3.99,
     "description": "this is an apple"
   }'

curl -s -X POST "http://localhost:9200/sample_index/_doc/3" \
-H "Content-type: application/json" \
-d'{
     "name": "test",
     "price": 19.99,
     "description": "this is a test"
   }'