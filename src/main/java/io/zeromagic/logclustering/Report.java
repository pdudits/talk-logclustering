package io.zeromagic.logclustering;

import io.zeromagic.logclustering.input.LogEntry;
import io.zeromagic.logclustering.naivecluster.Cluster;
import io.zeromagic.logclustering.naivecluster.Metric;

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
    private final Metric<T> metric;

    public Report(List<Cluster<T>> clustering, Function<T, LogEntry> entryExtractor, Metric<T> metric) {
        this.clustering = new ArrayList<>(clustering);
        this.entryExtractor = entryExtractor;
        this.metric = metric;
        Collections.sort(this.clustering, Comparator.comparingInt(c -> c.members().size()));
    }

    public void report(Path output, int maxExamples, double sampleRate) throws IOException {
        // create and output basic statistics such as:
        // number of messages, number of clusters, percentile distribution of cluster sizes

        var reportFile = output.resolve("report.txt");
        try (var reportWriter = Files.newBufferedWriter(reportFile)) {
            writeStats(reportWriter);
            writeStats(System.out);

            var index = 1;
            for (var cluster : clustering.reversed()) {
                // pick at most sampleRate..maxExamples random examples from the cluster
                var samples = Math.max(1, Math.min(Math.round(cluster.members().size() * sampleRate), maxExamples));

                var writer = reportWriter;

                writeExamples(cluster, samples, writer, clustering.size()-index, cluster.distributionStats(metric));
                index++;
            }
        }
    }
    
    private void writeStats(Appendable out) throws IOException {
        var totalMessages = clustering.stream().mapToInt(c -> c.members().size()).sum();
        var totalClusters = clustering.size();
        var percentiles = new int[10];
        for (int i = 0; i < percentiles.length; i++) {
            var index = (int) Math.round((i + 1) * 0.1 * (clustering.size() - 1));
            percentiles[i] = clustering.get(index).members().size();
        }
        var entryPercentile = new int[10];
        int runningTotal = 0;
        var c = 0;
        for (var cluster : clustering) {
            runningTotal += cluster.members().size();
            int index = (totalMessages - runningTotal) * entryPercentile.length / totalMessages;
            for (int i = index; i < entryPercentile.length; i++) {
                entryPercentile[i]++;
            }
        }

        var top9clusterSizes = clustering.reversed().stream().limit(9).mapToInt(v -> v.members().size()).toArray();
        var restSize = totalMessages - IntStream.of(top9clusterSizes).sum();

        out.append("Total messages: %8d\n".formatted(totalMessages))
                .append("Total clusters: %8d\n\n".formatted(totalClusters))
                .append("| Percentile | Cluster Size | Number of clusters |\n")
                .append("|------------|--------------|--------------------|\n");

        for (int i = 0; i < percentiles.length; i++) {
            out.append("| %9d%% | %12d | %18d |\n".formatted((i + 1) * 10, percentiles[i], entryPercentile[i]));
        }

        // Finally print out 9 largest clusters
        out.append("\n").append("""
            | Rank | Cluster Size |
            |------|--------------|\n""");
        for(int i = 0; i < top9clusterSizes.length; i++) {
            out.append("| %4d | %12d |\n".formatted(i+1, top9clusterSizes[i]));
        }
        out.append("| rest | %12d |\n".formatted(restSize));
    }

    public void outputClusterMappings(Path output) throws IOException {
        // output a csv with clusterIndex, entryIndex
        try (var writer = Files.newBufferedWriter(output.resolve("cluster-mappings.csv"))) {
            writer.append("ClusterIndex,EntryIndex\n");
            for (int i = 0; i < clustering.size(); i++) {
                var cluster = clustering.get(i);
                for (int j = 0; j < cluster.members().size(); j++) {
                    var entry = entryExtractor.apply(cluster.members().get(j));
                    writer.append(String.valueOf(i)).append(",").append(entry.metadata().get("EntryIndex")).append("\n");
                }
            }
        }
    }

    private void writeExamples(Cluster<T> cluster, long samples, Appendable writer, int index, Cluster.Stats stats) throws IOException {
        // todo: min and max timestamp, do we just assume that input is sorted by time?
        var minTimestamp = cluster.members().stream().map(entryExtractor).map(e -> e.metadata().get("Timestamp"))
                .min(Comparator.naturalOrder()).orElse("n/a");
        var maxTimestamp = cluster.members().stream().map(entryExtractor).map(e -> e.metadata().get("Timestamp"))
                .max(Comparator.naturalOrder()).orElse("n/a");
        writer.append("\nCluster %d\n".formatted(index))
              .append("Number of entries: %d\n".formatted(cluster.members().size()));
        writer.append("First timestamp:   %s\nLast timestamp:    %s\n".formatted(
                minTimestamp,
                maxTimestamp));
        writer.append("Avg/ StdDev / Max: %f / %f / %f = %.2f*stdev+avg\n----------------------------------------------------------\n\n".formatted(
                stats.average(), stats.stdDev(), stats.max(), (stats.max()-stats.average())/stats.stdDev()));
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
