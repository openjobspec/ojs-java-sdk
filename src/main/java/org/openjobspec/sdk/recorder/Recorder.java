package org.openjobspec.sdk.recorder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Captures execution traces for OJS job handlers.
 * Thread-safe.
 *
 * <pre>{@code
 * Recorder recorder = new Recorder();
 * long start = System.currentTimeMillis();
 * Object result = handler.handle(args);
 * recorder.recordCall("handle", args, result, System.currentTimeMillis() - start);
 * recorder.attachSourceMap("abc123", "Handler.java", 42);
 * List<TraceEntry> trace = recorder.trace();
 * }</pre>
 */
public class Recorder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<TraceEntry> entries = new ArrayList<>();

    /** Record a successful function call. */
    public synchronized void recordCall(String funcName, Object args, Object result, long durationMs) {
        entries.add(new TraceEntry(
            funcName,
            serialize(args),
            serialize(result),
            durationMs,
            null,
            Instant.now(),
            null
        ));
    }

    /** Record a failed function call. */
    public synchronized void recordError(String funcName, Object args, Throwable error, long durationMs) {
        entries.add(new TraceEntry(
            funcName,
            serialize(args),
            "",
            durationMs,
            null,
            Instant.now(),
            error != null ? error.getMessage() : null
        ));
    }

    /** Attach source location to the most recent trace entry. */
    public synchronized void attachSourceMap(String gitSHA, String filePath, int line) {
        if (entries.isEmpty()) return;
        TraceEntry last = entries.get(entries.size() - 1);
        entries.set(entries.size() - 1, new TraceEntry(
            last.funcName(), last.args(), last.result(), last.durationMs(),
            new SourceMap(gitSHA, filePath, line),
            last.timestamp(), last.error()
        ));
    }

    /** Return a copy of all recorded trace entries. */
    public synchronized List<TraceEntry> trace() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /** Number of recorded entries. */
    public synchronized int size() {
        return entries.size();
    }

    /** Clear all recorded entries. */
    public synchronized void reset() {
        entries.clear();
    }

    private static String serialize(Object obj) {
        if (obj == null) return "null";
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }
}
