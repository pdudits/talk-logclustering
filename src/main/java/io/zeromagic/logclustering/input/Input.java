package io.zeromagic.logclustering.input;

import java.io.IOException;
import java.util.function.Consumer;

public interface Input {
    <X extends Throwable> int produceTo(ThrowingConsumer<X> consumer) throws IOException, X;

    interface ThrowingConsumer<X extends Throwable> {
        void accept(LogEntry entry) throws X;
    }

}
