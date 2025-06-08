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
import org.opensearch.searchrelevance.transport.judgment.PutImportJudgmentRequest;
import org.opensearch.searchrelevance.transport.judgment.PutJudgmentRequest;
import org.opensearch.searchrelevance.transport.judgment.PutUbiJudgmentRequest;
import org.opensearch.test.OpenSearchTestCase;

public class PutJudgmentActionTests extends OpenSearchTestCase {

    public void testStreams() throws IOException {
        PutJudgmentRequest request = new PutUbiJudgmentRequest(JudgmentType.UBI_JUDGMENT, "name", "description", "coec", 20);
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PutUbiJudgmentRequest serialized = new PutUbiJudgmentRequest(in);
        assertEquals("name", serialized.getName());
        assertEquals(JudgmentType.UBI_JUDGMENT, serialized.getType());
        assertEquals("description", serialized.getDescription());
        assertEquals("coec", serialized.getClickModel());
    }

    public void testRequestValidation() {
        PutJudgmentRequest request = new PutUbiJudgmentRequest(JudgmentType.UBI_JUDGMENT, "name", "description", "coec", 20);
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
}
