/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.integration;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.opensearch.client.Response;
import org.opensearch.searchrelevance.BaseSearchRelevanceIT;

public class AutoExpandReplicasIT extends BaseSearchRelevanceIT {

    public void testAutoExpandReplicasSettingPresent() throws Exception {
        // Create an index via plugin flow by creating a search configuration that references it
        String userIndexName = "test-auto-expand-replicas-index";
        String template = readTemplate("src/test/resources/searchconfig/CreateSearchConfigurationSimpleMatch.json");
        String body = template.replace("{{index_name}}", userIndexName);

        // call plugin to create a search configuration which will trigger the plugin's system index creation
        Response resp = makeRequest(
            client(),
            "PUT",
            org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATIONS_URL,
            null,
            toHttpEntity(body),
            null
        );
        assertEquals(200, resp.getStatusLine().getStatusCode());

        // fetch the plugin system index settings that should be created by the plugin
        String systemIndex = org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATION_INDEX;
        Response settingsResp = makeRequest(client(), "GET", "/" + systemIndex + "/_settings", null, null, null);
        Map<String, Object> settingsMap = convertToMap(settingsResp);
        @SuppressWarnings("unchecked")
        Map<String, Object> indexSettings = (Map<String, Object>) ((Map<String, Object>) settingsMap.get(systemIndex)).get("settings");
        @SuppressWarnings("unchecked")
        Map<String, Object> index = (Map<String, Object>) indexSettings.get("index");
        assertEquals("0-1", index.get("auto_expand_replicas").toString());

        // Verify the system index is healthy (green) on a single-node cluster and has no unassigned shards
        Response healthResp = makeRequest(
            client(),
            "GET",
            "/_cluster/health/" + systemIndex + "?wait_for_status=green&timeout=30s",
            null,
            null,
            null
        );
        Map<String, Object> healthMap = convertToMap(healthResp);
        assertEquals("green", healthMap.get("status").toString());
        Object unassigned = healthMap.get("unassigned_shards");
        int unassignedInt = Integer.parseInt(unassigned.toString());
        assertEquals(0, unassignedInt);
    }

    public void testControlIndexReplicas1IsYellowOnSingleNode() throws Exception {
        // Create a control index with number_of_replicas: 1 which should be yellow on single-node
        String controlIndex = "control-replicas-1";
        String indexConfig = "{ \"settings\": { \"number_of_shards\": 1, \"number_of_replicas\": 1 } }";

        createIndexWithConfiguration(controlIndex, indexConfig);

        // Check index-level cluster health (no wait for green) - it should report yellow on single node
        Response healthResp = makeRequest(client(), "GET", "/_cluster/health/" + controlIndex + "?timeout=30s", null, null, null);
        Map<String, Object> healthMap = convertToMap(healthResp);
        assertEquals("yellow", healthMap.get("status").toString());
    }

    private String readTemplate(String path) throws Exception {
        // Load resource from classpath so tests running in testclusters can access it reliably
        String resourcePath = path;
        final String prefix = "src/test/resources/";
        if (resourcePath.startsWith(prefix)) {
            resourcePath = resourcePath.substring(prefix.length());
        } else if (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }
        try (java.io.InputStream is = this.getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found on classpath: " + resourcePath);
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

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
