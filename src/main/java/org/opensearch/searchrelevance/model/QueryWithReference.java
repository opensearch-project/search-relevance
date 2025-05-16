/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;
import java.util.Objects;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;

public class QueryWithReference implements Writeable {
    private final String queryText;
    private final String referenceAnswer;

    public final static String DELIMITER = "#";

    public QueryWithReference(String queryText, String referenceAnswer) {
        this.queryText = queryText;
        this.referenceAnswer = referenceAnswer;
    }

    public QueryWithReference(StreamInput in) throws IOException {
        this.queryText = in.readString();
        this.referenceAnswer = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(queryText);
        out.writeString(referenceAnswer);
    }

    public String getQueryText() {
        return queryText;
    }

    public String getReferenceAnswer() {
        return referenceAnswer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueryWithReference that = (QueryWithReference) o;
        return Objects.equals(queryText, that.queryText) && Objects.equals(referenceAnswer, that.referenceAnswer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryText, referenceAnswer);
    }

    @Override
    public String toString() {
        return "QueryWithReference{" + "queryText='" + queryText + '\'' + ", referenceAnswer='" + referenceAnswer + '\'' + '}';
    }
}
