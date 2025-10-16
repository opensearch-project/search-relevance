/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.XContentParser;

/**
 * Tests for RemoteResponseMapper
 */
public class RemoteResponseMapperTests extends org.apache.lucene.tests.util.LuceneTestCase {

    private RemoteResponseMapper mapper;

    // @Before
    public void setUp() throws Exception {
        super.setUp();
        mapper = new RemoteResponseMapper();
    }

    public void testMapResponseWithEmptyInput() throws Exception {
        String result = mapper.mapResponse(null, null);
        assertNotNull("Result should not be null", result);

        Map<String, Object> parsed = parseJson(result);
        assertTrue("Should contain hits", parsed.containsKey("hits"));

        @SuppressWarnings("unchecked")
        Map<String, Object> hits = (Map<String, Object>) parsed.get("hits");
        @SuppressWarnings("unchecked")
        Map<String, Object> total = (Map<String, Object>) hits.get("total");
        assertEquals("Total should be 0", 0, total.get("value"));
    }

    public void testMapResponseAlreadyOpenSearchFormat() throws Exception {
        String opensearchResponse = """
            {
                "hits": {
                    "total": {"value": 2, "relation": "eq"},
                    "max_score": 1.5,
                    "hits": [
                        {"_id": "1", "_score": 1.5, "_source": {"title": "Test Doc 1"}},
                        {"_id": "2", "_score": 1.0, "_source": {"title": "Test Doc 2"}}
                    ]
                },
                "took": 5,
                "timed_out": false
            }
            """;

        String result = mapper.mapResponse(opensearchResponse, null);
        assertNotNull("Result should not be null", result);

        Map<String, Object> parsed = parseJson(result);
        assertTrue("Should contain hits", parsed.containsKey("hits"));

        @SuppressWarnings("unchecked")
        Map<String, Object> hits = (Map<String, Object>) parsed.get("hits");
        @SuppressWarnings("unchecked")
        Map<String, Object> total = (Map<String, Object>) hits.get("total");
        assertEquals("Total should be 2", 2, total.get("value"));
    }

