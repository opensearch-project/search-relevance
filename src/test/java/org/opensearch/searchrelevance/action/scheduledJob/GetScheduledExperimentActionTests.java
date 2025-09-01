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
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.test.OpenSearchTestCase;

public class GetScheduledExperimentActionTests extends OpenSearchTestCase {
    public void testStreams() throws IOException {
        OpenSearchDocRequest request = new OpenSearchDocRequest("1234");
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        OpenSearchDocRequest serialized = new OpenSearchDocRequest(in);
        assertEquals("1234", serialized.getId());
    }

    public void testRequestValidation() {
        OpenSearchDocRequest request = new OpenSearchDocRequest("1234");
        assertNull(request.validate());
    }
}
