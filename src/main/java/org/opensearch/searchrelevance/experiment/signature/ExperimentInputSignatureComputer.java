/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment.signature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.opensearch.searchrelevance.model.ExperimentInputSignature;
import org.opensearch.searchrelevance.model.Judgment;
import org.opensearch.searchrelevance.model.QuerySet;
import org.opensearch.searchrelevance.model.QuerySetEntry;
import org.opensearch.searchrelevance.model.SearchConfigurationDetails;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Computes stable SHA-256 fingerprints for experiment inputs using canonical JSON serialization.
 */
public final class ExperimentInputSignatureComputer {
    private static final ObjectMapper CANONICAL_JSON = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    private ExperimentInputSignatureComputer() {}

    public static ExperimentInputSignature compute(
        QuerySet querySet,
        List<String> searchConfigurationIdsInOrder,
        Map<String, SearchConfigurationDetails> searchConfigurationsById,
        List<Judgment> judgmentsInRequestOrder
    ) {
        Objects.requireNonNull(querySet, "querySet");
        List<String> configOrder = searchConfigurationIdsInOrder == null ? List.of() : searchConfigurationIdsInOrder;
        Map<String, SearchConfigurationDetails> configs = searchConfigurationsById == null ? Map.of() : searchConfigurationsById;
        List<Judgment> judgments = judgmentsInRequestOrder == null ? List.of() : judgmentsInRequestOrder;

        return new ExperimentInputSignature(
            sha256Hex(canonicalJson(buildQuerySetPayload(querySet))),
            sha256Hex(canonicalJson(buildJudgmentListPayload(judgments))),
            sha256Hex(canonicalJson(buildSearchConfigurationsPayload(configOrder, configs)))
        );
    }

    private static Map<String, Object> buildQuerySetPayload(QuerySet querySet) {
        Map<String, Object> map = new TreeMap<>();
        // Only the actual query texts affect evaluation results.
        // name, description, id, and sampling are display/organizational metadata
        // and are intentionally excluded to avoid false drift reports.
        List<String> sortedQueries = querySet.querySetQueries()
            .stream()
            .map(QuerySetEntry::queryText)
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toList());
        map.put("queries", sortedQueries);
        return map;
    }

    private static List<Map<String, Object>> buildJudgmentListPayload(List<Judgment> judgmentsInRequestOrder) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Judgment j : judgmentsInRequestOrder) {
            Map<String, Object> jmap = new TreeMap<>();
            // Only the actual rating rows affect evaluation results.
            // id, name, type, status, and metadata are display/organizational metadata
            // and are intentionally excluded to avoid false drift reports.
            jmap.put("rows", canonicalJudgmentRows(j.getJudgmentRatings()));
            out.add(jmap);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> canonicalJudgmentRows(List<Map<String, Object>> judgmentRatings) {
        if (judgmentRatings == null || judgmentRatings.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> block : judgmentRatings) {
            if (block == null) {
                continue;
            }
            Object q = block.get("query");
            String queryKey = q == null ? "" : String.valueOf(q);
            List<Map<String, Object>> ratings = new ArrayList<>();
            Object ratingsObj = block.get("ratings");
            if (ratingsObj instanceof List<?> list) {
                for (Object r : list) {
                    if (r instanceof Map<?, ?> rm) {
                        ratings.add(sortedNestedMap((Map<String, Object>) rm));
                    }
                }
            }
            ratings.sort(Comparator.comparing(ExperimentInputSignatureComputer::canonicalJson));
            Map<String, Object> row = new TreeMap<>();
            row.put("query", queryKey);
            row.put("ratings", ratings);
            rows.add(row);
        }
        rows.sort(Comparator.comparing(m -> String.valueOf(m.get("query"))));
        return rows;
    }

    private static Map<String, Object> sortedNestedMap(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        return new TreeMap<>(metadata);
    }

    private static List<Map<String, Object>> buildSearchConfigurationsPayload(
        List<String> searchConfigurationIdsInOrder,
        Map<String, SearchConfigurationDetails> searchConfigurationsById
    ) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String id : searchConfigurationIdsInOrder) {
            SearchConfigurationDetails d = searchConfigurationsById.get(id);
            if (d == null) {
                throw new IllegalStateException("Missing search configuration details for id " + id);
            }
            Map<String, Object> entry = new TreeMap<>();
            // Only index, pipeline, and query affect search results.
            // id is a reference-only field and is intentionally excluded
            // to avoid false drift when a search configuration is renamed.
            entry.put("index", d.getIndex());
            entry.put("pipeline", d.getPipeline() == null ? "" : d.getPipeline());
            entry.put("query", normalizeSearchRequestBody(d.getQuery()));
            list.add(entry);
        }
        return list;
    }

    static String normalizeSearchRequestBody(String rawQuery) {
        if (rawQuery == null) {
            return "";
        }
        String trimmed = rawQuery.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        try {
            JsonNode node = CANONICAL_JSON.readTree(trimmed);
            if (node != null && node.isObject()) {
                ObjectNode obj = (ObjectNode) node;
                obj.remove("from");
                obj.remove("profile");
                obj.remove("size");
                return CANONICAL_JSON.writeValueAsString(obj);
            }
        } catch (JsonProcessingException ignored) {
            // fall through to literal
        }
        return trimmed;
    }

    static String canonicalJson(Object value) {
        try {
            return CANONICAL_JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize value for experiment signature", e);
        }
    }

    static String sha256Hex(String utf8) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(utf8.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(encoded.length * 2);
            for (byte b : encoded) {
                hex.append(String.format(java.util.Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
