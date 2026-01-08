/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.queryset;

import static org.opensearch.searchrelevance.common.PluginConstants.UBI_QUERIES_INDEX;

import java.io.IOException;
import java.util.Objects;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.Nullable;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

/**
 * Create Request supports sampling from ubi queries.
 */
public class PostQuerySetRequest extends ActionRequest {
    private String name;
    private String description;
    private String sampling;
    private int querySetSize;
    private String ubiQueriesIndex;

    public PostQuerySetRequest(String name, String description, String sampling, int querySetSize, String ubiQueriesIndex) {
        this.name = Objects.requireNonNull(name, "name cannot be null.");
        this.description = description;
        this.sampling = Objects.requireNonNull(sampling, "sampling cannot be null.");
        this.querySetSize = Objects.requireNonNull(querySetSize, "querySetSize cannot be null.");
        this.ubiQueriesIndex = ubiQueriesIndex != null ? ubiQueriesIndex : UBI_QUERIES_INDEX;
    }

    public PostQuerySetRequest(StreamInput in) throws IOException {
        super(in);
        this.name = in.readString();
        this.description = in.readString();
        this.sampling = in.readString();
        this.querySetSize = in.readInt();
        this.ubiQueriesIndex = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(name);
        out.writeString(description);
        out.writeString(sampling);
        out.writeInt(querySetSize);
        out.writeString(ubiQueriesIndex);
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public String getSampling() {
        return sampling;
    }

    public int getQuerySetSize() {
        return querySetSize;
    }

    public String getUbiQueriesIndex() {
        return ubiQueriesIndex;
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
