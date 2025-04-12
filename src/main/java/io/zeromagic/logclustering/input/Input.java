package io.zeromagic.logclustering.input;

import java.io.IOException;
import java.util.function.Consumer;

public interface Input {
    void produceTo(Consumer<LogEntry> consumer) throws IOException;
}
