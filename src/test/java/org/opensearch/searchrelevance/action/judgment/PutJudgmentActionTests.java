/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.judgment;

import java.io.IOException;
import java.util.HashMap;
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

        Map<String, Object> judgmentScores = new HashMap<>();

        // Add entries for "red dress" query
        Map<String, String> redDressScores = new HashMap<>();
        redDressScores.put("B077ZJXCTS", "0.700");
        redDressScores.put("B071S6LTJJ", "0.000");
        redDressScores.put("B01IDSPDJI", "0.000");

        // Add entries for "blue jeans" query
        Map<String, String> blueJeansScores = new HashMap<>();
        blueJeansScores.put("B07L9V4Y98", "0.000");
        blueJeansScores.put("B077ZJXCTS", "0.600");
        blueJeansScores.put("B001CRAWCQ", "0.000");

        judgmentScores.put("red dress", redDressScores);
        judgmentScores.put("blue jeans", blueJeansScores);

        PutJudgmentRequest request = new PutImportJudgmentRequest(JudgmentType.IMPORT_JUDGMENT, "name", "description", judgmentScores);
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PutImportJudgmentRequest serialized = new PutImportJudgmentRequest(in);
        assertEquals("name", serialized.getName());
        assertEquals(JudgmentType.IMPORT_JUDGMENT, serialized.getType());
        assertEquals("description", serialized.getDescription());

        Map<String, String> query = (Map<String, String>) serialized.getJudgmentScores().get("red dress");
        String score = query.get("B077ZJXCTS");
        assertEquals("0.700", score);
    }
}
