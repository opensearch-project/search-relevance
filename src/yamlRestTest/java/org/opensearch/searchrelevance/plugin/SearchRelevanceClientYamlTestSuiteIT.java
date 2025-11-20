/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.plugin;

import org.opensearch.test.rest.yaml.ClientYamlTestCandidate;
import org.opensearch.test.rest.yaml.OpenSearchClientYamlSuiteTestCase;

import com.carrotsearch.randomizedtesting.annotations.Name;
import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;

public class SearchRelevanceClientYamlTestSuiteIT extends OpenSearchClientYamlSuiteTestCase {

    public SearchRelevanceClientYamlTestSuiteIT(@Name("yaml") ClientYamlTestCandidate testCandidate) {
        super(testCandidate);
    }

    @ParametersFactory
    public static Iterable<Object[]> parameters() throws Exception {
        return OpenSearchClientYamlSuiteTestCase.createParameters();
    }

    /**
     * Preserve system indices created by the SearchRelevanceMappingUpdateListener.
     * These indices are created automatically on cluster startup and should not be
     * deleted during test cleanup to avoid warnings about accessing system indices.
     */
    @Override
    protected boolean preserveIndicesUponCompletion() {
        return true;
    }
}
