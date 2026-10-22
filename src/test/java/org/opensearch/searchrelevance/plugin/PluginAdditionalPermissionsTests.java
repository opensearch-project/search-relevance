/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.plugin;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.opensearch.common.xcontent.yaml.YamlXContent;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.test.OpenSearchTestCase;

/** Tests the shape of the permissions the security plugin grants to this plugin's subject. */
public class PluginAdditionalPermissionsTests extends OpenSearchTestCase {

    private static final String WRITE_PREFIX = "indices:data/write/";
    private static final String BULK_GRANT = "indices:data/write/bulk*";

    public void testClusterPermissionsKeyIsPresent() throws Exception {
        assertTrue("an absent key leaves an immutable list", load().get("cluster_permissions") instanceof List);
    }

    public void testEveryActionIsALiteralNameOrWildcard() throws Exception {
        for (Map<String, Object> grant : indexPermissions()) {
            for (String action : allowedActions(grant)) {
                // Action-group names such as "read" or "write" have no colon and match no action.
                assertTrue("not an action name: " + action, action.startsWith("indices:") || action.startsWith("cluster:"));
            }
        }
    }

    public void testEveryWriteGrantAlsoGrantsTheShardBulkAction() throws Exception {
        for (Map<String, Object> grant : indexPermissions()) {
            List<String> actions = allowedActions(grant);
            if (actions.stream().anyMatch(a -> a.startsWith(WRITE_PREFIX) && !a.equals(BULK_GRANT))) {
                // Index, update and delete run on the shard as indices:data/write/bulk[s] against the same indices.
                assertTrue("write grant without " + BULK_GRANT + ": " + grant, actions.contains(BULK_GRANT));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> indexPermissions() throws Exception {
        List<Map<String, Object>> grants = (List<Map<String, Object>>) load().get("index_permissions");
        assertFalse(grants.isEmpty());
        return grants;
    }

    @SuppressWarnings("unchecked")
    private static List<String> allowedActions(Map<String, Object> grant) {
        return (List<String>) grant.get("allowed_actions");
    }

    private static Map<String, Object> load() throws Exception {
        try (
            InputStream in = PluginAdditionalPermissionsTests.class.getClassLoader()
                .getResourceAsStream("plugin-additional-permissions.yml");
            XContentParser parser = YamlXContent.yamlXContent.createParser(
                NamedXContentRegistry.EMPTY,
                DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                in
            )
        ) {
            return parser.map();
        }
    }
}
