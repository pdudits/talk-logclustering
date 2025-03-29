package io.zeromagic.logclustering.input;

import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;
import jakarta.json.stream.JsonParsingException;

import java.io.Reader;
import java.io.StringReader;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class JsonArrayInput {
    private static final JsonParserFactory PARSER_FACTORY = JsonProvider.provider().createParserFactory(Map.of());

    public static void process(Reader input, Consumer<LogEntry> consumer) {
        var parser = PARSER_FACTORY.createParser(input);
        var index = new AtomicInteger(0);
        while (parser.hasNext()) {
            var event = parser.next();
            if (event == JsonParser.Event.START_OBJECT) {
                var object = parser.getObject();
                consumer.accept(new LogEntry() {

                    @Override
                    public String body() {
                        return object.getString("LogMessage");
                    }

                    @Override
                    public String exception() {
                        if (object.containsKey("Throwable")) {
                            try {
                                var throwable=object.getString("Throwable");
                                if (!throwable.startsWith("{")) {
                                    return null;
                                }
                                var parser = PARSER_FACTORY
                                        .createParser(new StringReader(throwable));
                                var evt = parser.next();
                                var throwableObj = parser.getObject();
                                return throwableObj.getString("StackTrace");
                            } catch (JsonParsingException e) {
                                throw new IllegalArgumentException("Unexpected Throwable value of " + object.getString("Throwable"), e);
                            }
                        }
                        return null;
                    }

                    @Override
                    public Map<String, String> metadata() {
                        return Map.of("Level", object.getString("Level"),
                                "LoggerName", object.getString("LoggerName"),
                                "Pod", object.getString("Name"),
                                "Timestamp", object.getString("Timestamp"),
                                "EntryIndex", String.valueOf(index.getAndIncrement()));
                    }
                });
            }
        }
    }
}
