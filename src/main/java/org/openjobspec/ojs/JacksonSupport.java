package org.openjobspec.ojs;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Optional Jackson support. Only used when Jackson is on the classpath.
 * This class is loaded lazily — if Jackson is absent, it will fail only
 * when {@link #requireMapper()} is called.
 */
final class JacksonSupport {

    private static final ObjectMapper MAPPER;

    static {
        var mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        MAPPER = mapper;
    }

    private JacksonSupport() {}

    /**
     * Get the shared ObjectMapper instance.
     *
     * @throws IllegalStateException if Jackson is not on the classpath
     */
    static ObjectMapper requireMapper() {
        try {
            return MAPPER;
        } catch (NoClassDefFoundError e) {
            throw new IllegalStateException(
                    "Jackson (com.fasterxml.jackson.databind) is required for TypedJobHandler. " +
                    "Add jackson-databind to your dependencies.", e);
        }
    }

    /**
     * Check if Jackson is available on the classpath.
     *
     * @return true if Jackson ObjectMapper can be loaded
     */
    static boolean isAvailable() {
        try {
            Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Serialize an object to a JSON string.
     *
     * @param value the object to serialize
     * @return the JSON string representation
     * @throws java.io.IOException if serialization fails
     */
    static String serialize(Object value) throws java.io.IOException {
        return requireMapper().writeValueAsString(value);
    }

    /**
     * Deserialize a JSON string to the specified type.
     *
     * @param json  the JSON string
     * @param clazz the target class
     * @param <T>   the target type
     * @return the deserialized object
     * @throws java.io.IOException if deserialization fails
     */
    static <T> T deserialize(String json, Class<T> clazz) throws java.io.IOException {
        return requireMapper().readValue(json, clazz);
    }

    /**
     * Deserialize a specific field from a JSON object string.
     *
     * @param json  the JSON string containing the object
     * @param field the field name to extract
     * @param clazz the target class for the field value
     * @param <T>   the target type
     * @return the deserialized field value, or null if the field is missing
     * @throws java.io.IOException if deserialization fails
     */
    static <T> T deserializeField(String json, String field, Class<T> clazz) throws java.io.IOException {
        var tree = requireMapper().readTree(json);
        var node = tree.get(field);
        if (node == null || node.isNull()) return null;
        return requireMapper().treeToValue(node, clazz);
    }
}
