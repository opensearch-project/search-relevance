/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.plugin;

import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENT_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENT_CACHE_INDEX;
import static org.opensearch.searchrelevance.settings.SearchRelevanceSettings.SEARCH_RELEVANCE_QUERY_SET_MAX_LIMIT;
import static org.opensearch.searchrelevance.settings.SearchRelevanceSettings.SEARCH_RELEVANCE_STATS_ENABLED;
import static org.opensearch.searchrelevance.settings.SearchRelevanceSettings.SEARCH_RELEVANCE_WORKBENCH_ENABLED;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import org.opensearch.action.ActionRequest;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.IndexScopedSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.settings.SettingsFilter;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.NamedWriteableRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.env.Environment;
import org.opensearch.env.NodeEnvironment;
import org.opensearch.indices.SystemIndexDescriptor;
import org.opensearch.ml.client.MachineLearningNodeClient;
import org.opensearch.plugins.ActionPlugin;
import org.opensearch.plugins.ClusterPlugin;
import org.opensearch.plugins.ExtensiblePlugin;
import org.opensearch.plugins.Plugin;
import org.opensearch.plugins.SystemIndexPlugin;
import org.opensearch.repositories.RepositoriesService;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestHandler;
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
import org.opensearch.searchrelevance.rest.RestCreateQuerySetAction;
import org.opensearch.searchrelevance.rest.RestDeleteExperimentAction;
import org.opensearch.searchrelevance.rest.RestDeleteJudgmentAction;
import org.opensearch.searchrelevance.rest.RestDeleteQuerySetAction;
import org.opensearch.searchrelevance.rest.RestDeleteSearchConfigurationAction;
import org.opensearch.searchrelevance.rest.RestGetExperimentAction;
import org.opensearch.searchrelevance.rest.RestGetJudgmentAction;
import org.opensearch.searchrelevance.rest.RestGetQuerySetAction;
import org.opensearch.searchrelevance.rest.RestGetSearchConfigurationAction;
import org.opensearch.searchrelevance.rest.RestPostExperimentAction;
import org.opensearch.searchrelevance.rest.RestPutExperimentAction;
import org.opensearch.searchrelevance.rest.RestPutJudgmentAction;
import org.opensearch.searchrelevance.rest.RestPutQuerySetAction;
import org.opensearch.searchrelevance.rest.RestPutSearchConfigurationAction;
import org.opensearch.searchrelevance.rest.RestSearchRelevanceStatsAction;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.stats.events.EventStatsManager;
import org.opensearch.searchrelevance.stats.info.InfoStatsManager;
import org.opensearch.searchrelevance.transport.experiment.DeleteExperimentAction;
import org.opensearch.searchrelevance.transport.experiment.DeleteExperimentTransportAction;
import org.opensearch.searchrelevance.transport.experiment.GetExperimentAction;
import org.opensearch.searchrelevance.transport.experiment.GetExperimentTransportAction;
import org.opensearch.searchrelevance.transport.experiment.PostExperimentAction;
import org.opensearch.searchrelevance.transport.experiment.PostExperimentTransportAction;
import org.opensearch.searchrelevance.transport.experiment.PutExperimentAction;
import org.opensearch.searchrelevance.transport.experiment.PutExperimentTransportAction;
import org.opensearch.searchrelevance.transport.judgment.DeleteJudgmentAction;
import org.opensearch.searchrelevance.transport.judgment.DeleteJudgmentTransportAction;
import org.opensearch.searchrelevance.transport.judgment.GetJudgmentAction;
import org.opensearch.searchrelevance.transport.judgment.GetJudgmentTransportAction;
import org.opensearch.searchrelevance.transport.judgment.PutJudgmentAction;
import org.opensearch.searchrelevance.transport.judgment.PutJudgmentTransportAction;
import org.opensearch.searchrelevance.transport.queryset.DeleteQuerySetAction;
import org.opensearch.searchrelevance.transport.queryset.DeleteQuerySetTransportAction;
import org.opensearch.searchrelevance.transport.queryset.GetQuerySetAction;
import org.opensearch.searchrelevance.transport.queryset.GetQuerySetTransportAction;
import org.opensearch.searchrelevance.transport.queryset.PostQuerySetAction;
import org.opensearch.searchrelevance.transport.queryset.PostQuerySetTransportAction;
import org.opensearch.searchrelevance.transport.queryset.PutQuerySetAction;
import org.opensearch.searchrelevance.transport.queryset.PutQuerySetTransportAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.DeleteSearchConfigurationAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.DeleteSearchConfigurationTransportAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.GetSearchConfigurationAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.GetSearchConfigurationTransportAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.PutSearchConfigurationAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.PutSearchConfigurationTransportAction;
import org.opensearch.searchrelevance.transport.stats.SearchRelevanceStatsAction;
import org.opensearch.searchrelevance.transport.stats.SearchRelevanceStatsTransportAction;
import org.opensearch.searchrelevance.utils.ClusterUtil;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;
import org.opensearch.watcher.ResourceWatcherService;

