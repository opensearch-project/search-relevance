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

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.NonNull;

/**
 * Put Request for LLM-based query set generation.
 */
public class PutLlmQuerySetRequest extends PutQuerySetRequest {
    private final String index;
    private final String modelId;
    private final int numberOfQueryTerms;
    private final List<String> contextFields;
    private final List<String> categories;

    public PutLlmQuerySetRequest(
        @NonNull String name,
        String description,
        @NonNull String sampling,
        @NonNull String index,
        @NonNull String modelId,
        int numberOfQueryTerms,
        @NonNull List<String> contextFields,
        @NonNull List<String> categories
    ) {
        super(name, description, sampling, Collections.emptyList());
        this.index = index;
        this.modelId = modelId;
        this.numberOfQueryTerms = numberOfQueryTerms;
        this.contextFields = contextFields;
        this.categories = categories;
    }

    public PutLlmQuerySetRequest(StreamInput in) throws IOException {
        super(in);
        this.index = in.readString();
        this.modelId = in.readString();
        this.numberOfQueryTerms = in.readInt();
        this.contextFields = in.readStringList();
        this.categories = in.readStringList();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(index);
        out.writeString(modelId);
        out.writeInt(numberOfQueryTerms);
        out.writeStringCollection(contextFields);
        out.writeStringCollection(categories);
    }

    public String getIndex() {
        return index;
    }

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
}
