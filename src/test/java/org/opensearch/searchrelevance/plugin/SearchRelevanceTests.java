/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.plugin;

import java.util.List;

import org.opensearch.action.ActionRequest;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.plugins.ActionPlugin;
import org.opensearch.plugins.IngestPlugin;
import org.opensearch.searchrelevance.transport.QuerySetAction;
import org.opensearch.test.OpenSearchTestCase;

public class SearchRelevanceTests extends OpenSearchTestCase {

    public void testIsAnIngestPlugin() {
        SearchRelevancePlugin plugin = new SearchRelevancePlugin();
        assertTrue(plugin instanceof IngestPlugin);
    }

    public void testTotalRestHandlers() {
        SearchRelevancePlugin plugin = new SearchRelevancePlugin();
        assertEquals(1, plugin.getRestHandlers(Settings.EMPTY, null, null, null, null, null, null).size());
    }

    public void testQuerySetTransportIsAdded() {
        SearchRelevancePlugin plugin = new SearchRelevancePlugin();
        final List<ActionPlugin.ActionHandler<? extends ActionRequest, ? extends ActionResponse>> actions = plugin.getActions();
        assertEquals(1, actions.stream().filter(actionHandler -> actionHandler.getAction() instanceof QuerySetAction).count());
    }
}
