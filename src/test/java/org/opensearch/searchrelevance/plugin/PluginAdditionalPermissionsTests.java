/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.plugin;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.yaml.YamlXContent;
import org.opensearch.indices.SystemIndexDescriptor;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndices;
import org.opensearch.test.OpenSearchTestCase;

/** Tests the shape of the permissions the security plugin grants to this plugin's subject. */
public class PluginAdditionalPermissionsTests extends OpenSearchTestCase {

    public void testClusterPermissionsKeyIsPresent() throws Exception {
        assertTrue("an absent key leaves an immutable list", load().get("cluster_permissions") instanceof List);
    }

    @SuppressWarnings("unchecked")
    public void testGrantsExactlyTheActionsThePluginClientSends() throws Exception {
        // Index, update and delete also run on the shard as indices:data/write/bulk[s].
        Set<String> expected = Set.of(
            "indices:admin/create",
            "indices:admin/mapping/put",
            "indices:data/read/search",
            "indices:data/write/index",
            "indices:data/write/update",
            "indices:data/write/delete",
            "indices:data/write/bulk*"
        );
        for (Map<String, Object> grant : indexPermissions()) {
            assertEquals(expected, new HashSet<>((List<String>) grant.get("allowed_actions")));
        }
    }

    @SuppressWarnings("unchecked")
    public void testCoversEveryIndexThatIsNotARegisteredSystemIndex() throws Exception {
        // A registered system index is already open to the plugin subject; every other index the plugin owns needs a grant.
        Set<String> systemIndexPatterns = new SearchRelevancePlugin().getSystemIndexDescriptors(Settings.EMPTY)
            .stream()
            .map(SystemIndexDescriptor::getIndexPattern)
            .collect(Collectors.toSet());
        Set<String> expected = Arrays.stream(SearchRelevanceIndices.values())
            .map(SearchRelevanceIndices::getIndexName)
            .filter(name -> !systemIndexPatterns.contains(name))
            .collect(Collectors.toSet());

        Set<String> granted = new HashSet<>();
        for (Map<String, Object> grant : indexPermissions()) {
            granted.addAll((List<String>) grant.get("index_patterns"));
        }
        assertEquals(expected, granted);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> indexPermissions() throws Exception {
        List<Map<String, Object>> grants = (List<Map<String, Object>>) load().get("index_permissions");
        assertFalse(grants.isEmpty());
        return grants;
    }

    private static Map<String, Object> load() throws Exception {
        try (
            InputStream in = PluginAdditionalPermissionsTests.class.getClassLoader()
                .getResourceAsStream("plugin-additional-permissions.yml")
        ) {
            return XContentHelper.convertToMap(YamlXContent.yamlXContent, in, false);
        }
    }
}
