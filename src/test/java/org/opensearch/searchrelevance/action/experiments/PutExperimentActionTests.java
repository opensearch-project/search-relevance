/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.experiments;

import java.io.IOException;
import java.util.List;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.transport.experiment.PutExperimentRequest;
import org.opensearch.test.OpenSearchTestCase;

public class PutExperimentActionTests extends OpenSearchTestCase {

    public void testStreams() throws IOException {
        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.PAIRWISE_COMPARISON,
            null,
            "1234",
            List.of("5678", "0000"),
            List.of("5678", "0000"),
            10
        );
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PutExperimentRequest serialized = new PutExperimentRequest(in);
        assertEquals("1234", serialized.getQuerySetId());
        assertEquals(2, serialized.getSearchConfigurationList().size());
        assertEquals(10, serialized.getSize());
    }

    public void testRequestValidation() {
        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.PAIRWISE_COMPARISON,
            null,
            "1234",
            List.of("5678", "0000"),
            List.of("5678", "0000"),
            10
        );
        assertNull(request.validate());
    }

}
