/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;

import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * ABTest represents a system index object that stores the configuration for an A/B test
 * pairing two search configurations for Team Draft Interleaving evaluation.
 */
@AllArgsConstructor
@Getter
public class ABTest implements ToXContentObject {
    public static final String TEST_ID = "test_id";
    public static final String SEARCH_CONFIGURATION_A = "search_configuration_a";
    public static final String SEARCH_CONFIGURATION_B = "search_configuration_b";
    public static final String CONFIG_A_UUID = "config_a_uuid";
    public static final String CONFIG_B_UUID = "config_b_uuid";
    public static final String ENABLED = "enabled";
    public static final String VERSION = "version";
    public static final String CREATED_AT = "created_at";
    public static final String UPDATED_AT = "updated_at";

    private final String testId;
    private final String searchConfigurationA;
    private final String searchConfigurationB;
    private final String configAUuid;
    private final String configBUuid;
    private final boolean enabled;
    private final int version;
    private final String createdAt;
    private final String updatedAt;

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(TEST_ID, this.testId);
        xContentBuilder.field(SEARCH_CONFIGURATION_A, this.searchConfigurationA);
        xContentBuilder.field(SEARCH_CONFIGURATION_B, this.searchConfigurationB);
        xContentBuilder.field(CONFIG_A_UUID, this.configAUuid);
        xContentBuilder.field(CONFIG_B_UUID, this.configBUuid);
        xContentBuilder.field(ENABLED, this.enabled);
        xContentBuilder.field(VERSION, this.version);
        xContentBuilder.field(CREATED_AT, this.createdAt);
        xContentBuilder.field(UPDATED_AT, this.updatedAt);
        return xContentBuilder.endObject();
    }
}
