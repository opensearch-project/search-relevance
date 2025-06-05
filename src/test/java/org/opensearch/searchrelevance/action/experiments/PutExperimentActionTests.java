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
import org.opensearch.searchrelevance.transport.experiment.PutExperimentRequest;
import org.opensearch.test.OpenSearchTestCase;

public class PutExperimentActionTests extends OpenSearchTestCase {

    public void testStreams() throws IOException {
        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.PAIRWISE_COMPARISON,
            "1234",
            List.of("5678", "0000"),
            List.of("5678", "0000"),
            10
        );
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PutExperimentRequest serialized = new PutExperimentRequest(in);
        assertEquals("1234", serialized.getQuerySetId());
        assertEquals(2, serialized.getSearchConfigurationList().size());
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

    public void testImportStreams() throws IOException {
        List<Map<String, Object>> evaluationResults = List.of(
            Map.of("queryText", "test query 1", "dcg@10", 0.8, "ndcg@10", 0.75),
            Map.of("queryText", "test query 2", "dcg@10", 0.9, "ndcg@10", 0.85)
        );

        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.POINTWISE_EVALUATION_IMPORT,
            "1234",
            List.of("5678", "0000"),
            List.of("5678", "0000"),
            evaluationResults
        );

        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PutExperimentRequest serialized = new PutExperimentRequest(in);

        assertEquals("1234", serialized.getQuerySetId());
        assertEquals(2, serialized.getSearchConfigurationList().size());
        assertEquals(ExperimentType.POINTWISE_EVALUATION_IMPORT, serialized.getType());
        assertEquals(2, serialized.getEvaluationResultList().size());
        assertEquals("test query 1", serialized.getEvaluationResultList().get(0).get("queryText"));
    }

    public void testImportRequestValidation() {
        List<Map<String, Object>> evaluationResults = List.of(Map.of("queryText", "test query", "dcg@10", 0.8));

        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.POINTWISE_EVALUATION_IMPORT,
            "1234",
            List.of("5678", "0000"),
            List.of("5678", "0000"),
            evaluationResults
        );
        assertNull(request.validate());
    }

}
