package io.zeromagic.logclustering;

import io.zeromagic.logclustering.input.JsonArrayInput;
import io.zeromagic.logclustering.input.LogEntry;
import io.zeromagic.logclustering.input.Tokenizer;
import io.zeromagic.logclustering.naivecluster.Metric;
import io.zeromagic.logclustering.naivecluster.NaiveClustering;
import io.zeromagic.logclustering.vector.EmbeddingProcess;
import io.zeromagic.logclustering.vector.EmbeddingVector;
import io.zeromagic.logclustering.vector.TermVector;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) throws IOException {
        var input = Path.of("../sensitive.data/o4.json");
        var output = Path.of("target/clusters/");

        try (var in = new FileReader(input.toFile())) {
            // termVectorProcess(in, output.resolve("target/termvector-clusters"));
            embeddingProcess(in, output.resolve("target/embedding-clusters"));
        }
    }

    static void termVectorProcess(FileReader input, Path output) throws IOException {
        process(input, output, new Process<TermVector>() {

            @Override
            public TermVector process(LogEntry entry) {
                return TermVector.of(entry, Tokenizer.SIMPLE);
            }

            @Override
            public double distance(TermVector t1, TermVector t2) {
                return t1.cosineDistance(t2);
            }

            @Override
            public LogEntry entry(TermVector termVector) {
                return termVector.source();
            }

            @Override
            public double threshold() {
                return 0.3;
            }
        });
    }
    static void embeddingProcess(FileReader input, Path output) throws IOException {
        var model = new EmbeddingProcess();
        process(input, output, new Process<EmbeddingVector>() {
            @Override
            public EmbeddingVector process(LogEntry entry) {
                return model.process(entry);
            }

            @Override
            public double distance(EmbeddingVector t1, EmbeddingVector t2) {
                return t1.cosineDistance(t2);
            }

            @Override
            public LogEntry entry(EmbeddingVector t) {
                return t.entry();
            }

            @Override
            public double threshold() {
                return 0.08;
            }
        });
    }

    static <T> void process(FileReader input, Path output,
                            Process<T> process) throws IOException {
        var clustering = new NaiveClustering<>(process::distance, process.threshold());
        var start = Instant.now();
        JsonArrayInput.process(input, e -> CompletableFuture.supplyAsync(
                () -> process.process(e)).thenAccept(clustering::add));
        ForkJoinPool.commonPool().awaitQuiescence(1, TimeUnit.MINUTES);
        var end = Instant.now();
        System.out.format("Clustering took %s\n", Duration.between(start, end));
        new Report<>(clustering.getClusters(), process::entry).report(output, 20, 0.2);
    }

    interface Process<T> {
        T process(LogEntry entry);
        double distance(T t1, T t2);
        LogEntry entry(T t);
        double threshold();
    }
}
