package org.openjobspec.sdk.recorder;

/**
 * Maps a trace entry to its source code location.
 */
public record SourceMap(String gitSHA, String filePath, int line) {
}
