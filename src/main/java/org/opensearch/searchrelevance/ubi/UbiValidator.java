/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ubi;

import static org.opensearch.searchrelevance.common.PluginConstants.UBI_EVENTS_INDEX_PARAM;
import static org.opensearch.searchrelevance.common.PluginConstants.UBI_QUERIES_INDEX_PARAM;
import static org.opensearch.searchrelevance.common.PluginConstants.USER_QUERY_FIELD;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.IndicesOptions;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.core.index.Index;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.indices.IndexClosedException;

public class UbiValidator {

    private static final String MAPPING_PROPERTIES = "properties";
    private static final Index[] NO_INDICES = new Index[0];
    private static final IndicesOptions RESOLUTION_OPTIONS = SearchRequest.DEFAULT_INDICES_OPTIONS;
    private static final boolean INCLUDE_DATA_STREAMS = new SearchRequest().includeDataStreams();

    private enum UbiIndexKind {
        QUERIES(List.of(USER_QUERY_FIELD), "UBI queries", UBI_QUERIES_INDEX_PARAM),
        EVENTS(List.of("query_id", "action_name", "event_attributes.object.object_id"), "UBI events", UBI_EVENTS_INDEX_PARAM);

        private final List<String> requiredFields;
        private final String label;
        private final String requestParameter;

        UbiIndexKind(List<String> requiredFields, String label, String requestParameter) {
            this.requiredFields = requiredFields;
            this.label = label;
            this.requestParameter = requestParameter;
        }
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Validates the UBI queries index expression used to sample query sets.
     * @param clusterState current cluster state
     * @param indexNameExpressionResolver resolver used to expand the expression
     * @param ubiQueriesIndex the UBI queries index expression
     * @return a valid result, or an invalid result describing what the caller has to fix
     */
    public static ValidationResult validateUbiQueriesIndex(
        ClusterState clusterState,
        IndexNameExpressionResolver indexNameExpressionResolver,
        String ubiQueriesIndex
    ) {
        return validate(clusterState, indexNameExpressionResolver, ubiQueriesIndex, UbiIndexKind.QUERIES);
    }

    /**
     * Validates the UBI events index expression used to calculate implicit judgments.
     * @param clusterState current cluster state
     * @param indexNameExpressionResolver resolver used to expand the expression
     * @param ubiEventsIndex the UBI events index expression
     * @return a valid result, or an invalid result describing what the caller has to fix
     */
    public static ValidationResult validateUbiEventsIndex(
        ClusterState clusterState,
        IndexNameExpressionResolver indexNameExpressionResolver,
        String ubiEventsIndex
    ) {
        return validate(clusterState, indexNameExpressionResolver, ubiEventsIndex, UbiIndexKind.EVENTS);
    }

    private static ValidationResult validate(
        ClusterState clusterState,
        IndexNameExpressionResolver indexNameExpressionResolver,
        String indexExpression,
        UbiIndexKind kind
    ) {
        if (indexExpression == null || indexExpression.isBlank()) {
            return invalid("%s index must not be empty. Set it with the '%s' parameter.", kind.label, kind.requestParameter);
        }

        Index[] concreteIndices;
        try {
            concreteIndices = indexNameExpressionResolver.concreteIndices(
                clusterState,
                RESOLUTION_OPTIONS,
                INCLUDE_DATA_STREAMS,
                indexExpression
            );
        } catch (IndexNotFoundException e) {
            concreteIndices = NO_INDICES;
        } catch (IndexClosedException e) {
            return invalid(
                "%s index [%s] resolves to closed index [%s], which cannot be searched. Reopen it, or point at open indices "
                    + "with the '%s' parameter.",
                kind.label,
                indexExpression,
                e.getIndex() == null ? indexExpression : e.getIndex().getName(),
                kind.requestParameter
            );
        }

        Map<String, List<String>> missingFieldsByIndex = new LinkedHashMap<>();
        for (Index index : concreteIndices) {
            IndexMetadata indexMetadata = clusterState.metadata().index(index);
            if (indexMetadata == null) {
                continue;
            }
            List<String> missingFields = findMissingFields(indexMetadata.mapping(), kind.requiredFields);
            if (missingFields.isEmpty()) {
                return new ValidationResult(true, null);
            }
            missingFieldsByIndex.put(index.getName(), missingFields);
        }

        if (missingFieldsByIndex.isEmpty()) {
            return invalid(
                "%s index [%s] does not exist. Ingest %s data into it, or point at an existing index, alias or data stream "
                    + "with the '%s' parameter.",
                kind.label,
                indexExpression,
                kind.label,
                kind.requestParameter
            );
        }
        if (missingFieldsByIndex.size() == 1) {
            Map.Entry<String, List<String>> onlyIndex = missingFieldsByIndex.entrySet().iterator().next();
            return invalid(
                "Index [%s] resolved from [%s] is not a valid %s index: its mapping is missing the required field(s) %s.",
                onlyIndex.getKey(),
                indexExpression,
                kind.label,
                onlyIndex.getValue()
            );
        }
        return invalid(
            "No index resolved from [%s] is a valid %s index. Missing required field(s) per index: %s.",
            indexExpression,
            kind.label,
            missingFieldsByIndex
        );
    }

    private static List<String> findMissingFields(MappingMetadata mappingMetadata, List<String> requiredFields) {
        if (mappingMetadata == null) {
            return new ArrayList<>(requiredFields);
        }

        List<String> missingFields = new ArrayList<>();
        for (String requiredField : requiredFields) {
            if (!hasField(mappingMetadata, requiredField)) {
                missingFields.add(requiredField);
            }
        }
        return missingFields;
    }

    private static ValidationResult invalid(String messageFormat, Object... args) {
        return new ValidationResult(false, String.format(Locale.ROOT, messageFormat, args));
    }

    @SuppressWarnings("unchecked")
    private static boolean hasField(MappingMetadata mappingMetadata, String fieldPath) {
        var sourceAsMap = mappingMetadata.sourceAsMap();
        var properties = (Map<String, Object>) sourceAsMap.get(MAPPING_PROPERTIES);

        if (properties == null) {
            return false;
        }

        String[] pathParts = fieldPath.split("\\.");
        Map<String, Object> current = properties;

        for (int i = 0; i < pathParts.length; i++) {
            if (!current.containsKey(pathParts[i])) {
                return false;
            }

            if (i < pathParts.length - 1) {
                var fieldDef = (Map<String, Object>) current.get(pathParts[i]);
                current = (Map<String, Object>) fieldDef.get(MAPPING_PROPERTIES);
                if (current == null) {
                    return false;
                }
            }
        }

        return true;
    }
}
