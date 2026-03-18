package org.openjobspec.sdk.recorder;

import java.time.Instant;

/**
 * A single recorded function call in an execution trace.
 */
public record TraceEntry(
    String funcName,
    String args,
    String result,
    long durationMs,
    SourceMap sourceMap,
    Instant timestamp,
    String error
) {
    /** Convenience constructor for successful calls. */
    public TraceEntry(String funcName, String args, String result, long durationMs) {
        this(funcName, args, result, durationMs, null, Instant.now(), null);
    }

    /** Convenience constructor for failed calls. */
    public static TraceEntry ofError(String funcName, String args, String error, long durationMs) {
        return new TraceEntry(funcName, args, "", durationMs, null, Instant.now(), error);
    }
}
