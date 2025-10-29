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
import static org.opensearch.searchrelevance.common.PluginConstants.SCHEDULED_JOBS_INDEX;
import static org.opensearch.searchrelevance.settings.SearchRelevanceSettings.SEARCH_RELEVANCE_QUERY_SET_MAX_LIMIT;
import static org.opensearch.searchrelevance.settings.SearchRelevanceSettings.SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_ENABLED;
import static org.opensearch.searchrelevance.settings.SearchRelevanceSettings.SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_MINIMUM_INTERVAL;
import static org.opensearch.searchrelevance.settings.SearchRelevanceSettings.SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_TIMEOUT;
import static org.opensearch.searchrelevance.settings.SearchRelevanceSettings.SEARCH_RELEVANCE_STATS_ENABLED;
import static org.opensearch.searchrelevance.settings.SearchRelevanceSettings.SEARCH_RELEVANCE_WORKBENCH_ENABLED;

import java.io.IOException;
import java.time.Instant;
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
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.core.xcontent.XContentParserUtils;
import org.opensearch.env.Environment;
import org.opensearch.env.NodeEnvironment;
import org.opensearch.indices.SystemIndexDescriptor;
import org.opensearch.jobscheduler.spi.JobSchedulerExtension;
import org.opensearch.jobscheduler.spi.ScheduledJobParser;
import org.opensearch.jobscheduler.spi.ScheduledJobRunner;
import org.opensearch.jobscheduler.spi.schedule.ScheduleParser;
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
import org.opensearch.searchrelevance.dao.ScheduledExperimentHistoryDao;
import org.opensearch.searchrelevance.dao.ScheduledJobsDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.executors.ExperimentRunningManager;
import org.opensearch.searchrelevance.executors.ExperimentTaskManager;
import org.opensearch.searchrelevance.executors.SearchRelevanceExecutor;
import org.opensearch.searchrelevance.experiment.HybridOptimizerExperimentProcessor;
import org.opensearch.searchrelevance.experiment.PointwiseExperimentProcessor;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.metrics.MetricsHelper;
import org.opensearch.searchrelevance.ml.MLAccessor;
import org.opensearch.searchrelevance.model.ScheduledJob;
import org.opensearch.searchrelevance.rest.RestCreateQuerySetAction;
import org.opensearch.searchrelevance.rest.RestDeleteExperimentAction;
import org.opensearch.searchrelevance.rest.RestDeleteJudgmentAction;
import org.opensearch.searchrelevance.rest.RestDeleteQuerySetAction;
import org.opensearch.searchrelevance.rest.RestDeleteScheduledExperimentAction;
import org.opensearch.searchrelevance.rest.RestDeleteSearchConfigurationAction;
import org.opensearch.searchrelevance.rest.RestGetExperimentAction;
import org.opensearch.searchrelevance.rest.RestGetJudgmentAction;
import org.opensearch.searchrelevance.rest.RestGetQuerySetAction;
import org.opensearch.searchrelevance.rest.RestGetScheduledExperimentAction;
import org.opensearch.searchrelevance.rest.RestGetSearchConfigurationAction;
import org.opensearch.searchrelevance.rest.RestPostScheduledExperimentAction;
import org.opensearch.searchrelevance.rest.RestPutExperimentAction;
import org.opensearch.searchrelevance.rest.RestPutJudgmentAction;
import org.opensearch.searchrelevance.rest.RestPutQuerySetAction;
import org.opensearch.searchrelevance.rest.RestPutSearchConfigurationAction;
import org.opensearch.searchrelevance.rest.RestSearchRelevanceStatsAction;
import org.opensearch.searchrelevance.scheduler.ScheduledExperimentRunnerManager;
import org.opensearch.searchrelevance.scheduler.SearchRelevanceJobParameters;
import org.opensearch.searchrelevance.scheduler.SearchRelevanceJobRunner;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.stats.events.EventStatsManager;
import org.opensearch.searchrelevance.stats.info.InfoStatsManager;
import org.opensearch.searchrelevance.transport.experiment.DeleteExperimentAction;
import org.opensearch.searchrelevance.transport.experiment.DeleteExperimentTransportAction;
import org.opensearch.searchrelevance.transport.experiment.GetExperimentAction;
import org.opensearch.searchrelevance.transport.experiment.GetExperimentTransportAction;
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
import org.opensearch.searchrelevance.transport.scheduledJob.DeleteScheduledExperimentAction;
import org.opensearch.searchrelevance.transport.scheduledJob.DeleteScheduledExperimentTransportAction;
import org.opensearch.searchrelevance.transport.scheduledJob.GetScheduledExperimentAction;
import org.opensearch.searchrelevance.transport.scheduledJob.GetScheduledExperimentTransportAction;
import org.opensearch.searchrelevance.transport.scheduledJob.PostScheduledExperimentAction;
import org.opensearch.searchrelevance.transport.scheduledJob.PostScheduledExperimentTransportAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.DeleteSearchConfigurationAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.DeleteSearchConfigurationTransportAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.GetSearchConfigurationAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.GetSearchConfigurationTransportAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.PutSearchConfigurationAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.PutSearchConfigurationTransportAction;
import org.opensearch.searchrelevance.transport.stats.SearchRelevanceStatsAction;
import org.opensearch.searchrelevance.transport.stats.SearchRelevanceStatsTransportAction;
import org.opensearch.searchrelevance.utils.ClusterUtil;
import org.opensearch.searchrelevance.utils.CronUtil;
import org.opensearch.threadpool.ExecutorBuilder;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;
import org.opensearch.watcher.ResourceWatcherService;

