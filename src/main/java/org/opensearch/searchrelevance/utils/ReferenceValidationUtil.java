/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import java.util.function.BiConsumer;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;

/**
 * Utility class for validating references to cluster resources and plugin
 * entities
 */
public class ReferenceValidationUtil {

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
     * Validates that a cluster index exists
     *
     * @param clusterService ClusterService instance
     * @param indexName      Index name to validate
     * @return ValidationResult indicating if the index exists
     */
    public static ValidationResult validateIndexExists(ClusterService clusterService, String indexName) {
        if (clusterService == null) {
            return new ValidationResult(false, "ClusterService is not available");
        }

        if (indexName == null || indexName.isEmpty()) {
            return new ValidationResult(false, "Index name cannot be null or empty");
        }

        if (clusterService.state().metadata().hasIndex(indexName)) {
            return new ValidationResult(true, null);
        }

        return new ValidationResult(false, "Index [" + indexName + "] does not exist");
    }

    /**
     * Validates that an entity exists using a DAO get method
     *
     * @param entityId     ID of the entity to validate
     * @param entityType   Type name for error messages (e.g., "QuerySet",
     *                     "SearchConfiguration")
     * @param daoGetMethod DAO method reference that accepts (String id,
     *                     ActionListener callback)
     * @param listener     Callback listener that receives success (null) or failure
     *                     (exception)
     */
    public static void validateEntityExists(
        String entityId,
        String entityType,
        BiConsumer<String, ActionListener<SearchResponse>> daoGetMethod,
        ActionListener<Void> listener
    ) {
        if (entityId == null || entityId.isEmpty()) {
            listener.onFailure(new SearchRelevanceException(entityType + " ID cannot be null or empty", RestStatus.BAD_REQUEST));
            return;
        }

        daoGetMethod.accept(entityId, ActionListener.wrap(response -> {
            if (response.getHits().getTotalHits().value() > 0) {
                listener.onResponse(null);
            } else {
                listener.onFailure(new SearchRelevanceException(entityType + " [" + entityId + "] does not exist", RestStatus.BAD_REQUEST));
            }
        }, listener::onFailure));
    }
}
