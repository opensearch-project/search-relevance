/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.QuerySet;
import org.opensearch.searchrelevance.model.QuerySetType;
import org.opensearch.test.OpenSearchTestCase;

public class QuerySetDaoTests extends OpenSearchTestCase {

    @Mock
    private SearchRelevanceIndicesManager searchRelevanceIndicesManager;

    private QuerySetDao querySetDao;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        querySetDao = new QuerySetDao(searchRelevanceIndicesManager);
    }

    public void testConvertToQuerySet_AllFieldsPresent() {
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("id", "qs-1");
        sourceMap.put("name", "test-qs");
        sourceMap.put("description", "test desc");
        sourceMap.put("timestamp", "2024-01-01T00:00:00Z");
        sourceMap.put("sampling", "manual");
        sourceMap.put("status", "COMPLETED");
        sourceMap.put("type", "manual_query_set");
        sourceMap.put("numberOfQueryTerms", 3);
        sourceMap.put("querySetQueries", List.of(Map.of("queryText", "q1"), Map.of("queryText", "q2"), Map.of("queryText", "q3")));

        SearchResponse response = buildSearchResponse(sourceMap);
        QuerySet querySet = querySetDao.convertToQuerySet(response);

        assertEquals("qs-1", querySet.getId());
        assertEquals("test-qs", querySet.getName());
        assertEquals("manual", querySet.getSampling());
        assertEquals(AsyncStatus.COMPLETED, querySet.getStatus());
        assertEquals(QuerySetType.MANUAL_QUERY_SET, querySet.getType());
        assertEquals(3, querySet.getNumberOfQueryTerms());
        assertEquals(3, querySet.getQuerySetQueries().size());
    }

    public void testConvertToQuerySet_MissingNewFields_ManualSampling() {
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("id", "qs-old");
        sourceMap.put("name", "old-qs");
        sourceMap.put("timestamp", "2024-01-01T00:00:00Z");
        sourceMap.put("sampling", "manual");
        sourceMap.put("querySetQueries", List.of(Map.of("queryText", "q1")));

        SearchResponse response = buildSearchResponse(sourceMap);
        QuerySet querySet = querySetDao.convertToQuerySet(response);

        assertEquals(AsyncStatus.COMPLETED, querySet.getStatus());
        assertEquals(QuerySetType.MANUAL_QUERY_SET, querySet.getType());
        assertEquals(1, querySet.getNumberOfQueryTerms());
    }

    public void testConvertToQuerySet_MissingNewFields_UbiSampling() {
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("id", "qs-ubi");
        sourceMap.put("name", "ubi-qs");
        sourceMap.put("timestamp", "2024-01-01T00:00:00Z");
        sourceMap.put("sampling", "pptss");
        sourceMap.put("querySetQueries", List.of(Map.of("queryText", "q1"), Map.of("queryText", "q2")));

        SearchResponse response = buildSearchResponse(sourceMap);
        QuerySet querySet = querySetDao.convertToQuerySet(response);

        assertEquals(AsyncStatus.COMPLETED, querySet.getStatus());
        assertEquals(QuerySetType.UBI_QUERY_SET, querySet.getType());
        assertEquals(2, querySet.getNumberOfQueryTerms());
    }

    public void testConvertToQuerySet_EmptyResponse() {
        SearchResponse response = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(response.getHits()).thenReturn(searchHits);

        SearchRelevanceException exception = expectThrows(SearchRelevanceException.class, () -> querySetDao.convertToQuerySet(response));
        assertEquals(RestStatus.NOT_FOUND, exception.status());
    }

    public void testConvertToQuerySet_NoQuerySetQueries() {
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("id", "qs-empty");
        sourceMap.put("name", "empty-qs");
        sourceMap.put("timestamp", "2024-01-01T00:00:00Z");
        sourceMap.put("sampling", "manual");
        sourceMap.put("status", "COMPLETED");
        sourceMap.put("type", "manual_query_set");
        sourceMap.put("numberOfQueryTerms", 0);

        SearchResponse response = buildSearchResponse(sourceMap);
        QuerySet querySet = querySetDao.convertToQuerySet(response);

        assertTrue(querySet.getQuerySetQueries().isEmpty());
        assertEquals(0, querySet.getNumberOfQueryTerms());
    }

    private SearchResponse buildSearchResponse(Map<String, Object> sourceMap) {
        SearchHit hit = new SearchHit(1);
        hit.sourceRef(
            new org.opensearch.core.common.bytes.BytesArray(
                new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(sourceMap).toString()
            )
        );
        SearchHits searchHits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
        SearchResponse response = mock(SearchResponse.class);
        when(response.getHits()).thenReturn(searchHits);
        return response;
    }
}
