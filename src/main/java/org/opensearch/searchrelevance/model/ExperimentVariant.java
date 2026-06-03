/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_COMBINATION_TECHNIQUE;
import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE;
import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_RANK_CONSTANT;
import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Experiment is a system index object that store experiment variant results.
 */
@AllArgsConstructor
@Builder
@Getter
public class ExperimentVariant implements ToXContentObject {
    public static final String ID = "id";
    public static final String TIME_STAMP = "timestamp";
    public static final String TYPE = "type";
    public static final String STATUS = "status";
    public static final String EXPERIMENT_ID = "experimentId";
    public static final String PARAMETERS = "parameters";
    public static final String RESULTS = "results";

    /**
     * Identifier of the system index
     */
    private final String id;
    private final String timestamp;
    private final ExperimentType type;
    private final AsyncStatus status;
    private final String experimentId;
    private final Map<String, Object> parameters;
    private final Map<String, Object> results;

    /**
     * Computes the textual parameters for this experiment variant based on its parameters.
     * The textual parameters are generated on-demand and not stored.
     * <p>
     * Output uses a uniform {@code key=value} format separated by commas, so different
     * variant shapes (e.g. score-based vs rank-based) remain self-describing:
     * <ul>
     *   <li>Score-based: {@code combination=arithmetic_mean, normalization=min_max, weights=0.5;0.5}</li>
     *   <li>RRF: {@code combination=rrf, rank_constant=60}</li>
     * </ul>
     * Only fields actually present on the variant are emitted; missing fields are skipped.
     *
     * @return The computed textual parameters string
     */
    public String getTextualParameters() {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, EXPERIMENT_OPTION_COMBINATION_TECHNIQUE, parameters.get(EXPERIMENT_OPTION_COMBINATION_TECHNIQUE));
        appendIfPresent(sb, EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE, parameters.get(EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE));

        Object weightsObj = parameters.get(EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION);
        if (weightsObj instanceof float[] weightsArray && weightsArray.length > 0) {
            StringBuilder weightsBuilder = new StringBuilder();
            NumberFormat formatter = NumberFormat.getNumberInstance(Locale.ROOT);
            formatter.setMaximumFractionDigits(2);
            formatter.setMinimumFractionDigits(0);
            for (int i = 0; i < weightsArray.length; i++) {
                if (i > 0) {
                    weightsBuilder.append(";");
                }
                weightsBuilder.append(formatter.format(weightsArray[i]));
            }
            appendIfPresent(sb, EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION, weightsBuilder.toString());
        }

        appendIfPresent(sb, EXPERIMENT_OPTION_RANK_CONSTANT, parameters.get(EXPERIMENT_OPTION_RANK_CONSTANT));
        return sb.toString();
    }

    private static void appendIfPresent(StringBuilder sb, String key, Object value) {
        if (value == null) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(key).append('=').append(value);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(ID, this.id.trim());
        xContentBuilder.field(TIME_STAMP, this.timestamp.trim());
        xContentBuilder.field(TYPE, this.type.name().trim());
        xContentBuilder.field(STATUS, this.status.name().trim());
        xContentBuilder.field(EXPERIMENT_ID, this.experimentId.trim());
        xContentBuilder.field(PARAMETERS, this.parameters);
        xContentBuilder.field(RESULTS, this.results);
        return xContentBuilder.endObject();
    }
}
