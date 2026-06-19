/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.AB_TEST;

import java.io.IOException;

import org.opensearch.action.DocWriteRequest.OpType;
import org.opensearch.action.StepListener;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.model.ABTest;
import org.opensearch.searchrelevance.model.ABTestSnapshot;
import org.opensearch.searchrelevance.shared.StashedThreadContext;
import org.opensearch.transport.client.Client;

public class ABTestDao {

    private final SearchRelevanceIndicesManager searchRelevanceIndicesManager;
    private final Client client;

    @Inject
    public ABTestDao(SearchRelevanceIndicesManager searchRelevanceIndicesManager, Client client) {
        this.searchRelevanceIndicesManager = searchRelevanceIndicesManager;
        this.client = client;
    }

    public void createIndexIfAbsent(final StepListener<Void> stepListener) {
        searchRelevanceIndicesManager.createIndexIfAbsent(AB_TEST, stepListener);
    }

    public void putABTest(final ABTest abTest, final ActionListener listener) {
        if (abTest == null) {
            listener.onFailure(new SearchRelevanceException("ABTest cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.putDoc(
                abTest.getTestId(),
                abTest.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                AB_TEST,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store ABTest", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void updateABTest(final ABTest abTest, final ActionListener listener) {
        if (abTest == null) {
            listener.onFailure(new SearchRelevanceException("ABTest cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.updateDoc(
                abTest.getTestId(),
                abTest.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                AB_TEST,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to update ABTest", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @SuppressWarnings("unchecked")
    public void updateABTestWithConcurrencyControl(
        final ABTest abTest,
        final long seqNo,
        final long primaryTerm,
        final ActionListener listener
    ) {
        if (abTest == null) {
            listener.onFailure(new SearchRelevanceException("ABTest cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            org.opensearch.core.xcontent.XContentBuilder xContent = abTest.toXContent(
                XContentFactory.jsonBuilder(),
                ToXContent.EMPTY_PARAMS
            );
            StashedThreadContext.run(
                client,
                () -> client.prepareIndex(AB_TEST.getIndexName())
                    .setId(abTest.getTestId())
                    .setOpType(OpType.INDEX)
                    .setIfSeqNo(seqNo)
                    .setIfPrimaryTerm(primaryTerm)
                    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                    .setSource(xContent)
                    .execute((ActionListener<IndexResponse>) listener)
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to update ABTest", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void putSnapshot(final ABTestSnapshot snapshot, final ActionListener listener) {
        if (snapshot == null) {
            listener.onFailure(new SearchRelevanceException("ABTestSnapshot cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.updateDoc(
                snapshot.getDocId(),
                snapshot.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                AB_TEST,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store snapshot", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void getABTest(String testId, ActionListener<SearchResponse> listener) {
        if (testId == null || testId.isEmpty()) {
            listener.onFailure(new SearchRelevanceException("testId must not be null or empty", RestStatus.BAD_REQUEST));
            return;
        }
        searchRelevanceIndicesManager.getDocByDocId(testId, AB_TEST, listener);
    }

    public void deleteABTest(String testId, ActionListener<org.opensearch.action.delete.DeleteResponse> listener) {
        if (testId == null || testId.isEmpty()) {
            listener.onFailure(new SearchRelevanceException("testId must not be null or empty", RestStatus.BAD_REQUEST));
            return;
        }
        searchRelevanceIndicesManager.deleteDocByDocId(testId, AB_TEST, listener);
    }

    public void deleteSnapshotsByTestId(String testId, ActionListener<org.opensearch.index.reindex.BulkByScrollResponse> listener) {
        org.opensearch.index.reindex.DeleteByQueryRequest deleteRequest = new org.opensearch.index.reindex.DeleteByQueryRequest(
            AB_TEST.getIndexName()
        );
        deleteRequest.setQuery(
            org.opensearch.index.query.QueryBuilders.boolQuery()
                .must(org.opensearch.index.query.QueryBuilders.termQuery("test_id", testId))
                .must(org.opensearch.index.query.QueryBuilders.termQuery("doc_type", "snapshot"))
        );
        StashedThreadContext.run(
            client,
            () -> client.execute(org.opensearch.index.reindex.DeleteByQueryAction.INSTANCE, deleteRequest, listener)
        );
    }
}
