/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments.clickmodel.coec;

import org.opensearch.searchrelevance.judgments.clickmodel.ClickModelParameters;

/**
 * The parameters for the {@link CoecClickModel}.
 */
public class CoecClickModelParameters extends ClickModelParameters {

    private final int maxRank;
    private int roundingDigits = 3;

    private String startDate;
    private String endDate;

    /**
     * Creates new parameters.
     * @param maxRank The max rank to use when calculating the judgments.
     */
    public CoecClickModelParameters(final int maxRank) {
        this.maxRank = maxRank;
    }

    /**
     * Creates new parameters which includes the UBI event dates to consider.
     * @param maxRank The max rank to use when calculating the judgments.
     * @param startDate The start date for filtered date range.
     * @param endDate The end date for filtered date range.
     */
    public CoecClickModelParameters(final int maxRank, final String startDate, final String endDate) {
        this.maxRank = maxRank;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * Creates new parameters.
     * @param maxRank The max rank to use when calculating the judgments.
     * @param roundingDigits The number of decimal places to round calculated values to.
     */
    public CoecClickModelParameters(final int maxRank, final int roundingDigits) {
        this.maxRank = maxRank;
        this.roundingDigits = roundingDigits;
    }

    /**
     * Gets the max rank for the implicit judgments calculation.
     * @return The max rank for the implicit judgments calculation.
     */
    public int getMaxRank() {
        return maxRank;
    }

    /**
     * Gets the number of rounding digits to use for judgments.
     * @return The number of rounding digits to use for judgments.
     */
    public int getRoundingDigits() {
        return roundingDigits;
    }

    /**
     * Gets the start date for UBI timestamp filter.
     * @return The start date for UBI timestamp filter.
     */
    public String getStartDate() {
        return startDate;
    }

    /**
     * Gets the end date for UBI timestamp filter.
     * @return The end date for UBI timestamp filter.
     */
    public String getEndDate() {
        return endDate;
    }

}
