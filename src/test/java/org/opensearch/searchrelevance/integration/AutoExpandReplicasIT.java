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

import org.junit.Test;
import org.opensearch.client.Response;
import org.opensearch.searchrelevance.BaseSearchRelevanceIT;

public class AutoExpandReplicasIT extends BaseSearchRelevanceIT {

    @Test
    public void testAutoExpandReplicasSettingPresent() throws Exception {
        // Create an index via plugin flow by creating a search configuration that references it
        String userIndexName = "test-auto-expand-replicas-index";
        String template = readTemplate("src/test/resources/searchconfig/CreateSearchConfigurationSimpleMatch.json");
        String body = template.replace("{{index_name}}", userIndexName);

        // call plugin to create a search configuration which will trigger the plugin's system index creation
        Response resp = makeRequest(
            client(),
            "PUT",
            "/_plugins/_search_relevance/search_configurations/" + "test-config",
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
    }

    private String readTemplate(String path) throws Exception {
        java.nio.file.Path p = java.nio.file.Paths.get(path);
        return java.nio.file.Files.readString(p);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToMap(Response response) throws Exception {
        String json = org.apache.http.util.EntityUtils.toString(response.getEntity());
        return (Map<String, Object>) org.opensearch.common.xcontent.json.JsonXContent.jsonXContent.createParser(
            org.opensearch.core.xcontent.NamedXContentRegistry.EMPTY,
            json
        ).map();
    }
}
