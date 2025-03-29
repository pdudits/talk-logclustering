package io.zeromagic.logclustering;

import io.zeromagic.logclustering.input.JsonArrayInput;
import io.zeromagic.logclustering.input.Tokenizer;
import io.zeromagic.logclustering.naivecluster.NaiveClustering;
import io.zeromagic.logclustering.simple.TermVector;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) throws IOException {
        var input = Path.of("../sensitive.data/o4.json");
        var output = Path.of("target/clusters/");

        try (var in = new FileReader(input.toFile())) {
            var clustering = new NaiveClustering<TermVector>(TermVector::cosineDistance, 0.4);
            JsonArrayInput.process(in, e -> clustering.add(TermVector.of(e, Tokenizer.SIMPLE)));

            new Report(clustering.getClusters()).report(output, 20, 0.2);
        }
    }
}
