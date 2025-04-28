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
import org.opensearch.searchrelevance.transport.judgment.PutJudgmentRequest;
import org.opensearch.test.OpenSearchTestCase;

public class PutJudgmentActionTests extends OpenSearchTestCase {

    public void testStreams() throws IOException {
        PutJudgmentRequest request = new PutJudgmentRequest("llm", "test_modelId", "test_question", "test_content", "test_reference");
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PutJudgmentRequest serialized = new PutJudgmentRequest(in);
        assertEquals("test_modelId", serialized.getModelId());
        assertEquals("test_question", serialized.getQuestion());
        assertEquals("test_content", serialized.getContent());
        assertEquals("test_reference", serialized.getReference());
    }

    public void testRequestValidation() {
        PutJudgmentRequest request = new PutJudgmentRequest("llm", "test_modelId", "test_question", "test_content", "test_reference");
        assertNull(request.validate());
    }
}
