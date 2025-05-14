/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.JUDGMENT_CACHE;
import static org.opensearch.searchrelevance.model.JudgmentCache.DOCUMENT_ID;
import static org.opensearch.searchrelevance.model.JudgmentCache.QUERY_TEXT;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.StepListener;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.model.JudgmentCache;

public class JudgmentCacheDao {
    private static final Logger LOGGER = LogManager.getLogger(JudgmentCacheDao.class);
    private final SearchRelevanceIndicesManager searchRelevanceIndicesManager;

    @Inject
    public JudgmentCacheDao(SearchRelevanceIndicesManager searchRelevanceIndicesManager) {
        this.searchRelevanceIndicesManager = searchRelevanceIndicesManager;
    }

    /**
     * Create judgment cache index if not exists
     * @param stepListener - step lister for async operation
     */
    public void createIndexIfAbsent(final StepListener<Void> stepListener) {
        searchRelevanceIndicesManager.createIndexIfAbsent(JUDGMENT_CACHE, stepListener);
    }

    /**
     * Stores judgment cache to in the system index
     * @param judgmentCache - Judgment cache content to be stored
     * @param listener - action lister for async operation
     */
    public void putJudgementCache(final JudgmentCache judgmentCache, final ActionListener listener) {
        if (judgmentCache == null) {
            listener.onFailure(new SearchRelevanceException("judgmentCache cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.putDoc(
                judgmentCache.id(),
                judgmentCache.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                JUDGMENT_CACHE,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store judgment", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get judgment cache by queryText and documentId
     * @param queryText - queryText to be searched
     * @param documentId - documentId to be searched
     * @param listener - async operation
     */
    public SearchResponse getJudgmentCache(String queryText, String documentId, ActionListener<SearchResponse> listener) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must(QueryBuilders.matchQuery(QUERY_TEXT, queryText));
        boolQuery.must(QueryBuilders.matchQuery(DOCUMENT_ID, documentId));

        searchSourceBuilder.query(boolQuery);

        return searchRelevanceIndicesManager.listDocsBySearchRequest(searchSourceBuilder, JUDGMENT_CACHE, listener);
    }
}
