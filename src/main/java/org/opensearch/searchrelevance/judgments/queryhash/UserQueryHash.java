/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments.queryhash;

/**
 * In interface for creating hashes of user queries.
 */
public interface UserQueryHash {

    /**
     * Creates a unique integer given a user query.
     * @param userQuery The user query.
     * @return A unique integer representing the user query.
     */
    int getHash(String userQuery);

}
