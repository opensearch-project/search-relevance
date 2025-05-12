/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import org.opensearch.searchrelevance.utils.MathUtils;

/**
 * A query result and its number of clicks and total events.
 */
public class ClickthroughRate {

    private final String objectId;
    private int clicks;
    private int impressions;

    /**
     * Creates a new clickthrough rate for an object.
     * @param objectId The ID of the object.
     */
    public ClickthroughRate(final String objectId) {
        this.objectId = objectId;
        this.clicks = 0;
        this.impressions = 0;
    }

    /**
     * Creates a new clickthrough rate for an object given counts of clicks and events.
     * @param objectId The object ID.
     * @param clicks The count of clicks.
     * @param impressions The count of events.
     */
    public ClickthroughRate(final String objectId, final int clicks, final int impressions) {
        this.objectId = objectId;
        this.clicks = clicks;
        this.impressions = impressions;
    }

    @Override
    public String toString() {
        return "object_id: "
            + objectId
            + ", clicks: "
            + clicks
            + ", events: "
            + impressions
            + ", ctr: "
            + MathUtils.round(getClickthroughRate());
    }

    /**
     * Log a click to this object.
     * This increments clicks and events.
     */
    public void logClick() {
        clicks++;
    }

    /**
     * Log an impression to this object.
     */
    public void logImpression() {
        impressions++;
    }

    /**
     * Calculate the clickthrough rate.
     * @return The clickthrough rate as clicks divided by events.
     */
    public double getClickthroughRate() {
        return (double) clicks / impressions;
    }

    /**
     * Gets the count of clicks.
     * @return The count of clicks.
     */
    public int getClicks() {
        return clicks;
    }

    /**
     * Gets the count of events.
     * @return The count of events.
     */
    public int getImpressions() {
        return impressions;
    }

    /**
     * Gets the object ID.
     * @return The object ID.
     */
    public String getObjectId() {
        return objectId;
    }

}
