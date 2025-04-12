package io.zeromagic.logclustering.naivecluster;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public record Cluster<T>(T leader, List<T> members) {
    public static <T> Cluster<T> of(T leader) {
        return new Cluster<>(leader, new CopyOnWriteArrayList<>(List.of(leader)));
    }

    public record Stats(double average, double max, double stdDev) {}

    public Stats distributionStats(Metric<T> metric) {
        var distances = new ArrayList<Double>();
        for (var member : members) {
            distances.add(metric.distance(leader, member));
        }
        var average = distances.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        var max = distances.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        var stdDev = Math.sqrt(distances.stream().mapToDouble(d -> Math.pow(d - average, 2)).sum() / distances.size());
        return new Stats(average, max, stdDev);
    }
}
