/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_COMBINATION_TECHNIQUE;
import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE;
import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.neuralsearch.query.HybridQueryBuilder;
import org.opensearch.searchrelevance.model.ExperimentVariant;
import org.opensearch.searchrelevance.model.builder.SearchRequestBuilder;

import lombok.extern.log4j.Log4j2;

/**
 * Utility class for a query source
 */
@Log4j2
public class QuerySourceUtil {

    public static final int NUMBER_OF_SUBQUERIES_IN_HYBRID_QUERY = 2;

    public static final String POOL_NORMALIZATION = "min_max";
    public static final String POOL_COMBINATION = "arithmetic_mean";

    /**
     * Creates a definition of a temporary search pipeline for hybrid search.
     * @param experimentVariant sub-experiment to create the pipeline for
     * @return definition of a temporary search pipeline
     */
    public static Map<String, Object> createDefinitionOfTemporarySearchPipeline(final ExperimentVariant experimentVariant) {
        Map<String, Object> experimentVariantParameters = experimentVariant.getParameters();
        Map<String, Object> normalizationTechniqueConfig = new HashMap<>(
            Map.of("technique", experimentVariantParameters.get(EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE))
        );

        Map<String, Object> combinationTechniqueConfig = new HashMap<>(
            Map.of("technique", experimentVariantParameters.get(EXPERIMENT_OPTION_COMBINATION_TECHNIQUE))
        );
        if (Objects.nonNull(experimentVariantParameters.get(EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION))) {
            float[] weights = (float[]) experimentVariantParameters.get(EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION);
            List<Double> weightsList = new ArrayList<>(weights.length);
            for (float weight : weights) {
                weightsList.add((double) weight);
            }
            combinationTechniqueConfig.put("parameters", new HashMap<>(Map.of("weights", weightsList)));
        }

        Map<String, Object> normalizationProcessorConfig = new HashMap<>(
            Map.of("normalization", normalizationTechniqueConfig, "combination", combinationTechniqueConfig)
        );
        Map<String, Object> phaseProcessorObject = new HashMap<>(Map.of("normalization-processor", normalizationProcessorConfig));
        Map<String, Object> temporarySearchPipeline = new HashMap<>();
        temporarySearchPipeline.put("phase_results_processors", List.of(phaseProcessorObject));
        return temporarySearchPipeline;
    }

