/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ubi;

import static org.opensearch.searchrelevance.common.PluginConstants.USER_QUERY_FIELD;

import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.service.ClusterService;

public class UbiValidator {

    /**
     * Checks if UBI queries index exists in the cluster and has required fields
     * @param clusterService opensearch cluster instance
     * @param ubiQueriesIndex the UBI queries index name
     * @return true if queries index exists with required fields, false otherwise
     */
    public static boolean checkUbiQueriesIndexExists(ClusterService clusterService, String ubiQueriesIndex) {
        if (clusterService == null || !clusterService.state().metadata().hasIndex(ubiQueriesIndex)) {
            return false;
        }

        MappingMetadata mappingMetadata = clusterService.state().metadata().index(ubiQueriesIndex).mapping();
        if (mappingMetadata == null) {
            return false;
        }

        return hasField(mappingMetadata, USER_QUERY_FIELD);
    }

    /**
     * Checks if UBI events index exists in the cluster and has required fields
     * @param clusterService opensearch cluster instance
     * @param ubiEventsIndex the UBI events index name
     * @return true if events index exists with required fields, false otherwise
     */
    public static boolean checkUbiEventsIndexExists(ClusterService clusterService, String ubiEventsIndex) {
        if (clusterService == null || !clusterService.state().metadata().hasIndex(ubiEventsIndex)) {
            return false;
        }

        MappingMetadata mappingMetadata = clusterService.state().metadata().index(ubiEventsIndex).mapping();
        if (mappingMetadata == null) {
            return false;
        }

        return hasField(mappingMetadata, "query_id")
            && hasField(mappingMetadata, "action_name")
            && hasField(mappingMetadata, "event_attributes.object.object_id");
    }

    @SuppressWarnings("unchecked")
    private static boolean hasField(MappingMetadata mappingMetadata, String fieldPath) {
        var sourceAsMap = mappingMetadata.sourceAsMap();
        var properties = (java.util.Map<String, Object>) sourceAsMap.get("properties");

        if (properties == null) {
            return false;
        }

        String[] pathParts = fieldPath.split("\\.");
        java.util.Map<String, Object> current = properties;

        for (int i = 0; i < pathParts.length; i++) {
            if (!current.containsKey(pathParts[i])) {
                return false;
            }

            if (i < pathParts.length - 1) {
                var fieldDef = (java.util.Map<String, Object>) current.get(pathParts[i]);
                current = (java.util.Map<String, Object>) fieldDef.get("properties");
                if (current == null) {
                    return false;
                }
            }
        }

        return true;
    }
}
