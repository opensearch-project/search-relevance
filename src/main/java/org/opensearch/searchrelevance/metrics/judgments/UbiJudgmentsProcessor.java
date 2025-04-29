/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics.judgments;

import static org.opensearch.searchrelevance.common.MetricsConstants.JUDGMENT_IDS;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.core.action.ActionListener;

public class UbiJudgmentsProcessor implements JudgmentsProcessor {
    private static final Logger LOGGER = LogManager.getLogger(UbiJudgmentsProcessor.class);

    @Override
    public Map<String, Double> processJudgments(
        Map<String, Object> metadata,
        Set<Map<String, String>> unionHits,
        String queryText,
        ActionListener<Map<String, Object>> listener
    ) {
        List<String> judgmentIds = (List<String>) metadata.get(JUDGMENT_IDS);
        LOGGER.debug("calculating UBI evaluation with judgmentIds: {}", judgmentIds);
        return getUbiJudgments(judgmentIds, listener);
    }

    private Map<String, Double> getUbiJudgments(List<String> judgmentIdList, ActionListener<Map<String, Object>> listener) {
        // please add UBI judgment business logics here.
        Map<String, Double> example_judgments = new HashMap<>();
        example_judgments.put("001", 0.9);
        example_judgments.put("002", 0.85);
        example_judgments.put("003", 0.8);
        return example_judgments;
    }

}
