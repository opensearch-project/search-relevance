/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.transport.client.Client;

public class ImportJudgmentsProcessor implements BaseJudgmentsProcessor {
    private static final Logger LOGGER = LogManager.getLogger(ImportJudgmentsProcessor.class);
    private final Client client;

    @Inject
    public ImportJudgmentsProcessor(Client client) {
        this.client = client;
    }

    @Override
    public JudgmentType getJudgmentType() {
        return JudgmentType.IMPORT_JUDGMENT;
    }

    @Override
    public void generateJudgmentRating(Map<String, Object> metadata, ActionListener<List<Map<String, Object>>> listener) {

        List<Map<String, Object>> sourceJudgementRatings = (List<Map<String, Object>>) metadata.get("judgmentRatings");
        metadata.remove("judgmentRatings");

        // Create the result map in the expected format
        List<Map<String, Object>> formattedRatings = new ArrayList<>();

        // Process each query
        for (Map<String, Object> queryJudgment : sourceJudgementRatings) {
            String queryText = queryJudgment.get("query").toString();
            Object ratingData = queryJudgment.get("ratings");

            if (!(ratingData instanceof List)) {
                listener.onFailure(
                    new SearchRelevanceException("queryText " + queryText + " must have a list of rating data.", RestStatus.BAD_REQUEST)
                );
                return;
            }

            Map<String, String> docRatings = new HashMap<>();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ratingsList = (List<Map<String, Object>>) ratingData;

            // Process each document's rating
            for (Map<String, Object> ratingInfo : ratingsList) {
                String docId = (String) ratingInfo.get("docId");
                String rating = (String) ratingInfo.get("rating");

                if (docId == null || docId.isEmpty()) {
                    listener.onFailure(
                        new SearchRelevanceException(
                            "docId for queryText " + queryText + " must not be null or empty",
                            RestStatus.BAD_REQUEST
                        )
                    );
                    return;
                }
                if (rating == null) {
                    listener.onFailure(
                        new SearchRelevanceException("rating for queryText " + queryText + " must not be null", RestStatus.BAD_REQUEST)
                    );
                    return;
                }
                try {
                    Float.parseFloat(rating);
                } catch (NumberFormatException e) {
                    listener.onFailure(
                        new SearchRelevanceException(
                            "rating '" + rating + "' for queryText " + queryText + " must be a valid float",
                            RestStatus.BAD_REQUEST
                        )
                    );
                    return;
                }

                docRatings.put(docId, rating);
            }

            // Add the formatted ratings for this query
            Map<String, Object> queryRatings = new HashMap<>();
            queryRatings.put("query", queryText);
            List<Map<String, String>> docIdRatings = docRatings.entrySet()
                .stream()
                .map(entry -> Map.of("docId", entry.getKey(), "rating", entry.getValue()))
                .toList();
            queryRatings.put("ratings", docIdRatings);
            formattedRatings.add(queryRatings);
        }

        listener.onResponse(formattedRatings);

    }

}
