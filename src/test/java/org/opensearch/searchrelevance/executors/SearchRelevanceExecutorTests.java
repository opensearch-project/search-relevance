/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import static org.opensearch.searchrelevance.executors.SearchRelevanceExecutor.SEARCH_RELEVANCE_EXEC_THREAD_POOL_NAME;

import org.opensearch.common.settings.Settings;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ExecutorBuilder;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;

public class SearchRelevanceExecutorTests extends OpenSearchTestCase {

    public void testGetExecutorBuilder() {
        Settings settings = Settings.builder().put("node.processors", 4).build();

        ExecutorBuilder<?> executorBuilder = SearchRelevanceExecutor.getExecutorBuilder(settings);

        assertNotNull(executorBuilder);
        assertTrue(executorBuilder instanceof ScalingExecutorBuilder);
    }

    public void testGetExecutorBuilderWithMinThreadSize() {
        Settings settings = Settings.builder().put("node.processors", 1).build();

        ExecutorBuilder<?> executorBuilder = SearchRelevanceExecutor.getExecutorBuilder(settings);

        assertNotNull(executorBuilder);
        assertTrue(executorBuilder instanceof ScalingExecutorBuilder);
    }

    public void testInitializeThrowsExceptionForNullThreadPool() {
        IllegalArgumentException exception = expectThrows(IllegalArgumentException.class, () -> SearchRelevanceExecutor.initialize(null));

        assertTrue(exception.getMessage().contains("thread-pool"));
        assertTrue(exception.getMessage().contains("cannot be null"));
    }

    public void testInitializeAndGetExecutor() {
        Settings settings = Settings.builder().put("node.processors", 2).build();

        ExecutorBuilder<?> executorBuilder = SearchRelevanceExecutor.getExecutorBuilder(settings);

        TestThreadPool threadPool = new TestThreadPool("test-pool", executorBuilder);
        try {
            SearchRelevanceExecutor.initialize(threadPool);

            assertNotNull(SearchRelevanceExecutor.getExecutor());
        } finally {
            threadPool.shutdown();
        }
    }

    public void testGetExecutorWithoutInitialization() {
        // When not initialized, should return a fallback executor
        assertNotNull(SearchRelevanceExecutor.getExecutor());
    }

    public void testGetThreadPoolName() {
        assertEquals("_plugin_search_relevance_executor", SEARCH_RELEVANCE_EXEC_THREAD_POOL_NAME);
    }
}
