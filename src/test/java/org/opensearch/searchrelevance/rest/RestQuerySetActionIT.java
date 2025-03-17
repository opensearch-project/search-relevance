/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import static org.opensearch.searchrelevance.common.Constants.QUERYSET_URI;

import java.io.IOException;

import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.test.rest.OpenSearchRestTestCase;

public class RestQuerySetActionIT extends OpenSearchRestTestCase {

    public void testQuerySetSuccess() throws IOException {
        Request request = new Request("POST", QUERYSET_URI);
        request.setJsonEntity("1234");
        Response response = client().performRequest(request);
        assertEquals(RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
    }
}
