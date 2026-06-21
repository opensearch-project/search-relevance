/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.test.OpenSearchTestCase;

public class ExperimentDocumentParserTests extends OpenSearchTestCase {

    public void testParsesInputSignature() {
        Map<String, Object> sig = new HashMap<>();
        sig.put(ExperimentInputSignature.QUERY_SET, "aa");
        sig.put(ExperimentInputSignature.JUDGMENT_LIST, "bb");
        sig.put(ExperimentInputSignature.SEARCH_CONFIGURATIONS, "cc");
        Map<String, Object> source = new HashMap<>();
        source.put(Experiment.ID, "e1");
        source.put(Experiment.TIME_STAMP, "2024-01-01T00:00:00Z");
        source.put(Experiment.TYPE, ExperimentType.PAIRWISE_COMPARISON.name());
        source.put(Experiment.STATUS, AsyncStatus.COMPLETED.name());
        source.put(Experiment.QUERY_SET_ID, "q1");
        source.put(Experiment.SEARCH_CONFIGURATION_LIST, List.of("c1"));
        source.put(Experiment.JUDGMENT_LIST, List.of("j1"));
        source.put(Experiment.SIZE, 10);
        source.put(Experiment.RESULTS, List.of());
        source.put(ExperimentInputSignature.FIELD, sig);

        Experiment e = ExperimentDocumentParser.fromSourceMap(source);
        assertNotNull(e.inputSignature());
        assertEquals("aa", e.inputSignature().querySetSha256());
    }

    public void testParsesSizeAsString() {
        Map<String, Object> source = baseSource();
        source.put(Experiment.SIZE, "25");
        Experiment e = ExperimentDocumentParser.fromSourceMap(source);
        assertEquals(25, e.size());
    }

    private static Map<String, Object> baseSource() {
        Map<String, Object> source = new HashMap<>();
        source.put(Experiment.ID, "e1");
        source.put(Experiment.TIME_STAMP, "2024-01-01T00:00:00Z");
        source.put(Experiment.TYPE, ExperimentType.PAIRWISE_COMPARISON.name());
        source.put(Experiment.STATUS, AsyncStatus.COMPLETED.name());
        source.put(Experiment.QUERY_SET_ID, "q1");
        source.put(Experiment.SEARCH_CONFIGURATION_LIST, List.of());
        source.put(Experiment.JUDGMENT_LIST, List.of());
        source.put(Experiment.SIZE, 10);
        source.put(Experiment.RESULTS, List.of());
        return source;
    }
}
