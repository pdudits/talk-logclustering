package io.zeromagic.logclustering.vector;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.e5smallv2q.E5SmallV2QuantizedEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import io.zeromagic.logclustering.input.LogEntry;

import java.net.http.HttpClient;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class Embedding {

    private final DimensionAwareEmbeddingModel model;
    private final Model meta;
    private int counter = 0;
    private int tokenCount = 0;

    public enum Model {
        E5SmallV2Quantized(E5SmallV2QuantizedEmbeddingModel::new, "query:", 0.18),
        BGESmall1_5Quantized(BgeSmallEnV15QuantizedEmbeddingModel::new,
                "Represent this sentence for searching relevant passages: ", 0.18),
        AllMiniLM_Ollama(() -> OllamaEmbeddingModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("all-minilm")
                .build(), "", 0.18),
        Nomic_Embed_NotebookLM(() -> OpenAiEmbeddingModel.builder()
                .baseUrl("http://localhost:1234/v1")
                .modelName("text-embedding-nomic-embed-text-v1.5")
                // use HTTP 1.1 only, LM Studio cannot do 2.0
                // https://github.com/langchain4j/langchain4j/issues/2758#issuecomment-2749439972
                .httpClientBuilder(JdkHttpClient.builder().httpClientBuilder(HttpClient.newBuilder().version(
                        HttpClient.Version.HTTP_1_1)))
                .build(), "clustering: ", 0.1),
        BGESmall1_5NotebookLM(() -> OpenAiEmbeddingModel.builder()
                .baseUrl("http://localhost:1234/v1")
                .modelName("text-embedding-bge-small-en")
                // use HTTP 1.1 only, LM Studio cannot do 2.0
                // https://github.com/langchain4j/langchain4j/issues/2758#issuecomment-2749439972
                .httpClientBuilder(JdkHttpClient.builder().httpClientBuilder(HttpClient.newBuilder().version(
                        HttpClient.Version.HTTP_1_1)))
                .build(), "Represent this sentence for searching relevant passages: ", 0.075),

        Nomic_Embed_Ollama(() -> OllamaEmbeddingModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("nomic-embed-text")
                .build(), "clustering: ", 0.1),

        ;

        private final Supplier<DimensionAwareEmbeddingModel> factory;
        private final String prefix;
        private final double threshold;

        Model(Supplier<DimensionAwareEmbeddingModel> factory, String prefix, double threshold) {
            this.factory = factory;
            this.prefix = prefix;
            this.threshold = threshold;
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
        if (result.tokenUsage() != null) {
            tokenCount += result.tokenUsage().inputTokenCount();
        }
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


    public double threshold() {
        return meta.threshold;
    }
}
