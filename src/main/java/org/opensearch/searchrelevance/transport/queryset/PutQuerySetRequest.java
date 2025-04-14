/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.queryset;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.Nullable;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import reactor.util.annotation.NonNull;

/**
 * Put Request supports sampling as manual, when querySetQueries is provided.
 */
public class PutQuerySetRequest extends ActionRequest {
    private final String name;
    private final String description;
    private final String sampling;
    private final String querySetQueries;

    public PutQuerySetRequest(@NonNull String name, String description, @NonNull String sampling, @NonNull String querySetQueries) {
        this.name = name;
        this.description = description;
        this.sampling = sampling;
        this.querySetQueries = querySetQueries;
    }

    public PutQuerySetRequest(StreamInput in) throws IOException {
        super(in);
        this.name = in.readString();
        this.description = in.readOptionalString();
        this.sampling = in.readString();
        this.querySetQueries = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(name);
        out.writeOptionalString(description);
        out.writeString(sampling);
        out.writeString(querySetQueries);
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

    public String getQuerySetQueries() {
        return querySetQueries;
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
