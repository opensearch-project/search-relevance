/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action;

import java.io.IOException;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.searchrelevance.transport.QuerySetRequest;
import org.opensearch.test.OpenSearchTestCase;

public class QuerySetActionTests extends OpenSearchTestCase {

    public void testStreams() throws IOException {
        String requestBody = "1234";
        QuerySetRequest request = new QuerySetRequest("1234");
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        QuerySetRequest serialized = new QuerySetRequest(in);
        assertEquals(requestBody, serialized.getQuerySetId());
    }

    public void testRequestValidation() {
        QuerySetRequest request = new QuerySetRequest("1234");
        assertNull(request.validate());
    }
}
