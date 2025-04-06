package io.zeromagic.logclustering.naivecluster;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NaiveClustering<T> {
    private final Metric<T> metric;
    private final double threshold;
    private final List<Cluster<T>> clusters = new ArrayList<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public NaiveClustering(Metric<T> metric, double threshold) {
        this.metric = metric;
        this.threshold = threshold;
    }

    public void add(T member) {
        var bestMatch = findMatch(member);
        if (bestMatch.isPresent()) {
            bestMatch.get().members().add(member);
        } else try {
            lock.writeLock().lock();
            // Check again in case another thread added a cluster
            bestMatch = findMatch(member);
            if (bestMatch.isPresent()) {
                bestMatch.get().members().add(member);
            } else {
                clusters.add(Cluster.of(member));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Optional<Cluster<T>> findMatch(T member) {
        try {
            lock.readLock().lock();
            var bestMatch = clusters.parallelStream()
                    .min(Comparator.comparingDouble(c -> metric.distance(c.leader(), member)))
                    .filter(c -> metric.distance(c.leader(), member) < threshold);
            return bestMatch;
        } finally {
            lock.readLock().unlock();
        }
    }


    public List<Cluster<T>> getClusters() {
        return clusters;
    }
}
