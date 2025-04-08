/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.queryset;

import java.io.IOException;
import java.util.Objects;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.Nullable;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class PutQuerySetRequest extends ActionRequest {
    private String name;
    private String description;
    private String sampling;
    private String querySetQueries;

    public PutQuerySetRequest(String name, String description, String sampling, String querySetQueries) {
        this.name = Objects.requireNonNull(name, "name cannot be null.");
        this.description = description;
        this.sampling = Objects.requireNonNull(sampling, "sampling cannot be null.");
        this.querySetQueries = Objects.requireNonNull(querySetQueries, "querySetQueries cannot be null.");
    }

    public PutQuerySetRequest(StreamInput in) throws IOException {
        super(in);
        this.name = in.readString();
        this.description = in.readString();
        this.sampling = in.readString();
        this.querySetQueries = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(name);
        out.writeString(description);
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
