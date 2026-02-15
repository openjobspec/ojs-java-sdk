/**
 * OJS Java SDK — Official Open Job Spec SDK for Java 21+.
 *
 * <p>Provides client and worker implementations for interacting with
 * OJS-compatible job servers, including job enqueuing, worker processing,
 * workflow orchestration, and observability middleware.
 */
module org.openjobspec.ojs {
    requires java.net.http;

    // Optional: Jackson support for TypedJobHandler
    requires static com.fasterxml.jackson.databind;

    exports org.openjobspec.ojs;
    exports org.openjobspec.ojs.transport;
    exports org.openjobspec.ojs.testing;
}
