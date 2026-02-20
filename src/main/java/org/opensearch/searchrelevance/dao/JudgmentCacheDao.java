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
import static org.opensearch.searchrelevance.model.JudgmentCache.PROMPT_TEMPLATE_ID;
import static org.opensearch.searchrelevance.model.JudgmentCache.QUERY_TEXT;
import static org.opensearch.searchrelevance.model.JudgmentCache.TIME_STAMP;
import static org.opensearch.searchrelevance.utils.ParserUtils.convertListToSortedStr;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.opensearch.action.StepListener;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.model.JudgmentCache;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class JudgmentCacheDao {
    private final SearchRelevanceIndicesManager searchRelevanceIndicesManager;
    private volatile SearchRelevanceSettingsAccessor settingsAccessor;

    public JudgmentCacheDao(SearchRelevanceIndicesManager searchRelevanceIndicesManager) {
        this.searchRelevanceIndicesManager = searchRelevanceIndicesManager;
    }

    /**
     * Sets the settings accessor for reading cache TTL configuration.
     * Called during plugin initialization after both DAO and settings accessor are created.
     */
    public void setSettingsAccessor(SearchRelevanceSettingsAccessor settingsAccessor) {
        this.settingsAccessor = settingsAccessor;
    }

    /**
     * Create judgment cache index if not exists
     * @param stepListener - step listener for async operation
     */
    public void createIndexIfAbsent(final StepListener<Void> stepListener) {
        searchRelevanceIndicesManager.createIndexIfAbsent(JUDGMENT_CACHE, stepListener);
    }

    /**
     * Stores judgment cache to in the system index
     * @param judgmentCache - Judgment cache content to be stored
     * @param listener - action listener for async operation
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

        try {
            // Create XContent once
            XContentBuilder content = judgmentCache.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);

            // Use updateDoc which will create or update the document
            searchRelevanceIndicesManager.updateDoc(judgmentCache.id(), content, JUDGMENT_CACHE, ActionListener.wrap(response -> {
                log.debug(
                    "Successfully upserted judgment cache for queryText: {} and documentId: {}",
                    judgmentCache.queryText(),
                    judgmentCache.documentId()
                );
                listener.onResponse(response);
            }, e -> {
                log.error(
                    "Failed to upsert judgment cache for queryText: {} and documentId: {}",
                    judgmentCache.queryText(),
                    judgmentCache.documentId(),
                    e
                );
                listener.onFailure(new SearchRelevanceException("Failed to upsert judgment cache", e, RestStatus.INTERNAL_SERVER_ERROR));
            }));
        } catch (IOException e) {
            listener.onFailure(
                new SearchRelevanceException("Failed to prepare judgment cache document", e, RestStatus.INTERNAL_SERVER_ERROR)
            );
        }
    }

    /**
     * Cleanup stale cache entries older than the specified TTL.
     * This is a fire-and-forget operation — failures are logged but do not block callers.
     * @param ttlDays number of days after which cache entries are considered stale
     */
    public void cleanupStaleEntries(final long ttlDays) {
        long cutoffMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ttlDays);
        String cutoffDate = Instant.ofEpochMilli(cutoffMillis).toString();

        log.info("Starting judgment cache cleanup for entries older than {} days (before {})", ttlDays, cutoffDate);

        searchRelevanceIndicesManager.deleteByQuery(
            QueryBuilders.rangeQuery(TIME_STAMP).lt(cutoffDate),
            JUDGMENT_CACHE,
            ActionListener.wrap((BulkByScrollResponse response) -> {
                long deleted = response.getDeleted();
                if (deleted > 0) {
                    log.info("Judgment cache cleanup completed: deleted {} stale entries older than {} days", deleted, ttlDays);
                } else {
                    log.debug("Judgment cache cleanup completed: no stale entries found older than {} days", ttlDays);
                }
            }, e -> log.warn("Judgment cache cleanup failed - continuing without cleanup", e))
        );
    }

    /**
     * Cleanup stale cache entries based on the configured TTL setting.
     * When TTL is disabled (-1, the default), this method is a no-op.
     * This is a fire-and-forget operation — failures are logged but do not block callers.
     */
    public void cleanupStaleEntries() {
        if (settingsAccessor == null) {
            log.debug("Settings accessor not set, skipping cache cleanup");
            return;
        }
        long ttlMillis = settingsAccessor.getJudgmentCacheTtl().millis();
        if (ttlMillis < 0) {
            log.debug("Judgment cache TTL is disabled (-1), skipping cleanup");
            return;
        }
        long ttlDays = TimeUnit.MILLISECONDS.toDays(ttlMillis);
        if (ttlDays < 1) {
            ttlDays = 1; // minimum 1 day
        }
        cleanupStaleEntries(ttlDays);
    }

    /**
     * Get judgment cache by queryText and documentId
     * @param queryText - queryText to be searched
     * @param documentId - documentId to be searched
     * @param contextFields - contextFields to be searched
     * @param promptTemplateCode - hash of promptTemplate and ratingType
     * @param listener - async operation
     */
    public SearchResponse getJudgmentCache(
        String queryText,
        String documentId,
        List<String> contextFields,
        String promptTemplateCode,
        ActionListener<SearchResponse> listener
    ) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        String contextFieldsStr = contextFields != null ? convertListToSortedStr(contextFields) : "";

        log.debug(
            "Building cache search query - queryText: '{}', documentId: '{}', contextFields: '{}', promptTemplateCode: '{}'",
            queryText,
            documentId,
            contextFieldsStr,
            promptTemplateCode
        );

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
            .must(QueryBuilders.matchQuery(QUERY_TEXT, queryText))
            .must(QueryBuilders.matchQuery(DOCUMENT_ID, documentId));

        if (contextFields != null && !contextFields.isEmpty()) {
            boolQuery.must(QueryBuilders.matchQuery(CONTEXT_FIELDS_STR, contextFieldsStr));
        }

        if (promptTemplateCode != null && !promptTemplateCode.isEmpty()) {
            boolQuery.must(QueryBuilders.termQuery(PROMPT_TEMPLATE_ID, promptTemplateCode));
        }

        searchSourceBuilder.query(boolQuery);

        ActionListener<SearchResponse> wrappedListener = ActionListener.wrap(response -> {
            if (response.getHits().getTotalHits().value() > 0) {
                SearchHit hit = response.getHits().getHits()[0];
            }
            listener.onResponse(response);
        }, e -> {
            log.debug("Cache lookup failed for docId: {} - continuing without cache", documentId);
            listener.onFailure(e);
        });

        return searchRelevanceIndicesManager.listDocsBySearchRequest(searchSourceBuilder, JUDGMENT_CACHE, wrappedListener);
    }
}
