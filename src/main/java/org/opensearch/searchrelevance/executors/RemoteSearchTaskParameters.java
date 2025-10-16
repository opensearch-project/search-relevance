/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Parameters for scheduling a remote search variant task
 */
@Getter
@SuperBuilder
public class RemoteSearchTaskParameters extends VariantTaskParameters {
    private final String remoteConfigId;
}
