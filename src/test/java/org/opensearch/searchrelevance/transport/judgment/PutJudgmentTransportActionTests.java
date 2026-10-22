/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.judgments.BaseJudgmentsProcessor;
import org.opensearch.searchrelevance.judgments.JudgmentsProcessorFactory;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;
import org.opensearch.searchrelevance.model.SearchConfiguration;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class PutJudgmentTransportActionTests extends OpenSearchTestCase {

    @Mock
    private ClusterService clusterService;
    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private JudgmentDao judgmentDao;
    @Mock
    private QuerySetDao querySetDao;
    @Mock
    private SearchConfigurationDao searchConfigurationDao;
    @Mock
    private JudgmentsProcessorFactory judgmentsProcessorFactory;
    @Mock
    private ThreadPool threadPool;

    private PutJudgmentTransportAction action;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        // Make the GENERIC executor (used for the reuse index-consistency check) run inline.
        ExecutorService directExecutor = mock(ExecutorService.class);
        when(threadPool.executor(any())).thenReturn(directExecutor);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(directExecutor).execute(any(Runnable.class));
        action = new PutJudgmentTransportAction(
            clusterService,
            transportService,
            actionFilters,
            judgmentDao,
            querySetDao,
            searchConfigurationDao,
            judgmentsProcessorFactory,
            threadPool
        );
    }

    // --- Helpers for the existing-judgment reuse index-consistency validation ---

    /** Stub QuerySet existence to pass. */
    private void stubQuerySetExists(String querySetId) {
        SearchResponse response = mock(SearchResponse.class);
        SearchHits hits = new SearchHits(new SearchHit[0], new TotalHits(1, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(response.getHits()).thenReturn(hits);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(querySetDao).checkQuerySetExists(eq(querySetId), any(ActionListener.class));
    }

    /** Stub a search config existence check (used by validateEntityExists) to pass. */
    private void stubSearchConfigExists(String configId) {
        SearchResponse response = mock(SearchResponse.class);
        SearchHits hits = new SearchHits(new SearchHit[0], new TotalHits(1, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(response.getHits()).thenReturn(hits);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(searchConfigurationDao).checkSearchConfigurationExists(eq(configId), any(ActionListener.class));
    }

    /** Stub the synchronous search-config lookup to return a config on the given index. */
    private void stubSearchConfigIndex(String configId, String index) {
        SearchConfiguration config = new SearchConfiguration(configId, "name", "ts", index, "query", null, "desc");
        when(searchConfigurationDao.getSearchConfigurationSync(configId)).thenReturn(config);
    }

    /** Stub a stored judgment whose metadata records the given search configuration ids. */
    private void stubExistingJudgment(String judgmentId, List<String> searchConfigurationList) {
        stubExistingJudgment(judgmentId, searchConfigurationList, null);
    }

    /**
     * Stub a stored judgment recording the given search configuration ids and rating type. The
     * rating type is stored as a String, matching how it is read back from the index.
     */
    private void stubExistingJudgment(String judgmentId, List<String> searchConfigurationList, String ratingType) {
        SearchHit hit = new SearchHit(1);
        Map<String, Object> source = new java.util.HashMap<>();
        Map<String, Object> metadata = new java.util.HashMap<>();
        if (searchConfigurationList != null) {
            metadata.put("searchConfigurationList", searchConfigurationList);
        }
        if (ratingType != null) {
            metadata.put("llmJudgmentRatingType", ratingType);
        }
        source.put("metadata", metadata);
        hit.sourceRef(org.opensearch.core.common.bytes.BytesReference.bytes(mapToXContent(source)));
        SearchResponse response = mock(SearchResponse.class);
        SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
        when(response.getHits()).thenReturn(hits);
        when(judgmentDao.getJudgmentSync(judgmentId)).thenReturn(response);
    }

    private static org.opensearch.core.xcontent.XContentBuilder mapToXContent(Map<String, Object> map) {
        try {
            org.opensearch.core.xcontent.XContentBuilder builder = org.opensearch.common.xcontent.XContentFactory.jsonBuilder();
            builder.map(map);
            return builder;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PutLlmJudgmentRequest llmRequestWithReuse(
        String querySetId,
        List<String> searchConfigurationList,
        List<String> existingJudgments
    ) {
        return llmRequestWithReuse(querySetId, searchConfigurationList, existingJudgments, null);
    }

    private PutLlmJudgmentRequest llmRequestWithReuse(
        String querySetId,
        List<String> searchConfigurationList,
        List<String> existingJudgments,
        LLMJudgmentRatingType ratingType
    ) {
        return new PutLlmJudgmentRequest(
            JudgmentType.LLM_JUDGMENT,
            "test-judgment",
            "test description",
            "test-model-id",
            querySetId,
            searchConfigurationList,
            10,
            1000,
            null, // contextFields
            false, // ignoreFailure
            null, // promptTemplate
            ratingType,
            existingJudgments
        );
    }

    public void testValidation_LlmJudgment_QuerySetNotFound() {
        PutLlmJudgmentRequest request = new PutLlmJudgmentRequest(
            JudgmentType.LLM_JUDGMENT,
            "test-judgment",
            "test description",
            "test-model-id",
            "missing-queryset-id",
            List.of(),
            10,
            1000,
            null, // contextFields
            false, // ignoreFailure
            null, // promptTemplate
            null, // llmJudgmentRatingType
            null // existingJudgments
        );

        // Mock QuerySet DAO to return 0 hits (entity not found)
        SearchResponse mockResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockResponse.getHits()).thenReturn(searchHits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockResponse);
            return null;
        }).when(querySetDao).checkQuerySetExists(eq("missing-queryset-id"), any(ActionListener.class));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception.getMessage().contains("QuerySet [missing-queryset-id] does not exist"));
    }

    public void testValidation_LlmJudgment_TooManyExistingJudgments() {
        // Reference 6 existing judgments — one more than the allowed maximum of 5.
        PutLlmJudgmentRequest request = new PutLlmJudgmentRequest(
            JudgmentType.LLM_JUDGMENT,
            "test-judgment",
            "test description",
            "test-model-id",
            "valid-queryset-id",
            List.of(),
            10,
            1000,
            null, // contextFields
            false, // ignoreFailure
            null, // promptTemplate
            null, // llmJudgmentRatingType
            List.of("j1", "j2", "j3", "j4", "j5", "j6") // existingJudgments — over the limit
        );

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception.getMessage().contains("Too many existing judgments referenced"));
    }

    public void testValidation_LlmJudgment_SearchConfigNotFound() {
        PutLlmJudgmentRequest request = new PutLlmJudgmentRequest(
            JudgmentType.LLM_JUDGMENT,
            "test-judgment",
            "test description",
            "test-model-id",
            "valid-queryset-id",
            List.of("missing-config-id"),
            10,
            1000,
            null, // contextFields
            false, // ignoreFailure
            null, // promptTemplate
            null, // llmJudgmentRatingType
            null // existingJudgments
        );

        // Mock QuerySet exists
        SearchResponse mockQuerySetResponse = mock(SearchResponse.class);
        SearchHits querySetHits = new SearchHits(new SearchHit[0], new TotalHits(1, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockQuerySetResponse.getHits()).thenReturn(querySetHits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockQuerySetResponse);
            return null;
        }).when(querySetDao).checkQuerySetExists(eq("valid-queryset-id"), any(ActionListener.class));

        // Mock SearchConfiguration DAO to return 0 hits
        SearchResponse mockResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockResponse.getHits()).thenReturn(searchHits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockResponse);
            return null;
        }).when(searchConfigurationDao).checkSearchConfigurationExists(eq("missing-config-id"), any(ActionListener.class));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception.getMessage().contains("SearchConfiguration [missing-config-id] does not exist"));
    }

    public void testValidation_ReuseExistingJudgment_MatchingIndex_Passes() {
        stubQuerySetExists("valid-queryset-id");
        stubSearchConfigExists("cfg-1");
        stubSearchConfigIndex("cfg-1", "products");
        // Existing judgment built on the same index.
        stubExistingJudgment("judg-1", List.of("cfg-existing"));
        stubSearchConfigIndex("cfg-existing", "products");

        PutLlmJudgmentRequest request = llmRequestWithReuse("valid-queryset-id", List.of("cfg-1"), List.of("judg-1"));

        // Initial putJudgement succeeds so validation passing reaches createJudgment.
        IndexResponse indexResponse = mock(IndexResponse.class);
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(judgmentDao).putJudgement(any(), any(ActionListener.class));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, request, responseListener);

        // Validation passed -> the initial judgment was created and the response surfaced.
        verify(judgmentDao).putJudgement(any(), any(ActionListener.class));
        verify(responseListener).onResponse(indexResponse);
    }

    public void testValidation_ReuseExistingJudgment_MismatchedIndex_Returns400() {
        stubQuerySetExists("valid-queryset-id");
        stubSearchConfigExists("cfg-1");
        stubSearchConfigIndex("cfg-1", "products");
        // Existing judgment built on a DIFFERENT index.
        stubExistingJudgment("judg-1", List.of("cfg-existing"));
        stubSearchConfigIndex("cfg-existing", "movies");

        PutLlmJudgmentRequest request = llmRequestWithReuse("valid-queryset-id", List.of("cfg-1"), List.of("judg-1"));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("different target index and cannot be reused"));
        // Mismatched reuse must never create the judgment.
        verify(judgmentDao, org.mockito.Mockito.never()).putJudgement(any(), any(ActionListener.class));
    }

    public void testValidation_ReuseExistingJudgment_SubsetIndex_Passes() {
        stubQuerySetExists("valid-queryset-id");
        stubSearchConfigExists("cfg-1");
        stubSearchConfigIndex("cfg-1", "products");
        // Existing judgment covers products AND movies — a superset of this request's {products}.
        stubExistingJudgment("judg-1", List.of("cfg-a", "cfg-b"));
        stubSearchConfigIndex("cfg-a", "products");
        stubSearchConfigIndex("cfg-b", "movies");

        PutLlmJudgmentRequest request = llmRequestWithReuse("valid-queryset-id", List.of("cfg-1"), List.of("judg-1"));

        IndexResponse indexResponse = mock(IndexResponse.class);
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(judgmentDao).putJudgement(any(), any(ActionListener.class));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, request, responseListener);

        verify(judgmentDao).putJudgement(any(), any(ActionListener.class));
        verify(responseListener).onResponse(indexResponse);
    }

    public void testValidation_ReuseExistingJudgment_ContinuousIntoBinary_Returns400() {
        stubQuerySetExists("valid-queryset-id");
        stubSearchConfigExists("cfg-1");
        stubSearchConfigIndex("cfg-1", "products");
        // Same index, but the existing judgment holds continuous ratings that cannot be expressed
        // on this request's binary scale.
        stubExistingJudgment("judg-1", List.of("cfg-existing"), LLMJudgmentRatingType.SCORE0_1.name());
        stubSearchConfigIndex("cfg-existing", "products");

        PutLlmJudgmentRequest request = llmRequestWithReuse(
            "valid-queryset-id",
            List.of("cfg-1"),
            List.of("judg-1"),
            LLMJudgmentRatingType.RELEVANT_IRRELEVANT
        );

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("the rating scales are not comparable"));
        verify(judgmentDao, org.mockito.Mockito.never()).putJudgement(any(), any(ActionListener.class));
    }

    public void testValidation_ReuseExistingJudgment_BinaryIntoContinuous_Returns400() {
        stubQuerySetExists("valid-queryset-id");
        stubSearchConfigExists("cfg-1");
        stubSearchConfigIndex("cfg-1", "products");
        // Any rating scale mismatch is rejected, in either direction.
        stubExistingJudgment("judg-1", List.of("cfg-existing"), LLMJudgmentRatingType.RELEVANT_IRRELEVANT.name());
        stubSearchConfigIndex("cfg-existing", "products");

        PutLlmJudgmentRequest request = llmRequestWithReuse(
            "valid-queryset-id",
            List.of("cfg-1"),
            List.of("judg-1"),
            LLMJudgmentRatingType.SCORE0_1
        );

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("the rating scales are not comparable"));
        verify(judgmentDao, org.mockito.Mockito.never()).putJudgement(any(), any(ActionListener.class));
    }

    public void testValidation_ReuseExistingJudgment_SameRatingType_Passes() {
        stubQuerySetExists("valid-queryset-id");
        stubSearchConfigExists("cfg-1");
        stubSearchConfigIndex("cfg-1", "products");
        stubExistingJudgment("judg-1", List.of("cfg-existing"), LLMJudgmentRatingType.RELEVANT_IRRELEVANT.name());
        stubSearchConfigIndex("cfg-existing", "products");

        PutLlmJudgmentRequest request = llmRequestWithReuse(
            "valid-queryset-id",
            List.of("cfg-1"),
            List.of("judg-1"),
            LLMJudgmentRatingType.RELEVANT_IRRELEVANT
        );

        IndexResponse indexResponse = mock(IndexResponse.class);
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(judgmentDao).putJudgement(any(), any(ActionListener.class));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, request, responseListener);

        verify(judgmentDao).putJudgement(any(), any(ActionListener.class));
        verify(responseListener).onResponse(indexResponse);
    }

    public void testValidation_ReuseExistingJudgment_MissingRatingType_TreatedAsDefault_Returns400() {
        stubQuerySetExists("valid-queryset-id");
        stubSearchConfigExists("cfg-1");
        stubSearchConfigIndex("cfg-1", "products");
        // Pre-dates the rating type field, so it is treated as the default (SCORE0_1) — which cannot
        // be reused in a binary run.
        stubExistingJudgment("judg-1", List.of("cfg-existing"), null);
        stubSearchConfigIndex("cfg-existing", "products");

        PutLlmJudgmentRequest request = llmRequestWithReuse(
            "valid-queryset-id",
            List.of("cfg-1"),
            List.of("judg-1"),
            LLMJudgmentRatingType.RELEVANT_IRRELEVANT
        );

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("the rating scales are not comparable"));
    }

    public void testValidation_ReuseExistingJudgment_NoMetadata_Returns400() {
        stubQuerySetExists("valid-queryset-id");
        stubSearchConfigExists("cfg-1");
        stubSearchConfigIndex("cfg-1", "products");
        // Existing judgment records no search configurations -> index cannot be resolved.
        stubExistingJudgment("judg-1", null);

        PutLlmJudgmentRequest request = llmRequestWithReuse("valid-queryset-id", List.of("cfg-1"), List.of("judg-1"));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("does not record its target index"));
        verify(judgmentDao, org.mockito.Mockito.never()).putJudgement(any(), any(ActionListener.class));
    }

    public void testValidation_ReuseExistingJudgment_DeletedSearchConfig_Returns400() {
        stubQuerySetExists("valid-queryset-id");
        stubSearchConfigExists("cfg-1");
        stubSearchConfigIndex("cfg-1", "products");
        // Existing judgment references a search config that has since been deleted.
        stubExistingJudgment("judg-1", List.of("cfg-deleted"));
        when(searchConfigurationDao.getSearchConfigurationSync("cfg-deleted")).thenThrow(new RuntimeException("not found"));

        PutLlmJudgmentRequest request = llmRequestWithReuse("valid-queryset-id", List.of("cfg-1"), List.of("judg-1"));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("could not be resolved to a target index"));
        verify(judgmentDao, org.mockito.Mockito.never()).putJudgement(any(), any(ActionListener.class));
    }

    private void stubIndexAbsent(String index) {
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex(index)).thenReturn(false);
    }

    private void stubIndexPresentWithMappingFields(String index, Map<String, Object> properties) {
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);
        IndexMetadata indexMetadata = mock(IndexMetadata.class);
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex(index)).thenReturn(true);
        when(metadata.index(index)).thenReturn(indexMetadata);
        when(indexMetadata.mapping()).thenReturn(mappingMetadata);
        when(mappingMetadata.sourceAsMap()).thenReturn(Map.of("properties", properties));
    }

    private PutUbiJudgmentRequest ubiJudgmentRequest(String ubiEventsIndex) {
        return new PutUbiJudgmentRequest(
            JudgmentType.UBI_JUDGMENT,
            "my-implicit-judgments",
            "test description",
            "coec",
            20,
            "",
            "",
            ubiEventsIndex
        );
    }

    private SearchRelevanceException runUbiValidationExpectingFailure(PutUbiJudgmentRequest request) {
        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());
        verify(judgmentDao, org.mockito.Mockito.never()).putJudgement(any(), any(ActionListener.class));

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof SearchRelevanceException);
        assertEquals(RestStatus.BAD_REQUEST, ((SearchRelevanceException) exception).status());
        return (SearchRelevanceException) exception;
    }

    public void testValidation_UbiJudgment_DefaultEventsIndexNotFound_Returns400() {
        stubIndexAbsent("ubi_events");

        String message = runUbiValidationExpectingFailure(ubiJudgmentRequest(null)).getMessage();

        assertTrue(message, message.contains("No 'ubiEventsIndex' parameter was provided"));
        assertTrue(message, message.contains("default UBI events index [ubi_events]"));
    }

    public void testValidation_UbiJudgment_ExplicitEventsIndexNotFound_Returns400() {
        stubIndexAbsent("my_custom_events");

        String message = runUbiValidationExpectingFailure(ubiJudgmentRequest("my_custom_events")).getMessage();

        assertTrue(message, message.contains("UBI events index [my_custom_events] set by the 'ubiEventsIndex' parameter"));
        assertFalse(message, message.contains("No 'ubiEventsIndex' parameter was provided"));
    }

    public void testValidation_UbiJudgment_ExplicitDefaultEventsIndexNotFound_ReportedAsProvided_Returns400() {
        stubIndexAbsent("ubi_events");

        String message = runUbiValidationExpectingFailure(ubiJudgmentRequest("ubi_events")).getMessage();

        assertTrue(message, message.contains("UBI events index [ubi_events] set by the 'ubiEventsIndex' parameter"));
        assertFalse(message, message.contains("No 'ubiEventsIndex' parameter was provided"));
    }

    public void testValidation_UbiJudgment_NoParameter_ValidDefaultIndex_Succeeds() {
        Map<String, Object> validProperties = Map.of(
            "query_id",
            Map.of("type", "keyword"),
            "action_name",
            Map.of("type", "keyword"),
            "event_attributes",
            Map.of("properties", Map.of("object", Map.of("properties", Map.of("object_id", Map.of("type", "keyword")))))
        );
        stubIndexPresentWithMappingFields("ubi_events", validProperties);

        IndexResponse indexResponse = mock(IndexResponse.class);
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(judgmentDao).putJudgement(any(), any(ActionListener.class));

        BaseJudgmentsProcessor processor = mock(BaseJudgmentsProcessor.class);
        when(judgmentsProcessorFactory.getProcessor(any())).thenReturn(processor);
        doAnswer(invocation -> {
            ActionListener<List<Map<String, Object>>> listener = invocation.getArgument(1);
            listener.onResponse(List.of());
            return null;
        }).when(processor).generateJudgmentRating(any(), any(ActionListener.class));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, ubiJudgmentRequest(null), responseListener);

        verify(judgmentDao).putJudgement(any(), any(ActionListener.class));
        verify(responseListener).onResponse(indexResponse);
        verify(responseListener, org.mockito.Mockito.never()).onFailure(any());
    }

    public void testValidation_UbiJudgment_EventsIndexExistsButMissingRequiredField_Returns400() {
        Map<String, Object> propertiesMissingObjectId = Map.of(
            "query_id",
            Map.of("type", "keyword"),
            "action_name",
            Map.of("type", "keyword")
        );
        stubIndexPresentWithMappingFields("my_custom_events", propertiesMissingObjectId);

        String message = runUbiValidationExpectingFailure(ubiJudgmentRequest("my_custom_events")).getMessage();

        assertTrue(message, message.contains("UBI events index [my_custom_events] set by the 'ubiEventsIndex' parameter"));
        assertTrue(
            message,
            message.contains("missing required UBI event fields (query_id, action_name, event_attributes.object.object_id)")
        );
    }
}
