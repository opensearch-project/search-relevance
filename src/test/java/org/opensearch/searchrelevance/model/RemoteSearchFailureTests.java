/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.XContentBuilder;

/**
 * Tests for RemoteSearchFailure model
 */
public class RemoteSearchFailureTests extends org.apache.lucene.tests.util.LuceneTestCase {

    public void testRemoteSearchFailureCreation() {
        RemoteSearchFailure failure = new RemoteSearchFailure(
            "failure-1",
            "remote-config-1",
            "experiment-1",
            "{\"query\": {\"match\": {\"content\": \"test\"}}}",
            "test query",
            "CONNECTION_TIMEOUT",
            "Connection timed out after 30 seconds",
            "2025-01-29T10:00:00Z",
            "FAILED"
        );

        assertEquals("failure-1", failure.getId());
        assertEquals("remote-config-1", failure.getRemoteConfigId());
        assertEquals("experiment-1", failure.getExperimentId());
        assertEquals("{\"query\": {\"match\": {\"content\": \"test\"}}}", failure.getQuery());
        assertEquals("test query", failure.getQueryText());
        assertEquals("CONNECTION_TIMEOUT", failure.getErrorType());
        assertEquals("Connection timed out after 30 seconds", failure.getErrorMessage());
        assertEquals("2025-01-29T10:00:00Z", failure.getTimestamp());
        assertEquals("FAILED", failure.getStatus());
    }

