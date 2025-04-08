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
import org.opensearch.searchrelevance.transport.queryset.PutQuerySetRequest;
import org.opensearch.test.OpenSearchTestCase;

public class PutQuerySetActionTests extends OpenSearchTestCase {

    public void testStreams() throws IOException {
        PutQuerySetRequest request = new PutQuerySetRequest("test_name", "test_description", "manual", "apple, banana");
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PutQuerySetRequest serialized = new PutQuerySetRequest(in);
        assertEquals("test_name", serialized.getName());
        assertEquals("test_description", serialized.getDescription());
        assertEquals("manual", serialized.getSampling());
        assertEquals("apple, banana", serialized.getQuerySetQueries());
    }

    public void testRequestValidation() {
        PutQuerySetRequest request = new PutQuerySetRequest("test_name", "test_description", "manual", "apple, banana");
        assertNull(request.validate());
    }
}
