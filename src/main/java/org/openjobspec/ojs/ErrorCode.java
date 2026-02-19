package org.openjobspec.ojs;

/**
 * Standardized OJS error codes as defined in the OJS SDK Error Catalog
 * ({@code spec/ojs-error-catalog.md}). Each code maps to a canonical wire-format
 * string code from the OJS Error Specification.
 *
 * @param code          the OJS-XXXX numeric identifier (e.g., "OJS-1000")
 * @param name          human-readable error name (e.g., "InvalidPayload")
 * @param canonicalCode SCREAMING_SNAKE_CASE wire-format code, or empty for client-side errors
 * @param httpStatus    default HTTP status code, or 0 for client-side errors
 * @param message       default human-readable description
 * @param retryable     default retryability
 */
public enum ErrorCode {

    // -----------------------------------------------------------------------
    // OJS-1xxx: Client Errors
    // -----------------------------------------------------------------------

    INVALID_PAYLOAD("OJS-1000", "InvalidPayload", "INVALID_PAYLOAD", 400, "Job envelope fails structural validation", false),
    INVALID_JOB_TYPE("OJS-1001", "InvalidJobType", "INVALID_JOB_TYPE", 400, "Job type is not registered or does not match the allowlist", false),
    INVALID_QUEUE("OJS-1002", "InvalidQueue", "INVALID_QUEUE", 400, "Queue name is invalid or does not match naming rules", false),
    INVALID_ARGS("OJS-1003", "InvalidArgs", "INVALID_ARGS", 400, "Job args fail type checking or schema validation", false),
    INVALID_METADATA("OJS-1004", "InvalidMetadata", "INVALID_METADATA", 400, "Metadata field is malformed or exceeds the 64 KB size limit", false),
    INVALID_STATE_TRANSITION("OJS-1005", "InvalidStateTransition", "INVALID_STATE_TRANSITION", 409, "Attempted an invalid lifecycle state change", false),
    INVALID_RETRY_POLICY("OJS-1006", "InvalidRetryPolicy", "INVALID_RETRY_POLICY", 400, "Retry policy configuration is invalid", false),
    INVALID_CRON_EXPRESSION("OJS-1007", "InvalidCronExpression", "INVALID_CRON_EXPRESSION", 400, "Cron expression syntax cannot be parsed", false),
    SCHEMA_VALIDATION_FAILED("OJS-1008", "SchemaValidationFailed", "SCHEMA_VALIDATION_FAILED", 422, "Job args do not conform to the registered schema", false),
    PAYLOAD_TOO_LARGE("OJS-1009", "PayloadTooLarge", "PAYLOAD_TOO_LARGE", 413, "Job envelope exceeds the server's maximum payload size", false),
    METADATA_TOO_LARGE("OJS-1010", "MetadataTooLarge", "METADATA_TOO_LARGE", 413, "Metadata field exceeds the 64 KB limit", false),
    CONNECTION_ERROR("OJS-1011", "ConnectionError", "", 0, "Could not establish a connection to the OJS server", true),
    REQUEST_TIMEOUT("OJS-1012", "RequestTimeout", "", 0, "HTTP request to the OJS server timed out", true),
    SERIALIZATION_ERROR("OJS-1013", "SerializationError", "", 0, "Failed to serialize the request or deserialize the response", false),
    QUEUE_NAME_TOO_LONG("OJS-1014", "QueueNameTooLong", "QUEUE_NAME_TOO_LONG", 400, "Queue name exceeds the 255-byte maximum length", false),
    JOB_TYPE_TOO_LONG("OJS-1015", "JobTypeTooLong", "JOB_TYPE_TOO_LONG", 400, "Job type exceeds the 255-byte maximum length", false),
    CHECKSUM_MISMATCH("OJS-1016", "ChecksumMismatch", "CHECKSUM_MISMATCH", 400, "External payload reference checksum verification failed", false),
    UNSUPPORTED_COMPRESSION("OJS-1017", "UnsupportedCompression", "UNSUPPORTED_COMPRESSION", 400, "The specified compression codec is not supported", false),

    // -----------------------------------------------------------------------
    // OJS-2xxx: Server Errors
    // -----------------------------------------------------------------------

