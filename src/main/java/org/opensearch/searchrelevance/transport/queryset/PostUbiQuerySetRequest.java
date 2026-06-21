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

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.NonNull;

/**
 * Create Request for UBI-based query sets with UBI queries index.
 */
public class PostUbiQuerySetRequest extends PostQuerySetRequest {
    private final String ubiQueriesIndex;

    public PostUbiQuerySetRequest(
        @NonNull String name,
        String description,
        @NonNull String sampling,
        int querySetSize,
        String ubiQueriesIndex
    ) {
        super(name, description, sampling, querySetSize);
        // Default to standard UBI queries index if not specified.
        // Index existence and required fields (user_query) are validated at cluster level
        // in PostQuerySetTransportAction before query sampling begins.
        this.ubiQueriesIndex = ubiQueriesIndex != null ? ubiQueriesIndex : UBI_QUERIES_INDEX;
    }

    public PostUbiQuerySetRequest(StreamInput in) throws IOException {
        super(in);
        this.ubiQueriesIndex = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(ubiQueriesIndex);
    }

    public String getUbiQueriesIndex() {
        return ubiQueriesIndex;
    }
}
