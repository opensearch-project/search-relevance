/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.queryset;

import static org.opensearch.searchrelevance.common.PluginConstants.UBI_QUERIES_INDEX;

import java.io.IOException;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.searchrelevance.transport.queryset.PostUbiQuerySetRequest;
import org.opensearch.test.OpenSearchTestCase;

public class CreateQuerySetActionTests extends OpenSearchTestCase {

    public void testStreams() throws IOException {
        PostUbiQuerySetRequest request = new PostUbiQuerySetRequest("test_name", "test_description", "random", 10, null);
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PostUbiQuerySetRequest serialized = new PostUbiQuerySetRequest(in);
        assertEquals("test_name", serialized.getName());
        assertEquals("test_description", serialized.getDescription());
        assertEquals("random", serialized.getSampling());
        assertEquals(10, serialized.getQuerySetSize());
        assertEquals(UBI_QUERIES_INDEX, serialized.getUbiQueriesIndex());
    }

    public void testRequestValidation() {
        PostUbiQuerySetRequest request = new PostUbiQuerySetRequest("test_name", "test_description", "random", 10, null);
        assertNull(request.validate());
    }

    public void testStreamsWithCustomIndexes() throws IOException {
        PostUbiQuerySetRequest request = new PostUbiQuerySetRequest("test_name", "test_description", "topn", 20, "custom_ubi_queries");
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PostUbiQuerySetRequest serialized = new PostUbiQuerySetRequest(in);
        assertEquals("test_name", serialized.getName());
        assertEquals("test_description", serialized.getDescription());
        assertEquals("topn", serialized.getSampling());
        assertEquals(20, serialized.getQuerySetSize());
        assertEquals("custom_ubi_queries", serialized.getUbiQueriesIndex());
    }

    public void testDefaultIndexesWhenNull() {
        PostUbiQuerySetRequest request = new PostUbiQuerySetRequest("test_name", "test_description", "random", 10, null);
        assertEquals(UBI_QUERIES_INDEX, request.getUbiQueriesIndex());
    }

    public void testCustomIndexes() {
        PostUbiQuerySetRequest request = new PostUbiQuerySetRequest("test_name", "test_description", "pptss", 15, "my_queries_index");
        assertEquals("my_queries_index", request.getUbiQueriesIndex());
    }
}
