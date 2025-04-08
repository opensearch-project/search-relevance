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
import org.opensearch.searchrelevance.transport.queryset.CreateQuerySetRequest;
import org.opensearch.test.OpenSearchTestCase;

public class CreateQuerySetActionTests extends OpenSearchTestCase {

    public void testStreams() throws IOException {
        CreateQuerySetRequest request = new CreateQuerySetRequest("test_name", "test_description", "random", 10);
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        CreateQuerySetRequest serialized = new CreateQuerySetRequest(in);
        assertEquals("test_name", serialized.getName());
        assertEquals("test_description", serialized.getDescription());
        assertEquals("random", serialized.getSampling());
        assertEquals(10, serialized.getQuerySetSize());
    }

    public void testRequestValidation() {
        CreateQuerySetRequest request = new CreateQuerySetRequest("test_name", "test_description", "random", 10);
        assertNull(request.validate());
    }
}
