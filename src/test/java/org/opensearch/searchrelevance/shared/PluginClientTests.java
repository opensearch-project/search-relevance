/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.shared;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.concurrent.atomic.AtomicReference;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.CheckedRunnable;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.identity.NamedPrincipal;
import org.opensearch.identity.PluginSubject;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class PluginClientTests extends OpenSearchTestCase {

    private static final String CALLER_HEADER = "x-caller";

    private Client delegate;
    private ThreadContext threadContext;
    private PluginClient pluginClient;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        Settings settings = Settings.EMPTY;
        threadContext = new ThreadContext(settings);
        ThreadPool threadPool = mock(ThreadPool.class);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        delegate = mock(Client.class);
        when(delegate.settings()).thenReturn(settings);
        when(delegate.threadPool()).thenReturn(threadPool);
        pluginClient = new PluginClient(delegate);
    }

    public void testActionIsDelegatedAndCallerContextSurvives() {
        pluginClient.setSubject(new StashingSubject());
        threadContext.putHeader(CALLER_HEADER, "caller-value");

        pluginClient.search(new SearchRequest("index"), ActionListener.wrap(r -> {}, e -> {}));

        verify(delegate, times(1)).execute(any(), any(), any());
        assertEquals("caller-value", threadContext.getHeader(CALLER_HEADER));
    }

    public void testSynchronousFailureIsReportedToListener() {
        pluginClient.setSubject(new FailingSubject());

        AtomicReference<SearchResponse> response = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        pluginClient.search(new SearchRequest("index"), ActionListener.wrap(response::set, failure::set));

        assertNull(response.get());
        assertNotNull("a synchronous failure must reach the listener, not escape doExecute", failure.get());
        assertEquals("subject refused", failure.get().getMessage());
        verify(delegate, times(0)).execute(any(), any(), any());
    }

    public void testUnassignedSubjectIsRejected() {
        expectThrows(
            IllegalStateException.class,
            () -> pluginClient.search(new SearchRequest("index"), ActionListener.wrap(r -> {}, e -> {}))
        );
    }

    /**
     * Mirrors the real plugin subjects, which stash the context for the duration of the body and
     * restore it on exit.
     */
    private class StashingSubject implements PluginSubject {
        @Override
        public Principal getPrincipal() {
            return new NamedPrincipal("plugin:test");
        }

        @Override
        public <E extends Exception> void runAs(CheckedRunnable<E> r) throws E {
            try (ThreadContext.StoredContext ignored = threadContext.stashContext()) {
                r.run();
            }
        }
    }

    private static class FailingSubject implements PluginSubject {
        @Override
        public Principal getPrincipal() {
            return new NamedPrincipal("plugin:test");
        }

        @Override
        public <E extends Exception> void runAs(CheckedRunnable<E> r) {
            throw new IllegalStateException("subject refused");
        }
    }
}
