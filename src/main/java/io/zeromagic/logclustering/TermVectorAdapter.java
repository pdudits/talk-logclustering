package io.zeromagic.logclustering;

import io.zeromagic.logclustering.input.LogEntry;
import io.zeromagic.logclustering.input.Tokenizer;
import io.zeromagic.logclustering.vector.OptimizedTermVector;

enum TermVectorAdapter implements VectorAdapter<OptimizedTermVector> {
    INSTANCE;

    @Override
    public OptimizedTermVector vectorize(LogEntry entry) {
        return OptimizedTermVector.of(entry, Tokenizer.SIMPLE);
    }

    @Override
    public double distance(OptimizedTermVector vec1, OptimizedTermVector vec2) {
        return vec1.cosineDistance(vec2);
    }

    @Override
    public LogEntry entry(OptimizedTermVector vec) {
        return vec.source();
    }

    @Override
    public double threshold() {
        return 0.35;
    }
}
