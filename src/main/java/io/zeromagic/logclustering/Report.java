package io.zeromagic.logclustering;

import io.zeromagic.logclustering.input.LogEntry;
import io.zeromagic.logclustering.naivecluster.Cluster;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.IntStream;

public class Report<T> {
    private final List<Cluster<T>> clustering;
    private final Function<T, LogEntry> entryExtractor;
    private final Random rand = new Random();

    public Report(List<Cluster<T>> clustering, Function<T, LogEntry> entryExtractor) {
        this.clustering = new ArrayList<>(clustering);
        this.entryExtractor = entryExtractor;
        Collections.sort(this.clustering, Comparator.comparingInt(c -> c.members().size()));
    }

    public void report(Path output, int maxExamples, double sampleRate) throws IOException {
        // create and output basic statistics such as:
        // number of messages, number of clusters, percentile distribution of cluster sizes

        var totalMessages = clustering.stream().mapToInt(c -> c.members().size()).sum();
        var totalClusters = clustering.size();
        var percentiles = new int[10];
        for (int i = 0; i < percentiles.length; i++) {
            var index = (int) Math.round((i + 1) * 0.1 * (clustering.size() - 1));
            percentiles[i] = clustering.get(index).members().size();
        }

        System.out.format("Total messages: %8d\n", totalMessages)
                .format("Total clusters: %8d\n\n", totalClusters)
                .append("| Percentile | Cluster Size |\n")
                .append("|------------|--------------|\n");

        for (int i = 0; i < percentiles.length; i++) {
            System.out.format("| %9d%% | %12d |\n", (i + 1) * 10, percentiles[i]);
        }

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
        var index = 0;
        var smallClustersFile = output.resolve("0000-small-clusters.txt");
        try (var smallFilesWriter = Files.newBufferedWriter(smallClustersFile)) {
            for (var cluster : clustering) {
                // pick at most sampleRate..maxExamples random examples from the cluster
                var samples = Math.max(1, Math.min(Math.round(cluster.members().size() * sampleRate), maxExamples));

                var writer = smallFilesWriter;

                if (samples > 3) {
                    //file name is 3-digit cluster size and then the index
                    var fileName = String.format("%04d-%03d.txt", cluster.members().size(), index);
                    writer = Files.newBufferedWriter(output.resolve(fileName));
                }
                writeExamples(cluster, samples, writer, index);
                if (writer != smallFilesWriter) {
                    writer.close();
                }
                index++;
            }
        }

    }

    private void writeExamples(Cluster<T> cluster, long samples, Appendable writer, int index) throws IOException {
        writer.append("Cluster #%3d, size %6d\n-------------------------\n\n".formatted(index, cluster.members().size()));
        IntStream.generate(() -> rand.nextInt(cluster.members().size()))
                .distinct()
                .limit(samples)
                .mapToObj(i -> cluster.members().get(i))
                .forEach(member -> {
                    try {
                        write(writer, entryExtractor.apply(member));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private void write(Appendable out, LogEntry e) throws IOException {
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
