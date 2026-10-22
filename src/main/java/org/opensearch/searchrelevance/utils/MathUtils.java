/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

public class MathUtils {

    private MathUtils() {

    }

    public static String round(final double value, final int decimalPlaces) {
        double factor = Math.pow(10, decimalPlaces);
        return String.valueOf(Math.round(value * factor) / factor);
    }

    public static String round(final double value) {
        return round(value, 3);
    }

}
