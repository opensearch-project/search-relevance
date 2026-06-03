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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Computes stable SHA-256 fingerprints for experiment inputs using canonical JSON serialization.
 */
public final class ExperimentInputSignatureComputer {
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
    private static final ObjectMapper CANONICAL_JSON = JsonMapper.builder()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
        .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
        .build();

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
            // Pre-compute canonical JSON once per rating to avoid O(M log M) Jackson serializations.
            List<Map.Entry<String, Map<String, Object>>> canonicalPairs = new ArrayList<>(ratings.size());
            for (Map<String, Object> rating : ratings) {
                canonicalPairs.add(Map.entry(canonicalJson(rating), rating));
            }
            canonicalPairs.sort(Map.Entry.comparingByKey());
            ratings.clear();
            for (Map.Entry<String, Map<String, Object>> pair : canonicalPairs) {
                ratings.add(pair.getValue());
            }
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
        } catch (JacksonException ignored) {
            // fall through to literal
        }
        return trimmed;
    }

    static String canonicalJson(Object value) {
        try {
            return CANONICAL_JSON.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize value for experiment signature", e);
        }
    }

    static String sha256Hex(String utf8) {
        try {
            byte[] encoded = MessageDigest.getInstance("SHA-256").digest(utf8.getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[encoded.length * 2];
            for (int i = 0; i < encoded.length; i++) {
                int v = encoded[i] & 0xFF;
                hex[i * 2] = HEX_DIGITS[v >>> 4];
                hex[i * 2 + 1] = HEX_DIGITS[v & 0x0F];
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
