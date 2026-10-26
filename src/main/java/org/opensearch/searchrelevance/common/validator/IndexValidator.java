/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.common.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.service.ClusterService;

/**
 * Utility class for validating index existence and mapping fields.
 */
public class IndexValidator {

    /**
     * Checks if the given index exists in the cluster and has a mapping.
     * @param clusterService opensearch cluster instance
     * @param index the index name
     * @return true if index exists with a mapping, false otherwise
     */
    public static boolean checkIndexAndMappingExists(ClusterService clusterService, String index) {
        if (clusterService == null || !clusterService.state().metadata().hasIndex(index)) {
            return false;
        }

        MappingMetadata mappingMetadata = clusterService.state().metadata().index(index).mapping();
        return mappingMetadata != null;
    }

    /**
     * Validates that the specified context fields exist in the index mapping.
     *
     * @param mappingMetadata the mapping metadata of the index to validate against
     * @param contextFields the list of field paths to check, supports nested fields using dot notation
     */
    public static void validateFieldsExistInIndexMapping(MappingMetadata mappingMetadata, List<String> contextFields) {
        List<String> invalidFields = new ArrayList<>();
        for (String contextField : contextFields) {
            if (!hasField(mappingMetadata, contextField)) {
                invalidFields.add(contextField);
            }
        }
        if (!invalidFields.isEmpty()) {
            throw new IllegalArgumentException("Context fields " + invalidFields + " do not exist in the index mapping");
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean hasField(MappingMetadata mappingMetadata, String fieldPath) {
        var sourceAsMap = mappingMetadata.sourceAsMap();
        if (!(sourceAsMap.get("properties") instanceof Map)) {
            return false;
        }
        Map<String, Object> current = (Map<String, Object>) sourceAsMap.get("properties");

        String[] pathParts = fieldPath.split("\\.");
        for (int i = 0; i < pathParts.length; i++) {
            if (!current.containsKey(pathParts[i])) {
                return false;
            }
            if (i < pathParts.length - 1) {
                if (!(current.get(pathParts[i]) instanceof Map)) {
                    return false;
                }
                var fieldDef = (Map<String, Object>) current.get(pathParts[i]);
                if (!(fieldDef.get("properties") instanceof Map)) {
                    return false;
                }
                current = (Map<String, Object>) fieldDef.get("properties");
            }
        }
        return true;
    }
}
