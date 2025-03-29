package io.zeromagic.logclustering.input;

import java.util.Map;

public interface LogEntry {
    String body();
    String exception();
    Map<String, String> metadata();
}
