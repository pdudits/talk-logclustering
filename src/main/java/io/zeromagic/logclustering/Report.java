package io.zeromagic.logclustering;

import io.zeromagic.logclustering.naivecluster.Cluster;
import io.zeromagic.logclustering.simple.TermVector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

public class Report {
    private final List<Cluster<TermVector>> clustering;

    public Report(List<Cluster<TermVector>> clustering) {
        this.clustering = clustering;
    }

    public void report(Path output, int maxExamples, double sampleRate) throws IOException {
        Files.createDirectories(output);
        var index = 0;
        for (var cluster : clustering) {
            //file name is 3-digit cluster size and then the index
            var fileName = String.format("%04d-%03d.txt", cluster.members().size(), index++);

            try (var writer = Files.newBufferedWriter(output.resolve(fileName))) {
                // pick at most sampleRate..maxExamples random examples from the cluster
                var random = new Random();
                var indices = IntStream.generate(() -> random.nextInt(cluster.members().size()))
                        .distinct()
                        .limit(Math.max(1,Math.min((long)(cluster.members().size() * sampleRate), maxExamples)))
                        .toArray();
                Arrays.stream(indices).mapToObj(i -> cluster.members().get(i)).forEach(tv -> {
                    try {
                        write(writer, tv);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

            }
        }
    }

    private void write(Appendable out, TermVector termVector) throws IOException {
        var e = termVector.source();
        var meta = e.metadata();
        out.append(meta.get("Pod")).append(" ");
        out.append("[").append(meta.get("Timestamp")).append("] ")
         .append(meta.get("Level")).append(" ")
                .append(meta.get("LoggerName")).append(" : ")
                .append(e.body()).append("\n");
        if (e.exception() != null) {
            out.append(e.exception()).append("\n");
        }
        out.append("\n");
    }
}
