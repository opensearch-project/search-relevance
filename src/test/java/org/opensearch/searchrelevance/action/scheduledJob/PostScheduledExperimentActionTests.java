/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.scheduledJob;

import java.io.IOException;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.searchrelevance.transport.scheduledJob.PostScheduledExperimentRequest;
import org.opensearch.test.OpenSearchTestCase;

public class PostScheduledExperimentActionTests extends OpenSearchTestCase {
    public void testStreams() throws IOException {
        PostScheduledExperimentRequest request = new PostScheduledExperimentRequest("test_experiment_id", "12 * * * *");
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PostScheduledExperimentRequest serialized = new PostScheduledExperimentRequest(in);
        assertEquals("test_experiment_id", serialized.getExperimentId());
        assertEquals("12 * * * *", serialized.getCronExpression());
    }
}
