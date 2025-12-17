/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.bwc;

import java.io.IOException;
import java.util.Map;

import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.logging.log4j.Logger;
import org.opensearch.client.Request;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.client.WarningsHandler;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;

/**
 * Utility class for index mapping BWC tests.
 * Provides common helper methods for creating, validating, and updating index mappings
 * during backward compatibility tests.
 */
public final class IndexMappingTestHelper {

    public static final String JUDGMENT_CACHE_INDEX = ".plugins-search-relevance-judgment-cache";

    // Old schema (version 0) - without modelId and encodedPromptTemplate fields
    public static final String OLD_MAPPING = "{"
        + "\"_meta\": { \"schema_version\": 0 },"
        + "\"properties\": {"
        + "  \"id\": { \"type\": \"keyword\" },"
        + "  \"timestamp\": { \"type\": \"date\", \"format\": \"strict_date_time\" },"
        + "  \"querySet\": { \"type\": \"keyword\" },"
        + "  \"documentId\": { \"type\": \"keyword\" },"
        + "  \"contextFieldsStr\": { \"type\": \"keyword\" },"
        + "  \"rating\": { \"type\": \"keyword\" }"
        + "}"
        + "}";

    // New schema (version 1) - with modelId and encodedPromptTemplate fields
    public static final String NEW_MAPPING = "{"
        + "\"_meta\": { \"schema_version\": 1 },"
        + "\"properties\": {"
        + "  \"id\": { \"type\": \"keyword\" },"
        + "  \"timestamp\": { \"type\": \"date\", \"format\": \"strict_date_time\" },"
        + "  \"querySet\": { \"type\": \"keyword\" },"
        + "  \"documentId\": { \"type\": \"keyword\" },"
        + "  \"contextFieldsStr\": { \"type\": \"keyword\" },"
        + "  \"rating\": { \"type\": \"keyword\" },"
        + "  \"modelId\": { \"type\": \"keyword\" },"
        + "  \"encodedPromptTemplate\": { \"type\": \"keyword\" }"
        + "}"
        + "}";

    public static final String TEST_DOCUMENT = "{"
        + "\"id\": \"test-cache-entry\","
        + "\"timestamp\": \"2024-01-01T00:00:00.000Z\","
        + "\"querySet\": \"test-query-set\","
        + "\"documentId\": \"doc-1\","
        + "\"contextFieldsStr\": \"field1,field2\","
        + "\"rating\": \"0.85\""
        + "}";

    public static final String TEST_DOC_ID = "test-doc-1";

    private IndexMappingTestHelper() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates an index with the specified mapping.
     */
    public static void createIndexWithMapping(RestClient client, String indexName, String mapping, Logger logger) throws IOException {
        Request request = new Request("PUT", "/" + indexName);
        setPermissiveWarningsHandler(request);
        request.setJsonEntity(
            "{"
                + "\"settings\": {"
                + "  \"index\": {"
                + "    \"number_of_shards\": 1,"
                + "    \"number_of_replicas\": 0,"
                + "    \"auto_expand_replicas\": \"0-1\""
                + "  }"
                + "},"
                + "\"mappings\": "
                + mapping
                + "}"
        );

        Response response = client.performRequest(request);
        int statusCode = response.getStatusLine().getStatusCode();
        logger.info("PUT response status for {}: {}", indexName, statusCode);
        if (statusCode != 200) {
            throw new IOException("Failed to create index " + indexName + ": status " + statusCode);
        }
    }

    /**
     * Checks if an index exists.
     */
    public static boolean checkIndexExists(RestClient client, String indexName, Logger logger) throws IOException {
        try {
            Request request = new Request("HEAD", "/" + indexName);
            setPermissiveWarningsHandler(request);
            Response response = client.performRequest(request);
            int status = response.getStatusLine().getStatusCode();
            logger.info("HEAD request for {} returned status: {}", indexName, status);
            return status == 200;
        } catch (Exception e) {
            logger.info("HEAD request for {} threw exception: {}", indexName, e.getMessage());
            return false;
        }
    }

