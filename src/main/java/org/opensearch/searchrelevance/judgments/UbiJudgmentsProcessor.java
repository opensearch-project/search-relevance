/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

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
    public void generateJudgmentScore(Map<String, Object> metadata, ActionListener<Map<String, Map<String, String>>> listener) {
        String clickModel = (String) metadata.get("clickModel");
        int maxRank = (int) metadata.get("maxRank");

        if (CoecClickModel.CLICK_MODEL_NAME.equalsIgnoreCase(clickModel)) {
            final CoecClickModelParameters coecClickModelParameters = new CoecClickModelParameters(maxRank);
            final CoecClickModel coecClickModel = new CoecClickModel(client, coecClickModelParameters);

            // Create StepListener for the click model calculation
            StepListener<Map<String, Map<String, String>>> clickModelStep = new StepListener<>();

            try {
                coecClickModel.calculateJudgments(new ActionListener<Map<String, Map<String, String>>>() {
                    @Override
                    public void onResponse(Map<String, Map<String, String>> judgments) {
                        listener.onResponse(judgments);
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
