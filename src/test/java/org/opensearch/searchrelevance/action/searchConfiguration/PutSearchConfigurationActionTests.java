/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.searchConfiguration;

import java.io.IOException;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.searchrelevance.transport.searchConfiguration.PutSearchConfigurationRequest;
import org.opensearch.test.OpenSearchTestCase;

public class PutSearchConfigurationActionTests extends OpenSearchTestCase {

    public void testStreams() throws IOException {
        PutSearchConfigurationRequest request = new PutSearchConfigurationRequest("base_line", "sample_id", "{\"match_all\":{}}", "n/a");
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PutSearchConfigurationRequest serialized = new PutSearchConfigurationRequest(in);
        assertEquals("base_line", serialized.getName());
        assertEquals("sample_id", serialized.getIndex());
        assertEquals("{\"match_all\":{}}", serialized.getQueryBody());
        assertEquals("n/a", serialized.getSearchPipeline());
    }

    public void testRequestValidation() {
        PutSearchConfigurationRequest request = new PutSearchConfigurationRequest("base_line", "sample_id", "{\"match_all\":{}}", "n/a");
        assertNull(request.validate());
    }
}
