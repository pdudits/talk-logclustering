package io.zeromagic.logclustering.naivecluster;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.DoubleStream;

public record Cluster<VEC>(VEC leader, List<VEC> members) {
    public static <T> Cluster<T> of(T leader) {
        return new Cluster<>(leader, new CopyOnWriteArrayList<>(List.of(leader)));
    }

    public record Stats(double average, double max, double stdDev) {}

    public Stats distributionStats(Metric<VEC> metric) {
        var distances = members.stream().mapToDouble(m -> metric.distance(leader, m)).toArray();
        var average = DoubleStream.of(distances).average().orElse(0);
        var max = DoubleStream.of(distances).max().orElse(0);
        var stdDev = Math.sqrt(DoubleStream.of(distances).map(d -> Math.pow(d - average, 2)).sum() / distances.length);
        return new Stats(average, max, stdDev);
    }
}
