/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.JUDGMENT_CACHE;
import static org.opensearch.searchrelevance.model.JudgmentCache.CONTEXT_FIELDS_STR;
import static org.opensearch.searchrelevance.model.JudgmentCache.DOCUMENT_ID;
import static org.opensearch.searchrelevance.model.JudgmentCache.QUERY_TEXT;
import static org.opensearch.searchrelevance.utils.ParserUtils.convertListToSortedStr;
import static org.opensearch.searchrelevance.utils.ParserUtils.convertSortedStrToList;

import java.io.IOException;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ResourceAlreadyExistsException;
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
     * Updates or creates judgment cache in the system index
     * @param judgmentCache - Judgment cache content to be stored
     * @param listener - action listener for async operation
     */
    public void upsertJudgmentCache(final JudgmentCache judgmentCache, final ActionListener listener) {
        if (judgmentCache == null) {
            listener.onFailure(new SearchRelevanceException("judgmentCache cannot be null", RestStatus.BAD_REQUEST));
            return;
        }

        // First check if the document exists
        getJudgmentCache(
            judgmentCache.queryText(),
            judgmentCache.documentId(),
            convertSortedStrToList(judgmentCache.contextFieldsStr()),
            new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse searchResponse) {
                    try {
                        if (searchResponse.getHits().getTotalHits().value() > 0) {
                            // Document exists, update it
                            searchRelevanceIndicesManager.updateDoc(
                                judgmentCache.id(),
                                judgmentCache.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                                JUDGMENT_CACHE,
                                ActionListener.wrap(response -> {
                                    LOGGER.debug(
                                        "Successfully updated judgment cache for queryText: {} and documentId: {}",
                                        judgmentCache.queryText(),
                                        judgmentCache.documentId()
                                    );
                                    listener.onResponse(response);
                                }, e -> {
                                    LOGGER.error(
                                        "Failed to update judgment cache for queryText: {} and documentId: {}",
                                        judgmentCache.queryText(),
                                        judgmentCache.documentId(),
                                        e
                                    );
                                    listener.onFailure(e);
                                })
                            );
                        } else {
                            // Document doesn't exist, create it
                            searchRelevanceIndicesManager.putDoc(
                                judgmentCache.id(),
                                judgmentCache.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                                JUDGMENT_CACHE,
                                ActionListener.wrap(response -> {
                                    LOGGER.debug(
                                        "Successfully created judgment cache for queryText: {} and documentId: {}",
                                        judgmentCache.queryText(),
                                        judgmentCache.documentId()
                                    );
                                    listener.onResponse(response);
                                }, e -> {
                                    if (e instanceof ResourceAlreadyExistsException) {
                                        // Handle race condition where document was created between our check and create
                                        LOGGER.debug(
                                            "Judgment cache already exists for queryText: {} and documentId: {}",
                                            judgmentCache.queryText(),
                                            judgmentCache.documentId()
                                        );
                                        listener.onResponse(null);
                                    } else {
                                        LOGGER.error(
                                            "Failed to create judgment cache for queryText: {} and documentId: {}",
                                            judgmentCache.queryText(),
                                            judgmentCache.documentId(),
                                            e
                                        );
                                        listener.onFailure(e);
                                    }
                                })
                            );
                        }
                    } catch (IOException e) {
                        listener.onFailure(
                            new SearchRelevanceException("Failed to prepare judgment cache document", e, RestStatus.INTERNAL_SERVER_ERROR)
                        );
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    listener.onFailure(
                        new SearchRelevanceException("Failed to check existing judgment cache", e, RestStatus.INTERNAL_SERVER_ERROR)
                    );
                }
            }
        );
    }

    /**
     * Get judgment cache by queryText and documentId
     * @param queryText - queryText to be searched
     * @param documentId - documentId to be searched
     * @param contextFields - contextFields to be searched
     * @param listener - async operation
     */
    public SearchResponse getJudgmentCache(
        String queryText,
        String documentId,
        List<String> contextFields,
        ActionListener<SearchResponse> listener
    ) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must(QueryBuilders.termQuery(QUERY_TEXT + ".keyword", queryText));
        boolQuery.must(QueryBuilders.termQuery(DOCUMENT_ID + ".keyword", documentId));
        boolQuery.must(QueryBuilders.termQuery(CONTEXT_FIELDS_STR + ".keyword", convertListToSortedStr(contextFields)));

        searchSourceBuilder.query(boolQuery);

        return searchRelevanceIndicesManager.listDocsBySearchRequest(searchSourceBuilder, JUDGMENT_CACHE, listener);
    }
}
