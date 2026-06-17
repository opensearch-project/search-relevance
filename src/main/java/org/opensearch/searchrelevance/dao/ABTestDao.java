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
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.StepListener;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.model.ABTest;

public class ABTestDao {
    private static final Logger LOGGER = LogManager.getLogger(ABTestDao.class);

    private final SearchRelevanceIndicesManager searchRelevanceIndicesManager;

    @Inject
    public ABTestDao(SearchRelevanceIndicesManager searchRelevanceIndicesManager) {
        this.searchRelevanceIndicesManager = searchRelevanceIndicesManager;
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

    public void putSnapshot(
        final String snapshotId,
        final String testId,
        final Map<String, Object> record,
        final String created,
        final ActionListener listener
    ) {
        try {
            org.opensearch.core.xcontent.XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
            builder.field("doc_id", snapshotId);
            builder.field("test_id", testId);
            builder.field("record", record);
            builder.field("created", created);
            builder.endObject();
            searchRelevanceIndicesManager.updateDoc(snapshotId, builder, AB_TEST, listener);
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

    private ABTest convertToABTest(SearchResponse response) {
        Map<String, Object> source = response.getHits().getHits()[0].getSourceAsMap();
        return new ABTest(
            (String) source.get(ABTest.TEST_ID),
            (String) source.get(ABTest.SEARCH_CONFIGURATION_A),
            (String) source.get(ABTest.SEARCH_CONFIGURATION_B),
            (String) source.get(ABTest.CONFIG_A_UUID),
            (String) source.get(ABTest.CONFIG_B_UUID),
            (Boolean) source.get(ABTest.ENABLED),
            (Integer) source.get(ABTest.VERSION),
            (String) source.get(ABTest.CREATED_AT),
            (String) source.get(ABTest.UPDATED_AT)
        );
    }
}
