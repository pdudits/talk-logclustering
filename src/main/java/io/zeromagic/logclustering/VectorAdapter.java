package io.zeromagic.logclustering;

import io.zeromagic.logclustering.input.LogEntry;

import java.util.List;

/**
 * An adapter between log entry and the vector representation used in the pipeline.
 *
 * @param <VEC> The type of the vector representation produced by the pipeline.
 */
interface VectorAdapter<VEC> {
    /**
     * Vectorize single Log Entry
     * @param entry
     * @return
     */
    VEC vectorize(LogEntry entry);

    /**
     * Vectorize a batch of Log Entries
     * @param batch
     * @return
     */
    default List<VEC> vectorizeBatch(List<LogEntry> batch) {
        return batch.stream().map(this::vectorize).toList();
    }

    /**
     * Compute the distance between two vectors by metric relevant for grouping.
     * @param vec1
     * @param vec2
     * @return
     */
    double distance(VEC vec1, VEC vec2);

    /**
     * Extract entry back from a vector
     * @param vec
     * @return original Log entry this vector represents
     */
    LogEntry entry(VEC vec);

    /**
     * Clustering threshold for the pipeline
     * @return clustering threshold
     */
    double threshold();
}
