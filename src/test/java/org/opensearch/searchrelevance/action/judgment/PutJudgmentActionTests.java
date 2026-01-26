/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.judgment;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;
import org.opensearch.searchrelevance.transport.judgment.PutImportJudgmentRequest;
import org.opensearch.searchrelevance.transport.judgment.PutJudgmentRequest;
import org.opensearch.searchrelevance.transport.judgment.PutLlmJudgmentRequest;
import org.opensearch.searchrelevance.transport.judgment.PutUbiJudgmentRequest;
import org.opensearch.test.OpenSearchTestCase;

public class PutJudgmentActionTests extends OpenSearchTestCase {

    public void testStreams() throws IOException {
        PutJudgmentRequest request = new PutUbiJudgmentRequest(JudgmentType.UBI_JUDGMENT, "name", "description", "coec", 20, "", "", null);
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PutUbiJudgmentRequest serialized = new PutUbiJudgmentRequest(in);
        assertEquals("name", serialized.getName());
        assertEquals(JudgmentType.UBI_JUDGMENT, serialized.getType());
        assertEquals("description", serialized.getDescription());
        assertEquals("coec", serialized.getClickModel());
        assertEquals("", serialized.getStartDate());
        assertEquals("", serialized.getEndDate());
    }

    public void testRequestValidation() {
        PutJudgmentRequest request = new PutUbiJudgmentRequest(JudgmentType.UBI_JUDGMENT, "name", "description", "coec", 20, "", "", null);
        assertNull(request.validate());
    }

    public void testImportJudgementStream() throws IOException {

        // Add entries for "red dress" query
        List<Map<String, String>> redDressScores = List.of(
            Map.of("docId", "B077ZJXCTS", "rating", "0.700"),
            Map.of("docId", "B071S6LTJJ", "rating", "0.000"),
            Map.of("docId", "B01IDSPDJI", "rating", "0.000")
        );

        // Add entries for "blue jeans" query
        List<Map<String, String>> blueJeansScores = List.of(
            Map.of("docId", "B07L9V4Y98", "rating", "0.000"),
            Map.of("docId", "B077ZJXCTS", "rating", "0.600"),
            Map.of("docId", "B001CRAWCQ", "rating", "0.000")
        );

        List<Map<String, Object>> judgmentScores = List.of(
            Map.of("query", "red dress", "ratings", redDressScores),
            Map.of("query", "blue jeans", "ratings", blueJeansScores)
        );

        PutJudgmentRequest request = new PutImportJudgmentRequest(JudgmentType.IMPORT_JUDGMENT, "name", "description", judgmentScores);
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PutImportJudgmentRequest serialized = new PutImportJudgmentRequest(in);
        assertEquals("name", serialized.getName());
        assertEquals(JudgmentType.IMPORT_JUDGMENT, serialized.getType());
        assertEquals("description", serialized.getDescription());

        Map<String, Object> queryAndRatings = (Map<String, Object>) serialized.getJudgmentRatings().get(0);
        assertEquals("red dress", queryAndRatings.get("query"));
        Map<String, String> ratings = (Map<String, String>) ((List<Map<String, String>>) queryAndRatings.get("ratings")).get(0);
        assertEquals("B077ZJXCTS", ratings.get("docId"));
        assertEquals("0.700", ratings.get("rating"));
    }

    public void testLlmJudgmentRequestStreams() throws IOException {
        PutLlmJudgmentRequest request = new PutLlmJudgmentRequest(
            JudgmentType.LLM_JUDGMENT,
            "test_name",
            "test_description",
            "test_model_id",
            "test_query_set_id",
            List.of("config1", "config2"),
            10,
            1000,
            List.of("field1", "field2"),
            false,
            "test_prompt_template",
            LLMJudgmentRatingType.SCORE0_1,
            true
        );

        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PutLlmJudgmentRequest serialized = new PutLlmJudgmentRequest(in);

        assertEquals("test_name", serialized.getName());
        assertEquals(JudgmentType.LLM_JUDGMENT, serialized.getType());
        assertEquals("test_description", serialized.getDescription());
        assertEquals("test_model_id", serialized.getModelId());
        assertEquals("test_query_set_id", serialized.getQuerySetId());
        assertEquals(List.of("config1", "config2"), serialized.getSearchConfigurationList());
        assertEquals(10, serialized.getSize());
        assertEquals(1000, serialized.getTokenLimit());
        assertEquals(List.of("field1", "field2"), serialized.getContextFields());
        assertEquals(false, serialized.isIgnoreFailure());
        assertEquals("test_prompt_template", serialized.getPromptTemplate());
        assertEquals(LLMJudgmentRatingType.SCORE0_1, serialized.getLlmJudgmentRatingType());
        assertEquals(true, serialized.isOverwriteCache());
    }

    public void testLlmJudgmentRequestStreamsWithNullOptionalFields() throws IOException {
        PutLlmJudgmentRequest request = new PutLlmJudgmentRequest(
            JudgmentType.LLM_JUDGMENT,
            "test_name",
            "test_description",
            "test_model_id",
            "test_query_set_id",
            List.of("config1"),
            5,
            500,
            List.of("field1"),
            true,
            null,
            null,
            false
        );

        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PutLlmJudgmentRequest serialized = new PutLlmJudgmentRequest(in);

        assertEquals("test_name", serialized.getName());
        assertEquals(JudgmentType.LLM_JUDGMENT, serialized.getType());
        assertEquals("test_description", serialized.getDescription());
        assertNull(serialized.getPromptTemplate());
        assertNull(serialized.getLlmJudgmentRatingType());
        assertEquals(false, serialized.isOverwriteCache());
    }

    public void testUbiJudgmentWithCustomIndexes() throws IOException {
        PutUbiJudgmentRequest request = new PutUbiJudgmentRequest(
            JudgmentType.UBI_JUDGMENT,
            "name",
            "description",
            "coec",
            20,
            "2024-01-01",
            "2024-12-31",
            "custom_events_index"
        );
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PutUbiJudgmentRequest serialized = new PutUbiJudgmentRequest(in);
        assertEquals("name", serialized.getName());
        assertEquals(JudgmentType.UBI_JUDGMENT, serialized.getType());
        assertEquals("description", serialized.getDescription());
        assertEquals("coec", serialized.getClickModel());
        assertEquals(20, serialized.getMaxRank());
        assertEquals("2024-01-01", serialized.getStartDate());
        assertEquals("2024-12-31", serialized.getEndDate());
        assertEquals("custom_events_index", serialized.getUbiEventsIndex());
    }

    public void testUbiJudgmentDefaultIndexesWhenNull() {
        PutUbiJudgmentRequest request = new PutUbiJudgmentRequest(
            JudgmentType.UBI_JUDGMENT,
            "name",
            "description",
            "coec",
            10,
            "",
            "",
            null
        );
        assertEquals("ubi_events", request.getUbiEventsIndex());
    }
}
