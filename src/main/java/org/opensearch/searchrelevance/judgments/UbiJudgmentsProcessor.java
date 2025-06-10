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
import org.opensearch.action.StepListener;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.judgments.clickmodel.coec.CoecClickModel;
import org.opensearch.searchrelevance.judgments.clickmodel.coec.CoecClickModelParameters;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.transport.client.Client;

public class UbiJudgmentsProcessor implements BaseJudgmentsProcessor {
    private static final Logger LOGGER = LogManager.getLogger(UbiJudgmentsProcessor.class);
    private final Client client;

    @Inject
    public UbiJudgmentsProcessor(Client client) {
        this.client = client;
    }

    @Override
    public JudgmentType getJudgmentType() {
        return JudgmentType.UBI_JUDGMENT;
    }

    @Override
    public void generateJudgmentRating(Map<String, Object> metadata, ActionListener<List<Map<String, Object>>> listener) {
        String clickModel = (String) metadata.get("clickModel");
        int maxRank = (int) metadata.get("maxRank");

        if (CoecClickModel.CLICK_MODEL_NAME.equalsIgnoreCase(clickModel)) {
            final CoecClickModelParameters coecClickModelParameters = new CoecClickModelParameters(maxRank);
            final CoecClickModel coecClickModel = new CoecClickModel(client, coecClickModelParameters);

            // Create StepListener for the click model calculation
            StepListener<Map<String, Map<String, String>>> clickModelStep = new StepListener<>();

            try {
                coecClickModel.calculateJudgments(new ActionListener<>() {
                    @Override
                    public void onResponse(List<Map<String, Object>> judgments) {
                        // Create the result map in the expected format
                        List<Map<String, Object>> formattedRatings = new ArrayList<>();
                        for (Map<String, Object> queryJudgment : judgments) {
                            String queryText = (String) queryJudgment.get("query");
                            Object ratingData = queryJudgment.get("ratings");

                            if (!(ratingData instanceof Map)) {
                                listener.onFailure(
                                    new SearchRelevanceException(
                                        "queryText " + queryText + " must have rating data as a Map.",
                                        RestStatus.BAD_REQUEST
                                    )
                                );
                                return;
                            }

                            @SuppressWarnings("unchecked")
                            Map<String, Object> ratingsMap = (Map<String, Object>) ratingData; // Cast to Map, not List

                            // Prepare a list to hold the docId and score maps for the current query
                            List<Map<String, String>> docIdScoreList = new ArrayList<>();

                            // Iterate over the entrySet of the HashMap ***
                            for (Map.Entry<String, Object> entry : ratingsMap.entrySet()) {
                                String docId = entry.getKey(); // The key is the docId
                                Object ratingObject = entry.getValue(); // The value is the rating

                                if (docId == null || docId.isEmpty()) {
                                    // This case is unlikely if the keys of the map are docIds, but good for defensive coding
                                    listener.onFailure(
                                        new SearchRelevanceException(
                                            "docId (map key) for queryText " + queryText + " must not be null or empty",
                                            RestStatus.BAD_REQUEST
                                        )
                                    );
                                    return;
                                }
                                if (ratingObject == null) {
                                    listener.onFailure(
                                        new SearchRelevanceException(
                                            "rating for docId '" + docId + "' in queryText " + queryText + " must not be null",
                                            RestStatus.BAD_REQUEST
                                        )
                                    );
                                    return;
                                }

                                String rating = String.valueOf(ratingObject); // Convert rating to String

                                try {
                                    Float.parseFloat(rating);
                                } catch (NumberFormatException e) {
                                    listener.onFailure(
                                        new SearchRelevanceException(
                                            "rating '"
                                                + rating
                                                + "' for docId '"
                                                + docId
                                                + "' in queryText "
                                                + queryText
                                                + " must be a valid float",
                                            RestStatus.BAD_REQUEST
                                        )
                                    );
                                    return;
                                }

                                // Add the docId and score to the list for the current query
                                Map<String, String> docScoreMap = new HashMap<>();
                                docScoreMap.put("docId", docId);
                                docScoreMap.put("score", rating);
                                docIdScoreList.add(docScoreMap);
                            }

                            // Add the formatted ratings for this query
                            Map<String, Object> queryRatings = new HashMap<>();
                            queryRatings.put("query", queryText);
                            queryRatings.put("ratings", docIdScoreList);
                            formattedRatings.add(queryRatings);
                        }
                        listener.onResponse(formattedRatings);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        LOGGER.error("Failed to calculate COEC click model judgments", e);
                        listener.onFailure(
                            new SearchRelevanceException(
                                "Failed to calculate COEC click model judgments",
                                e,
                                RestStatus.INTERNAL_SERVER_ERROR
                            )
                        );
                    }
                });
            } catch (Exception e) {
                LOGGER.error("Error initiating COEC click model calculation", e);
                listener.onFailure(
                    new SearchRelevanceException("Error initiating COEC click model calculation", e, RestStatus.INTERNAL_SERVER_ERROR)
                );
            }
        } else {
            listener.onFailure(new SearchRelevanceException("Unsupported click model: " + clickModel, RestStatus.BAD_REQUEST));
        }
    }
}
