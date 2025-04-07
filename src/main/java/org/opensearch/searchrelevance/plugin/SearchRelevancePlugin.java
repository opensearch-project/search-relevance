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
import org.opensearch.searchrelevance.rest.RestCreateQuerySetAction;
import org.opensearch.searchrelevance.rest.RestDeleteQuerySetAction;
import org.opensearch.searchrelevance.rest.RestGetQuerySetAction;
import org.opensearch.searchrelevance.transport.CreateQuerySetAction;
import org.opensearch.searchrelevance.transport.CreateQuerySetTransportAction;
import org.opensearch.searchrelevance.transport.DeleteQuerySetAction;
import org.opensearch.searchrelevance.transport.DeleteQuerySetTransportAction;
import org.opensearch.searchrelevance.transport.GetQuerySetAction;
import org.opensearch.searchrelevance.transport.GetQuerySetTransportAction;

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
        return List.of(new RestCreateQuerySetAction(), new RestDeleteQuerySetAction(), new RestGetQuerySetAction());
    }

    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        return List.of(
            new ActionHandler<>(CreateQuerySetAction.INSTANCE, CreateQuerySetTransportAction.class),
            new ActionHandler<>(DeleteQuerySetAction.INSTANCE, DeleteQuerySetTransportAction.class),
            new ActionHandler<>(GetQuerySetAction.INSTANCE, GetQuerySetTransportAction.class)
        );
    }
}
