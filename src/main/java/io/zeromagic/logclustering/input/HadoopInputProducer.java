package io.zeromagic.logclustering.input;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Input for hadoop log entries from LogPAI LogHub dataset.
 */
public class HadoopInputProducer implements InputProducer {
    private final Path source;

    public HadoopInputProducer(Path source) {
        this.source = source;
    }

    @Override
    public <X extends Throwable> int produceTo(ThrowingConsumer<X> consumer) throws IOException, X {
        return Files.walk(source)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".log"))
                .mapToInt(path -> {
                    try (var reader = Files.newBufferedReader(path)) {
                        return process(path.getFileName().toString(), reader, consumer);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                })
                .sum();
    }

    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3})\\s+(\\w+)\\s+\\[(.*?)\\]\\s+(\\S+):\\s+(.*)$");

    private static <X extends Throwable> int process(String filename, BufferedReader input, ThrowingConsumer<X> consumer) throws IOException, X {
        String line;
        LogEntryBuilder currentEntry = null;
        int lines = 0;
        while ((line = input.readLine()) != null) {
            Matcher matcher = TIMESTAMP_PATTERN.matcher(line);
            if (matcher.matches()) {
                // Push the previous entry to the consumer
                if (currentEntry != null) {
                    lines++;
                    consumer.accept(currentEntry.build());
                }

                // Start a new log entry
                currentEntry = new LogEntryBuilder()
                        .withMetadata(Map.of(
                                LogEntry.MetadataKeys.TIMESTAMP, matcher.group(1),
                                LogEntry.MetadataKeys.LEVEL, matcher.group(2),
                                LogEntry.MetadataKeys.LOGGER_NAME, matcher.group(4),
                                LogEntry.MetadataKeys.POD, filename
                        ))
                        .withBody(matcher.group(5));
            } else if (currentEntry != null) {
                // Handle exception stack trace
                if (line.isBlank()) {
                    lines++;
                    consumer.accept(currentEntry.build());
                    currentEntry = null;
                } else {
                    currentEntry.appendException(line);
                }
            }
        }

        // Push the last entry if it exists
        if (currentEntry != null) {
            lines++;
            consumer.accept(currentEntry.build());
        }
        return lines;
    }

    private static class LogEntryBuilder {
        private String body;
        private StringBuilder exception = new StringBuilder();
        private Map<String, String> metadata;

        public LogEntryBuilder withBody(String body) {
            this.body = body;
            return this;
        }

        public LogEntryBuilder appendException(String line) {
            if (exception.length() > 0) {
                exception.append("\n");
            }
            exception.append(line);
            return this;
        }

        public LogEntryBuilder withMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public LogEntry build() {
            String exceptionString = exception.length() > 0 ? exception.toString() : null;
            return new LogEntry() {
                @Override
                public String body() {
                    return body;
                }

                @Override
                public String exception() {
                    return exceptionString;
                }

                @Override
                public Map<String, String> metadata() {
                    return metadata;
                }
            };
        }
    }
}
