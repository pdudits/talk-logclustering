package io.zeromagic.logclustering.vector;

import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.e5smallv2q.E5SmallV2QuantizedEmbeddingModel;
import io.zeromagic.logclustering.input.LogEntry;

import java.util.function.Supplier;

public class EmbeddingProcess {

    private final DimensionAwareEmbeddingModel model;
    private final EmbeddingModel meta;
    private int counter = 0;
    private int tokenCount = 0;

    public enum EmbeddingModel {
        E5SmallV2Quantized(E5SmallV2QuantizedEmbeddingModel::new, "query:"),
        BGESmall1_5Quantized(BgeSmallEnV15QuantizedEmbeddingModel::new, "Represent this sentence for searching relevant passages: ");

        private final Supplier<DimensionAwareEmbeddingModel> factory;
        private final String prefix;

        EmbeddingModel(Supplier<DimensionAwareEmbeddingModel> factory, String prefix) {
            this.factory = factory;
            this.prefix = prefix;
        }

        DimensionAwareEmbeddingModel create() {
            return factory.get();
        }
    }

    public EmbeddingProcess(EmbeddingModel model) {
        this.meta = model;
        this.model = model.create();
    }

    public EmbeddingVector process(LogEntry logEntry) {
        // small progress indicator, because this is slow on CPU.
        if (++counter % 100 == 0) {
            System.out.printf("Processed entries: %d, token count: %d\n", counter, tokenCount);
        }
        var m = logEntry.metadata();
        StringBuilder text = new StringBuilder()
                .append(meta.prefix)
                .append("logmessage of ").append(m.get("Pod"))
                .append(" at ").append(m.get("TimeStamp"))
                .append(" by logger ").append(m.get("LoggerName"))
                .append("\n")
                .append(logEntry.body())
                .append(logEntry.exception());

        var result = model.embed(text.toString());
        tokenCount += result.tokenUsage().inputTokenCount();
        return new EmbeddingVector(result.content().vector(), logEntry);
    }
}
