/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.plugin;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENT_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENT_CACHE_INDEX;
import static org.opensearch.searchrelevance.settings.SearchRelevanceSettings.SEARCH_RELEVANCE_QUERY_SET_MAX_LIMIT;
import static org.opensearch.searchrelevance.settings.SearchRelevanceSettings.SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_ENABLED;
import static org.opensearch.searchrelevance.settings.SearchRelevanceSettings.SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_MINIMUM_INTERVAL;
import static org.opensearch.searchrelevance.settings.SearchRelevanceSettings.SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_TIMEOUT;
import static org.opensearch.searchrelevance.settings.SearchRelevanceSettings.SEARCH_RELEVANCE_STATS_ENABLED;
import static org.opensearch.searchrelevance.settings.SearchRelevanceSettings.SEARCH_RELEVANCE_WORKBENCH_ENABLED;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionRequest;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.NamedWriteableRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.env.Environment;
import org.opensearch.env.NodeEnvironment;
import org.opensearch.indices.SystemIndexDescriptor;
import org.opensearch.plugins.ActionPlugin;
import org.opensearch.plugins.SystemIndexPlugin;
import org.opensearch.repositories.RepositoriesService;
import org.opensearch.script.ScriptService;
import org.opensearch.searchrelevance.dao.EvaluationResultDao;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.ExperimentVariantDao;
import org.opensearch.searchrelevance.dao.JudgmentCacheDao;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.ScheduledExperimentHistoryDao;
import org.opensearch.searchrelevance.dao.ScheduledJobsDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.executors.ExperimentRunningManager;
import org.opensearch.searchrelevance.executors.ExperimentTaskManager;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.metrics.MetricsHelper;
import org.opensearch.searchrelevance.ml.MLAccessor;
import org.opensearch.searchrelevance.scheduler.SearchRelevanceJobRunner;
import org.opensearch.searchrelevance.stats.info.InfoStatsManager;
import org.opensearch.searchrelevance.transport.experiment.DeleteExperimentAction;
import org.opensearch.searchrelevance.transport.experiment.GetExperimentAction;
import org.opensearch.searchrelevance.transport.experiment.PutExperimentAction;
import org.opensearch.searchrelevance.transport.queryset.DeleteQuerySetAction;
import org.opensearch.searchrelevance.transport.queryset.GetQuerySetAction;
import org.opensearch.searchrelevance.transport.queryset.PostQuerySetAction;
import org.opensearch.searchrelevance.transport.queryset.PutQuerySetAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.DeleteSearchConfigurationAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.GetSearchConfigurationAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.PutSearchConfigurationAction;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;
import org.opensearch.watcher.ResourceWatcherService;

public class SearchRelevancePluginTests extends OpenSearchTestCase {

    @Mock
    private Client client;
    @Mock
    private ClusterService clusterService;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private ResourceWatcherService resourceWatcherService;
    @Mock
    private ScriptService scriptService;
    @Mock
    private NamedXContentRegistry xContentRegistry;
    @Mock
    private Environment environment;
    @Mock
    private NamedWriteableRegistry namedWriteableRegistry;
    @Mock
    private IndexNameExpressionResolver indexNameExpressionResolver;
    @Mock
    private Supplier<RepositoriesService> repositoriesServiceSupplier;

    private AutoCloseable openMocks;
    private NodeEnvironment nodeEnvironment;
    private SearchRelevancePlugin plugin;

    public static final Set<String> SUPPORTED_SYSTEM_INDEX_PATTERN = Set.of(EXPERIMENT_INDEX, JUDGMENT_CACHE_INDEX);

    private final Set<Class> SUPPORTED_COMPONENTS = Set.of(
        SearchRelevanceIndicesManager.class,
        QuerySetDao.class,
        ExperimentDao.class,
        ExperimentVariantDao.class,
        SearchConfigurationDao.class,
        JudgmentDao.class,
        EvaluationResultDao.class,
        JudgmentCacheDao.class,
        ScheduledJobsDao.class,
        ScheduledExperimentHistoryDao.class,
        MLAccessor.class,
        MetricsHelper.class,
        InfoStatsManager.class,
        ExperimentTaskManager.class,
        SearchRelevanceJobRunner.class,
        ExperimentRunningManager.class
    );

    @Override
    public void setUp() throws Exception {
        super.setUp();
        openMocks = MockitoAnnotations.openMocks(this);
        nodeEnvironment = null;
        Settings settings = Settings.builder().put("path.home", createTempDir()).build();

        // Mock environment
        when(environment.settings()).thenReturn(settings);

        // Mock ThreadPool to return a mock executor for SearchRelevanceExecutor
        ExecutorService mockExecutor = mock(ExecutorService.class);
        when(threadPool.executor("_plugin_search_relevance_executor")).thenReturn(mockExecutor);

        // Mock ClusterService
        when(clusterService.getClusterSettings()).thenReturn(
            new ClusterSettings(
                settings,
                new HashSet<>(
                    Arrays.asList(
                        SEARCH_RELEVANCE_WORKBENCH_ENABLED,
                        SEARCH_RELEVANCE_STATS_ENABLED,
                        SEARCH_RELEVANCE_QUERY_SET_MAX_LIMIT,
                        SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_ENABLED,
                        SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_TIMEOUT,
                        SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_MINIMUM_INTERVAL
                    )
                )
            )
        );
        plugin = new SearchRelevancePlugin();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        openMocks.close();
    }

