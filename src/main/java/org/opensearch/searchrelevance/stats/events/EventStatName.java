/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.stats.events;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.opensearch.Version;
import org.opensearch.searchrelevance.stats.common.StatName;

import lombok.Getter;

/**
 * Enum that contains all event stat names, paths, and types
 */
@Getter
public enum EventStatName implements StatName {
    IMPORT_JUDGMENT_RATING_GENERATIONS(
        "import_judgment_rating_generations",
        "judgments",
        EventStatType.TIMESTAMPED_EVENT_COUNTER,
        Version.V_3_1_0
    ),
    LLM_JUDGMENT_RATING_GENERATIONS(
        "llm_judgment_rating_generations",
        "judgments",
        EventStatType.TIMESTAMPED_EVENT_COUNTER,
        Version.V_3_1_0
    ),
    UBI_JUDGMENT_RATING_GENERATIONS(
        "ubi_judgment_rating_generations",
        "judgments",
        EventStatType.TIMESTAMPED_EVENT_COUNTER,
        Version.V_3_1_0
    ),
    EXPERIMENT_EXECUTIONS("experiment_executions", "experiments", EventStatType.TIMESTAMPED_EVENT_COUNTER, Version.V_3_1_0),
    EXPERIMENT_PAIRWISE_COMPARISON_EXECUTIONS(
        "experiment_pairwise_comparison_executions",
        "experiments",
        EventStatType.TIMESTAMPED_EVENT_COUNTER,
        Version.V_3_1_0
    ),
    EXPERIMENT_POINTWISE_EVALUATION_EXECUTIONS(
        "experiment_pointwise_evaluation_executions",
        "experiments",
        EventStatType.TIMESTAMPED_EVENT_COUNTER,
        Version.V_3_1_0
    ),
    EXPERIMENT_HYBRID_OPTIMIZER_EXECUTIONS(
        "experiment_hybrid_optimizer_executions",
        "experiments",
        EventStatType.TIMESTAMPED_EVENT_COUNTER,
        Version.V_3_1_0
    ),;

    private final String nameString;
    private final String path;
    private final EventStatType statType;
    private EventStat eventStat;
    private final Version version;

    /**
     * Enum lookup table by nameString
     */
    private static final Map<String, EventStatName> BY_NAME = Arrays.stream(values())
        .collect(Collectors.toMap(stat -> stat.nameString, stat -> stat));

    /**
     * Constructor
     * @param nameString the unique name of the stat.
     * @param path the unique path of the stat
     * @param statType the category of stat
     */
    EventStatName(String nameString, String path, EventStatType statType, Version version) {
        this.nameString = nameString;
        this.path = path;
        this.statType = statType;
        this.version = version;

        switch (statType) {
            case EventStatType.TIMESTAMPED_EVENT_COUNTER:
                eventStat = new TimestampedEventStat(this);
                break;
        }

        // Validates all event stats are instantiated correctly. This is covered by unit tests as well.
        if (eventStat == null) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Unable to initialize event stat [%s]. Unrecognized event stat type: [%s]", nameString, statType)
            );
        }
    }

    /**
     * Gets the StatName associated with a unique string name
     * @throws IllegalArgumentException if stat name does not exist
     * @param name the string name of the stat
     * @return the StatName enum associated with that String name
     */
    public static EventStatName from(String name) {
        if (isValidName(name) == false) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "Event stat not found: %s", name));
        }
        return BY_NAME.get(name);
    }

    /**
     * Gets the full dot notation path of the stat, defining its location in the response body
     * @return the destination dot notation path of the stat value
     */
    public String getFullPath() {
        if (path == null || path.isBlank()) {
            return nameString;
        }
        return String.join(".", path, nameString);
    }

    /**
     * Determines whether a given string is a valid stat name
     * @param name name of the stat
     * @return whether the name is valid
     */
    public static boolean isValidName(String name) {
        return BY_NAME.containsKey(name);
    }

    /**
     * Gets the version the stat was added
     * @return the version the stat was added
     */
    public Version version() {
        return this.version;
    }

    @Override
    public String toString() {
        return getNameString();
    }
}
