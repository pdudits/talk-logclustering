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
        process(input, output, e -> TermVector.of(e, Tokenizer.SIMPLE), TermVector::cosineDistance, TermVector::source);
    }
    static void embeddingProcess(FileReader input, Path output) throws IOException {
        var model = new EmbeddingProcess();
        process(input, output, model::process, EmbeddingVector::cosineDistance, EmbeddingVector::entry);
    }

    static <T> void process(FileReader input, Path output,
                            Function<LogEntry, T> processor,
                            Metric<T> metric,
                            Function<T, LogEntry> extractor) throws IOException {
        var clustering = new NaiveClustering<>(metric, 0.3);
        var start = Instant.now();
        JsonArrayInput.process(input, e -> clustering.add(processor.apply(e)));
        var end = Instant.now();
        System.out.format("Clustering took %s\n", Duration.between(start, end));
        new Report<>(clustering.getClusters(), extractor).report(output, 20, 0.2);
    }

}
