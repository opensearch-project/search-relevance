/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments.queryhash;

import java.util.HashMap;
import java.util.Map;

/**
 * Facilitates the hashing of user queries.
 */
public class IncrementalUserQueryHash implements UserQueryHash {

    private final Map<String, Integer> userQueries;
    private int count = 1;

    /**
     * Creates a new instance of this class.
     */
    public IncrementalUserQueryHash() {
        this.userQueries = new HashMap<>();
    }

    @Override
    public int getHash(String userQuery) {

        final int hash;

        if (userQueries.containsKey(userQuery)) {

            return userQueries.get(userQuery);

        } else {

            userQueries.put(userQuery, count);
            hash = count;
            count++;

        }

        return hash;

    }

}
