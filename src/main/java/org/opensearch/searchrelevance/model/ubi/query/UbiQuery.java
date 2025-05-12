/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model.ubi.query;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a UBI query.
 */
public class UbiQuery {

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("application")
    private String application;

    @JsonProperty("query_id")
    private String queryId;

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("user_query")
    private String userQuery;

    @JsonProperty("query")
    private String query;

    @JsonProperty("query_attributes")
    private Map<String, String> queryAttributes;

    @JsonProperty("query_response_id")
    private String queryResponseId;

    @JsonProperty("query_response_hit_ids")
    private List<String> queryResponseHitIds;

    /**
     * Creates a new UBI query object.
     */
    public UbiQuery() {

    }

    /**
     * Gets the timestamp for the query.
     * @return The timestamp for the query.
     */
    public String getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the timestamp for the query.
     * @param timestamp The timestamp for the query.
     */
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Gets the query ID.
     * @return The query ID.
     */
    public String getQueryId() {
        return queryId;
    }

    /**
     * Sets the query ID.
     * @param queryId The query ID.
     */
    public void setQueryId(String queryId) {
        this.queryId = queryId;
    }

    /**
     * Sets the client ID.
     * @param clientId The client ID.
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * Gets the client ID.
     * @return The client ID.
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Gets the user query.
     * @return The user query.
     */
    public String getUserQuery() {
        return userQuery;
    }

    /**
     * Sets the user query.
     * @param userQuery The user query.
     */
    public void setUserQuery(String userQuery) {
        this.userQuery = userQuery;
    }

    /**
     * Gets the query.
     * @return The query.
     */
    public String getQuery() {
        return query;
    }

    /**
     * Sets the query.
     * @param query The query.
     */
    public void setQuery(String query) {
        this.query = query;
    }

    /**
     * Sets the query attributes.
     * @return The query attributes.
     */
    public Map<String, String> getQueryAttributes() {
        return queryAttributes;
    }

    /**
     * Sets the query attributes.
     * @param queryAttributes The query attributes.
     */
    public void setQueryAttributes(Map<String, String> queryAttributes) {
        this.queryAttributes = queryAttributes;
    }

    public String getQueryResponseId() {
        return queryResponseId;
    }

    public void setQueryResponseId(String queryResponseId) {
        this.queryResponseId = queryResponseId;
    }

    public List<String> getQueryResponseHitIds() {
        return queryResponseHitIds;
    }

    public void setQueryResponseHitIds(List<String> queryResponseHitIds) {
        this.queryResponseHitIds = queryResponseHitIds;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

}
