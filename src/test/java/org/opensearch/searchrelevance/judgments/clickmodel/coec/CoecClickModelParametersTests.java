/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments.clickmodel.coec;

import static org.opensearch.searchrelevance.common.PluginConstants.UBI_EVENTS_INDEX;

import org.opensearch.test.OpenSearchTestCase;

public class CoecClickModelParametersTests extends OpenSearchTestCase {

    public void testDefaultConstructor() {
        CoecClickModelParameters params = new CoecClickModelParameters(10, UBI_EVENTS_INDEX);
        assertEquals(10, params.getMaxRank());
        assertEquals(3, params.getRoundingDigits());
        assertEquals(UBI_EVENTS_INDEX, params.getUbiEventsIndex());
    }

    public void testConstructorWithDates() {
        CoecClickModelParameters params = new CoecClickModelParameters(20, "2024-01-01", "2024-12-31", UBI_EVENTS_INDEX);
        assertEquals(20, params.getMaxRank());
        assertEquals("2024-01-01", params.getStartDate());
        assertEquals("2024-12-31", params.getEndDate());
        assertEquals(UBI_EVENTS_INDEX, params.getUbiEventsIndex());
    }

    public void testConstructorWithRoundingDigits() {
        CoecClickModelParameters params = new CoecClickModelParameters(30, 5, UBI_EVENTS_INDEX);
        assertEquals(30, params.getMaxRank());
        assertEquals(5, params.getRoundingDigits());
        assertEquals(UBI_EVENTS_INDEX, params.getUbiEventsIndex());
    }
}