    BACKEND_ERROR("OJS-2000", "BackendError", "BACKEND_ERROR", 500, "Internal backend storage or transport failure", true),
    BACKEND_UNAVAILABLE("OJS-2001", "BackendUnavailable", "BACKEND_UNAVAILABLE", 503, "Backend storage system is unreachable", true),
    BACKEND_TIMEOUT("OJS-2002", "BackendTimeout", "BACKEND_TIMEOUT", 504, "Backend operation timed out", true),
    REPLICATION_LAG("OJS-2003", "ReplicationLag", "REPLICATION_LAG", 500, "Operation failed due to replication consistency issue", true),
    INTERNAL_SERVER_ERROR("OJS-2004", "InternalServerError", "", 500, "Unclassified internal server error", true),

    // -----------------------------------------------------------------------
    // OJS-3xxx: Job Lifecycle Errors
    // -----------------------------------------------------------------------

    JOB_NOT_FOUND("OJS-3000", "JobNotFound", "NOT_FOUND", 404, "The requested job, queue, or resource does not exist", false),
    DUPLICATE_JOB("OJS-3001", "DuplicateJob", "DUPLICATE_JOB", 409, "Unique job constraint was violated", false),
    JOB_ALREADY_COMPLETED("OJS-3002", "JobAlreadyCompleted", "JOB_ALREADY_COMPLETED", 409, "Operation attempted on a job that has already completed", false),
    JOB_ALREADY_CANCELLED("OJS-3003", "JobAlreadyCancelled", "JOB_ALREADY_CANCELLED", 409, "Operation attempted on a job that has already been cancelled", false),
    QUEUE_PAUSED("OJS-3004", "QueuePaused", "QUEUE_PAUSED", 422, "The target queue is paused and not accepting new jobs", true),
    HANDLER_ERROR("OJS-3005", "HandlerError", "HANDLER_ERROR", 0, "Job handler threw an exception during execution", true),
    HANDLER_TIMEOUT("OJS-3006", "HandlerTimeout", "HANDLER_TIMEOUT", 0, "Job handler exceeded the configured execution timeout", true),
    HANDLER_PANIC("OJS-3007", "HandlerPanic", "HANDLER_PANIC", 0, "Job handler caused an unrecoverable error", true),
    NON_RETRYABLE_ERROR("OJS-3008", "NonRetryableError", "NON_RETRYABLE_ERROR", 0, "Error type matched non_retryable_errors in the retry policy", false),
    JOB_CANCELLED("OJS-3009", "JobCancelled", "JOB_CANCELLED", 0, "Job was cancelled while it was executing", false),
    NO_HANDLER_REGISTERED("OJS-3010", "NoHandlerRegistered", "", 0, "No handler is registered for the received job type", false),

    // -----------------------------------------------------------------------
    // OJS-4xxx: Workflow Errors
    // -----------------------------------------------------------------------

    WORKFLOW_NOT_FOUND("OJS-4000", "WorkflowNotFound", "", 404, "The specified workflow does not exist", false),
    CHAIN_STEP_FAILED("OJS-4001", "ChainStepFailed", "", 422, "A step in a chain workflow failed, halting subsequent steps", false),
    GROUP_TIMEOUT("OJS-4002", "GroupTimeout", "", 504, "A group workflow did not complete within the allowed timeout", true),
    DEPENDENCY_FAILED("OJS-4003", "DependencyFailed", "", 422, "A required dependency job failed, preventing execution", false),
    CYCLIC_DEPENDENCY("OJS-4004", "CyclicDependency", "", 400, "The workflow definition contains circular dependencies", false),
    BATCH_CALLBACK_FAILED("OJS-4005", "BatchCallbackFailed", "", 422, "The batch completion callback job failed", true),
    WORKFLOW_CANCELLED("OJS-4006", "WorkflowCancelled", "", 409, "The entire workflow was cancelled", false),

    // -----------------------------------------------------------------------
    // OJS-5xxx: Authentication & Authorization Errors
    // -----------------------------------------------------------------------