    /**
     * Checks if the query is a hybrid query with exactly {@link #NUMBER_OF_SUBQUERIES_IN_HYBRID_QUERY} sub-queries.
     * Non-throwing variant of {@link #validateHybridQuery(Map)}.
     * Used by the HYBRID_OPTIMIZER experiment which requires exactly 2 sub-queries (lexical + neural).
     * For expandCoverage (which supports any sub-query count), use {@link #isHybridQueryAnySize(Map)} instead.
     * @param fullQueryMap the parsed query body
     * @return true if the query is a valid hybrid query with the required sub-query count
     */
    public static boolean isHybridQuery(final Map<String, Object> fullQueryMap) {
        try {
            validateHybridQuery(fullQueryMap);
            return true;
        } catch (Exception e) {
            log.debug("Query is not a valid hybrid query: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the query in the search configuration is a hybrid query with any number of sub-queries (≥ 1).
     * @param fullQueryMap the parsed query body
     * @return true if the query is a valid hybrid query with at least 1 sub-query
     */
    public static boolean isHybridQueryAnySize(final Map<String, Object> fullQueryMap) {
        try {
            return getSubQueryCount(fullQueryMap) >= 1;
        } catch (Exception e) {
            log.debug("Query is not a valid hybrid query: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extracts the number of sub-queries from a hybrid query.
     * Uses typed parsing via {@link HybridQueryBuilder#fromXContent(XContentParser)} at runtime,
     * falls back to map-based inspection in unit tests without registry.
     *
     * @param fullQueryMap the parsed query body
     * @return number of sub-queries in the hybrid query
     * @throws IllegalArgumentException if the query is not a valid hybrid query
     */
    public static int getSubQueryCount(final Map<String, Object> fullQueryMap) {
        if (Objects.isNull(fullQueryMap) || !fullQueryMap.containsKey("query") || !(fullQueryMap.get("query") instanceof Map)) {
            throw new IllegalArgumentException("search configuration must have at least one query");
        }
        Map<String, Object> queryMap = (Map<String, Object>) fullQueryMap.get("query");
        if (!queryMap.containsKey(HybridQueryBuilder.NAME) || !(queryMap.get(HybridQueryBuilder.NAME) instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "query in search configuration must be of type [%s]", HybridQueryBuilder.NAME)
            );
        }

        if (Objects.nonNull(SearchRequestBuilder.getNamedXContentRegistry())) {
            try {
                return getSubQueryCountViaTypedParsing(queryMap, SearchRequestBuilder.getNamedXContentRegistry());
            } catch (Exception e) {
                log.debug("Typed hybrid query parsing failed, falling back to map-based parsing: {}", e.getMessage());
            }
        }
        return getSubQueryCountFromMap(queryMap);
    }

    /**
     * Parses the hybrid query using {@link HybridQueryBuilder#fromXContent(XContentParser)} and returns
     * the sub-query count via the typed {@link HybridQueryBuilder#queries()} accessor.
     */
    private static int getSubQueryCountViaTypedParsing(final Map<String, Object> queryMap, final NamedXContentRegistry registry) {
        Map<String, Object> hybridSection = (Map<String, Object>) queryMap.get(HybridQueryBuilder.NAME);
        try (XContentBuilder builder = JsonXContent.contentBuilder()) {
            builder.startObject();
            builder.field(HybridQueryBuilder.NAME);
            builder.map(hybridSection);
            builder.endObject();

            try (
                XContentParser parser = JsonXContent.jsonXContent.createParser(
                    registry,
                    DeprecationHandler.IGNORE_DEPRECATIONS,
                    builder.toString()
                )
            ) {
                parser.nextToken();
                parser.nextToken();
                parser.nextToken();

                HybridQueryBuilder hybridQueryBuilder = HybridQueryBuilder.fromXContent(parser);
                int count = hybridQueryBuilder.queries().size();
                if (count < 1) {
                    throw new IllegalArgumentException(
                        String.format(Locale.ROOT, "[%s] query must have at least one sub-query", HybridQueryBuilder.NAME)
                    );
                }
                return count;
            }
        } catch (IllegalArgumentException e) {
            log.error("Invalid hybrid query structure: {}", e.getMessage(), e);
            throw e;
        } catch (IOException e) {
            log.error("Failed to parse hybrid query: {}", e.getMessage(), e);
            throw new IllegalArgumentException(String.format(Locale.ROOT, "failed to parse [%s] query", HybridQueryBuilder.NAME), e);
        }
    }

    /**
     * Extracts sub-query count from the raw map structure.
     * Used when {@link NamedXContentRegistry} is not available (e.g. in unit tests).
     */
    private static int getSubQueryCountFromMap(final Map<String, Object> queryMap) {
        Map<String, Object> hybridMap = (Map<String, Object>) queryMap.get(HybridQueryBuilder.NAME);
        if (!hybridMap.containsKey("queries") || !(hybridMap.get("queries") instanceof List<?>)) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "[%s] query in search configuration does not have sub-queries", HybridQueryBuilder.NAME)
            );
        }
        List<?> queries = (List<?>) hybridMap.get("queries");
        if (queries.isEmpty()) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "[%s] query must have at least one sub-query", HybridQueryBuilder.NAME)
            );
        }
        return queries.size();
    }

