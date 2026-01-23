/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.bwc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

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
 * Provides common helper methods, constants, and configuration values
 * for backward compatibility tests.
 */
public final class IndexMappingTestHelper {

    // ==================== Client Configuration ====================

    /**
     * Client socket timeout value for BWC tests.
     * Extended timeout (120s) to accommodate delays during cluster transitions
     * such as node upgrades, shard rebalancing, and cluster state propagation.
     */
    public static final String CLIENT_TIMEOUT_VALUE = "120s";

    // ==================== System Property Keys ====================

    /** System property key for BWC suite cluster type (old_cluster, mixed_cluster, upgraded_cluster) */
    public static final String BWC_CLUSTER_TYPE_PROPERTY = "tests.rest.bwcsuite_cluster";

    /** System property key for first round flag in mixed cluster phase */
    public static final String FIRST_ROUND_PROPERTY = "tests.rest.first_round";

    /** System property key for the BWC plugin version being tested */
    public static final String BWC_VERSION_PROPERTY = "tests.plugin_bwc_version";

    // ==================== Cluster Type Values ====================

    /** Value for old cluster type in system property */
    public static final String OLD_CLUSTER = "old_cluster";

    /** Value for mixed cluster type in system property */
    public static final String MIXED_CLUSTER = "mixed_cluster";

    /** Value for upgraded cluster type in system property */
    public static final String UPGRADED_CLUSTER = "upgraded_cluster";

    // ==================== BWC Test Prefixes ====================

    /** Prefix for rolling upgrade BWC test index names */
    public static final String ROLLING_UPGRADE_BWC_PREFIX = "search-relevance-bwc-";

    /** Prefix for restart upgrade BWC test index names */
    public static final String RESTART_UPGRADE_BWC_PREFIX = "search-relevance-bwc-restart-";

    /** Prefix for rolling upgrade query set names */
    public static final String ROLLING_UPGRADE_QUERYSET_PREFIX = "bwc-queryset-";

    /** Prefix for restart upgrade query set names */
    public static final String RESTART_UPGRADE_QUERYSET_PREFIX = "bwc-restart-queryset-";

    /** Prefix for rolling upgrade judgment names */
    public static final String ROLLING_UPGRADE_JUDGMENT_PREFIX = "bwc-judgment-";

    /** Prefix for restart upgrade judgment names */
    public static final String RESTART_UPGRADE_JUDGMENT_PREFIX = "bwc-restart-judgment-";

    /** Prefix for rolling upgrade search config names */
    public static final String ROLLING_UPGRADE_SEARCH_CONFIG_PREFIX = "bwc-search-config-";

    /** Prefix for restart upgrade search config names */
    public static final String RESTART_UPGRADE_SEARCH_CONFIG_PREFIX = "bwc-restart-search-config-";

    // ==================== Index and Mapping Configuration ====================

    public static final String JUDGMENT_CACHE_INDEX = ".plugins-search-relevance-judgment-cache";

    /** Resource path for old mapping (schema version 0) */
    public static final String OLD_MAPPING_RESOURCE = "mappings/judgment_cache_v0.json";

    /** Resource path for new mapping (schema version 1) */
    public static final String NEW_MAPPING_RESOURCE = "mappings/judgment_cache_v1.json";

    /** Resource path for test document */
    public static final String TEST_DOCUMENT_RESOURCE = "mappings/test_document.json";

    /** Test document ID for BWC tests */
    public static final String TEST_DOC_ID = "test-doc-1";

    private IndexMappingTestHelper() {
        // Utility class - prevent instantiation
    }

    // ==================== Resource Loading Methods ====================

    /**
     * Reads a mapping file from the classpath resources.
     *
     * @param resourcePath Path to the resource file (e.g., "mappings/judgment_cache_v0.json")
     * @return The content of the file as a String
     * @throws IOException if the resource cannot be read
     */
    public static String readMappingResource(String resourcePath) throws IOException {
        try (InputStream inputStream = IndexMappingTestHelper.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    /**
     * Gets the old mapping (schema version 0) from resources.
     *
     * @return The old mapping JSON string
     * @throws IOException if the resource cannot be read
     */
    public static String getOldMapping() throws IOException {
        return readMappingResource(OLD_MAPPING_RESOURCE);
    }

    /**
     * Gets the new mapping (schema version 1) from resources.
     *
     * @return The new mapping JSON string
     * @throws IOException if the resource cannot be read
     */
    public static String getNewMapping() throws IOException {
        return readMappingResource(NEW_MAPPING_RESOURCE);
    }

    /**
     * Gets the test document from resources.
     *
     * @return The test document JSON string
     * @throws IOException if the resource cannot be read
     */
    public static String getTestDocument() throws IOException {
        return readMappingResource(TEST_DOCUMENT_RESOURCE);
    }

    // ==================== Index Operations ====================

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
