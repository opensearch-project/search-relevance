/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.experiment.signature.ExperimentInputSignatureComputer;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.model.ExperimentInputSignature;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.Judgment;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.model.QuerySet;
import org.opensearch.searchrelevance.model.QuerySetEntry;
import org.opensearch.searchrelevance.model.SearchConfigurationDetails;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

public class ValidateExperimentTransportActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private ExperimentDao experimentDao;
    @Mock
    private QuerySetDao querySetDao;
    @Mock
    private SearchConfigurationDao searchConfigurationDao;
    @Mock
    private JudgmentDao judgmentDao;

    private ValidateExperimentTransportAction transportAction;

    // Fixed experiment ID used across tests
    private static final String EXPERIMENT_ID = "exp-123";
    private static final String QUERY_SET_ID = "qs-456";
    private static final String CONFIG_ID = "cfg-789";
    private static final String JUDGMENT_ID = "jud-000";

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        transportAction = new ValidateExperimentTransportAction(
            transportService,
            actionFilters,
            experimentDao,
            querySetDao,
            searchConfigurationDao,
            judgmentDao
        );
    }

    // ============================================
    // 1. Experiment not found -> NOT_FOUND error
    // ============================================
    public void testExperimentNotFoundReturnsError() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(emptySearchResponse());
            return null;
        }).when(experimentDao).getExperiment(eq(EXPERIMENT_ID), any(ActionListener.class));

        ActionListener<ValidateExperimentResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, new OpenSearchDocRequest(EXPERIMENT_ID), responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Experiment not found"));
    }

    // ============================================
    // 2. Experiment has no inputSignature -> UNAVAILABLE
    // ============================================
    public void testLegacyExperimentWithoutSignatureReturnsUnavailable() {
        Experiment experiment = new Experiment(
            EXPERIMENT_ID,
            "2024-01-01T00:00:00Z",
            "Test",
            "Desc",
            ExperimentType.PAIRWISE_COMPARISON,
            AsyncStatus.COMPLETED,
            QUERY_SET_ID,
            List.of(CONFIG_ID),
            List.of(JUDGMENT_ID),
            10,
            List.of()
        );

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(experimentHit(experiment));
            return null;
        }).when(experimentDao).getExperiment(eq(EXPERIMENT_ID), any(ActionListener.class));

        ActionListener<ValidateExperimentResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, new OpenSearchDocRequest(EXPERIMENT_ID), responseListener);

        ArgumentCaptor<ValidateExperimentResponse> responseCaptor = ArgumentCaptor.forClass(ValidateExperimentResponse.class);
        verify(responseListener).onResponse(responseCaptor.capture());

        ValidateExperimentResponse response = responseCaptor.getValue();
        assertEquals(ValidateExperimentResponse.STATUS_UNAVAILABLE, response.status());
        assertTrue(response.message().contains("not available"));
    }

    // ============================================
    // 3. Inputs unchanged -> VALID response
    // ============================================
    public void testUnchangedInputsReturnsValid() {
        QuerySet querySet = QuerySet.Builder.builder()
            .id(QUERY_SET_ID)
            .name("qs-name")
            .description("qs-desc")
            .timestamp("2024-01-01T00:00:00Z")
            .sampling("random")
            .querySetQueries(List.of(QuerySetEntry.Builder.builder().queryText("query1").build()))
            .build();

        SearchConfigurationDetails configDetails = SearchConfigurationDetails.builder()
            .index("test-index")
            .query("{\"match_all\":{}}")
            .pipeline("")
            .build();

        Judgment judgment = new Judgment(
            JUDGMENT_ID,
            "2024-01-01T00:00:00Z",
            "judgment-name",
            AsyncStatus.COMPLETED,
            JudgmentType.IMPORT_JUDGMENT,
            Map.of(),
            List.of()
        );

        ExperimentInputSignature signature = ExperimentInputSignatureComputer.compute(
            querySet,
            List.of(CONFIG_ID),
            Map.of(CONFIG_ID, configDetails),
            List.of(judgment)
        );

        Experiment experiment = new Experiment(
            EXPERIMENT_ID,
            "2024-01-01T00:00:00Z",
            "Test",
            "Desc",
            ExperimentType.PAIRWISE_COMPARISON,
            AsyncStatus.COMPLETED,
            QUERY_SET_ID,
            List.of(CONFIG_ID),
            List.of(JUDGMENT_ID),
            10,
            List.of(),
            signature
        );

        setupMocksForExperiment(experiment, querySet, configDetails, judgment);

        ActionListener<ValidateExperimentResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, new OpenSearchDocRequest(EXPERIMENT_ID), responseListener);

        ArgumentCaptor<ValidateExperimentResponse> responseCaptor = ArgumentCaptor.forClass(ValidateExperimentResponse.class);
        verify(responseListener).onResponse(responseCaptor.capture());

        ValidateExperimentResponse response = responseCaptor.getValue();
        assertEquals(ValidateExperimentResponse.STATUS_VALID, response.status());
        assertTrue(response.driftedInputs().isEmpty());
    }

    // ============================================
    // 4. QuerySet changed -> DRIFTED with query_set in drifted_inputs
    // ============================================
    public void testChangedQuerySetReturnsDrifted() {
        QuerySet originalQuerySet = QuerySet.Builder.builder()
            .id(QUERY_SET_ID)
            .name("qs-name")
            .description("qs-desc")
            .timestamp("2024-01-01T00:00:00Z")
            .sampling("random")
            .querySetQueries(List.of(QuerySetEntry.Builder.builder().queryText("original-query").build()))
            .build();

        QuerySet changedQuerySet = QuerySet.Builder.builder()
            .id(QUERY_SET_ID)
            .name("qs-name")
            .description("qs-desc")
            .timestamp("2024-01-01T00:00:00Z")
            .sampling("random")
            .querySetQueries(List.of(QuerySetEntry.Builder.builder().queryText("changed-query").build()))
            .build();

        SearchConfigurationDetails configDetails = SearchConfigurationDetails.builder()
            .index("test-index")
            .query("{\"match_all\":{}}")
            .pipeline("")
            .build();

        Judgment judgment = new Judgment(
            JUDGMENT_ID,
            "2024-01-01T00:00:00Z",
            "judgment-name",
            AsyncStatus.COMPLETED,
            JudgmentType.IMPORT_JUDGMENT,
            Map.of(),
            List.of()
        );

        // Signature computed with original query set
        ExperimentInputSignature signature = ExperimentInputSignatureComputer.compute(
            originalQuerySet,
            List.of(CONFIG_ID),
            Map.of(CONFIG_ID, configDetails),
            List.of(judgment)
        );

        Experiment experiment = new Experiment(
            EXPERIMENT_ID,
            "2024-01-01T00:00:00Z",
            "Test",
            "Desc",
            ExperimentType.PAIRWISE_COMPARISON,
            AsyncStatus.COMPLETED,
            QUERY_SET_ID,
            List.of(CONFIG_ID),
            List.of(JUDGMENT_ID),
            10,
            List.of(),
            signature
        );

        // But mock returns changed query set
        setupMocksForExperiment(experiment, changedQuerySet, configDetails, judgment);

        ActionListener<ValidateExperimentResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, new OpenSearchDocRequest(EXPERIMENT_ID), responseListener);

        ArgumentCaptor<ValidateExperimentResponse> responseCaptor = ArgumentCaptor.forClass(ValidateExperimentResponse.class);
        verify(responseListener).onResponse(responseCaptor.capture());

        ValidateExperimentResponse response = responseCaptor.getValue();
        assertEquals(ValidateExperimentResponse.STATUS_DRIFTED, response.status());
        assertEquals(1, response.driftedInputs().size());
        assertTrue(response.driftedInputs().contains(ExperimentInputSignature.QUERY_SET));
    }

    // ============================================
    // 5. QuerySet deleted -> DRIFTED (not an error)
    // ============================================
    public void testDeletedQuerySetReturnsDriftedNotError() {
        ExperimentInputSignature signature = new ExperimentInputSignature("dummy-qs-hash", "dummy-judgment-hash", "dummy-config-hash");

        Experiment experiment = new Experiment(
            EXPERIMENT_ID,
            "2024-01-01T00:00:00Z",
            "Test",
            "Desc",
            ExperimentType.PAIRWISE_COMPARISON,
            AsyncStatus.COMPLETED,
            QUERY_SET_ID,
            List.of(CONFIG_ID),
            List.of(JUDGMENT_ID),
            10,
            List.of(),
            signature
        );

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(experimentHit(experiment));
            return null;
        }).when(experimentDao).getExperiment(eq(EXPERIMENT_ID), any(ActionListener.class));

        // QuerySet DAO returns empty response (0 hits) -> SystemIndexConverters.toQuerySet throws NOT_FOUND
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(emptySearchResponse());
            return null;
        }).when(querySetDao).getQuerySet(eq(QUERY_SET_ID), any(ActionListener.class));

        ActionListener<ValidateExperimentResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, new OpenSearchDocRequest(EXPERIMENT_ID), responseListener);

        ArgumentCaptor<ValidateExperimentResponse> responseCaptor = ArgumentCaptor.forClass(ValidateExperimentResponse.class);
        verify(responseListener).onResponse(responseCaptor.capture());

        ValidateExperimentResponse response = responseCaptor.getValue();
        assertEquals(ValidateExperimentResponse.STATUS_DRIFTED, response.status());
        assertTrue(response.driftedInputs().contains(ExperimentInputSignature.QUERY_SET));
    }

    // ============================================
    // 6. Infrastructure failure fetching inputs -> onFailure (not drift)
    // ============================================
    public void testInfrastructureFailureReturnsErrorNotDrift() {
        ExperimentInputSignature signature = new ExperimentInputSignature("dummy-qs-hash", "dummy-judgment-hash", "dummy-config-hash");

        Experiment experiment = new Experiment(
            EXPERIMENT_ID,
            "2024-01-01T00:00:00Z",
            "Test",
            "Desc",
            ExperimentType.PAIRWISE_COMPARISON,
            AsyncStatus.COMPLETED,
            QUERY_SET_ID,
            List.of(CONFIG_ID),
            List.of(JUDGMENT_ID),
            10,
            List.of(),
            signature
        );

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(experimentHit(experiment));
            return null;
        }).when(experimentDao).getExperiment(eq(EXPERIMENT_ID), any(ActionListener.class));

        // QuerySet DAO throws a non-NOT_FOUND error (infrastructure failure)
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Cluster is temporarily unavailable"));
            return null;
        }).when(querySetDao).getQuerySet(eq(QUERY_SET_ID), any(ActionListener.class));

        ActionListener<ValidateExperimentResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, new OpenSearchDocRequest(EXPERIMENT_ID), responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception.getMessage().contains("Failed to load query set for drift validation"));
    }

    // ============================================
    // 7. Blank experiment ID -> BAD_REQUEST error
    // ============================================
    public void testBlankExperimentIdReturnsBadRequest() {
        ActionListener<ValidateExperimentResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, new OpenSearchDocRequest(""), responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception.getMessage().contains("Experiment id is required"));
    }

    // ============================================
    // 8. Search configuration deleted -> DRIFTED
    // ============================================
    public void testDeletedSearchConfigurationReturnsDrifted() {
        QuerySet querySet = QuerySet.Builder.builder()
            .id(QUERY_SET_ID)
            .name("qs-name")
            .description("qs-desc")
            .timestamp("2024-01-01T00:00:00Z")
            .sampling("random")
            .querySetQueries(List.of(QuerySetEntry.Builder.builder().queryText("query1").build()))
            .build();

        ExperimentInputSignature signature = new ExperimentInputSignature("dummy-qs-hash", "dummy-judgment-hash", "dummy-config-hash");

        Experiment experiment = new Experiment(
            EXPERIMENT_ID,
            "2024-01-01T00:00:00Z",
            "Test",
            "Desc",
            ExperimentType.PAIRWISE_COMPARISON,
            AsyncStatus.COMPLETED,
            QUERY_SET_ID,
            List.of(CONFIG_ID),
            List.of(JUDGMENT_ID),
            10,
            List.of(),
            signature
        );

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(experimentHit(experiment));
            return null;
        }).when(experimentDao).getExperiment(eq(EXPERIMENT_ID), any(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(querySetHit(querySet));
            return null;
        }).when(querySetDao).getQuerySet(eq(QUERY_SET_ID), any(ActionListener.class));

        // Search config DAO returns empty response (0 hits) -> SystemIndexConverters.toSearchConfiguration throws NOT_FOUND
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(emptySearchResponse());
            return null;
        }).when(searchConfigurationDao).getSearchConfiguration(eq(CONFIG_ID), any(ActionListener.class));

        ActionListener<ValidateExperimentResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, new OpenSearchDocRequest(EXPERIMENT_ID), responseListener);

        ArgumentCaptor<ValidateExperimentResponse> responseCaptor = ArgumentCaptor.forClass(ValidateExperimentResponse.class);
        verify(responseListener).onResponse(responseCaptor.capture());

        ValidateExperimentResponse response = responseCaptor.getValue();
        assertEquals(ValidateExperimentResponse.STATUS_DRIFTED, response.status());
        assertTrue(response.driftedInputs().contains(ExperimentInputSignature.SEARCH_CONFIGURATIONS));
    }

    // ============================================
    // 9. Multiple inputs changed simultaneously -> DRIFTED with all in drifted_inputs
    // ============================================
    public void testMultipleInputsDriftedReturnsAllDriftedFields() {
        // Use original inputs to compute the stored signature
        QuerySet originalQuerySet = QuerySet.Builder.builder()
            .id(QUERY_SET_ID)
            .name("qs-name")
            .description("qs-desc")
            .timestamp("2024-01-01T00:00:00Z")
            .sampling("random")
            .querySetQueries(List.of(QuerySetEntry.Builder.builder().queryText("original-query").build()))
            .build();

        SearchConfigurationDetails originalConfig = SearchConfigurationDetails.builder()
            .index("test-index")
            .query("{\"match_all\":{}}")
            .pipeline("")
            .build();

        Map<String, Object> originalRow = new HashMap<>();
        originalRow.put("query", "q1");
        originalRow.put("ratings", List.of(Map.of("doc", "d1", "rating", 3)));
        Judgment originalJudgment = new Judgment(
            JUDGMENT_ID,
            "2024-01-01T00:00:00Z",
            "judgment-name",
            AsyncStatus.COMPLETED,
            JudgmentType.IMPORT_JUDGMENT,
            Map.of(),
            List.of(originalRow)
        );

        ExperimentInputSignature signature = ExperimentInputSignatureComputer.compute(
            originalQuerySet,
            List.of(CONFIG_ID),
            Map.of(CONFIG_ID, originalConfig),
            List.of(originalJudgment)
        );

        Experiment experiment = new Experiment(
            EXPERIMENT_ID,
            "2024-01-01T00:00:00Z",
            "Test",
            "Desc",
            ExperimentType.PAIRWISE_COMPARISON,
            AsyncStatus.COMPLETED,
            QUERY_SET_ID,
            List.of(CONFIG_ID),
            List.of(JUDGMENT_ID),
            10,
            List.of(),
            signature
        );

        // Changed inputs: different query, different config, different judgment
        QuerySet changedQuerySet = QuerySet.Builder.builder()
            .id(QUERY_SET_ID)
            .name("qs-name")
            .description("qs-desc")
            .timestamp("2024-01-01T00:00:00Z")
            .sampling("random")
            .querySetQueries(List.of(QuerySetEntry.Builder.builder().queryText("changed-query").build()))
            .build();

        SearchConfigurationDetails changedConfig = SearchConfigurationDetails.builder()
            .index("different-index")
            .query("{\"match_all\":{}}")
            .pipeline("")
            .build();

        Map<String, Object> changedRow = new HashMap<>();
        changedRow.put("query", "q1");
        changedRow.put("ratings", List.of(Map.of("doc", "d1", "rating", 5)));
        Judgment changedJudgment = new Judgment(
            JUDGMENT_ID,
            "2024-01-01T00:00:00Z",
            "judgment-name",
            AsyncStatus.COMPLETED,
            JudgmentType.IMPORT_JUDGMENT,
            Map.of(),
            List.of(changedRow)
        );

        setupMocksForExperiment(experiment, changedQuerySet, changedConfig, changedJudgment);

        ActionListener<ValidateExperimentResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, new OpenSearchDocRequest(EXPERIMENT_ID), responseListener);

        ArgumentCaptor<ValidateExperimentResponse> responseCaptor = ArgumentCaptor.forClass(ValidateExperimentResponse.class);
        verify(responseListener).onResponse(responseCaptor.capture());

        ValidateExperimentResponse response = responseCaptor.getValue();
        assertEquals(ValidateExperimentResponse.STATUS_DRIFTED, response.status());
        assertEquals(3, response.driftedInputs().size());
        assertTrue(response.driftedInputs().contains(ExperimentInputSignature.QUERY_SET));
        assertTrue(response.driftedInputs().contains(ExperimentInputSignature.JUDGMENT_LIST));
        assertTrue(response.driftedInputs().contains(ExperimentInputSignature.SEARCH_CONFIGURATIONS));
        assertTrue(response.message().contains("One or more experiment inputs"));
    }

    // ============================================
    // Helper methods
    // ============================================

    private void setupMocksForExperiment(
        Experiment experiment,
        QuerySet querySet,
        SearchConfigurationDetails configDetails,
        Judgment judgment
    ) {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(experimentHit(experiment));
            return null;
        }).when(experimentDao).getExperiment(eq(EXPERIMENT_ID), any(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(querySetHit(querySet));
            return null;
        }).when(querySetDao).getQuerySet(eq(QUERY_SET_ID), any(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchConfigurationHit(invocation.getArgument(0), configDetails));
            return null;
        }).when(searchConfigurationDao).getSearchConfiguration(any(String.class), any(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(judgmentHit(judgment));
            return null;
        }).when(judgmentDao).getJudgment(any(String.class), any(ActionListener.class));
    }

    private SearchResponse emptySearchResponse() {
        SearchResponse response = mock(SearchResponse.class);
        SearchHits hits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(response.getHits()).thenReturn(hits);
        return response;
    }

    private SearchResponse experimentHit(Experiment experiment) {
        try {
            SearchResponse response = mock(SearchResponse.class);
            Map<String, Object> sourceMap = new HashMap<>();
            sourceMap.put(Experiment.ID, experiment.id());
            sourceMap.put(Experiment.TIME_STAMP, experiment.timestamp());
            sourceMap.put("name", experiment.name());
            sourceMap.put("description", experiment.description());
            sourceMap.put(Experiment.TYPE, experiment.type().name());
            sourceMap.put(Experiment.STATUS, experiment.status().name());
            sourceMap.put(Experiment.QUERY_SET_ID, experiment.querySetId());
            sourceMap.put(Experiment.SEARCH_CONFIGURATION_LIST, experiment.searchConfigurationList());
            sourceMap.put(Experiment.JUDGMENT_LIST, experiment.judgmentList());
            sourceMap.put(Experiment.SIZE, experiment.size());
            sourceMap.put(Experiment.RESULTS, experiment.results());
            if (experiment.inputSignature() != null) {
                Map<String, String> sigMap = new HashMap<>();
                sigMap.put(ExperimentInputSignature.QUERY_SET, experiment.inputSignature().querySetSha256());
                sigMap.put(ExperimentInputSignature.JUDGMENT_LIST, experiment.inputSignature().judgmentListSha256());
                sigMap.put(ExperimentInputSignature.SEARCH_CONFIGURATIONS, experiment.inputSignature().searchConfigurationsSha256());
                sourceMap.put(ExperimentInputSignature.FIELD, sigMap);
            }
            SearchHit hit = new SearchHit(1, experiment.id(), Map.of(), Map.of());
            hit.sourceRef(BytesReference.bytes(XContentFactory.jsonBuilder().map(sourceMap)));
            SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
            when(response.getHits()).thenReturn(hits);
            return response;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private SearchResponse querySetHit(QuerySet querySet) {
        try {
            SearchResponse response = mock(SearchResponse.class);
            Map<String, Object> sourceMap = new HashMap<>();
            sourceMap.put(QuerySet.ID, querySet.id());
            sourceMap.put(QuerySet.NAME, querySet.name());
            sourceMap.put(QuerySet.DESCRIPTION, querySet.description());
            sourceMap.put(QuerySet.TIME_STAMP, querySet.timestamp());
            sourceMap.put(QuerySet.SAMPLING, querySet.sampling());
            List<Map<String, String>> queries = new java.util.ArrayList<>();
            for (QuerySetEntry entry : querySet.querySetQueries()) {
                Map<String, String> q = new HashMap<>();
                q.put(QuerySetEntry.QUERY_TEXT, entry.queryText());
                queries.add(q);
            }
            sourceMap.put(QuerySet.QUERY_SET_QUERIES, queries);
            SearchHit hit = new SearchHit(1, querySet.id(), Map.of(), Map.of());
            hit.sourceRef(BytesReference.bytes(XContentFactory.jsonBuilder().map(sourceMap)));
            SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
            when(response.getHits()).thenReturn(hits);
            return response;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private SearchResponse searchConfigurationHit(String configId, SearchConfigurationDetails details) {
        try {
            SearchResponse response = mock(SearchResponse.class);
            Map<String, Object> sourceMap = new HashMap<>();
            sourceMap.put("id", configId);
            sourceMap.put("name", "cfg-" + configId);
            sourceMap.put("timestamp", "2024-01-01T00:00:00Z");
            sourceMap.put("index", details.getIndex());
            sourceMap.put("query", details.getQuery());
            sourceMap.put("searchPipeline", details.getPipeline() == null ? "" : details.getPipeline());
            sourceMap.put("description", "");
            SearchHit hit = new SearchHit(1, configId, Map.of(), Map.of());
            hit.sourceRef(BytesReference.bytes(XContentFactory.jsonBuilder().map(sourceMap)));
            SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
            when(response.getHits()).thenReturn(hits);
            return response;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private SearchResponse judgmentHit(Judgment judgment) {
        try {
            SearchResponse response = mock(SearchResponse.class);
            Map<String, Object> sourceMap = new HashMap<>();
            sourceMap.put(Judgment.ID, judgment.getId());
            sourceMap.put(Judgment.TIME_STAMP, judgment.getTimestamp());
            sourceMap.put(Judgment.NAME, judgment.getName());
            sourceMap.put(Judgment.STATUS, judgment.getStatus().name());
            sourceMap.put(Judgment.TYPE, judgment.getType().name());
            sourceMap.put(Judgment.METADATA, judgment.getMetadata());
            sourceMap.put(Judgment.JUDGMENT_RATINGS, judgment.getJudgmentRatings());
            SearchHit hit = new SearchHit(1, judgment.getId(), Map.of(), Map.of());
            hit.sourceRef(BytesReference.bytes(XContentFactory.jsonBuilder().map(sourceMap)));
            SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
            when(response.getHits()).thenReturn(hits);
            return response;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
