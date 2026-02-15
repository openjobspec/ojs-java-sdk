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
}
