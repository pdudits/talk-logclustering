package io.zeromagic.logclustering;

import io.zeromagic.logclustering.vector.EmbeddingVector;
import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;

class EmbeddingOutput implements Closeable {

    private final JsonGenerator generator;
    private final Writer writer;

    EmbeddingOutput(Writer writer) {
        this.writer = writer;
        this.generator = Json.createGenerator(writer);
        generator.writeStartArray();
    }

    synchronized void write(EmbeddingVector vector, Integer cluster) throws IOException {
        try {
            generator.writeStartObject();
            var e = vector.entry();
            var m = e.metadata();
            generator.write("Timestamp", m.get("Timestamp"));
            generator.write("Pod", m.get("Pod"));
            generator.write("LoggerName", m.get("LoggerName"));
            generator.write("Level", m.get("Level"));
            if (e.body() != null) {
                generator.write("Message", e.body());
            }
            if (e.exception() != null) {
                generator.write("Exception", e.exception());
            }
            if (cluster != null) {
                generator.write("Cluster", cluster);
            }
            generator.writeStartArray("Vector");
            for (var f : vector.vector()) {
                generator.write(f);
            }
            generator.writeEnd();
            generator.writeEnd();
            generator.flush();
            writer.write("\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        generator.writeEnd();
        generator.close();
    }
}
