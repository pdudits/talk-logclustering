package io.zeromagic.logclustering;

import io.zeromagic.logclustering.input.LogEntry;
import io.zeromagic.logclustering.vector.Embedding;
import io.zeromagic.logclustering.vector.EmbeddingVector;

import java.io.IOException;
import java.util.List;

class EmbeddingVectorAdapter implements VectorAdapter<EmbeddingVector> {
    private final Embedding model;
    private final EmbeddingOutput out;

    public EmbeddingVectorAdapter(Embedding model, EmbeddingOutput out) {
        this.model = model;
        this.out = out;
    }

    @Override
    public EmbeddingVector vectorize(LogEntry entry) {
        var v = model.process(entry);
        writeEmbedding(v);
        return v;
    }

    private void writeEmbedding(EmbeddingVector v) {
        try {
            out.write(v, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<EmbeddingVector> vectorizeBatch(List<LogEntry> batch) {
        var embeddingBatch = model.processBatch(batch);
        embeddingBatch.forEach(this::writeEmbedding);
        return embeddingBatch;
    }

    @Override
    public double distance(EmbeddingVector vec1, EmbeddingVector vec2) {
        return vec1.cosineDistance(vec2);
    }

    @Override
    public LogEntry entry(EmbeddingVector vec) {
        return vec.entry();
    }

    @Override
    public double threshold() {
        return model.threshold();
    }
}
