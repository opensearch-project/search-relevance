/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.model.JudgmentType;

/**
 * Common functions for judgment processor.
 */
public interface BaseJudgmentsProcessor {

    /**
     * Return the type of judgment
     */
    public JudgmentType getJudgmentType();

    /**
     * Generate judgment score based on metadata
     * @param metadata used generate judgment scores for various judgment type
     * @param listener async action
     */
    public void generateJudgmentScore(Map<String, Object> metadata, ActionListener<Map<String, Map<String, String>>> listener);
}
