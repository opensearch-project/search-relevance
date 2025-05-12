/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments.clickmodel;

import java.util.Map;

import org.opensearch.core.action.ActionListener;

/**
 * Base class for creating click models.
 */
public abstract class ClickModel {

    /**
     * Calculate implicit judgments.
     */
    public abstract void calculateJudgments(ActionListener<Map<String, Map<String, String>>> listener);
}
