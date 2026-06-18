/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;
import java.util.Map;

import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * ABTestSnapshot represents a versioned snapshot of an AB test's state
 * before an update, stored for audit trail purposes.
 */
@AllArgsConstructor
@Getter
public class ABTestSnapshot implements ToXContentObject {
    public static final String DOC_ID = "doc_id";
    public static final String TEST_ID = "test_id";
    public static final String RECORD = "record";
    public static final String CREATED = "created";

    private final String docId;
    private final String testId;
    private final Map<String, Object> record;
    private final String created;

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(DOC_ID, this.docId);
        xContentBuilder.field(TEST_ID, this.testId);
        xContentBuilder.field(RECORD, this.record);
        xContentBuilder.field(CREATED, this.created);
        return xContentBuilder.endObject();
    }
}
