/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.plugin;

import static org.opensearch.client.WarningsHandler.PERMISSIVE;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.opensearch.client.Request;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.Response;
import org.opensearch.common.settings.Settings;
import org.opensearch.test.rest.OpenSearchRestTestCase;

import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class SearchRelevanceRestTestCase extends OpenSearchRestTestCase {

    public static final String QUERY_SETS_ENDPOINT = "/_plugins/search_relevance/query_sets";
    public static final String EXPERIMENTS_ENDPOINT = "/_plugins/search_relevance/experiments";
    public static final String SEARCH_CONFIGURATIONS_ENDPOINT = "/_plugins/search_relevance/search_configurations";

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Override
    protected Settings restClientSettings() {
        return super.restClientSettings();
    }

    @Override
    protected boolean enableWarningsCheck() {
        return false;  // Disable warnings check
    }

    public Response makeRequest(String method, String endpoint, String body) throws IOException {
        Request request = new Request(method, endpoint);
        request.setOptions(suppressWarnings());
        if (body != null) {
            request.setJsonEntity(body);
        }
        return client().performRequest(request);
    }

    public RequestOptions suppressWarnings() {
        final RequestOptions.Builder options = RequestOptions.DEFAULT.toBuilder();
        options.setWarningsHandler(PERMISSIVE);
        return options.build();
    }
}
