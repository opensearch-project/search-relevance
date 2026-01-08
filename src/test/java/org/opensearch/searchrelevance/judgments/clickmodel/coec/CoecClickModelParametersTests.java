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
        CoecClickModelParameters params = new CoecClickModelParameters(10);
        assertEquals(10, params.getMaxRank());
        assertEquals(3, params.getRoundingDigits());
        assertEquals(UBI_EVENTS_INDEX, params.getUbiEventsIndex());
    }

    public void testConstructorWithDates() {
        CoecClickModelParameters params = new CoecClickModelParameters(20, "2024-01-01", "2024-12-31");
        assertEquals(20, params.getMaxRank());
        assertEquals("2024-01-01", params.getStartDate());
        assertEquals("2024-12-31", params.getEndDate());
        assertEquals(UBI_EVENTS_INDEX, params.getUbiEventsIndex());
    }

    public void testConstructorWithCustomIndexes() {
        CoecClickModelParameters params = new CoecClickModelParameters(15, "2024-01-01", "2024-06-30", "my_ubi_events");
        assertEquals(15, params.getMaxRank());
        assertEquals("2024-01-01", params.getStartDate());
        assertEquals("2024-06-30", params.getEndDate());
        assertEquals("my_ubi_events", params.getUbiEventsIndex());
    }

    public void testConstructorWithNullCustomIndexes() {
        CoecClickModelParameters params = new CoecClickModelParameters(25, "2024-01-01", "2024-12-31", null);
        assertEquals(25, params.getMaxRank());
        assertEquals(UBI_EVENTS_INDEX, params.getUbiEventsIndex());
    }

    public void testConstructorWithRoundingDigits() {
        CoecClickModelParameters params = new CoecClickModelParameters(30, 5);
        assertEquals(30, params.getMaxRank());
        assertEquals(5, params.getRoundingDigits());
        assertEquals(UBI_EVENTS_INDEX, params.getUbiEventsIndex());
    }
}
