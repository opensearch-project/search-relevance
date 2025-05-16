/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.queryset;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.searchrelevance.model.QueryWithReference;
import org.opensearch.searchrelevance.transport.queryset.PutQuerySetRequest;
import org.opensearch.test.OpenSearchTestCase;

public class PutQuerySetActionTests extends OpenSearchTestCase {

    public void testStreams() throws IOException {
        PutQuerySetRequest request = new PutQuerySetRequest("test_name", "test_description", "manual", getQuerySetQueries());
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PutQuerySetRequest serialized = new PutQuerySetRequest(in);
        assertEquals("test_name", serialized.getName());
        assertEquals("test_description", serialized.getDescription());
        assertEquals("manual", serialized.getSampling());
        assertEquals(getQuerySetQueries(), serialized.getQuerySetQueries());
    }

    public void testRequestValidation() {
        PutQuerySetRequest request = new PutQuerySetRequest("test_name", "test_description", "manual", getQuerySetQueries());
        assertNull(request.validate());
    }

    private List<QueryWithReference> getQuerySetQueries() {
        List<QueryWithReference> querySetQueries = new ArrayList<>();
        querySetQueries.add(new QueryWithReference("apple", ""));
        querySetQueries.add(new QueryWithReference("banana", ""));
        return querySetQueries;
    }
}
