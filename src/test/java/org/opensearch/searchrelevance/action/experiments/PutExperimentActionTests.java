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

    public void testImportWithSearchTextStreams() throws IOException {
        List<Map<String, Object>> evaluationResults = List.of(
            Map.of("searchText", "test query 1", "dcg@10", 0.8, "ndcg@10", 0.75),
            Map.of("searchText", "test query 2", "dcg@10", 0.9, "ndcg@10", 0.85)
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
        assertEquals("test query 1", serialized.getEvaluationResultList().get(0).get("searchText"));
        assertEquals("test query 2", serialized.getEvaluationResultList().get(1).get("searchText"));
    }

    public void testImportWithNestedMetricsStreams() throws IOException {
        List<Map<String, Object>> evaluationResults = List.of(
            Map.of(
                "searchText",
                "test query",
                "metrics",
                Map.of("dcg@10", 0.8, "ndcg@10", 0.75),
                "judgmentIds",
                List.of("j1", "j2"),
                "documentIds",
                List.of("d1", "d2")
            )
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
        assertEquals(ExperimentType.POINTWISE_EVALUATION_IMPORT, serialized.getType());
        assertEquals(1, serialized.getEvaluationResultList().size());

        Map<String, Object> result = serialized.getEvaluationResultList().get(0);
        assertEquals("test query", result.get("searchText"));
        assertTrue(result.containsKey("metrics"));
        assertTrue(result.containsKey("judgmentIds"));
        assertTrue(result.containsKey("documentIds"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
        assertEquals(0.8, metrics.get("dcg@10"));
        assertEquals(0.75, metrics.get("ndcg@10"));
    }

    public void testImportWithEmptyEvaluationResults() throws IOException {
        List<Map<String, Object>> evaluationResults = List.of();

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
        assertEquals(ExperimentType.POINTWISE_EVALUATION_IMPORT, serialized.getType());
        assertEquals(0, serialized.getEvaluationResultList().size());
    }

    public void testImportWithNullEvaluationResults() throws IOException {
        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.POINTWISE_EVALUATION_IMPORT,
            "1234",
            List.of("5678", "0000"),
            List.of("5678", "0000"),
            null
        );

        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PutExperimentRequest serialized = new PutExperimentRequest(in);

        assertEquals("1234", serialized.getQuerySetId());
        assertEquals(ExperimentType.POINTWISE_EVALUATION_IMPORT, serialized.getType());
        assertNull(serialized.getEvaluationResultList());
    }

    public void testRegularRequestCompatibility() throws IOException {
        // Ensure regular requests (non-import) still work correctly
        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.POINTWISE_EVALUATION,
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
        assertEquals(ExperimentType.POINTWISE_EVALUATION, serialized.getType());
        assertNull(serialized.getEvaluationResultList());
    }

}
