package io.zeromagic.logclustering.naivecluster;

public interface Metric<T> {
    double distance(T a, T b);
}