    public void testSystemIndexDescriptors() {
        Set<String> registeredSystemIndexPatterns = new HashSet<>();
        for (SystemIndexDescriptor descriptor : plugin.getSystemIndexDescriptors(Settings.EMPTY)) {
            registeredSystemIndexPatterns.add(descriptor.getIndexPattern());
        }
        assertEquals(SUPPORTED_SYSTEM_INDEX_PATTERN, registeredSystemIndexPatterns);
    }

    public void testCreateComponents() {
        Set<Class> registeredComponents = new HashSet<>();
        Collection<Object> components = plugin.createComponents(
            client,
            clusterService,
            threadPool,
            resourceWatcherService,
            scriptService,
            xContentRegistry,
            environment,
            nodeEnvironment,
            namedWriteableRegistry,
            indexNameExpressionResolver,
            repositoriesServiceSupplier
        );
        for (Object component : components) {
            registeredComponents.add(component.getClass());
        }
        assertEquals(SUPPORTED_COMPONENTS, registeredComponents);
    }

    public void testIsAnActionPlugin() {
        assertTrue(plugin instanceof ActionPlugin);
    }

    public void testIsAnSystemIndexPlugin() {
        assertTrue(plugin instanceof SystemIndexPlugin);
    }

    public void testTotalRestHandlers() {
        assertEquals(17, plugin.getRestHandlers(Settings.EMPTY, null, null, null, null, null, null).size());
    }

    public void testQuerySetTransportIsAdded() {
        final List<ActionPlugin.ActionHandler<? extends ActionRequest, ? extends ActionResponse>> actions = plugin.getActions();
        assertEquals(1, actions.stream().filter(actionHandler -> actionHandler.getAction() instanceof PostQuerySetAction).count());
        assertEquals(1, actions.stream().filter(actionHandler -> actionHandler.getAction() instanceof PutQuerySetAction).count());
        assertEquals(1, actions.stream().filter(actionHandler -> actionHandler.getAction() instanceof GetQuerySetAction).count());
        assertEquals(1, actions.stream().filter(actionHandler -> actionHandler.getAction() instanceof DeleteQuerySetAction).count());
        assertEquals(
            1,
            actions.stream().filter(actionHandler -> actionHandler.getAction() instanceof PutSearchConfigurationAction).count()
        );
        assertEquals(
            1,
            actions.stream().filter(actionHandler -> actionHandler.getAction() instanceof GetSearchConfigurationAction).count()
        );
        assertEquals(
            1,
            actions.stream().filter(actionHandler -> actionHandler.getAction() instanceof DeleteSearchConfigurationAction).count()
        );
        assertEquals(1, actions.stream().filter(actionHandler -> actionHandler.getAction() instanceof PutExperimentAction).count());
        assertEquals(1, actions.stream().filter(actionHandler -> actionHandler.getAction() instanceof GetExperimentAction).count());
        assertEquals(1, actions.stream().filter(actionHandler -> actionHandler.getAction() instanceof DeleteExperimentAction).count());
    }

    public void testGetSettings() {
        List<Setting<?>> settings = plugin.getSettings();
        assertEquals(6, settings.size());

        Setting<?> setting0 = settings.get(0);
        assertEquals("plugins.search_relevance.workbench_enabled", setting0.getKey());
        assertEquals(true, setting0.get(Settings.EMPTY));

        Setting<?> setting1 = settings.get(1);
        assertEquals("plugins.search_relevance.stats_enabled", setting1.getKey());
        assertEquals(true, setting1.get(Settings.EMPTY));

        Setting<?> setting2 = settings.get(2);
        assertEquals("plugins.search_relevance.query_set.maximum", setting2.getKey());
        assertEquals(1000, setting2.get(Settings.EMPTY));

        Setting<?> setting3 = settings.get(3);
        assertEquals("plugins.search_relevance.scheduled_experiments_enabled", setting3.getKey());
        assertEquals(true, setting3.get(Settings.EMPTY));

        Setting<?> setting4 = settings.get(4);
        assertEquals("plugins.search_relevance.scheduled_experiments_timeout", setting4.getKey());
        assertEquals(TimeValue.timeValueMinutes(60), setting4.get(Settings.EMPTY));

        Setting<?> setting5 = settings.get(5);
        assertEquals("plugins.search_relevance.scheduled_experiments_minimum_interval", setting5.getKey());
        assertEquals(TimeValue.timeValueSeconds(1), setting5.get(Settings.EMPTY));
    }
}