    public void testMapResponseCommonFormat() throws Exception {
        String commonResponse = """
            {
                "results": [
                    {"id": "doc1", "title": "First Document", "score": 0.95},
                    {"id": "doc2", "title": "Second Document", "score": 0.87}
                ],
                "took": 10
            }
            """;

        String result = mapper.mapResponse(commonResponse, null);
        assertNotNull("Result should not be null", result);

        Map<String, Object> parsed = parseJson(result);
        assertTrue("Should contain hits", parsed.containsKey("hits"));

        @SuppressWarnings("unchecked")
        Map<String, Object> hits = (Map<String, Object>) parsed.get("hits");
        @SuppressWarnings("unchecked")
        Map<String, Object> total = (Map<String, Object>) hits.get("total");
        assertEquals("Total should be 2", 2, total.get("value"));

        @SuppressWarnings("unchecked")
        java.util.List<Object> hitsList = (java.util.List<Object>) hits.get("hits");
        assertEquals("Should have 2 hits", 2, hitsList.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> firstHit = (Map<String, Object>) hitsList.get(0);
        assertEquals("First hit ID should be doc1", "doc1", firstHit.get("_id"));
        assertEquals("First hit score should be 0.95", 0.95, firstHit.get("_score"));
    }

    public void testMapResponseWithTemplate() throws Exception {
        String remoteResponse = """
            {
                "data": {
                    "search_results": [
                        {"document_id": "123", "relevance_score": 0.92, "content": {"title": "Test Title"}},
                        {"document_id": "456", "relevance_score": 0.78, "content": {"title": "Another Title"}}
                    ],
                    "total_count": 2,
                    "execution_time": 15
                }
            }
            """;

        String template = """
            {
                "hits": {
                    "total": {"path": "data.total_count", "type": "integer"},
                    "hits": {
                        "path": "data.search_results",
                        "mapping": {
                            "_id": "document_id",
                            "_score": "relevance_score",
                            "_source": "content"
                        }
                    }
                },
                "took": {"path": "data.execution_time", "type": "integer"}
            }
            """;

        String result = mapper.mapResponse(remoteResponse, template);
        assertNotNull("Result should not be null", result);

        Map<String, Object> parsed = parseJson(result);
        assertTrue("Should contain hits", parsed.containsKey("hits"));
        assertTrue("Should contain took", parsed.containsKey("took"));
        assertEquals("Took should be 15", 15, parsed.get("took"));
    }

    public void testMapResponseWithSimpleTemplate() throws Exception {
        String remoteResponse = """
            {
                "search": {
                    "documents": [
                        {"id": "1", "score": 1.5, "data": {"title": "Document 1"}},
                        {"id": "2", "score": 1.2, "data": {"title": "Document 2"}}
                    ],
                    "count": 2
                }
            }
            """;

        String template = """
            {
                "hits": {
                    "total": "search.count",
                    "hits": "search.documents"
                }
            }
            """;

        String result = mapper.mapResponse(remoteResponse, template);
        assertNotNull("Result should not be null", result);

        Map<String, Object> parsed = parseJson(result);
        assertTrue("Should contain hits", parsed.containsKey("hits"));

        @SuppressWarnings("unchecked")
        Map<String, Object> hits = (Map<String, Object>) parsed.get("hits");
        assertEquals("Total should be 2", 2, hits.get("total"));

        @SuppressWarnings("unchecked")
        java.util.List<Object> hitsList = (java.util.List<Object>) hits.get("hits");
        assertEquals("Should have 2 hits", 2, hitsList.size());
    }

    public void testMapResponseWithArrayAccess() throws Exception {
        String remoteResponse = """
            {
                "results": [
                    {"docs": [{"id": "1", "title": "First"}, {"id": "2", "title": "Second"}]},
                    {"docs": [{"id": "3", "title": "Third"}]}
                ]
            }
            """;

        String template = """
            {
                "first_doc_id": "results[0].docs[0].id",
                "first_doc_title": "results[0].docs[0].title",
                "second_batch_first_doc": "results[1].docs[0].id"
            }
            """;

        String result = mapper.mapResponse(remoteResponse, template);
        assertNotNull("Result should not be null", result);

        Map<String, Object> parsed = parseJson(result);
        assertEquals("First doc ID should be 1", "1", parsed.get("first_doc_id"));
        assertEquals("First doc title should be First", "First", parsed.get("first_doc_title"));
        assertEquals("Second batch first doc should be 3", "3", parsed.get("second_batch_first_doc"));
    }

    public void testMapResponseWithTypeTransformation() throws Exception {
        String remoteResponse = """
            {
                "stats": {
                    "total": "100",
                    "score": "95.5",
                    "active": "true"
                }
            }
            """;

        String template = """
            {
                "total_count": {"path": "stats.total", "type": "integer"},
                "average_score": {"path": "stats.score", "type": "double"},
                "is_active": {"path": "stats.active", "type": "boolean"}
            }
            """;

        String result = mapper.mapResponse(remoteResponse, template);
        assertNotNull("Result should not be null", result);

        Map<String, Object> parsed = parseJson(result);
        assertEquals("Total should be integer 100", 100, parsed.get("total_count"));
        assertEquals("Score should be double 95.5", 95.5, parsed.get("average_score"));
        assertEquals("Active should be boolean true", true, parsed.get("is_active"));
    }

    public void testMapResponseWithDefaultValues() throws Exception {
        String remoteResponse = """
            {
                "partial_data": {
                    "available": "yes"
                }
            }
            """;

        String template = """
            {
                "available": "partial_data.available",
                "missing_field": {"path": "partial_data.missing", "default": "not_found"},
                "missing_number": {"path": "partial_data.count", "type": "integer", "default": 0}
            }
            """;

        String result = mapper.mapResponse(remoteResponse, template);
        assertNotNull("Result should not be null", result);

        Map<String, Object> parsed = parseJson(result);
        assertEquals("Available should be yes", "yes", parsed.get("available"));
        assertEquals("Missing field should use default", "not_found", parsed.get("missing_field"));
        assertEquals("Missing number should use default", 0, parsed.get("missing_number"));
    }

    public void testMapResponseWithDocumentsFormat() throws Exception {
        String documentsResponse = """
            {
                "documents": [
                    {"id": "doc1", "title": "Document 1", "score": 0.9},
                    {"id": "doc2", "title": "Document 2", "score": 0.8}
                ],
                "total": 2
            }
            """;

        String result = mapper.mapResponse(documentsResponse, null);
        assertNotNull("Result should not be null", result);

        Map<String, Object> parsed = parseJson(result);
        assertTrue("Should contain hits", parsed.containsKey("hits"));

        @SuppressWarnings("unchecked")
        Map<String, Object> hits = (Map<String, Object>) parsed.get("hits");
        @SuppressWarnings("unchecked")
        Map<String, Object> total = (Map<String, Object>) hits.get("total");
        assertEquals("Total should be 2", 2, total.get("value"));

        @SuppressWarnings("unchecked")
        java.util.List<Object> hitsList = (java.util.List<Object>) hits.get("hits");
        assertEquals("Should have 2 hits", 2, hitsList.size());
    }

    public void testMapResponseWithUnknownFormat() throws Exception {
        String unknownResponse = """
            {
                "custom_field": "custom_value",
                "nested": {
                    "data": "some data"
                }
            }
            """;

        String result = mapper.mapResponse(unknownResponse, null);
        assertNotNull("Result should not be null", result);

        Map<String, Object> parsed = parseJson(result);
        assertTrue("Should contain hits", parsed.containsKey("hits"));

        @SuppressWarnings("unchecked")
        Map<String, Object> hits = (Map<String, Object>) parsed.get("hits");
        @SuppressWarnings("unchecked")
        Map<String, Object> total = (Map<String, Object>) hits.get("total");
        assertEquals("Total should be 1", 1, total.get("value"));

        @SuppressWarnings("unchecked")
        java.util.List<Object> hitsList = (java.util.List<Object>) hits.get("hits");
        assertEquals("Should have 1 hit", 1, hitsList.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> firstHit = (Map<String, Object>) hitsList.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> source = (Map<String, Object>) firstHit.get("_source");
        assertEquals("Source should contain custom_field", "custom_value", source.get("custom_field"));
    }

    public void testMapResponseWithInvalidJson() throws Exception {
        String invalidJson = "{ invalid json }";

        String result = mapper.mapResponse(invalidJson, null);
        assertNotNull("Result should not be null", result);

        // Should return the original response when parsing fails
        assertEquals("Should return original response", invalidJson, result);
    }

    public void testMapResponseWithErrorTemplate() throws Exception {
        String remoteResponse = """
            {
                "data": {
                    "results": []
                }
            }
            """;

        String invalidTemplate = "{ invalid template json }";

        String result = mapper.mapResponse(remoteResponse, invalidTemplate);
        assertNotNull("Result should not be null", result);

        Map<String, Object> parsed = parseJson(result);
        assertTrue("Should contain error", parsed.containsKey("error"));

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) parsed.get("error");
        assertEquals("Error type should be remote_mapping_exception", "remote_mapping_exception", error.get("type"));
    }

    public void testMapResponseSolrFormat() throws Exception {
        String solrResponse = """
            {
              "responseHeader": {"QTime": 7},
              "response": {
                "numFound": 2,
                "start": 0,
                "docs": [
                  {"id": "A1", "title": "Doc A", "score": 1.23},
                  {"title": "Doc B"}
                ]
              }
            }
            """;

        String result = mapper.mapResponse(solrResponse, null);
        assertNotNull("Result should not be null", result);

        Map<String, Object> parsed = parseJson(result);
        assertTrue("Should contain hits", parsed.containsKey("hits"));
        assertTrue("Should contain took", parsed.containsKey("took"));
        assertEquals("Took should map from QTime", 7, parsed.get("took"));

        @SuppressWarnings("unchecked")
        Map<String, Object> hits = (Map<String, Object>) parsed.get("hits");
        @SuppressWarnings("unchecked")
        Map<String, Object> total = (Map<String, Object>) hits.get("total");
        assertEquals("Total should equal numFound", 2, total.get("value"));

        @SuppressWarnings("unchecked")
        java.util.List<Object> hitsList = (java.util.List<Object>) hits.get("hits");
        assertEquals("Should have 2 hits", 2, hitsList.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> firstHit = (Map<String, Object>) hitsList.get(0);
        assertEquals("First hit ID should be A1", "A1", firstHit.get("_id"));
        assertEquals("First hit score should be 1.23", 1.23, firstHit.get("_score"));

        @SuppressWarnings("unchecked")
        Map<String, Object> secondHit = (Map<String, Object>) hitsList.get(1);
        // When id is missing, fallback to ordinal index string ("1")
        assertEquals("Second hit ID should fallback to index string", "1", secondHit.get("_id"));
        // Score should fallback to 1.0 when missing
        assertEquals("Second hit score should fallback to 1.0", 1.0, secondHit.get("_score"));
    }

    public void testMapResponseWithTemplateArrayInsertion() throws Exception {
        String remoteResponse = """
            {
                "data": {
                    "items": [
                        {"id": "1", "title": "A"},
                        {"id": "2", "title": "B"}
                    ]
                }
            }
            """;

        String template = """
            {
                "items": ${data.items}
            }
            """;

        String result = mapper.mapResponse(remoteResponse, template);
        assertNotNull("Result should not be null", result);

        Map<String, Object> parsed = parseJson(result);
        assertTrue("Result should contain items", parsed.containsKey("items"));

        @SuppressWarnings("unchecked")
        java.util.List<Object> items = (java.util.List<Object>) parsed.get("items");
        assertEquals("Should have 2 items", 2, items.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) items.get(0);
        assertEquals("First item id should be 1", "1", first.get("id"));
    }

    public void testMapResponseWithTemplateObjectInsertion() throws Exception {
        String remoteResponse = """
            {
                "meta": {
                    "stats": {
                        "count": 5,
                        "ok": true
                    }
                }
            }
            """;

        String template = """
            {
                "stats": ${meta.stats}
            }
            """;

        String result = mapper.mapResponse(remoteResponse, template);
        assertNotNull("Result should not be null", result);

        Map<String, Object> parsed = parseJson(result);
        assertTrue("Result should contain stats", parsed.containsKey("stats"));

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) parsed.get("stats");
        assertEquals("Count should be 5", 5, stats.get("count"));
        assertEquals("Ok should be true", true, stats.get("ok"));
    }

    /**
     * Helper method to parse JSON string to Map
     */
    private Map<String, Object> parseJson(String json) throws Exception {
        try (XContentParser parser = XContentFactory.jsonBuilder().contentType().xContent().createParser(null, null, json)) {
            return parser.map();
        }
    }
}
