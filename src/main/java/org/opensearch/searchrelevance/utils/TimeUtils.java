/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * This is a utility class.
 */
public class TimeUtils {

    /**
     * Generate a timestamp in the <code>yyyy-MM-ddTHH:mm:ss.SSSZ</code> format.
     * @return A timestamp in the <code>yyyy-MM-ddTHH:mm:ss.SSSZ</code> format.
     */
    public static String getTimestamp() {

        final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));

        final Date date = new Date();
        return formatter.format(date);

    }

    private TimeUtils() {

    }

}
