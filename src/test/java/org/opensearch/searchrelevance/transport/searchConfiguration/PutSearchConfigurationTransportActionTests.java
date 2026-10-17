/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.searchConfiguration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

public class PutSearchConfigurationTransportActionTests extends OpenSearchTestCase {

    @Mock
    private ClusterService clusterService;
    @Mock
    private ClusterState clusterState;
    @Mock
    private Metadata metadata;
    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private SearchConfigurationDao dao;

    private PutSearchConfigurationTransportAction action;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);

        action = new PutSearchConfigurationTransportAction(clusterService, transportService, actionFilters, dao);
    }

    public void testValidationFailure_IndexNotFound() {
        when(metadata.hasIndex("missing-index")).thenReturn(false);

        PutSearchConfigurationRequest request = new PutSearchConfigurationRequest(
            "test-config",
            "missing-index",
            "{\"match_all\": {}}",
            null,
            null
        );

        action.doExecute(null, request, new ActionListener<org.opensearch.action.index.IndexResponse>() {
            @Override
            public void onResponse(org.opensearch.action.index.IndexResponse response) {
                fail("Should not succeed when index does not exist");
            }

            @Override
            public void onFailure(Exception e) {
                assertTrue(e.getMessage().contains("Index [missing-index] does not exist"));
            }
        });

        verify(dao, never()).putSearchConfiguration(any(), any());
    }

    public void testValidationSuccess_IndexExists() {
        when(metadata.hasIndex("valid-index")).thenReturn(true);
        doAnswer(invocation -> {
            ActionListener<Void> listener = invocation.getArgument(0);
            listener.onResponse(null);
            return null;
        }).when(dao).createIndexIfAbsent(any());

        PutSearchConfigurationRequest request = new PutSearchConfigurationRequest(
            "test-config",
            "valid-index",
            "{\"match_all\": {}}",
            null,
            null
        );

        action.doExecute(null, request, new ActionListener<org.opensearch.action.index.IndexResponse>() {
            @Override
            public void onResponse(org.opensearch.action.index.IndexResponse response) {
                // Success
            }

            @Override
            public void onFailure(Exception e) {
                fail("Should succeed when index exists: " + e.getMessage());
            }
        });

        verify(dao, times(1)).putSearchConfiguration(any(), any());
    }
}