    UNAUTHENTICATED("OJS-5000", "Unauthenticated", "UNAUTHENTICATED", 401, "No authentication credentials provided or credentials are invalid", false),
    PERMISSION_DENIED("OJS-5001", "PermissionDenied", "PERMISSION_DENIED", 403, "Authenticated but lacks the required permission", false),
    TOKEN_EXPIRED("OJS-5002", "TokenExpired", "TOKEN_EXPIRED", 401, "The authentication token has expired", false),
    TENANT_ACCESS_DENIED("OJS-5003", "TenantAccessDenied", "TENANT_ACCESS_DENIED", 403, "Operation on a tenant the caller does not have access to", false),

    // -----------------------------------------------------------------------
    // OJS-6xxx: Rate Limiting & Backpressure Errors
    // -----------------------------------------------------------------------

    RATE_LIMITED("OJS-6000", "RateLimited", "RATE_LIMITED", 429, "Rate limit exceeded", true),
    QUEUE_FULL("OJS-6001", "QueueFull", "QUEUE_FULL", 429, "The queue has reached its configured maximum depth", true),
    CONCURRENCY_LIMITED("OJS-6002", "ConcurrencyLimited", "", 429, "The concurrency limit has been reached", true),
    BACKPRESSURE_APPLIED("OJS-6003", "BackpressureApplied", "", 429, "The server is applying backpressure", true),

    // -----------------------------------------------------------------------
    // OJS-7xxx: Extension Errors
    // -----------------------------------------------------------------------

    UNSUPPORTED_FEATURE("OJS-7000", "UnsupportedFeature", "UNSUPPORTED_FEATURE", 422, "Feature requires a conformance level the backend does not support", false),
    CRON_SCHEDULE_CONFLICT("OJS-7001", "CronScheduleConflict", "", 409, "The cron schedule conflicts with an existing schedule", false),
    UNIQUE_KEY_INVALID("OJS-7002", "UniqueKeyInvalid", "", 400, "The unique key specification is invalid or malformed", false),
    MIDDLEWARE_ERROR("OJS-7003", "MiddlewareError", "", 500, "An error occurred in the middleware chain", true),
    MIDDLEWARE_TIMEOUT("OJS-7004", "MiddlewareTimeout", "", 504, "A middleware handler exceeded its allowed execution time", true);

    private final String code;
    private final String displayName;
    private final String canonicalCode;
    private final int httpStatus;
    private final String message;
    private final boolean retryable;

    ErrorCode(String code, String displayName, String canonicalCode, int httpStatus, String message, boolean retryable) {
        this.code = code;
        this.displayName = displayName;
        this.canonicalCode = canonicalCode;
        this.httpStatus = httpStatus;
        this.message = message;
        this.retryable = retryable;
    }

    /** Returns the OJS-XXXX numeric identifier. */
    public String code() { return code; }

    /** Returns the human-readable error name. */
    public String displayName() { return displayName; }

    /** Returns the canonical wire-format code, or empty string for client-side errors. */
    public String canonicalCode() { return canonicalCode; }

    /** Returns the default HTTP status code, or 0 for client-side errors. */
    public int httpStatus() { return httpStatus; }

    /** Returns the default human-readable description. */
    public String message() { return message; }

    /** Returns the default retryability. */
    public boolean retryable() { return retryable; }

    /**
     * Look up an ErrorCode by its canonical wire-format code (e.g., "INVALID_PAYLOAD").
     *
     * @param canonical the SCREAMING_SNAKE_CASE code
     * @return the matching ErrorCode, or {@code null} if not found
     */
    public static ErrorCode fromCanonicalCode(String canonical) {
        if (canonical == null || canonical.isEmpty()) return null;
        for (ErrorCode ec : values()) {
            if (ec.canonicalCode.equals(canonical)) return ec;
        }
        return null;
    }

    /**
     * Look up an ErrorCode by its OJS-XXXX numeric code (e.g., "OJS-1000").
     *
     * @param ojsCode the OJS-XXXX code
     * @return the matching ErrorCode, or {@code null} if not found
     */
    public static ErrorCode fromCode(String ojsCode) {
        if (ojsCode == null || ojsCode.isEmpty()) return null;
        for (ErrorCode ec : values()) {
            if (ec.code.equals(ojsCode)) return ec;
        }
        return null;
    }
}
