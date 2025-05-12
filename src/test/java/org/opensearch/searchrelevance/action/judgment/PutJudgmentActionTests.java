/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.judgment;

import java.io.IOException;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.searchrelevance.model.JudgmentType;
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
}