/**
 * Search Relevance plugin class
 */
public class SearchRelevancePlugin extends Plugin implements ActionPlugin, SystemIndexPlugin, ClusterPlugin, ExtensiblePlugin {

    private Client client;
    private ClusterService clusterService;
    private SearchRelevanceIndicesManager searchRelevanceIndicesManager;
    private QuerySetDao querySetDao;
    private SearchConfigurationDao searchConfigurationDao;
    private ExperimentDao experimentDao;
    private ExperimentVariantDao experimentVariantDao;
    private JudgmentDao judgmentDao;
    private EvaluationResultDao evaluationResultDao;
    private JudgmentCacheDao judgmentCacheDao;
    private MLAccessor mlAccessor;
    private MetricsHelper metricsHelper;
    private SearchRelevanceSettingsAccessor settingsAccessor;
    private ClusterUtil clusterUtil;
    private InfoStatsManager infoStatsManager;

    @Override
    public Collection<SystemIndexDescriptor> getSystemIndexDescriptors(Settings settings) {
        return List.of(
            new SystemIndexDescriptor(EXPERIMENT_INDEX, "System index used for experiment data"),
            new SystemIndexDescriptor(JUDGMENT_CACHE_INDEX, "System index used for judgment cache data")
        );
    }

    @Override
    public Collection<Object> createComponents(
        Client client,
        ClusterService clusterService,
        ThreadPool threadPool,
        ResourceWatcherService resourceWatcherService,
        ScriptService scriptService,
        NamedXContentRegistry xContentRegistry,
        Environment environment,
        NodeEnvironment nodeEnvironment,
        NamedWriteableRegistry namedWriteableRegistry,
        IndexNameExpressionResolver indexNameExpressionResolver,
        Supplier<RepositoriesService> repositoriesServiceSupplier
    ) {
        this.client = client;
        this.clusterService = clusterService;
        this.searchRelevanceIndicesManager = new SearchRelevanceIndicesManager(clusterService, client);
        this.experimentDao = new ExperimentDao(searchRelevanceIndicesManager);
        this.experimentVariantDao = new ExperimentVariantDao(searchRelevanceIndicesManager);
        this.querySetDao = new QuerySetDao(searchRelevanceIndicesManager);
        this.searchConfigurationDao = new SearchConfigurationDao(searchRelevanceIndicesManager);
        this.judgmentDao = new JudgmentDao(searchRelevanceIndicesManager);
        this.evaluationResultDao = new EvaluationResultDao(searchRelevanceIndicesManager);
        this.judgmentCacheDao = new JudgmentCacheDao(searchRelevanceIndicesManager);
        MachineLearningNodeClient mlClient = new MachineLearningNodeClient(client);
        this.mlAccessor = new MLAccessor(mlClient);
        this.metricsHelper = new MetricsHelper(clusterService, client, judgmentDao, evaluationResultDao, experimentVariantDao);
        this.settingsAccessor = new SearchRelevanceSettingsAccessor(clusterService, environment.settings());
        this.clusterUtil = new ClusterUtil(clusterService);
        this.infoStatsManager = new InfoStatsManager(settingsAccessor);
        EventStatsManager.instance().initialize(settingsAccessor);

        return List.of(
            searchRelevanceIndicesManager,
            querySetDao,
            searchConfigurationDao,
            experimentDao,
            experimentVariantDao,
            judgmentDao,
            evaluationResultDao,
            judgmentCacheDao,
            mlAccessor,
            metricsHelper,
            infoStatsManager
        );
    }