    /**
     * Generates pooling weight configurations for expandCoverage.
     * Produces N+1 configurations: 1 equal-weight + N one-hot configurations.
     *
     * @param numSubQueries number of sub-queries in the hybrid query (must be ≥ 1)
     * @return unmodifiable list of unmodifiable weight configurations
     */
    public static List<List<Float>> generatePoolingWeights(final int numSubQueries) {
        if (numSubQueries < 1) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "numSubQueries must be at least 1, got [%d]", numSubQueries));
        }

        List<List<Float>> weightConfigs = new ArrayList<>(numSubQueries + 1);

        // Equal weights: null signals that the "parameters" key should be omitted from the pipeline,
        // allowing the hybrid query processor to apply its default equal-weight behavior.
        weightConfigs.add(null);

        for (int i = 0; i < numSubQueries; i++) {
            List<Float> oneHotConfig = new ArrayList<>(numSubQueries);
            for (int j = 0; j < numSubQueries; j++) {
                oneHotConfig.add((i == j) ? 1.0f : 0.0f);
            }
            weightConfigs.add(Collections.unmodifiableList(oneHotConfig));
        }

        return Collections.unmodifiableList(weightConfigs);
    }

    /**
     * Creates a temporary search pipeline definition for hybrid search pooling with specified weights.
     * @param weights list of weights, one per sub-query
     * @param normalization normalization technique name
     * @param combination combination technique name
     * @return definition of a temporary search pipeline
     */
    public static Map<String, Object> createPoolingSearchPipeline(
        final List<Float> weights,
        final String normalization,
        final String combination
    ) {
        Map<String, Object> normalizationConfig = new HashMap<>(Map.of("technique", normalization));
        Map<String, Object> combinationConfig = new HashMap<>(Map.of("technique", combination));
        // When weights is null, omit the "parameters" key entirely — this lets the hybrid query
        // processor apply its default equal-weight behavior (1.0 per sub-query).
        if (weights != null) {
            List<Double> weightsList = new ArrayList<>(weights.size());
            for (Float w : weights) {
                weightsList.add(w.doubleValue());
            }
            combinationConfig.put("parameters", new HashMap<>(Map.of("weights", weightsList)));
        }

        Map<String, Object> processorConfig = new HashMap<>(Map.of("normalization", normalizationConfig, "combination", combinationConfig));
        Map<String, Object> phaseProcessor = new HashMap<>(Map.of("normalization-processor", processorConfig));
        Map<String, Object> pipeline = new HashMap<>();
        pipeline.put("phase_results_processors", List.of(phaseProcessor));
        return pipeline;
    }

    /**
     * Validate that the query in the search configuration is a hybrid query with exactly two sub-queries.
     * @param fullQueryMap the parsed query body
     * @throws IOException if the query cannot be parsed
     */
    public static void validateHybridQuery(final Map<String, Object> fullQueryMap) throws IOException {
        if (fullQueryMap.containsKey("query") == false || fullQueryMap.get("query") instanceof Map == false) {
            throw new IllegalArgumentException("search configuration must have at least one query");
        }
        Map<String, Object> queryMap = (Map<String, Object>) fullQueryMap.get("query");
        if (queryMap.containsKey(HybridQueryBuilder.NAME) == false || queryMap.get(HybridQueryBuilder.NAME) instanceof Map<?, ?> == false) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "query in search configuration must be of type [%s]", HybridQueryBuilder.NAME)
            );
        }
        Map<String, Object> hybridMap = (Map<String, Object>) queryMap.get(HybridQueryBuilder.NAME);
        if (hybridMap.containsKey("queries") == false || hybridMap.get("queries") instanceof List<?> == false) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "[%s] query in search configuration does not have sub-queries", HybridQueryBuilder.NAME)
            );
        }
        List<?> queriesMap = (List<?>) hybridMap.get("queries");
        if (queriesMap.size() != NUMBER_OF_SUBQUERIES_IN_HYBRID_QUERY) {
            throw new IllegalArgumentException(
                String.format(
                    Locale.ROOT,
                    "invalid hybrid query: expected exactly [%d] sub-queries but found [%d]",
                    NUMBER_OF_SUBQUERIES_IN_HYBRID_QUERY,
                    queriesMap.size()
                )
            );
        }
    }
}
