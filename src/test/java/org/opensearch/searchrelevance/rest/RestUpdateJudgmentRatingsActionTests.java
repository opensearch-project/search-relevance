/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.mockito.ArgumentCaptor;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.plugin.SearchRelevanceRestTestCase;
import org.opensearch.searchrelevance.transport.judgment.UpdateJudgmentRatingsAction;
import org.opensearch.searchrelevance.transport.judgment.UpdateJudgmentRatingsRequest;

public class RestUpdateJudgmentRatingsActionTests extends SearchRelevanceRestTestCase {
    public void testValidDocumentIdsPreserved() throws Exception {
        for (String docId : List.of("sku\"<red>\\part", " ", "é".repeat(256))) {
            RestRequest request = requestWithDocumentId(docId);
            new RestUpdateJudgmentRatingsAction(settingsAccessor).handleRequest(request, channel, client);
        }
        ArgumentCaptor<UpdateJudgmentRatingsRequest> captor = ArgumentCaptor.forClass(UpdateJudgmentRatingsRequest.class);
        verify(client, times(3)).execute(eq(UpdateJudgmentRatingsAction.INSTANCE), captor.capture(), any());
        assertEquals(
            List.of("sku\"<red>\\part", " ", "é".repeat(256)),
            captor.getAllValues().stream().map(r -> r.getAdjustments().get(0).getDocId()).toList()
        );
    }

    public void testInvalidDocumentIdsRejectedBeforeDispatch() throws Exception {
        for (Object docId : List.of("é".repeat(257), "", 42)) {
            RestRequest request = requestWithDocumentId(docId);
            new RestUpdateJudgmentRatingsAction(settingsAccessor).handleRequest(request, channel, client);
        }
        ArgumentCaptor<BytesRestResponse> captor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel, times(3)).sendResponse(captor.capture());
        for (BytesRestResponse response : captor.getAllValues()) {
            assertEquals(RestStatus.BAD_REQUEST, response.status());
        }
        verify(client, never()).execute(eq(UpdateJudgmentRatingsAction.INSTANCE), any(), any());
    }

    private RestRequest requestWithDocumentId(Object docId) throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        String content = BytesReference.bytes(JsonXContent.contentBuilder().map(
            Map.of("judgmentRatings", List.of(Map.of("query", "query", "ratings", List.of(Map.of("docId", docId, "rating", "0.5")))))
        )).utf8ToString();
        RestRequest request = createPutRestRequestWithContent(content, "judgments/id-1");
        request.params().put("id", "id-1");
        return request;
    }
}
