/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.exception;

import org.opensearch.OpenSearchException;
import org.opensearch.core.rest.RestStatus;

/**
 * Representations of Search Relevance Exceptions
 */
public class SearchRelevanceException extends OpenSearchException {
    private final RestStatus restStatus;

    /**
     * Constructor with error message and status code.
     * @param message error message from the exception
     * @param restStatus HTTP status code from the response
     */
    public SearchRelevanceException(String message, RestStatus restStatus) {
        super(message);
        this.restStatus = restStatus;
    }

    /**
     * Constructor with cause and status code.
     * @param cause cause from the exception
     * @param restStatus HTTP status code form the response
     */
    public SearchRelevanceException(Throwable cause, RestStatus restStatus) {
        super(cause);
        this.restStatus = restStatus;
    }

    /**
     * Constructor with error message, cause and status code
     * @param message error message from the exception
     * @param cause cause from the exception
     * @param restStatus status code from the response
     */
    public SearchRelevanceException(String message, Throwable cause, RestStatus restStatus) {
        super(message, cause);
        this.restStatus = restStatus;
    }
}