    public void testRemoteSearchFailureToXContent() throws IOException {
        RemoteSearchFailure failure = new RemoteSearchFailure(
            "failure-1",
            "remote-config-1",
            "experiment-1",
            "{\"query\": {\"match\": {\"content\": \"test\"}}}",
            "test query",
            "AUTH_FAILURE",
            "Authentication failed",
            "2025-01-29T10:00:00Z",
            "FAILED"
        );

        XContentBuilder builder = failure.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), null);
        assertNotNull(builder);

        String jsonString = builder.toString();
        assertNotNull(jsonString);

        // Verify key fields are present in JSON
        assertTrue(jsonString.contains("failure-1"));
        assertTrue(jsonString.contains("remote-config-1"));
        assertTrue(jsonString.contains("experiment-1"));
        assertTrue(jsonString.contains("AUTH_FAILURE"));
        assertTrue(jsonString.contains("Authentication failed"));
    }

    public void testFromExceptionWithTimeout() {
        SocketTimeoutException timeoutException = new SocketTimeoutException("Read timed out");

        RemoteSearchFailure failure = RemoteSearchFailure.fromException(
            "failure-2",
            "config-2",
            "experiment-2",
            "{\"query\": \"test\"}",
            "test",
            timeoutException,
            "2025-01-29T10:00:00Z"
        );

        assertEquals("failure-2", failure.getId());
        assertEquals("config-2", failure.getRemoteConfigId());
        assertEquals("experiment-2", failure.getExperimentId());
        assertEquals("CONNECTION_TIMEOUT", failure.getErrorType());
        assertEquals("Read timed out", failure.getErrorMessage());
        assertEquals("FAILED", failure.getStatus());
    }

    public void testFromExceptionWithAuthFailure() {
        Exception authException = new RuntimeException("401 Unauthorized - Authentication failed");

        RemoteSearchFailure failure = RemoteSearchFailure.fromException(
            "failure-3",
            "config-3",
            "experiment-3",
            "{\"query\": \"test\"}",
            "test",
            authException,
            "2025-01-29T10:00:00Z"
        );

        assertEquals("AUTH_FAILURE", failure.getErrorType());
        assertTrue(failure.getErrorMessage().contains("Authentication failed"));
    }

    public void testFromExceptionWithNetworkError() {
        ConnectException networkException = new ConnectException("Connection refused");

        RemoteSearchFailure failure = RemoteSearchFailure.fromException(
            "failure-4",
            "config-4",
            "experiment-4",
            "{\"query\": \"test\"}",
            "test",
            networkException,
            "2025-01-29T10:00:00Z"
        );

        assertEquals("NETWORK_ERROR", failure.getErrorType());
        assertEquals("Connection refused", failure.getErrorMessage());
    }

    public void testFromExceptionWithRateLimit() {
        Exception rateLimitException = new RuntimeException("429 Too Many Requests - Rate limit exceeded");

        RemoteSearchFailure failure = RemoteSearchFailure.fromException(
            "failure-5",
            "config-5",
            "experiment-5",
            "{\"query\": \"test\"}",
            "test",
            rateLimitException,
            "2025-01-29T10:00:00Z"
        );

        assertEquals("RATE_LIMIT_EXCEEDED", failure.getErrorType());
        assertTrue(failure.getErrorMessage().contains("Rate limit exceeded"));
    }

    public void testFromExceptionWithServerError() {
        Exception serverException = new RuntimeException("500 Internal Server Error");

        RemoteSearchFailure failure = RemoteSearchFailure.fromException(
            "failure-6",
            "config-6",
            "experiment-6",
            "{\"query\": \"test\"}",
            "test",
            serverException,
            "2025-01-29T10:00:00Z"
        );

        assertEquals("REMOTE_SERVER_ERROR", failure.getErrorType());
        assertTrue(failure.getErrorMessage().contains("500"));
    }

    public void testFromExceptionWithInvalidResponse() {
        Exception parseException = new RuntimeException("Failed to parse response JSON");

        RemoteSearchFailure failure = RemoteSearchFailure.fromException(
            "failure-7",
            "config-7",
            "experiment-7",
            "{\"query\": \"test\"}",
            "test",
            parseException,
            "2025-01-29T10:00:00Z"
        );

        assertEquals("INVALID_RESPONSE", failure.getErrorType());
        assertTrue(failure.getErrorMessage().contains("parse"));
    }

    public void testFromExceptionWithUnknownError() {
        Exception unknownException = new RuntimeException("Some unexpected error");

        RemoteSearchFailure failure = RemoteSearchFailure.fromException(
            "failure-8",
            "config-8",
            "experiment-8",
            "{\"query\": \"test\"}",
            "test",
            unknownException,
            "2025-01-29T10:00:00Z"
        );

        assertEquals("UNKNOWN_ERROR", failure.getErrorType());
        assertEquals("Some unexpected error", failure.getErrorMessage());
    }

    public void testFromExceptionWithNullMessage() {
        Exception nullMessageException = new RuntimeException((String) null);

        RemoteSearchFailure failure = RemoteSearchFailure.fromException(
            "failure-9",
            "config-9",
            "experiment-9",
            "{\"query\": \"test\"}",
            "test",
            nullMessageException,
            "2025-01-29T10:00:00Z"
        );

        assertEquals("UNKNOWN_ERROR", failure.getErrorType());
        assertEquals(null, failure.getErrorMessage());
    }

    public void testRemoteSearchFailureConstants() {
        // Verify field name constants
        assertEquals("id", RemoteSearchFailure.ID);
        assertEquals("remoteConfigId", RemoteSearchFailure.REMOTE_CONFIG_ID);
        assertEquals("experimentId", RemoteSearchFailure.EXPERIMENT_ID);
        assertEquals("query", RemoteSearchFailure.QUERY);
        assertEquals("queryText", RemoteSearchFailure.QUERY_TEXT);
        assertEquals("errorType", RemoteSearchFailure.ERROR_TYPE);
        assertEquals("errorMessage", RemoteSearchFailure.ERROR_MESSAGE);
        assertEquals("timestamp", RemoteSearchFailure.TIMESTAMP);
        assertEquals("status", RemoteSearchFailure.STATUS);
    }

    public void testErrorTypeEnum() {
        // Verify all error types are available
        RemoteSearchFailure.ErrorType[] errorTypes = RemoteSearchFailure.ErrorType.values();
        assertEquals(7, errorTypes.length);

        // Verify specific error types exist
        assertEquals("CONNECTION_TIMEOUT", RemoteSearchFailure.ErrorType.CONNECTION_TIMEOUT.name());
        assertEquals("AUTH_FAILURE", RemoteSearchFailure.ErrorType.AUTH_FAILURE.name());
        assertEquals("INVALID_RESPONSE", RemoteSearchFailure.ErrorType.INVALID_RESPONSE.name());
        assertEquals("RATE_LIMIT_EXCEEDED", RemoteSearchFailure.ErrorType.RATE_LIMIT_EXCEEDED.name());
        assertEquals("REMOTE_SERVER_ERROR", RemoteSearchFailure.ErrorType.REMOTE_SERVER_ERROR.name());
        assertEquals("NETWORK_ERROR", RemoteSearchFailure.ErrorType.NETWORK_ERROR.name());
        assertEquals("UNKNOWN_ERROR", RemoteSearchFailure.ErrorType.UNKNOWN_ERROR.name());
    }

    public void testStatusEnum() {
        // Verify all status values are available
        RemoteSearchFailure.Status[] statuses = RemoteSearchFailure.Status.values();
        assertEquals(3, statuses.length);

        // Verify specific statuses exist
        assertEquals("FAILED", RemoteSearchFailure.Status.FAILED.name());
        assertEquals("RETRY_PENDING", RemoteSearchFailure.Status.RETRY_PENDING.name());
        assertEquals("RESOLVED", RemoteSearchFailure.Status.RESOLVED.name());
    }
}
