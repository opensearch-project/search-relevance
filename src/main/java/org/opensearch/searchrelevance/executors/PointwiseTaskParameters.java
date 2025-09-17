/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import java.util.List;
import java.util.Map;

import org.opensearch.searchrelevance.model.ExperimentVariant;
import org.opensearch.searchrelevance.scheduler.ExperimentCancellationToken;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Task parameters specifically for pointwise evaluation experiments.
 * Extends VariantTaskParameters with pointwise-specific fields.
 */
@Getter
@SuperBuilder
public class PointwiseTaskParameters extends VariantTaskParameters {

    /**
     * Search pipeline to use for the pointwise evaluation.
     * Unlike hybrid optimizer, pointwise uses standard search pipelines without temporary modifications.
     */
    private final String searchPipeline;

    /**
     * Create pointwise task parameters with standard search configuration
     */
    public static PointwiseTaskParameters create(
        String experimentId,
        String searchConfigId,
        String index,
        String query,
        String queryText,
        int size,
        ExperimentVariant experimentVariant,
        List<String> judgmentIds,
        Map<String, String> docIdToScores,
        ExperimentTaskContext taskContext,
        String searchPipeline,
        ExperimentCancellationToken cancellationToken
    ) {
        return PointwiseTaskParameters.builder()
            .experimentId(experimentId)
            .searchConfigId(searchConfigId)
            .index(index)
            .query(query)
            .queryText(queryText)
            .size(size)
            .experimentVariant(experimentVariant)
            .judgmentIds(judgmentIds)
            .docIdToScores(docIdToScores)
            .taskContext(taskContext)
            .searchPipeline(searchPipeline)
            .cancellationToken(cancellationToken)
            .build();
    }
}
