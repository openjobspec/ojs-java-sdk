package org.openjobspec.ojs.transport;

import java.util.Map;

/**
 * Transport interface for OJS server communication.
 *
 * <p>Abstracts the HTTP communication layer, enabling alternative implementations
 * for testing or non-HTTP transports.
 */
public interface Transport {

    String OJS_CONTENT_TYPE = "application/openjobspec+json";
    String OJS_VERSION = "1.0.0-rc.1";
    String BASE_PATH = "/ojs/v1";

    /**
     * Perform an HTTP GET request.
     *
     * @param path the path (relative to base URL + BASE_PATH)
     * @return the parsed response body as a map
     */
    Map<String, Object> get(String path);

    /**
     * Perform an HTTP POST request.
     *
     * @param path the path (relative to base URL + BASE_PATH)
     * @param body the request body (will be serialized to JSON)
     * @return the parsed response body as a map
     */
    Map<String, Object> post(String path, Map<String, Object> body);

    /**
     * Perform an HTTP DELETE request.
     *
     * @param path the path (relative to base URL + BASE_PATH)
     * @return the parsed response body as a map
     */
    Map<String, Object> delete(String path);
}
