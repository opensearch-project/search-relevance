/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment.signature;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.ExperimentInputSignature;
import org.opensearch.searchrelevance.model.Judgment;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.model.QuerySet;
import org.opensearch.searchrelevance.model.QuerySetEntry;
import org.opensearch.searchrelevance.model.SearchConfigurationDetails;
import org.opensearch.test.OpenSearchTestCase;

public class ExperimentInputSignatureComputerTests extends OpenSearchTestCase {

    public void testNormalizeSearchRequestBodyStripsRuntimeFields() {
        String raw = "{\"query\":{\"match\":{\"f\":\"v\"}},\"size\":5,\"from\":10,\"profile\":true}";
        String normalized = ExperimentInputSignatureComputer.normalizeSearchRequestBody(raw);
        assertFalse(normalized.contains("\"size\""));
        assertFalse(normalized.contains("\"from\""));
        assertFalse(normalized.contains("\"profile\""));
        assertTrue(normalized.contains("\"match\""));
    }

    public void testNormalizeSearchRequestBodyMalformedFallsBackToLiteral() {
        String raw = "not-json";
        assertEquals("not-json", ExperimentInputSignatureComputer.normalizeSearchRequestBody(raw));
    }

    public void testQuerySetOrderingIsStableRegardlessOfQueryOrder() {
        QuerySet a = QuerySet.Builder.builder()
            .id("q")
            .name("n")
            .description("d")
            .timestamp("t")
            .sampling("s")
            .querySetQueries(
                List.of(QuerySetEntry.Builder.builder().queryText("z").build(), QuerySetEntry.Builder.builder().queryText("a").build())
            )
            .build();
        QuerySet b = QuerySet.Builder.builder()
            .id("q")
            .name("n")
            .description("d")
            .timestamp("t")
            .sampling("s")
            .querySetQueries(
                List.of(QuerySetEntry.Builder.builder().queryText("a").build(), QuerySetEntry.Builder.builder().queryText("z").build())
            )
            .build();
        ExperimentInputSignature sa = ExperimentInputSignatureComputer.compute(a, List.of(), Map.of(), List.of());
        ExperimentInputSignature sb = ExperimentInputSignatureComputer.compute(b, List.of(), Map.of(), List.of());
        assertEquals(sa.querySetSha256(), sb.querySetSha256());
    }

    public void testSearchConfigurationNormalizationIgnoresSizeInQueryJson() {
        SearchConfigurationDetails d1 = SearchConfigurationDetails.builder()
            .index("i")
            .query("{\"size\":99,\"query\":{\"match_all\":{}}}")
            .pipeline("")
            .build();
        SearchConfigurationDetails d2 = SearchConfigurationDetails.builder()
            .index("i")
            .query("{\"query\":{\"match_all\":{}},\"size\":1}")
            .pipeline("")
            .build();
        Map<String, SearchConfigurationDetails> m = Map.of("c", d1);
        ExperimentInputSignature s1 = ExperimentInputSignatureComputer.compute(
            QuerySet.Builder.builder().id("q").name("").description("").timestamp("t").sampling("").querySetQueries(List.of()).build(),
            List.of("c"),
            m,
            List.of()
        );
        ExperimentInputSignature s2 = ExperimentInputSignatureComputer.compute(
            QuerySet.Builder.builder().id("q").name("").description("").timestamp("t").sampling("").querySetQueries(List.of()).build(),
            List.of("c"),
            Map.of("c", d2),
            List.of()
        );
        assertEquals(s1.searchConfigurationsSha256(), s2.searchConfigurationsSha256());
    }

    public void testJudgmentRatingsOrderingStable() {
        Map<String, Object> row1 = new HashMap<>();
        row1.put("query", "q1");
        row1.put("ratings", List.of(Map.of("b", 2, "a", 1)));
        Map<String, Object> row2 = new HashMap<>();
        row2.put("query", "q1");
        row2.put("ratings", List.of(Map.of("a", 1, "b", 2)));
        Judgment j1 = new Judgment("j", "t", "n", AsyncStatus.COMPLETED, JudgmentType.IMPORT_JUDGMENT, Map.of(), List.of(row1));
        Judgment j2 = new Judgment("j", "t", "n", AsyncStatus.COMPLETED, JudgmentType.IMPORT_JUDGMENT, Map.of(), List.of(row2));
        QuerySet qs = QuerySet.Builder.builder()
            .id("q")
            .name("")
            .description("")
            .timestamp("t")
            .sampling("")
            .querySetQueries(List.of(QuerySetEntry.Builder.builder().queryText("x").build()))
            .build();
        ExperimentInputSignature s1 = ExperimentInputSignatureComputer.compute(qs, List.of(), Map.of(), List.of(j1));
        ExperimentInputSignature s2 = ExperimentInputSignatureComputer.compute(qs, List.of(), Map.of(), List.of(j2));
        assertEquals(s1.judgmentListSha256(), s2.judgmentListSha256());
    }

