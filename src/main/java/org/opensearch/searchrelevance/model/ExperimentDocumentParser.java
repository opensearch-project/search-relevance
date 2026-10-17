/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.searchrelevance.common.PluginConstants;

/**
 * Parses {@link Experiment} instances from experiment index documents.
 */
public final class ExperimentDocumentParser {

    private ExperimentDocumentParser() {}

    /**
     * @param timestampOverride if non-null, replaces the timestamp stored on the document (used when re-indexing after a side effect).
     */
    @SuppressWarnings("unchecked")
    public static Experiment fromSourceMapWithTimestamp(Map<String, Object> source, String timestampOverride) {
        if (source == null) {
            throw new IllegalArgumentException("source cannot be null");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        Object resultsObj = source.get(Experiment.RESULTS);
        if (resultsObj instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    results.add((Map<String, Object>) m);
                }
            }
        }

        ExperimentInputSignature signature = null;
        Object sigObj = source.get(ExperimentInputSignature.FIELD);
        if (sigObj instanceof Map<?, ?> sigMap) {
            signature = ExperimentInputSignature.fromStoredMap((Map<String, ?>) sigMap);
        }

        String timestamp = timestampOverride != null ? timestampOverride : stringOrEmpty(source.get(Experiment.TIME_STAMP));

        return new Experiment(
            stringOrEmpty(source.get(Experiment.ID)),
            timestamp,
            (String) source.get(PluginConstants.NAME),
            (String) source.get(PluginConstants.DESCRIPTION),
            ExperimentType.valueOf(stringOrEmpty(source.get(Experiment.TYPE))),
            AsyncStatus.valueOf(stringOrEmpty(source.get(Experiment.STATUS))),
            stringOrEmpty(source.get(Experiment.QUERY_SET_ID)),
            stringList(source.get(Experiment.SEARCH_CONFIGURATION_LIST)),
            stringList(source.get(Experiment.JUDGMENT_LIST)),
            parseSize(source.get(Experiment.SIZE)),
            results,
            signature
        );
    }

    public static Experiment fromSourceMap(Map<String, Object> source) {
        return fromSourceMapWithTimestamp(source, null);
    }

    private static String stringOrEmpty(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object v) {
        if (v == null) {
            return new ArrayList<>();
        }
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
            return out;
        }
        return new ArrayList<>();
    }

    private static int parseSize(Object o) {
        if (o == null) {
            return 10;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return 10;
            }
        }
        return 10;
    }
}
