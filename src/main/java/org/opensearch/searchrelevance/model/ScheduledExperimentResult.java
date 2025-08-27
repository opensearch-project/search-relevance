package org.opensearch.searchrelevance.model;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ScheduledExperimentResult implements ToXContentObject {
    public static final String ID_FIELD = "id";
    public static final String EXPERIMENT_ID_FIELD = "experimentId";
    public static final String JOB_ID_FIELD = "jobId";
    public static final String JOB_INDEX_NAME_FIELD = "jobIndexName";
    public static final String START_TIME_FIELD = "startTime";
    public static final String END_TIME_FIELD = "endTime";
    public static final String RESULTS_FIELD = "indexNameToWatch";

    private final String id;
    private final String experimentId;
    private final String jobId;
    private final String jobIndexName;
    private final String startTime;
    private final String endTime;
    private final List<Map<String, Object>> results;

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(ID_FIELD, this.id)
            .field(EXPERIMENT_ID_FIELD, this.experimentId)
            .field(JOB_ID_FIELD, this.jobId);
        if (this.startTime != null) {
            builder.field(START_TIME_FIELD, this.startTime);
        }
        if (this.endTime != null) {
            builder.field(END_TIME_FIELD, this.endTime);
        }
        builder.field(RESULTS_FIELD, this.results);
        builder.endObject();
        return builder;
    }
}