    /**
     * Gets the mapping for an index.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getIndexMapping(RestClient client, String indexName) throws IOException, ParseException {
        Request request = new Request("GET", "/" + indexName + "/_mapping");
        setPermissiveWarningsHandler(request);
        Response response = client.performRequest(request);
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new IOException("Failed to get mapping for " + indexName);
        }

        Map<String, Object> responseMap = parseResponse(response);
        Map<String, Object> indexMapping = (Map<String, Object>) responseMap.get(indexName);
        if (indexMapping != null) {
            return (Map<String, Object>) indexMapping.get("mappings");
        }
        return null;
    }

    /**
     * Extracts properties from mapping.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMappingProperties(Map<String, Object> mapping) {
        if (mapping != null) {
            return (Map<String, Object>) mapping.get("properties");
        }
        return null;
    }

    /**
     * Extracts _meta from mapping.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMappingMeta(Map<String, Object> mapping) {
        if (mapping != null) {
            return (Map<String, Object>) mapping.get("_meta");
        }
        return null;
    }

    /**
     * Inserts a test document into the specified index.
     */
    public static void insertTestDocument(RestClient client, String indexName, String docId, String document) throws IOException {
        Request request = new Request("POST", "/" + indexName + "/_doc/" + docId + "?refresh=true");
        setPermissiveWarningsHandler(request);
        request.setJsonEntity(document);

        Response response = client.performRequest(request);
        if (response.getStatusLine().getStatusCode() != 201) {
            throw new IOException("Failed to insert test document: status " + response.getStatusLine().getStatusCode());
        }
    }

    /**
     * Gets a document from the specified index.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getDocument(RestClient client, String indexName, String docId, Logger logger) throws IOException,
        ParseException {
        try {
            Request request = new Request("GET", "/" + indexName + "/_doc/" + docId);
            setPermissiveWarningsHandler(request);
            Response response = client.performRequest(request);
            if (response.getStatusLine().getStatusCode() == 200) {
                Map<String, Object> responseMap = parseResponse(response);
                return (Map<String, Object>) responseMap.get("_source");
            }
        } catch (Exception e) {
            logger.debug("Failed to get document {}: {}", docId, e.getMessage());
        }
        return null;
    }

    /**
     * Updates the mapping for an index.
     */
    public static void updateMapping(RestClient client, String indexName, String mapping, Logger logger) throws IOException {
        Request request = new Request("PUT", "/" + indexName + "/_mapping");
        setPermissiveWarningsHandler(request);
        request.setJsonEntity(mapping);

        Response response = client.performRequest(request);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            throw new IOException("Failed to update mapping for " + indexName + ": status " + statusCode);
        }
        logger.info("Updated mapping for index {}", indexName);
    }

    /**
     * Waits for mapping to be updated with expected fields.
     */
    public static void waitForMappingUpdate(RestClient client, String indexName, String[] expectedFields, int timeoutSeconds, Logger logger)
        throws Exception {
        long startTime = System.currentTimeMillis();
        long timeout = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeout) {
            try {
                Map<String, Object> mapping = getIndexMapping(client, indexName);
                Map<String, Object> properties = getMappingProperties(mapping);

                if (properties != null) {
                    boolean allFieldsPresent = true;
                    for (String field : expectedFields) {
                        if (!properties.containsKey(field)) {
                            allFieldsPresent = false;
                            break;
                        }
                    }
                    if (allFieldsPresent) {
                        logger.info("Mapping update detected - all expected fields present");
                        return;
                    }
                }
            } catch (Exception e) {
                logger.debug("Error checking mapping: {}", e.getMessage());
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Timeout waiting for mapping update with fields: " + String.join(", ", expectedFields));
    }

    /**
     * Deletes an index.
     */
    public static void deleteIndex(RestClient client, String indexName, Logger logger) {
        try {
            Request request = new Request("DELETE", "/" + indexName);
            setPermissiveWarningsHandler(request);
            client.performRequest(request);
            logger.info("Deleted index {}", indexName);
        } catch (Exception e) {
            logger.debug("Failed to delete index {}: {}", indexName, e.getMessage());
        }
    }

    /**
     * Parses HTTP response to Map.
     */
    public static Map<String, Object> parseResponse(Response response) throws IOException, ParseException {
        String responseBody = EntityUtils.toString(response.getEntity());
        try (
            XContentParser parser = JsonXContent.jsonXContent.createParser(
                NamedXContentRegistry.EMPTY,
                DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                responseBody
            )
        ) {
            return parser.map();
        }
    }

    /**
     * Sets permissive warnings handler on a request to ignore system index warnings.
     */
    private static void setPermissiveWarningsHandler(Request request) {
        RequestOptions.Builder options = RequestOptions.DEFAULT.toBuilder();
        options.setWarningsHandler(WarningsHandler.PERMISSIVE);
        request.setOptions(options.build());
    }
}
