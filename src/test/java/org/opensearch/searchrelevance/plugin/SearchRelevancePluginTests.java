/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.plugin;

import static org.mockito.Mockito.when;
import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENT_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENT_CACHE_INDEX;
import static org.opensearch.searchrelevance.settings.SearchRelevanceSettings.SEARCH_RELEVANCE_WORKBENCH_ENABLED;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionRequest;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
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
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.metrics.MetricsHelper;
import org.opensearch.searchrelevance.ml.MLAccessor;
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
        MLAccessor.class,
        MetricsHelper.class
    );

    @Override
    public void setUp() throws Exception {
        super.setUp();
        openMocks = MockitoAnnotations.openMocks(this);
        nodeEnvironment = null;
        Settings settings = Settings.builder().put("path.home", createTempDir()).build();

        // Mock environment
        when(environment.settings()).thenReturn(settings);

        // Mock ClusterService
        when(clusterService.getClusterSettings()).thenReturn(
            new ClusterSettings(settings, new HashSet<>(Arrays.asList(SEARCH_RELEVANCE_WORKBENCH_ENABLED)))
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
        assertEquals(13, plugin.getRestHandlers(Settings.EMPTY, null, null, null, null, null, null).size());
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
        Setting<?> setting = settings.get(0);
        assertEquals("plugins.search_relevance.workbench_enabled", setting.getKey());
        assertEquals(1, settings.size());
        assertEquals(true, setting.get(Settings.EMPTY));
    }
}
