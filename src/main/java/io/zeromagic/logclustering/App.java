package io.zeromagic.logclustering;

import io.zeromagic.logclustering.input.JsonArrayInput;
import io.zeromagic.logclustering.input.Tokenizer;
import io.zeromagic.logclustering.naivecluster.NaiveClustering;
import io.zeromagic.logclustering.simple.TermVector;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) throws IOException {
        var input = Path.of("../sensitive.data/o4.json");
        var output = Path.of("target/clusters/");

        try (var in = new FileReader(input.toFile())) {
            var clustering = new NaiveClustering<>(TermVector::cosineDistance, 0.3);
            var start = Instant.now();
            JsonArrayInput.process(in, e -> clustering.add(TermVector.of(e, Tokenizer.SIMPLE)));
            var end = Instant.now();

            System.out.format("Clustering took %s\n", Duration.between(start, end));

            new Report<>(clustering.getClusters(), TermVector::source).report(output, 20, 0.2);
        }
    }
}
