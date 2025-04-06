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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
        var input = Path.of("../sensitive.data/25-03.json");

        var timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm").format(OffsetDateTime.now());
        try (var in = new FileReader(input.toFile())) {
            termVectorProcess(in, Path.of("target/termvector-clusters_" + timestamp + "/"));
        }
        try (var in = new FileReader(input.toFile())) {
            //embeddingProcess(in, Path.of("target/embedding-cluster_" + timestamp + "/"));
        }
    }

    static void termVectorProcess(FileReader input, Path output) throws IOException, InterruptedException, ExecutionException {
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

    static void embeddingProcess(FileReader input, Path output) throws IOException, InterruptedException, ExecutionException {
        prepareOutputDirectory(output);
        var model = new EmbeddingProcess(EmbeddingProcess.EmbeddingModel.BGESmall1_5Quantized);
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
                public List<EmbeddingVector> batchProcess(List<LogEntry> batch) {
                    return model.processBatch(batch);
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
                    return 0.08;
                }
            });
        }
    }

    static <T> void process(FileReader input, Path output,
                            Process<T> process) throws IOException, InterruptedException, ExecutionException {
        var processor = new BatchProcessor<T>(process, 16);
        processor.run(input, output);
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

        default List<T> batchProcess(List<LogEntry> batch) {
            return batch.stream().map(this::process).toList();
        }

        double distance(T t1, T t2);

        LogEntry entry(T t);

        double threshold();
    }

    record BatchProcessor<T>(Process<T> process, int batchSize) {
        void run(FileReader input, Path output) throws IOException, InterruptedException, ExecutionException {
            var clustering = new NaiveClustering<>(process::distance, process.threshold());
            var entryQueue = new ArrayBlockingQueue<LogEntry>(batchSize);
            var processQueue = new ArrayBlockingQueue<List<T>>(batchSize);
            var executors = Executors.newVirtualThreadPerTaskExecutor();
            var start = Instant.now();

            var parseTask = executors.submit(() ->  JsonArrayInput.process(
                    input, entryQueue::put));

            // feed input into a queue for possible parallel or batch processing
            var batchTask = executors.submit(() -> {
                var buffer = new ArrayList<LogEntry>(batchSize);
                int items = 0;
                while (true) {
                    var entry = entryQueue.poll(40, TimeUnit.MILLISECONDS);
                    if (entry != null) {
                        buffer.add(entry);
                    }
                    if ((entry == null && !buffer.isEmpty()) || buffer.size() == batchSize) {
                        items += buffer.size();
                        processQueue.put(process.batchProcess(buffer));
                        buffer.clear();
                    }
                    if (entry == null && parseTask.isDone() && entryQueue.isEmpty()) {
                        return items;
                    }
                }
            });

            // collect batches and submit to processor
            // then cluster results in single thread
            var clusterTask = executors.submit(() -> {
                int items = 0;
                while (true) {
                    var batch = processQueue.poll(40, TimeUnit.MILLISECONDS);
                    if (batch == null) {
                        if (batchTask.isDone() && processQueue.isEmpty()) {
                            return items;
                        }
                    } else {
                        items += batch.size();
                        batch.forEach(clustering::add);
                    }
                }
            });

            var clusteredItems = clusterTask.get();
            var end = Instant.now();
            System.out.format("Clustering took %s\n", Duration.between(start, end));
            System.out.println("Total messages: " + parseTask.get());
            System.out.println("Total batched messages: " + batchTask.get());
            System.out.println("Total clustered messages: " + clusteredItems);
            var report = new Report<>(clustering.getClusters(), process::entry);
            report.report(output, 20, 0.2);
            report.outputClusterMappings(output);
        }
    }
}
