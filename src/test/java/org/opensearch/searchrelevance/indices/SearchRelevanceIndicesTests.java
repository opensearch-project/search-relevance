/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.indices;

import java.util.Set;

import org.opensearch.test.OpenSearchTestCase;

public class SearchRelevanceIndicesTests extends OpenSearchTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    public void testPropertiesOfEnumItems() {
        for (SearchRelevanceIndices index : SearchRelevanceIndices.values()) {
            assertNotNull(index.getMapping());
            assertTrue(index.getMapping().contains("\"properties\""));
            assertNotNull(index.getIndexName());
            assertFalse(index.getIndexName().isBlank());
        }
    }

    public void testProtectedFlag() {
        Set<SearchRelevanceIndices> protectedIndices = Set.of(SearchRelevanceIndices.EXPERIMENT);
        for (SearchRelevanceIndices index : protectedIndices) {
            assertTrue(index.isProtected());
        }

        Set<SearchRelevanceIndices> notProtectedIndices = Set.of(
            SearchRelevanceIndices.SEARCH_CONFIGURATION,
            SearchRelevanceIndices.JUDGMENT,
            SearchRelevanceIndices.JUDGMENT_CACHE,
            SearchRelevanceIndices.EVALUATION_RESULT,
            SearchRelevanceIndices.EXPERIMENT_VARIANT,
            SearchRelevanceIndices.QUERY_SET,
            SearchRelevanceIndices.SCHEDULED_JOBS,
            SearchRelevanceIndices.SCHEDULED_EXPERIMENT_HISTORY
        );
        for (SearchRelevanceIndices index : notProtectedIndices) {
            assertFalse(index.isProtected());
        }
    }
}
