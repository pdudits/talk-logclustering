package io.zeromagic.logclustering.naivecluster;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NaiveClustering<T> {
    private final Metric<T> metric;
    private final double threshold;
    private List<Cluster<T>> clusters = new ArrayList<>();

    public NaiveClustering(Metric<T> metric, double threshold) {
        this.metric = metric;
        this.threshold = threshold;
    }

    public synchronized void add(T member) {
        var bestMatch = clusters.parallelStream()
                .filter(c -> metric.distance(c.leader(), member) < threshold)
                .sorted(Comparator.comparingDouble(c -> metric.distance(c.leader(), member)))
                .findFirst();
        if (bestMatch.isPresent()) {
            bestMatch.get().members().add(member);
        } else {
            clusters.add(Cluster.of(member));
        }
    }

    public List<Cluster<T>> getClusters() {
        return clusters;
    }
}
