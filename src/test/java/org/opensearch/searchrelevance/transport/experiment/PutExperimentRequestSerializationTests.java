/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import java.io.IOException;
import java.util.List;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.test.OpenSearchTestCase;

public class PutExperimentRequestSerializationTests extends OpenSearchTestCase {

    public void testSerialization() throws IOException {
        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.PAIRWISE_COMPARISON,
            "result-id",
            "experiment-name",
            "experiment-description",
            "queryset-id",
            List.of("config1"),
            List.of("judgment1"),
            10
        );

        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        PutExperimentRequest readRequest = new PutExperimentRequest(in);
        assertEquals("experiment-name", readRequest.getName());
        assertEquals("experiment-description", readRequest.getDescription());
        assertEquals(ExperimentType.PAIRWISE_COMPARISON, readRequest.getType());
        assertEquals("queryset-id", readRequest.getQuerySetId());
    }
}
