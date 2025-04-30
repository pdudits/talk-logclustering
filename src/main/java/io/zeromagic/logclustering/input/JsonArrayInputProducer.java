package io.zeromagic.logclustering.input;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;
import jakarta.json.stream.JsonParsingException;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public record JsonArrayInputProducer(Path source, Function<String,String> stackTraceStemmer) implements InputProducer {
    private static final JsonParserFactory PARSER_FACTORY = JsonProvider.provider().createParserFactory(Map.of());
    private static final JsonObject EMPTY = Json.createObjectBuilder().build();


    @Override
    public <X extends Throwable> int produceTo(ThrowingConsumer<X> consumer) throws IOException,X {
        try (var reader = Files.newBufferedReader(source)) {
            return process(reader, consumer);
        } catch (JsonParsingException e) {
            throw new IOException("Failed to parse JSON file: " + source, e);
        } catch (Exception e) {
            throw new IOException("Failed to read JSON file: " + source, e);
        }
    }

    private <X extends Throwable> int process(Reader input, ThrowingConsumer<X> consumer) throws X {
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
                    private String stemmedStackTrace = null;

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
                        if (stemmedStackTrace != null) {
                            return stemmedStackTrace;
                        }
                        if (throwable.containsKey("StackTrace")) {
                            stemmedStackTrace = stackTraceStemmer.apply(throwable.getString("StackTrace"));
                            return stemmedStackTrace;
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
