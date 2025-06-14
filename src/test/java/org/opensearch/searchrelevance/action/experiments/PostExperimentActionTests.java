/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.experiments;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.transport.experiment.PostExperimentRequest;
import org.opensearch.searchrelevance.transport.experiment.PutExperimentRequest;
import org.opensearch.test.OpenSearchTestCase;

public class PostExperimentActionTests extends OpenSearchTestCase {

    public void testStreams() throws IOException {
        List<Map<String, Object>> evaluationResults = List.of(
            Map.of(
                "searchText",
                "test query",
                "judgmentIds",
                List.of("j1", "j2"),
                "documentIds",
                List.of("d1", "d2"),
                "metrics",
                List.of(Map.of("metric", "dcg@10", "value", 0.8), Map.of("metric", "ndcg@10", "value", 0.75))
            )
        );

        PostExperimentRequest request = new PostExperimentRequest(
            ExperimentType.POINTWISE_EVALUATION,
            "1234",
            List.of("5678", "0000"),
            List.of("5678", "0000"),
            10,
            evaluationResults
        );

        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PostExperimentRequest serialized = new PostExperimentRequest(in);

        assertEquals("1234", serialized.getQuerySetId());
        assertEquals(ExperimentType.POINTWISE_EVALUATION, serialized.getType());
        assertEquals(1, serialized.getEvaluationResultList().size());

        Map<String, Object> result = serialized.getEvaluationResultList().get(0);
        assertEquals("test query", result.get("searchText"));
        assertTrue(result.containsKey("metrics"));
        assertTrue(result.containsKey("judgmentIds"));
        assertTrue(result.containsKey("documentIds"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) result.get("metrics");
        Map<String, Object> dcg = metrics.get(0);
        assertEquals("dcg@10", dcg.get("metric"));
        assertEquals(0.8, dcg.get("value"));

        Map<String, Object> ndcg = metrics.get(1);
        assertEquals("ndcg@10", ndcg.get("metric"));
        assertEquals(0.75, ndcg.get("value"));

        assertEquals(10, serialized.getSize());
    }

    public void testRequestValidation() {
        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.PAIRWISE_COMPARISON,
            "1234",
            List.of("5678", "0000"),
            List.of("5678", "0000"),
            10
        );
        assertNull(request.validate());
    }

}
