/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics.judgments;

import static org.opensearch.searchrelevance.common.MetricsConstants.MODEL_ID;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.ml.MLAccessor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LlmJudgmentsProcessor implements JudgmentsProcessor {
    private static final Logger LOGGER = LogManager.getLogger(LlmJudgmentsProcessor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final MLAccessor mlAccessor;

    public LlmJudgmentsProcessor(MLAccessor mlAccessor) {
        this.mlAccessor = mlAccessor;
    }

    @Override
    public Map<String, Double> processJudgments(
        Map<String, Object> metadata,
        Set<Map<String, String>> unionHits,
        String queryText,
        ActionListener<Map<String, Object>> listener
    ) {
        String modelId = (String) metadata.get(MODEL_ID);
        LOGGER.debug("calculating LLM evaluation with modelId: {} and unionHits: {}", modelId, unionHits);
        return getLlmJudgments(queryText, modelId, unionHits, listener);
    }

    private Map<String, Double> getLlmJudgments(
        String queryText,
        String modelId,
        Set<Map<String, String>> unionHits,
        ActionListener<Map<String, Object>> listener
    ) {
        CompletableFuture<Map<String, Double>> future = new CompletableFuture<>();
        // TODO: handle reference use case when frontend support reference when create a queryset
        mlAccessor.predict(modelId, queryText, null, unionHits.stream().toList(), ActionListener.wrap(response -> {
            if (response == null) {
                listener.onFailure(new SearchRelevanceException("ML prediction returned null output", RestStatus.INTERNAL_SERVER_ERROR));
                return;
            }

            try {
                List<Map<String, Object>> scores = OBJECT_MAPPER.readValue(response, new TypeReference<List<Map<String, Object>>>() {
                });

                Map<String, Double> docIdToScore = new HashMap<>();
                for (Map<String, Object> score : scores) {
                    String id = (String) score.get("id");
                    Double ratingScore = ((Number) score.get("rating_score")).doubleValue();
                    docIdToScore.put(id, ratingScore);
                }

                future.complete(docIdToScore);
            } catch (Exception e) {
                future.completeExceptionally(
                    new SearchRelevanceException(
                        "Failed to parse ML prediction response: " + e.getMessage(),
                        RestStatus.INTERNAL_SERVER_ERROR
                    )
                );
            }
        },
            e -> future.completeExceptionally(
                new SearchRelevanceException("ML prediction failed: " + e.getMessage(), RestStatus.INTERNAL_SERVER_ERROR)
            )
        ));

        try {
            return future.get(300, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new SearchRelevanceException("Failed to get ML predictions: " + e.getMessage(), RestStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
