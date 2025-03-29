package io.zeromagic.logclustering.vector;

import dev.langchain4j.data.embedding.Embedding;
import io.zeromagic.logclustering.input.LogEntry;

import java.util.Objects;

public final class EmbeddingVector {
    private final float[] vector;
    private final LogEntry entry;
    private float magnitude = -1;

    public EmbeddingVector(float[] vector, LogEntry entry) {
        this.vector = vector;
        this.entry = entry;
    }

    public float magnitude() {
        if (magnitude >= 0) {
            return magnitude;
        }
        float result = 0;
        for (float v : vector) {
            result += v * v;
        }
        magnitude = (float) Math.sqrt(result);
        return magnitude;
    }

    public float dotProduct(EmbeddingVector other) {
        float result = 0;
        for (int i = 0; i < vector.length; i++) {
            result += vector[i] * other.vector[i];
        }
        return result;
    }

    public double cosineDistance(EmbeddingVector other) {
        return 1 - dotProduct(other) / (magnitude() * other.magnitude());
    }

    public float[] vector() {
        return vector;
    }

    public LogEntry entry() {
        return entry;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (EmbeddingVector) obj;
        return Objects.equals(this.vector, that.vector) &&
               Objects.equals(this.entry, that.entry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vector, entry);
    }

    @Override
    public String toString() {
        return "EmbeddingVector[" +
               "vector=" + vector + ", " +
               "entry=" + entry + ']';
    }

}
