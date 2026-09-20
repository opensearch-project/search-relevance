/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.test.OpenSearchTestCase;

public class EvaluationResultTests extends OpenSearchTestCase {

    public void testToXContentWritesTookMsWhenPresent() throws IOException {
        EvaluationResult result = new EvaluationResult(
            "eval-1",
            "2024-01-01T00:00:00.000Z",
            "config-1",
            "red shoes",
            List.of("judgment-1"),
            List.of("d1", "d2"),
            List.of(Map.of("metric", "NDCG@10", "value", 0.82)),
            "experiment-1",
            "variant-1",
            null,
            null,
            14L
        );

        Map<String, Object> source = serialize(result);

        assertEquals(14, ((Number) source.get(EvaluationResult.TOOK_MS)).longValue());
        assertEquals("eval-1", source.get(EvaluationResult.ID));
        assertEquals("red shoes", source.get(EvaluationResult.SEARCH_TEXT));
        assertFalse("tookMs must not be mixed into nested metrics", containsTookMsMetric(source));
    }

    public void testToXContentOmitsTookMsWhenNull() throws IOException {
        EvaluationResult result = new EvaluationResult(
            "eval-1",
            "2024-01-01T00:00:00.000Z",
            "config-1",
            "red shoes",
            List.of("judgment-1"),
            List.of("d1"),
            List.of(Map.of("metric", "NDCG@10", "value", 0.82))
        );

        Map<String, Object> source = serialize(result);

        assertFalse("legacy documents omit tookMs", source.containsKey(EvaluationResult.TOOK_MS));
        assertNull(result.tookMs());
    }

    public void testExistingConstructorsDefaultTookMsToNull() {
        EvaluationResult withScheduledRun = new EvaluationResult(
            "eval-1",
            "2024-01-01T00:00:00.000Z",
            "config-1",
            "red shoes",
            List.of(),
            List.of(),
            List.of(),
            "scheduled-run-1"
        );
        assertNull(withScheduledRun.tookMs());
        assertEquals("scheduled-run-1", withScheduledRun.scheduledRunId());

        EvaluationResult withExperimentFields = new EvaluationResult(
            "eval-1",
            "2024-01-01T00:00:00.000Z",
            "config-1",
            "red shoes",
            List.of(),
            List.of(),
            List.of(),
            "experiment-1",
            "variant-1",
            "combination=rrf"
        );
        assertNull(withExperimentFields.tookMs());
        assertEquals("experiment-1", withExperimentFields.experimentId());
    }

    public void testTookMsZeroIsSerialized() throws IOException {
        EvaluationResult result = new EvaluationResult(
            "eval-1",
            "2024-01-01T00:00:00.000Z",
            "config-1",
            "red shoes",
            List.of(),
            List.of(),
            List.of(),
            "experiment-1",
            "variant-1",
            null,
            null,
            0L
        );

        Map<String, Object> source = serialize(result);
        assertEquals(0, ((Number) source.get(EvaluationResult.TOOK_MS)).longValue());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> serialize(EvaluationResult result) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        result.toXContent(builder, ToXContent.EMPTY_PARAMS);
        return XContentHelper.convertToMap(BytesReference.bytes(builder), false, XContentType.JSON).v2();
    }

    @SuppressWarnings("unchecked")
    private boolean containsTookMsMetric(Map<String, Object> source) {
        Object metrics = source.get(EvaluationResult.METRICS);
        if (!(metrics instanceof List<?>)) {
            return false;
        }
        for (Object metric : (List<Object>) metrics) {
            if (metric instanceof Map<?, ?> metricMap && EvaluationResult.TOOK_MS.equals(metricMap.get("metric"))) {
                return true;
            }
        }
        return false;
    }
}