/**
 * Search Relevance plugin class
 */
public class SearchRelevancePlugin extends Plugin
    implements
        ActionPlugin,
        SystemIndexPlugin,
        ClusterPlugin,
        ExtensiblePlugin,
        JobSchedulerExtension {
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
    private ScheduledJobsDao scheduledJobsDao;
    private ScheduledExperimentHistoryDao scheduledExperimentHistoryDao;
    private MLAccessor mlAccessor;
    private MetricsHelper metricsHelper;
    private SearchRelevanceSettingsAccessor settingsAccessor;
    private ClusterUtil clusterUtil;
    private CronUtil cronUtil;
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
        this.scheduledJobsDao = new ScheduledJobsDao(searchRelevanceIndicesManager);
        this.scheduledExperimentHistoryDao = new ScheduledExperimentHistoryDao(searchRelevanceIndicesManager);
        MachineLearningNodeClient mlClient = new MachineLearningNodeClient(client);
        this.mlAccessor = new MLAccessor(mlClient);
        SearchRelevanceExecutor.initialize(threadPool);
        ExperimentTaskManager experimentTaskManager = new ExperimentTaskManager(
            client,
            evaluationResultDao,
            experimentVariantDao,
            threadPool
        );
        this.metricsHelper = new MetricsHelper(clusterService, client, judgmentDao, evaluationResultDao, experimentVariantDao);
        this.settingsAccessor = new SearchRelevanceSettingsAccessor(clusterService, environment.settings());
        this.clusterUtil = new ClusterUtil(clusterService);
        this.cronUtil = new CronUtil(settingsAccessor);
        this.infoStatsManager = new InfoStatsManager(settingsAccessor);
        EventStatsManager.instance().initialize(settingsAccessor);
        SearchRelevanceJobRunner jobRunner = SearchRelevanceJobRunner.INSTANCE;
        ExperimentRunningManager experimentRunningManager = new ExperimentRunningManager(
            experimentDao,
            querySetDao,
            searchConfigurationDao,
            scheduledExperimentHistoryDao,
            metricsHelper,
            new HybridOptimizerExperimentProcessor(judgmentDao, experimentTaskManager),
            new PointwiseExperimentProcessor(judgmentDao, experimentTaskManager),
            threadPool,
            settingsAccessor
        );

        ScheduledExperimentRunnerManager manager = new ScheduledExperimentRunnerManager(
            experimentDao,
            scheduledExperimentHistoryDao,
            experimentRunningManager,
            scheduledJobsDao
        );
        jobRunner.setThreadPool(threadPool);
        jobRunner.setClient(client);
        jobRunner.setSettingsAccessor(settingsAccessor);
        jobRunner.setManager(manager);

        return List.of(
            searchRelevanceIndicesManager,
            querySetDao,
            searchConfigurationDao,
            experimentDao,
            experimentVariantDao,
            judgmentDao,
            evaluationResultDao,
            judgmentCacheDao,
            scheduledJobsDao,
            scheduledExperimentHistoryDao,
            mlAccessor,
            metricsHelper,
            infoStatsManager,
            experimentTaskManager,
            jobRunner,
            experimentRunningManager
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
            new RestPutExperimentAction(settingsAccessor),
            new RestGetExperimentAction(settingsAccessor),
            new RestDeleteExperimentAction(settingsAccessor),
            new RestSearchRelevanceStatsAction(settingsAccessor, clusterUtil),
            new RestPostScheduledExperimentAction(settingsAccessor, cronUtil),
            new RestDeleteScheduledExperimentAction(settingsAccessor),
            new RestGetScheduledExperimentAction(settingsAccessor)
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
            new ActionHandler<>(PutExperimentAction.INSTANCE, PutExperimentTransportAction.class),
            new ActionHandler<>(DeleteExperimentAction.INSTANCE, DeleteExperimentTransportAction.class),
            new ActionHandler<>(GetExperimentAction.INSTANCE, GetExperimentTransportAction.class),
            new ActionHandler<>(SearchRelevanceStatsAction.INSTANCE, SearchRelevanceStatsTransportAction.class),
            new ActionHandler<>(PostScheduledExperimentAction.INSTANCE, PostScheduledExperimentTransportAction.class),
            new ActionHandler<>(DeleteScheduledExperimentAction.INSTANCE, DeleteScheduledExperimentTransportAction.class),
            new ActionHandler<>(GetScheduledExperimentAction.INSTANCE, GetScheduledExperimentTransportAction.class)
        );
    }

    @Override
    public String getJobType() {
        return "scheduled_experiment_job";
    }

    @Override
    public String getJobIndex() {
        return SCHEDULED_JOBS_INDEX;
    }

    @Override
    public ScheduledJobRunner getJobRunner() {
        return SearchRelevanceJobRunner.INSTANCE;
    }

    @Override
    public ScheduledJobParser getJobParser() {
        return (parser, id, jobDocVersion) -> {
            SearchRelevanceJobParameters jobParameter = new SearchRelevanceJobParameters();
            XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);

            while (!parser.nextToken().equals(XContentParser.Token.END_OBJECT)) {
                String fieldName = parser.currentName();
                parser.nextToken();
                switch (fieldName) {
                    case ScheduledJob.ID:
                        jobParameter.setExperimentId(parser.text());
                        break;
                    case ScheduledJob.TIME_STAMP:
                        break;
                    case SearchRelevanceJobParameters.ENABLED_FIELD:
                        jobParameter.setEnabled(parser.booleanValue());
                        break;
                    case SearchRelevanceJobParameters.ENABLED_TIME_FIELD:
                        jobParameter.setEnabledTime(parseInstantValue(parser));
                        break;
                    case SearchRelevanceJobParameters.LAST_UPDATE_TIME_FIELD:
                        jobParameter.setLastUpdateTime(parseInstantValue(parser));
                        break;
                    case SearchRelevanceJobParameters.SCHEDULE_FIELD:
                        jobParameter.setSchedule(ScheduleParser.parse(parser));
                        break;
                    default:
                        XContentParserUtils.throwUnknownToken(parser.currentToken(), parser.getTokenLocation());
                }
            }
            jobParameter.setLockDurationSeconds(20L);
            jobParameter.setIndexToWatch("index");
            jobParameter.setJobName("job");

            return jobParameter;
        };
    }

    private Instant parseInstantValue(XContentParser parser) throws IOException {
        if (XContentParser.Token.VALUE_NULL.equals(parser.currentToken())) {
            return null;
        }
        if (parser.currentToken().isValue()) {
            return Instant.ofEpochMilli(parser.longValue());
        }
        XContentParserUtils.throwUnknownToken(parser.currentToken(), parser.getTokenLocation());
        return null;
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
            SEARCH_RELEVANCE_WORKBENCH_ENABLED,
            SEARCH_RELEVANCE_STATS_ENABLED,
            SEARCH_RELEVANCE_QUERY_SET_MAX_LIMIT,
            SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_ENABLED,
            SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_TIMEOUT,
            SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_MINIMUM_INTERVAL
        );
    }

    @Override
    public List<ExecutorBuilder<?>> getExecutorBuilders(Settings settings) {
        return List.of(SearchRelevanceExecutor.getExecutorBuilder(settings));
    }
}
