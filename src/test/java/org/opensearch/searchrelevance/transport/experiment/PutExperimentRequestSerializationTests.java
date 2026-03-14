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

import org.opensearch.Version;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.test.OpenSearchTestCase;

public class PutExperimentRequestSerializationTests extends OpenSearchTestCase {

    public void testSerializationBWC() throws IOException {
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

        // Test with version that supports name and description
        {
            BytesStreamOutput out = new BytesStreamOutput();
            out.setVersion(Version.V_3_6_0);
            request.writeTo(out);
            StreamInput in = out.bytes().streamInput();
            in.setVersion(Version.V_3_6_0);
            PutExperimentRequest readRequest = new PutExperimentRequest(in);
            assertEquals("experiment-name", readRequest.getName());
            assertEquals("experiment-description", readRequest.getDescription());
            assertEquals(ExperimentType.PAIRWISE_COMPARISON, readRequest.getType());
            assertEquals("queryset-id", readRequest.getQuerySetId());
        }

        // Test with version that does NOT support name and description
        {
            BytesStreamOutput out = new BytesStreamOutput();
            out.setVersion(Version.V_3_1_0);
            request.writeTo(out);
            StreamInput in = out.bytes().streamInput();
            in.setVersion(Version.V_3_1_0);
            PutExperimentRequest readRequest = new PutExperimentRequest(in);
            assertNull(readRequest.getName());
            assertNull(readRequest.getDescription());
            assertEquals(ExperimentType.PAIRWISE_COMPARISON, readRequest.getType());
            assertEquals("queryset-id", readRequest.getQuerySetId());
        }
    }
}
