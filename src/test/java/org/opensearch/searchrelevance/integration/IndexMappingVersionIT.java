/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.opensearch.client.Response;
import org.opensearch.searchrelevance.BaseSearchRelevanceIT;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndices;

/**
 * Integration tests for index mapping versioning feature.
 *
 * These tests verify that:
 * 1. System indices are created with _meta.schema_version in their mappings
 * 2. The schema_version in mappings matches what's defined in the JSON mapping files
 * 3. All expected fields are present in the mappings
 */
public class IndexMappingVersionIT extends BaseSearchRelevanceIT {

    /**
     * Test that system indices have schema_version in their _meta field.
     * This test triggers index creation by calling the plugin API, then verifies
     * the mapping contains the expected _meta.schema_version.
     */
    public void testSystemIndicesHaveSchemaVersion() throws Exception {
        // Create a search configuration to trigger system index creation
        String userIndexName = "test-schema-version-index";
        String template = readTemplate("searchconfig/CreateSearchConfigurationSimpleMatch.json");
        String body = template.replace("{{index_name}}", userIndexName);

        Response resp = makeRequest(
            client(),
            "PUT",
            org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATIONS_URL,
            null,
            toHttpEntity(body),
            null
        );
        assertEquals(200, resp.getStatusLine().getStatusCode());

        // Verify search configuration index has schema_version in mapping
        String searchConfigIndex = org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATION_INDEX;
        verifyIndexHasSchemaVersion(searchConfigIndex, SearchRelevanceIndices.SEARCH_CONFIGURATION.getSchemaVersion());
    }

    /**
     * Test that query set index has correct schema_version.
     * This test creates a query set which triggers the query_set index creation.
     */
    public void testQuerySetIndexHasSchemaVersion() throws Exception {
        // Create test index
        String testIndexName = "test-queryset-schema";
        String indexConfig = "{ \"settings\": { \"number_of_shards\": 1, \"number_of_replicas\": 0 }, "
            + "\"mappings\": { \"properties\": { \"text\": { \"type\": \"text\" } } } }";
        createIndexWithConfiguration(testIndexName, indexConfig);

        // Create query set
        String querySetBody = "{"
            + "\"name\": \"test-queryset-schema\","
            + "\"description\": \"Test query set\","
            + "\"sampling\": \"manual\","
            + "\"querySetQueries\": ["
            + "  {\"queryText\": \"test\", \"referenceAnswer\": \"test answer\"}"
            + "]"
            + "}";

        Response querySetResp = makeRequest(
            client(),
            "PUT",
            org.opensearch.searchrelevance.common.PluginConstants.QUERYSETS_URL,
            null,
            toHttpEntity(querySetBody),
            null
        );
        assertEquals(200, querySetResp.getStatusLine().getStatusCode());

        // Verify query set index has schema_version
        String querySetIndex = org.opensearch.searchrelevance.common.PluginConstants.QUERY_SET_INDEX;
        verifyIndexHasSchemaVersion(querySetIndex, SearchRelevanceIndices.QUERY_SET.getSchemaVersion());
    }

    /**
     * Test that all SearchRelevanceIndices have valid schema versions defined.
     * This validates that the enum correctly parses schema_version from JSON mapping files.
     */
    public void testAllIndicesHaveValidSchemaVersions() {
        for (SearchRelevanceIndices index : SearchRelevanceIndices.values()) {
            int schemaVersion = index.getSchemaVersion();
            assertTrue("Index " + index.getIndexName() + " should have schema_version >= 0, but was " + schemaVersion, schemaVersion >= 0);

            // Verify mapping JSON contains _meta.schema_version
            String mapping = index.getMapping();
            assertTrue("Index " + index.getIndexName() + " mapping should contain _meta", mapping.contains("\"_meta\""));
            assertTrue("Index " + index.getIndexName() + " mapping should contain schema_version", mapping.contains("\"schema_version\""));
        }
    }

    /**
     * Test that judgment_cache index has the new fields (modelId, encodedPromptTemplate).
     * These fields were added in schema_version 1.
     */
    public void testJudgmentCacheMappingHasNewFields() {
        String mapping = SearchRelevanceIndices.JUDGMENT_CACHE.getMapping();

        // Verify new fields exist in the mapping
        assertTrue("Mapping should contain modelId field", mapping.contains("\"modelId\""));
        assertTrue("Mapping should contain encodedPromptTemplate field", mapping.contains("\"encodedPromptTemplate\""));

        // Verify schema_version is 1 (since we added new fields)
        assertEquals("JUDGMENT_CACHE should have schema_version 1", 1, SearchRelevanceIndices.JUDGMENT_CACHE.getSchemaVersion());
    }

    /**
     * Helper method to verify an index has the expected schema_version in its mapping.
     */
    private void verifyIndexHasSchemaVersion(String indexName, int expectedVersion) throws Exception {
        // Wait for index to be created
        waitForIndexCreation(indexName, 30);

        // Get mapping
        Response mappingResp = makeRequest(client(), "GET", "/" + indexName + "/_mapping", null, null, null);
        assertEquals(200, mappingResp.getStatusLine().getStatusCode());

        Map<String, Object> mappingMap = convertToMap(mappingResp);
        assertNotNull("Mapping response should not be null", mappingMap);

        @SuppressWarnings("unchecked")
        Map<String, Object> indexMapping = (Map<String, Object>) mappingMap.get(indexName);
        assertNotNull("Index mapping should exist for " + indexName, indexMapping);

        @SuppressWarnings("unchecked")
        Map<String, Object> mappings = (Map<String, Object>) indexMapping.get("mappings");
        assertNotNull("Mappings should exist for " + indexName, mappings);

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) mappings.get("_meta");
        assertNotNull("_meta should exist in mapping for " + indexName, meta);

        Object schemaVersion = meta.get("schema_version");
        assertNotNull("schema_version should exist in _meta for " + indexName, schemaVersion);

        int actualVersion = ((Number) schemaVersion).intValue();
        assertEquals("schema_version should match expected for " + indexName, expectedVersion, actualVersion);
    }

    /**
     * Helper method to wait for index creation.
     */
    private void waitForIndexCreation(String indexName, int timeoutSeconds) throws Exception {
        long startTime = System.currentTimeMillis();
        long timeout = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeout) {
            try {
                Response response = makeRequest(client(), "HEAD", "/" + indexName, null, null, null);
                if (response.getStatusLine().getStatusCode() == 200) {
                    return;
                }
            } catch (Exception e) {
                // Index doesn't exist yet, continue waiting
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Timeout waiting for index " + indexName + " to be created");
    }

    /**
     * Helper method to read a template file from test resources.
     */
    private String readTemplate(String resourcePath) throws Exception {
        try (java.io.InputStream is = this.getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found on classpath: " + resourcePath);
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * Helper method to convert Response to Map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToMap(Response response) throws Exception {
        String json = org.apache.hc.core5.http.io.entity.EntityUtils.toString(response.getEntity());
        return (Map<String, Object>) org.opensearch.common.xcontent.XContentHelper.convertToMap(
            org.opensearch.common.xcontent.XContentType.JSON.xContent(),
            json,
            false
        );
    }
}
