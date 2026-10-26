/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.queryset;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.opensearch.Version;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.Nullable;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.searchrelevance.model.QueryWithReference;

import reactor.util.annotation.NonNull;

/**
 * Put Request for query sets. Supports both manual and LLM-based creation.
 * LLM-specific fields (index, modelId, numberOfQueryTerms, contextFields, categories)
 * are optional and only populated for LLM query set requests.
 */
public class PutQuerySetRequest extends ActionRequest {
    private final String name;
    private final String description;
    private final String sampling;
    private final List<QueryWithReference> querySetQueries;

    // LLM-specific fields (null for manual query sets)
    private final String index;
    private final String modelId;
    private final int numberOfQueryTerms;
    private final List<String> contextFields;
    private final List<String> categories;

    public PutQuerySetRequest(
        @NonNull String name,
        String description,
        @NonNull String sampling,
        @NonNull List<QueryWithReference> querySetQueries
    ) {
        this(name, description, sampling, querySetQueries, null, null, 0, Collections.emptyList(), Collections.emptyList());
    }

    public PutQuerySetRequest(
        @NonNull String name,
        String description,
        @NonNull String sampling,
        @NonNull List<QueryWithReference> querySetQueries,
        String index,
        String modelId,
        int numberOfQueryTerms,
        @NonNull List<String> contextFields,
        @NonNull List<String> categories
    ) {
        this.name = name;
        this.description = description;
        this.sampling = sampling;
        this.querySetQueries = querySetQueries;
        this.index = index;
        this.modelId = modelId;
        this.numberOfQueryTerms = numberOfQueryTerms;
        this.contextFields = contextFields;
        this.categories = categories;
    }

    public PutQuerySetRequest(StreamInput in) throws IOException {
        super(in);
        this.name = in.readString();
        this.description = in.readOptionalString();
        this.sampling = in.readString();
        this.querySetQueries = in.readList(QueryWithReference::new);
        if (in.getVersion().onOrAfter(Version.V_3_6_0)) {
            this.index = in.readOptionalString();
            this.modelId = in.readOptionalString();
            this.numberOfQueryTerms = in.readInt();
            this.contextFields = in.readStringList();
            this.categories = in.readStringList();
        } else {
            this.index = null;
            this.modelId = null;
            this.numberOfQueryTerms = 0;
            this.contextFields = Collections.emptyList();
            this.categories = Collections.emptyList();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(name);
        out.writeOptionalString(description);
        out.writeString(sampling);
        out.writeList(querySetQueries);
        if (out.getVersion().onOrAfter(Version.V_3_6_0)) {
            out.writeOptionalString(index);
            out.writeOptionalString(modelId);
            out.writeInt(numberOfQueryTerms);
            out.writeStringCollection(contextFields);
            out.writeStringCollection(categories);
        }
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

    public List<QueryWithReference> getQuerySetQueries() {
        return querySetQueries;
    }

    @Nullable
    public String getIndex() {
        return index;
    }

    @Nullable
    public String getModelId() {
        return modelId;
    }

    public int getNumberOfQueryTerms() {
        return numberOfQueryTerms;
    }

    public List<String> getContextFields() {
        return contextFields;
    }

    public List<String> getCategories() {
        return categories;
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
