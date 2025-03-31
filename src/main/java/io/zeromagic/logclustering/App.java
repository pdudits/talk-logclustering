package io.zeromagic.logclustering;

import io.zeromagic.logclustering.input.JsonArrayInput;
import io.zeromagic.logclustering.input.LogEntry;
import io.zeromagic.logclustering.input.Tokenizer;
import io.zeromagic.logclustering.naivecluster.NaiveClustering;
import io.zeromagic.logclustering.vector.EmbeddingProcess;
import io.zeromagic.logclustering.vector.EmbeddingVector;
import io.zeromagic.logclustering.vector.TermVector;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) throws IOException, InterruptedException {
        var input = Path.of("../sensitive.data/o4.json");

        try (var in = new FileReader(input.toFile())) {
            var timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm").format(OffsetDateTime.now());
            termVectorProcess(in, Path.of("target/termvector-clusters_" + timestamp + "/"));
            embeddingProcess(in, Path.of("target/embedding-cluster_" + timestamp + "/"));
        }
    }

    static void termVectorProcess(FileReader input, Path output) throws IOException, InterruptedException {
        prepareOutputDirectory(output);
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
                return 0.35;
            }
        });
    }

    static void embeddingProcess(FileReader input, Path output) throws IOException, InterruptedException {
        prepareOutputDirectory(output);
        var model = new EmbeddingProcess();
        try (var embeddingFile = new FileWriter(output.resolve("embeddings.json").toFile());
             var out = new EmbeddingOutput(embeddingFile);
        ) {
            process(input, output, new Process<EmbeddingVector>() {
                @Override
                public EmbeddingVector process(LogEntry entry) {
                    var v = model.process(entry);
                    try {
                        out.write(v, null);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return v;
                }

                @Override
                public double distance(EmbeddingVector t1, EmbeddingVector t2) {
                    return t1.cosineDistance(t2);
                }

                @Override
                public LogEntry entry(EmbeddingVector embeddingVector) {
                    return embeddingVector.entry();
                }

                @Override
                public double threshold() {
                    return 0.07;
                }
            });
        }
    }

    static <T> void process(FileReader input, Path output,
                            Process<T> process) throws IOException, InterruptedException {
        var clustering = new NaiveClustering<>(process::distance, process.threshold());
        var start = Instant.now();
        var executor = Executors.newFixedThreadPool(ForkJoinPool.getCommonPoolParallelism());
        JsonArrayInput.process(input, e -> CompletableFuture.supplyAsync(
                () -> process.process(e), executor).thenAccept(clustering::add));
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.MINUTES);
        var end = Instant.now();
        System.out.format("Clustering took %s\n", Duration.between(start, end));
        var report = new Report<>(clustering.getClusters(), process::entry);
        report.report(output, 20, 0.2);
        report.outputClusterMappings(output);
    }

    static void prepareOutputDirectory(Path output) throws IOException {

        // creaete files with samples.
        Files.createDirectories(output);
        // delete any existing files
        Files.list(output).forEach(f -> {
            try {
                Files.delete(f);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    interface Process<T> {
        T process(LogEntry entry);

        double distance(T t1, T t2);

        LogEntry entry(T t);

        double threshold();
    }
}
