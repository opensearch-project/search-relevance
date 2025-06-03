/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

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
    public void generateJudgmentScore(Map<String, Object> metadata, ActionListener<Map<String, Map<String, String>>> listener) {

        Map<String, Object> sourceJudgementScores = (Map<String, Object>) metadata.get("judgmentScores");
        metadata.remove("judgmentScores");

        // Create the result map in the expected format
        Map<String, Map<String, String>> formattedScores = new HashMap<>();

        // Process each query
        for (Map.Entry<String, Object> queryEntry : sourceJudgementScores.entrySet()) {
            String queryText = queryEntry.getKey();
            Object scoreData = queryEntry.getValue();

            if (!(scoreData instanceof List)) {
                listener.onFailure(
                    new SearchRelevanceException("queryText " + queryText + " must have a list of score data.", RestStatus.BAD_REQUEST)
                );
                return;
            }

            Map<String, String> docScores = new HashMap<>();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> scoresList = (List<Map<String, Object>>) scoreData;

            // Process each document's score
            for (Map<String, Object> scoreInfo : scoresList) {
                String docId = (String) scoreInfo.get("docId");
                String score = (String) scoreInfo.get("score");

                if (docId == null || docId.isEmpty()) {
                    listener.onFailure(
                        new SearchRelevanceException(
                            "docId for queryText " + queryText + " must not be null or empty",
                            RestStatus.BAD_REQUEST
                        )
                    );
                    return;
                }
                if (score == null) {
                    listener.onFailure(
                        new SearchRelevanceException("score for queryText " + queryText + " must not be null", RestStatus.BAD_REQUEST)
                    );
                    return;
                }
                try {
                    Float.parseFloat(score);
                } catch (NumberFormatException e) {
                    listener.onFailure(
                        new SearchRelevanceException(
                            "score '" + score + "' for queryText " + queryText + " must be a valid float",
                            RestStatus.BAD_REQUEST
                        )
                    );
                    return;
                }

                docScores.put(docId, score);
            }

            // Add the formatted scores for this query
            formattedScores.put(queryText, docScores);
        }

        listener.onResponse(formattedScores);

    }

}