    // ============================================
    // Metadata-only changes must NOT trigger drift
    // ============================================

    public void testQuerySetMetadataChangesDoNotTriggerDrift() {
        QuerySet qsOriginal = QuerySet.Builder.builder()
            .id("qs-1")
            .name("Original Name")
            .description("Original Desc")
            .timestamp("2024-01-01T00:00:00Z")
            .sampling("random")
            .querySetQueries(List.of(QuerySetEntry.Builder.builder().queryText("query1").build()))
            .build();

        QuerySet qsRenamed = QuerySet.Builder.builder()
            .id("qs-1")
            .name("Renamed Name")
            .description("Changed Desc")
            .timestamp("2024-02-02T00:00:00Z")
            .sampling("topN")
            .querySetQueries(List.of(QuerySetEntry.Builder.builder().queryText("query1").build()))
            .build();

        ExperimentInputSignature s1 = ExperimentInputSignatureComputer.compute(qsOriginal, List.of(), Map.of(), List.of());
        ExperimentInputSignature s2 = ExperimentInputSignatureComputer.compute(qsRenamed, List.of(), Map.of(), List.of());
        assertEquals(s1.querySetSha256(), s2.querySetSha256());
    }

    public void testJudgmentMetadataChangesDoNotTriggerDrift() {
        Map<String, Object> row = new HashMap<>();
        row.put("query", "q1");
        row.put("ratings", List.of(Map.of("doc", "d1", "rating", 3)));

        Judgment jOriginal = new Judgment(
            "j-1",
            "2024-01-01T00:00:00Z",
            "Original Name",
            AsyncStatus.COMPLETED,
            JudgmentType.IMPORT_JUDGMENT,
            Map.of("key", "val"),
            List.of(row)
        );
        Judgment jChanged = new Judgment(
            "j-1",
            "2024-02-02T00:00:00Z",
            "Renamed Name",
            AsyncStatus.ERROR,
            JudgmentType.LLM_JUDGMENT,
            Map.of("key", "changed"),
            List.of(row)
        );

        QuerySet qs = QuerySet.Builder.builder()
            .id("q")
            .name("")
            .description("")
            .timestamp("t")
            .sampling("")
            .querySetQueries(List.of())
            .build();

        ExperimentInputSignature s1 = ExperimentInputSignatureComputer.compute(qs, List.of(), Map.of(), List.of(jOriginal));
        ExperimentInputSignature s2 = ExperimentInputSignatureComputer.compute(qs, List.of(), Map.of(), List.of(jChanged));
        assertEquals(s1.judgmentListSha256(), s2.judgmentListSha256());
    }

    public void testSearchConfigurationIdChangesDoNotTriggerDrift() {
        SearchConfigurationDetails d = SearchConfigurationDetails.builder()
            .index("test-index")
            .query("{\"match_all\":{}}")
            .pipeline("default-pipeline")
            .build();

        // Same config content, different IDs
        Map<String, SearchConfigurationDetails> m1 = Map.of("config-a", d);
        Map<String, SearchConfigurationDetails> m2 = Map.of("config-b", d);

        QuerySet qs = QuerySet.Builder.builder()
            .id("q")
            .name("")
            .description("")
            .timestamp("t")
            .sampling("")
            .querySetQueries(List.of())
            .build();

        ExperimentInputSignature s1 = ExperimentInputSignatureComputer.compute(qs, List.of("config-a"), m1, List.of());
        ExperimentInputSignature s2 = ExperimentInputSignatureComputer.compute(qs, List.of("config-b"), m2, List.of());
        assertEquals(s1.searchConfigurationsSha256(), s2.searchConfigurationsSha256());
    }
}
