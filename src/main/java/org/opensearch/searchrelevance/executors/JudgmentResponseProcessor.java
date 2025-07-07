/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.search.SearchHit;
import org.opensearch.searchrelevance.dao.JudgmentCacheDao;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Handles processing of responses for LLM judgment generation
 */
@Log4j2
@RequiredArgsConstructor
public class JudgmentResponseProcessor {
    private final JudgmentCacheDao judgmentCacheDao;

    /**
     * Process search response and collect hits
     */
    public void processSearchResponse(
        SearchResponse response,
        String configIndex,
        ConcurrentMap<String, SearchHit> allHits,
        JudgmentTaskContext context
    ) {
        try {
            if (response.getHits().getTotalHits().value() == 0) {
                log.warn("No hits found for search config index: {}", configIndex);
                context.completeSearchTask(true);
                return;
            }

            for (SearchHit hit : response.getHits().getHits()) {
                allHits.put(hit.getId(), hit);
            }

            log.debug("Collected {} hits from index: {}", response.getHits().getHits().length, configIndex);
            context.completeSearchTask(true);

        } catch (Exception e) {
            log.error("Failed to process search response for index: {}", configIndex, e);
            context.completeSearchTask(false);
        }
    }

    /**
     * Process cache lookup response
     */
    public void processCacheResponse(
        SearchResponse response,
        String docId,
        ConcurrentMap<String, Boolean> processedDocs,
        JudgmentTaskContext context
    ) {
        try {
            if (response.getHits().getTotalHits().value() > 0) {
                SearchHit hit = response.getHits().getHits()[0];
                Map<String, Object> source = hit.getSourceAsMap();
                String rating = (String) source.get("rating");
                String storedContextFields = (String) source.get("contextFieldsStr");

                log.info("Found existing judgment for docId: {}, rating: {}, storedContextFields: {}", docId, rating, storedContextFields);

                context.getDocIdToScore().put(docId, rating);
                processedDocs.put(docId, true);
            }

            context.completeCacheTask(true);

        } catch (Exception e) {
            log.error("Failed to process cache response for docId: {}", docId, e);
            context.completeCacheTask(false);
        }
    }

    /**
     * Handle search failure
     */
    public void handleSearchFailure(Exception e, String configIndex, JudgmentTaskContext context) {
        log.error("Search failed for index: {}", configIndex, e);
        context.completeSearchTask(false);

        if (!context.isIgnoreFailure()) {
            context.failJudgment(e);
        }
    }

    /**
     * Handle cache lookup failure
     */
    public void handleCacheFailure(Exception e, String docId, JudgmentTaskContext context) {
        log.error("Cache lookup failed for docId: {}", docId, e);
        context.completeCacheTask(false);
    }

    /**
     * Handle LLM processing failure
     */
    public void handleLLMFailure(
        Exception e,
        String queryTextWithReference,
        boolean ignoreFailure,
        ActionListener<Map<String, String>> listener
    ) {
        log.error("LLM processing failed for query: {}", queryTextWithReference, e);

        if (!ignoreFailure) {
            listener.onFailure(e);
        } else {
            log.warn("Ignoring LLM failure due to ignoreFailure=true", e);
            listener.onResponse(Map.of());
        }
    }
}
