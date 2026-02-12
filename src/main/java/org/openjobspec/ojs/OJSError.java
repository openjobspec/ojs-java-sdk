package org.openjobspec.ojs;

import java.util.Map;

/**
 * Structured error types for the OJS SDK.
 *
 * <p>Uses sealed interface hierarchy to represent different error categories
 * while maintaining a common structure for error code, message, and details.
 */
public sealed interface OJSError {

    String code();
    String message();
    boolean retryable();

    // Error code constants
    String CODE_HANDLER_ERROR = "handler_error";
    String CODE_TIMEOUT = "timeout";
    String CODE_CANCELLED = "cancelled";
    String CODE_INVALID_PAYLOAD = "invalid_payload";
    String CODE_INVALID_REQUEST = "invalid_request";
    String CODE_NOT_FOUND = "not_found";
    String CODE_BACKEND_ERROR = "backend_error";
    String CODE_RATE_LIMITED = "rate_limited";
    String CODE_DUPLICATE = "duplicate";
    String CODE_QUEUE_PAUSED = "queue_paused";
    String CODE_SCHEMA_VALIDATION = "schema_validation";
    String CODE_UNSUPPORTED = "unsupported";
    String CODE_CONFLICT = "conflict";

    /** API/transport error returned by the OJS server. */
    record ApiError(
            String code,
            String message,
            boolean retryable,
            Map<String, Object> details,
            String requestId,
            int httpStatus
    ) implements OJSError {
        @Override
        public String toString() {
            var sb = new StringBuilder("ojs: ").append(code).append(": ").append(message);
            if (requestId != null && !requestId.isEmpty()) {
                sb.append(" (request_id=").append(requestId).append(")");
            }
            return sb.toString();
        }
    }

    /** Client-side validation error. */
    record ValidationError(String code, String message) implements OJSError {
        @Override
        public boolean retryable() { return false; }

        @Override
        public String toString() {
            return "ojs: validation: " + message;
        }
    }

    /** Transport/network error. */
    record TransportError(String code, String message, Throwable cause) implements OJSError {
        @Override
        public boolean retryable() { return true; }

        @Override
        public String toString() {
            return "ojs: transport: " + message;
        }
    }

    /** OJS exception wrapping an OJSError. */
    final class OJSException extends RuntimeException {
        private final OJSError error;

        public OJSException(OJSError error) {
            super(error.toString());
            this.error = error;
            if (error instanceof TransportError te && te.cause() != null) {
                initCause(te.cause());
            }
        }

        public OJSError error() {
            return error;
        }

        public String code() {
            return error.code();
        }

        public boolean isRetryable() {
            return error.retryable();
        }

        public boolean isNotFound() {
            return CODE_NOT_FOUND.equals(error.code());
        }

        public boolean isDuplicate() {
            return CODE_DUPLICATE.equals(error.code());
        }

        public boolean isConflict() {
            return CODE_CONFLICT.equals(error.code());
        }

        public boolean isRateLimited() {
            return CODE_RATE_LIMITED.equals(error.code());
        }
    }
}
