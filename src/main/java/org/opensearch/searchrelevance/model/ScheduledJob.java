/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;
import java.time.Instant;

import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.jobscheduler.spi.schedule.Schedule;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ScheduledJob implements ToXContentObject {
    public static final String ID = "id";
    public static final String NAME_FIELD = "name";
    public static final String ENABLED_FILED = "enabled";
    public static final String LAST_UPDATE_TIME_FIELD = "lastUpdateTime";
    public static final String LAST_UPDATE_TIME_FIELD_READABLE = "lastUpdateTimeField";
    public static final String SCHEDULE_FIELD = "schedule";
    public static final String ENABLED_TIME_FIELD = "enabledTime";
    public static final String ENABLED_TIME_FIELD_READABLE = "enabledTimeField";
    public static final String INDEX_NAME_FIELD = "indexNameToWatch";
    public static final String LOCK_DURATION_SECONDS = "lockDurationSeconds";
    public static final String JITTER = "jitter";
    public static final String EXPERIMENT_ID = "experimentId";

    private final String id;
    private final String jobName;
    private final Instant lastUpdateTime;
    private final Instant enabledTime;
    private final boolean isEnabled;
    private final Schedule schedule;
    private final String indexToWatch;
    private final Long lockDurationSeconds;
    private final Double jitter;
    private final String experimentId;

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(ID, this.id)
            .field(NAME_FIELD, this.jobName)
            .field(ENABLED_FILED, this.isEnabled)
            .field(SCHEDULE_FIELD, this.schedule)
            .field(INDEX_NAME_FIELD, this.indexToWatch);
        if (this.enabledTime != null) {
            builder.timeField(ENABLED_TIME_FIELD, ENABLED_TIME_FIELD_READABLE, this.enabledTime.toEpochMilli());
        }
        if (this.lastUpdateTime != null) {
            builder.timeField(LAST_UPDATE_TIME_FIELD, LAST_UPDATE_TIME_FIELD_READABLE, this.lastUpdateTime.toEpochMilli());
        }
        if (this.lockDurationSeconds != null) {
            builder.field(LOCK_DURATION_SECONDS, this.lockDurationSeconds);
        }
        if (this.jitter != null) {
            builder.field(JITTER, this.jitter);
        }
        if (this.experimentId != null) {
            builder.field(EXPERIMENT_ID, this.experimentId);
        }
        builder.endObject();
        return builder;
    }
}
