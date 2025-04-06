package io.zeromagic.logclustering.input;

import jakarta.json.Json;
import jakarta.json.JsonObject;
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
    private static final JsonObject EMPTY = Json.createObjectBuilder().build();

    public interface ThrowingConsumer<X extends Throwable> {
        void accept(LogEntry entry) throws X;
    }
    public static <X extends Throwable> int process(Reader input, ThrowingConsumer<X> consumer) throws X {
        var parser = PARSER_FACTORY.createParser(input);
        var index = new AtomicInteger(0);
        while (parser.hasNext()) {
            var event = parser.next();
            if (event == JsonParser.Event.START_OBJECT) {
                var object = parser.getObject();
                var th = EMPTY;
                var thString=object.getString("Throwable");
                if (thString.startsWith("{")) {
                    var p = PARSER_FACTORY
                            .createParser(new StringReader(thString));
                    var evt = p.next();
                    if (evt == JsonParser.Event.START_OBJECT) {
                        th = p.getObject();
                    }
                }
                var throwable = th; // make this effectively final
                var entryIndex = index.getAndIncrement();
                consumer.accept(new LogEntry() {

                    @Override
                    public String body() {
                        // out specific query uses "None" as a placeholder for no message or no exception
                        if ("None".equals(object.getString("LogMessage")) && throwable.containsKey("Exception")) {
                            return throwable.getString("Exception");
                        }
                        return object.getString("LogMessage");
                    }

                    @Override
                    public String exception() {
                        if (throwable.containsKey("StackTrace")) {
                            return throwable.getString("StackTrace");
                        }
                        return null;
                    }

                    @Override
                    public Map<String, String> metadata() {
                        return Map.of("Level", object.getString("Level"),
                                "LoggerName", object.getString("LoggerName"),
                                "Pod", object.getString("Name"),
                                "Timestamp", object.getString("Timestamp"),
                                "EntryIndex", String.valueOf(entryIndex));
                    }
                });
            }
        }
        return index.get();
    }
}
