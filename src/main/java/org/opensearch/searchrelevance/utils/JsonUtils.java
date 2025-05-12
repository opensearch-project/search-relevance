/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;

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

        try {
            return AccessController.doPrivileged((PrivilegedAction<T>) () -> {
                try {
                    return objectMapper.readValue(json, clazz);
                } catch (IOException e) {
                    // Rethrow as a RuntimeException, so it's caught outside doPrivileged.
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            // Unwrap the RuntimeException to get the original IOException.
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new IOException("Error deserializing JSON: " + e.getMessage(), e);
        }

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

        try {
            return AccessController.doPrivileged((PrivilegedAction<String>) () -> {
                try {
                    return objectMapper.writeValueAsString(object);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new IOException("Error serializing object to JSON: " + e.getMessage(), e);
        }
    }

}
