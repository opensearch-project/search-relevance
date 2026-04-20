/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;
import java.util.List;

import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.test.OpenSearchTestCase;

public class QuerySetTests extends OpenSearchTestCase {

    public void testQuerySetBuilder() {
        QuerySet querySet = QuerySet.builder()
            .id("test-id")
            .name("test-name")
            .description("test-desc")
            .timestamp("2024-01-01T00:00:00Z")
            .sampling("manual")
            .status(AsyncStatus.COMPLETED)
            .type(QuerySetType.MANUAL_QUERY_SET)
            .numberOfQueryTerms(0)
            .querySetQueries(List.of())
            .build();

        assertEquals("test-id", querySet.getId());
        assertEquals("test-name", querySet.getName());
        assertEquals("test-desc", querySet.getDescription());
        assertEquals("2024-01-01T00:00:00Z", querySet.getTimestamp());
        assertEquals("manual", querySet.getSampling());
        assertEquals(AsyncStatus.COMPLETED, querySet.getStatus());
        assertEquals(QuerySetType.MANUAL_QUERY_SET, querySet.getType());
        assertEquals(0, querySet.getNumberOfQueryTerms());
        assertTrue(querySet.getQuerySetQueries().isEmpty());
    }

    public void testToXContentIncludesNewFields() throws IOException {
        QuerySet querySet = QuerySet.builder()
            .id("id-1")
            .name("name-1")
            .description("desc-1")
            .timestamp("2024-01-01T00:00:00Z")
            .sampling("manual")
            .status(AsyncStatus.COMPLETED)
            .type(QuerySetType.MANUAL_QUERY_SET)
            .numberOfQueryTerms(5)
            .querySetQueries(List.of())
            .build();

        XContentBuilder builder = XContentFactory.jsonBuilder();
        querySet.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String json = builder.toString();

        assertTrue(json.contains("\"status\":\"COMPLETED\""));
        assertTrue(json.contains("\"type\":\"manual_query_set\""));
        assertTrue(json.contains("\"numberOfQueryTerms\":5"));
        assertTrue(json.contains("\"id\":\"id-1\""));
        assertTrue(json.contains("\"name\":\"name-1\""));
        assertTrue(json.contains("\"sampling\":\"manual\""));
    }

    public void testToXContentWithUbiType() throws IOException {
        QuerySet querySet = QuerySet.builder()
            .id("id-2")
            .name("ubi-qs")
            .description(null)
            .timestamp("2024-01-01T00:00:00Z")
            .sampling("pps")
            .status(AsyncStatus.COMPLETED)
            .type(QuerySetType.UBI_QUERY_SET)
            .numberOfQueryTerms(10)
            .querySetQueries(List.of())
            .build();

        XContentBuilder builder = XContentFactory.jsonBuilder();
        querySet.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String json = builder.toString();

        assertTrue(json.contains("\"type\":\"ubi_query_set\""));
        assertTrue(json.contains("\"numberOfQueryTerms\":10"));
        assertTrue(json.contains("\"description\":\"\""));
    }

    public void testToXContentWithProcessingStatus() throws IOException {
        QuerySet querySet = QuerySet.builder()
            .id("id-3")
            .name("llm-qs")
            .description("llm desc")
            .timestamp("2024-01-01T00:00:00Z")
            .sampling("llm_random")
            .status(AsyncStatus.PROCESSING)
            .type(QuerySetType.LLM_QUERY_SET)
            .numberOfQueryTerms(1000)
            .querySetQueries(List.of())
            .build();

        XContentBuilder builder = XContentFactory.jsonBuilder();
        querySet.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String json = builder.toString();

        assertTrue(json.contains("\"status\":\"PROCESSING\""));
        assertTrue(json.contains("\"type\":\"llm_query_set\""));
        assertTrue(json.contains("\"numberOfQueryTerms\":1000"));
    }

    public void testFieldConstants() {
        assertEquals("id", QuerySet.ID);
        assertEquals("name", QuerySet.NAME);
        assertEquals("description", QuerySet.DESCRIPTION);
        assertEquals("timestamp", QuerySet.TIME_STAMP);
        assertEquals("sampling", QuerySet.SAMPLING);
        assertEquals("status", QuerySet.STATUS);
        assertEquals("type", QuerySet.TYPE);
        assertEquals("numberOfQueryTerms", QuerySet.NUMBER_OF_QUERY_TERMS);
        assertEquals("querySetQueries", QuerySet.QUERY_SET_QUERIES);
    }
}
