/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import java.util.List;
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
     * Generate judgment rating based on metadata
     * @param metadata used to generate judgment ratings for various judgment type
     * @param listener async action
     */
    public void generateJudgmentRating(Map<String, Object> metadata, ActionListener<List<Map<String, Object>>> listener);
}
