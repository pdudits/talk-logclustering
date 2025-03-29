package io.zeromagic.logclustering.vector;

import dev.langchain4j.model.embedding.onnx.e5smallv2.E5SmallV2EmbeddingModel;
import io.zeromagic.logclustering.input.LogEntry;

public class EmbeddingProcess {

    private final E5SmallV2EmbeddingModel model;
    private int counter = 0;

    public EmbeddingProcess() {
        this.model = new E5SmallV2EmbeddingModel();
    }

    public EmbeddingVector process(LogEntry logEntry) {
        // small progress indicator, because this is slow on CPU.
        if (++counter % 100 == 0) {
            System.out.println(counter);
        }
        var m = logEntry.metadata();
        StringBuilder text = new StringBuilder()
                .append("query: ")
                .append("logmessage of ").append(m.get("Pod"))
                .append(" at ").append(m.get("TimeStamp"))
                .append(" by logger ").append(m.get("LoggerName"))
                .append("\n")
                .append(logEntry.body())
                .append(logEntry.exception());

        var result = model.embed(text.toString()).content();
        return new EmbeddingVector(result.vector(), logEntry);
    }
}
