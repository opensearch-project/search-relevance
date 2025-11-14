/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Deserializes a JSON string into an object of the specified type.
     *
     * @param json  The JSON string to deserialize.
     * @param clazz The class of the object to deserialize into.
     * @param <T>   The type of the object to deserialize into.
     * @return The deserialized object, or null if an error occurs.
     * @throws IOException if there is an issue with the JSON string, or if there is an IO error.
     */
    public static <T> T fromJson(String json, Class<T> clazz) throws IOException {
        if (json == null || json.isEmpty() || clazz == null) {
            return null;
        }
        return objectMapper.readValue(json, clazz);
    }

    /**
     * Serializes an object into a JSON string.
     *
     * @param object The object to serialize.
     * @return The JSON string representation of the object, or null if an error occurs.
     * @throws IOException if there is an issue during serialization.
     */
    public static String toJson(Object object) throws IOException {
        if (object == null) {
            return null;
        }
        return objectMapper.writeValueAsString(object);
    }

}
