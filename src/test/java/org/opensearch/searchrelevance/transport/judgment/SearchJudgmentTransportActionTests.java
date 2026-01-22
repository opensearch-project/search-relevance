/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

public class SearchJudgmentTransportActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private JudgmentDao judgmentDao;
    @Mock
    private SearchResponse searchResponse;
    @Mock
    private ActionListener<SearchResponse> listener;

    private SearchJudgmentTransportAction transportAction;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        transportAction = new SearchJudgmentTransportAction(transportService, actionFilters, judgmentDao);
    }

    public void testDoExecute_withSourceBuilder_callsDaoAndResponds() {
        SearchSourceBuilder builder = new SearchSourceBuilder();
        SearchRequest request = new SearchRequest().source(builder);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> l = invocation.getArgument(1);
            l.onResponse(searchResponse);
            return null;
        }).when(judgmentDao).listJudgment(eq(builder), any());

        transportAction.doExecute(null, request, listener);

        verify(judgmentDao).listJudgment(eq(builder), any());
        verify(listener).onResponse(searchResponse);
    }

    public void testDoExecute_withoutSourceBuilder_constructsDefault() {
        SearchRequest request = new SearchRequest(); // no source set

        doAnswer(invocation -> {
            ActionListener<SearchResponse> l = invocation.getArgument(1);
            l.onResponse(searchResponse);
            return null;
        }).when(judgmentDao).listJudgment(any(SearchSourceBuilder.class), any());

        transportAction.doExecute(null, request, listener);

        ArgumentCaptor<SearchSourceBuilder> captor = ArgumentCaptor.forClass(SearchSourceBuilder.class);
        verify(judgmentDao).listJudgment(captor.capture(), any());
        assertNotNull(captor.getValue());
        verify(listener).onResponse(searchResponse);
    }

    public void testDoExecute_whenDaoThrows_returnsFailure() {
        SearchSourceBuilder builder = new SearchSourceBuilder();
        SearchRequest request = new SearchRequest().source(builder);

        doThrow(new RuntimeException("boom")).when(judgmentDao).listJudgment(eq(builder), any());

        transportAction.doExecute(null, request, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof SearchRelevanceException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((SearchRelevanceException) exception).status());
    }
}
