package io.zeromagic.logclustering.vector;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.e5smallv2q.E5SmallV2QuantizedEmbeddingModel;
import io.zeromagic.logclustering.input.LogEntry;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class Embedding {

    private final DimensionAwareEmbeddingModel model;
    private final Model meta;
    private int counter = 0;
    private int tokenCount = 0;

    public enum Model {
        E5SmallV2Quantized(E5SmallV2QuantizedEmbeddingModel::new, "query:"),
        BGESmall1_5Quantized(BgeSmallEnV15QuantizedEmbeddingModel::new, "Represent this sentence for searching relevant passages: ");

        private final Supplier<DimensionAwareEmbeddingModel> factory;
        private final String prefix;

        Model(Supplier<DimensionAwareEmbeddingModel> factory, String prefix) {
            this.factory = factory;
            this.prefix = prefix;
        }

        DimensionAwareEmbeddingModel create() {
            return factory.get();
        }
    }

    public Embedding(Model model) {
        this.meta = model;
        this.model = model.create();
    }

    public EmbeddingVector process(LogEntry logEntry) {
        var text = makeQuery(logEntry);
        var result = model.embed(text.toString());
        tokenCount += result.tokenUsage().inputTokenCount();
        return new EmbeddingVector(result.content().vector(), logEntry);
    }

    public List<EmbeddingVector> processBatch(List<LogEntry> batch) {
        var segments = batch.stream().map(this::makeQuery).map(TextSegment::from)
                .toList();
        var result = model.embedAll(segments);
        tokenCount += result.tokenUsage().inputTokenCount();
        return IntStream.range(0, batch.size())
                .mapToObj(i -> new EmbeddingVector(result.content().get(i).vector(), batch.get(i)))
                .toList();
    }

    private String makeQuery(LogEntry logEntry) {
        // small progress indicator, because this is slow on CPU.
        if (++counter % 100 == 0) {
            System.out.printf("Processed entries: %d, token count: %d\n", counter, tokenCount);
        }
        var m = logEntry.metadata();
        StringBuilder text = new StringBuilder()
                .append(meta.prefix)
                .append(logEntry.body())
                .append("-- ").append(m.get(LogEntry.MetadataKeys.LEVEL)).append(" logmessage of [")
                .append(m.get(LogEntry.MetadataKeys.POD))
                .append("] at ").append(m.get(LogEntry.MetadataKeys.TIMESTAMP))
                .append(" by [").append(m.get("LoggerName")).append("]\n")
                .append(logEntry.exception());
        return text.toString();
    }
}
