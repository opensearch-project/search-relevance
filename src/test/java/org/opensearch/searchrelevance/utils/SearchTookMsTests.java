/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.test.OpenSearchTestCase;

public class SearchTookMsTests extends OpenSearchTestCase {

    public void testFromReturnsMillis() {
        SearchResponse response = mock(SearchResponse.class);
        when(response.getTook()).thenReturn(TimeValue.timeValueMillis(14));

        assertEquals(Long.valueOf(14L), SearchTookMs.from(response));
    }

    public void testFromReturnsNullWhenResponseIsNull() {
        assertNull(SearchTookMs.from(null));
    }

    public void testFromReturnsNullWhenTookIsNull() {
        SearchResponse response = mock(SearchResponse.class);
        when(response.getTook()).thenReturn(null);

        assertNull(SearchTookMs.from(response));
    }

    public void testFromPreservesZero() {
        SearchResponse response = mock(SearchResponse.class);
        when(response.getTook()).thenReturn(TimeValue.timeValueMillis(0));

        assertEquals(Long.valueOf(0L), SearchTookMs.from(response));
    }
}
