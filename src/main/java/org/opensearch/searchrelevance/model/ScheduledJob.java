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
    public static final String ENABLED_FIELD = "enabled";
    public static final String LAST_UPDATE_TIME_FIELD = "lastUpdateTime";
    public static final String LAST_UPDATE_TIME_FIELD_READABLE = "lastUpdateTimeField";
    public static final String SCHEDULE_FIELD = "schedule";
    public static final String ENABLED_TIME_FIELD = "enabledTime";
    public static final String ENABLED_TIME_FIELD_READABLE = "enabledTimeField";
    public static final String TIME_STAMP = "timestamp";

    private final String id;
    private final Instant lastUpdateTime;
    private final Instant enabledTime;
    private final boolean isEnabled;
    private final Schedule schedule;
    private final String timestamp;

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(ID, this.id).field(ENABLED_FIELD, this.isEnabled).field(SCHEDULE_FIELD, this.schedule);
        if (this.enabledTime != null) {
            builder.timeField(ENABLED_TIME_FIELD, ENABLED_TIME_FIELD_READABLE, this.enabledTime.toEpochMilli());
        }
        if (this.lastUpdateTime != null) {
            builder.timeField(LAST_UPDATE_TIME_FIELD, LAST_UPDATE_TIME_FIELD_READABLE, this.lastUpdateTime.toEpochMilli());
        }
        builder.field(TIME_STAMP, timestamp);
        builder.endObject();
        return builder;
    }
}
