/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.plugin;

import java.util.List;
import java.util.function.Supplier;

import org.opensearch.action.ActionRequest;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.IndexScopedSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.settings.SettingsFilter;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.plugins.ActionPlugin;
import org.opensearch.plugins.IngestPlugin;
import org.opensearch.plugins.Plugin;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestHandler;
import org.opensearch.searchrelevance.rest.PutCreateSearchConfigurationAction;
import org.opensearch.searchrelevance.rest.RestCreateQuerySetAction;
import org.opensearch.searchrelevance.rest.RestDeleteExperimentAction;
import org.opensearch.searchrelevance.rest.RestDeleteJudgmentAction;
import org.opensearch.searchrelevance.rest.RestDeleteQuerySetAction;
import org.opensearch.searchrelevance.rest.RestDeleteSearchConfigurationAction;
import org.opensearch.searchrelevance.rest.RestGetExperimentAction;
import org.opensearch.searchrelevance.rest.RestGetJudgmentAction;
import org.opensearch.searchrelevance.rest.RestGetQuerySetAction;
import org.opensearch.searchrelevance.rest.RestGetSearchConfigurationAction;
import org.opensearch.searchrelevance.rest.RestPutExperimentAction;
import org.opensearch.searchrelevance.rest.RestPutJudgmentAction;
import org.opensearch.searchrelevance.rest.RestPutQuerySetAction;
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
import org.opensearch.searchrelevance.transport.queryset.CreateQuerySetAction;
import org.opensearch.searchrelevance.transport.queryset.CreateQuerySetTransportAction;
import org.opensearch.searchrelevance.transport.queryset.DeleteQuerySetAction;
import org.opensearch.searchrelevance.transport.queryset.DeleteQuerySetTransportAction;
import org.opensearch.searchrelevance.transport.queryset.GetQuerySetAction;
import org.opensearch.searchrelevance.transport.queryset.GetQuerySetTransportAction;
import org.opensearch.searchrelevance.transport.queryset.PutQuerySetAction;
import org.opensearch.searchrelevance.transport.queryset.PutQuerySetTransportAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.DeleteSearchConfigurationAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.DeleteSearchConfigurationTransportAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.GetSearchConfigurationAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.GetSearchConfigurationTransportAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.PutSearchConfigurationAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.PutSearchConfigurationTransportAction;

public class SearchRelevancePlugin extends Plugin implements IngestPlugin, ActionPlugin {

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
            new RestCreateQuerySetAction(),
            new RestPutQuerySetAction(),
            new RestDeleteQuerySetAction(),
            new RestGetQuerySetAction(),
            new RestPutJudgmentAction(),
            new RestDeleteJudgmentAction(),
            new RestGetJudgmentAction(),
            new PutCreateSearchConfigurationAction(),
            new RestDeleteSearchConfigurationAction(),
            new RestGetSearchConfigurationAction(),
            new RestPutExperimentAction(),
            new RestGetExperimentAction(),
            new RestDeleteExperimentAction()
        );
    }

    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        return List.of(
            new ActionHandler<>(CreateQuerySetAction.INSTANCE, CreateQuerySetTransportAction.class),
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
            new ActionHandler<>(GetExperimentAction.INSTANCE, GetExperimentTransportAction.class)
        );
    }
}
