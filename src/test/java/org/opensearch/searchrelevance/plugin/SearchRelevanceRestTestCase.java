/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.plugin;

import static org.mockito.Mockito.when;
import static org.opensearch.searchrelevance.common.PluginConstants.DOCUMENT_ID;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.transport.client.node.NodeClient;

public abstract class SearchRelevanceRestTestCase extends OpenSearchTestCase {

    @Mock
    protected NodeClient client;

    @Mock
    protected RestChannel channel;

    @Mock
    protected SearchRelevanceSettingsAccessor settingsAccessor;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);

        // Setup channel mock to handle XContentBuilder
        XContentBuilder builder = JsonXContent.contentBuilder();
        when(channel.newBuilder()).thenReturn(builder);
    }

    public RestRequest createPutRestRequestWithContent(String content, String endpoint) {
        Map<String, String> params = new HashMap<>(); // Create params map
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withContent(new BytesArray(content), XContentType.JSON)
            .withParams(params)
            .withMethod(RestRequest.Method.PUT)
            .withPath("/_plugins/_search_relevance/" + endpoint)
            .build();
    }

    public RestRequest createPostRestRequestWithContent(String content, String endpoint) throws IOException {
        Map<String, String> params = new HashMap<>(); // Create params map
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withContent(new BytesArray(content), XContentType.JSON)
            .withParams(params)
            .withMethod(RestRequest.Method.PUT)
            .withPath("/_plugins/_search_relevance/" + endpoint)
            .build();
    }

    protected RestRequest createGetRestRequestWithParams(String endpoint, String documentId, Map<String, String> additionalParams) {
        Map<String, String> params = new HashMap<>(additionalParams);
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params)
            .withMethod(RestRequest.Method.GET)
            .withPath("/_plugins/_search_relevance/" + endpoint + "/" + documentId)
            .build();
    }

    protected RestRequest createDeleteRestRequestWithPath(String endpoint, String documentId) {
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withMethod(RestRequest.Method.DELETE)
            .withPath("/_plugins/_search_relevance/" + endpoint + "/" + documentId)
            .build();
    }

    protected RestRequest createDeleteRestRequestWithParams(String endpoint, String documentId) {
        Map<String, String> params = new HashMap<>();
        params.put(DOCUMENT_ID, documentId);
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withMethod(RestRequest.Method.DELETE)
            .withPath("/_plugins/_search_relevance/" + endpoint)
            .withParams(params)
            .build();
    }

}
