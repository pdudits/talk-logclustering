package io.zeromagic.logclustering.input;

import java.util.Map;

public interface LogEntry {
    String body();
    String exception();
    Map<String, String> metadata();

    interface MetadataKeys {
        String LEVEL = "Level";
        String LOGGER_NAME = "LoggerName";
        String POD = "Pod";
        String TIMESTAMP = "Timestamp";
    }
}
