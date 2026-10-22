/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.stats.info;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.opensearch.Version;
import org.opensearch.searchrelevance.stats.common.StatName;

import lombok.Getter;

/**
 * Enum that contains all info stat names, paths, and types
 */
@Getter
public enum InfoStatName implements StatName {
    // Cluster info
    CLUSTER_VERSION("cluster_version", "", InfoStatType.INFO_STRING, Version.V_3_1_0),;

    private final String nameString;
    private final String path;
    private final InfoStatType statType;
    private final Version version;

    private static final Map<String, InfoStatName> BY_NAME = Arrays.stream(values())
        .collect(Collectors.toMap(stat -> stat.nameString, stat -> stat));

    /**
     * Constructor
     * @param nameString the unique name of the stat.
     * @param path the unique path of the stat
     * @param statType the category of stat
     */
    InfoStatName(String nameString, String path, InfoStatType statType, Version version) {
        this.nameString = nameString;
        this.path = path;
        this.statType = statType;
        this.version = version;
    }

    /**
     * Gets the StatName associated with a unique string name
     * @throws IllegalArgumentException if stat name does not exist
     * @param name the string name of the stat
     * @return the StatName enum associated with that String name
     */
    public static InfoStatName from(String name) {
        if (isValidName(name) == false) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "Info stat not found: %s", name));
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