    @Override
    public List<RestHandler> getRestHandlers(
        Settings settings,
        RestController restController,
        ClusterSettings clusterSettings,
        IndexScopedSettings indexScopedSettings,
        SettingsFilter settingsFilter,
        IndexNameExpressionResolver indexNameExpressionResolver,
        Supplier<DiscoveryNodes> nodesInCluster
    ) {
        return List.of(
            new RestCreateQuerySetAction(settingsAccessor),
            new RestPutQuerySetAction(settingsAccessor),
            new RestDeleteQuerySetAction(settingsAccessor),
            new RestGetQuerySetAction(settingsAccessor),
            new RestPutJudgmentAction(settingsAccessor),
            new RestDeleteJudgmentAction(settingsAccessor),
            new RestGetJudgmentAction(settingsAccessor),
            new RestPutSearchConfigurationAction(settingsAccessor),
            new RestDeleteSearchConfigurationAction(settingsAccessor),
            new RestGetSearchConfigurationAction(settingsAccessor),
            new RestPostExperimentAction(settingsAccessor),
            new RestPutExperimentAction(settingsAccessor),
            new RestGetExperimentAction(settingsAccessor),
            new RestDeleteExperimentAction(settingsAccessor),
            new RestSearchRelevanceStatsAction(settingsAccessor, clusterUtil)
        );
    }

    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        return List.of(
            new ActionHandler<>(PostQuerySetAction.INSTANCE, PostQuerySetTransportAction.class),
            new ActionHandler<>(PutQuerySetAction.INSTANCE, PutQuerySetTransportAction.class),
            new ActionHandler<>(DeleteQuerySetAction.INSTANCE, DeleteQuerySetTransportAction.class),
            new ActionHandler<>(GetQuerySetAction.INSTANCE, GetQuerySetTransportAction.class),
            new ActionHandler<>(PutJudgmentAction.INSTANCE, PutJudgmentTransportAction.class),
            new ActionHandler<>(DeleteJudgmentAction.INSTANCE, DeleteJudgmentTransportAction.class),
            new ActionHandler<>(GetJudgmentAction.INSTANCE, GetJudgmentTransportAction.class),
            new ActionHandler<>(PutSearchConfigurationAction.INSTANCE, PutSearchConfigurationTransportAction.class),
            new ActionHandler<>(DeleteSearchConfigurationAction.INSTANCE, DeleteSearchConfigurationTransportAction.class),
            new ActionHandler<>(GetSearchConfigurationAction.INSTANCE, GetSearchConfigurationTransportAction.class),
            new ActionHandler<>(PostExperimentAction.INSTANCE, PostExperimentTransportAction.class),
            new ActionHandler<>(PutExperimentAction.INSTANCE, PutExperimentTransportAction.class),
            new ActionHandler<>(DeleteExperimentAction.INSTANCE, DeleteExperimentTransportAction.class),
            new ActionHandler<>(GetExperimentAction.INSTANCE, GetExperimentTransportAction.class),
            new ActionHandler<>(SearchRelevanceStatsAction.INSTANCE, SearchRelevanceStatsTransportAction.class)
        );
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(SEARCH_RELEVANCE_WORKBENCH_ENABLED, SEARCH_RELEVANCE_STATS_ENABLED, SEARCH_RELEVANCE_QUERY_SET_MAX_LIMIT);
    }
}
