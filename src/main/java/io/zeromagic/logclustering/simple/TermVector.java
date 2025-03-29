package io.zeromagic.logclustering.simple;

import io.zeromagic.logclustering.input.LogEntry;
import io.zeromagic.logclustering.input.Tokenizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TermVector {
    private final Map<String, Integer> termVector;
    private final LogEntry source;
    private double magnitude = -1;

    public TermVector(Iterable<String> terms, LogEntry source) {
        var tv = new HashMap<String,Integer>();
        for (String term : terms) {
            tv.compute(term, (k, v) -> v == null ? 1 : v + 1);
        }
        this.termVector = Map.copyOf(tv);
        this.source = source;
    }

    public double dotProduct(TermVector other) {
        double result = 0;
        for (var entry : termVector.entrySet()) {
            result += entry.getValue() * other.termVector.getOrDefault(entry.getKey(), 0);
        }
        return result;
    }

    public double magnitude() {
        if (magnitude > 0) {
            return magnitude;
        }
        double result = 0;
        for (var entry : termVector.entrySet()) {
            result += entry.getValue() * entry.getValue();
        }
        magnitude = Math.sqrt(result);
        return magnitude;
    }

    public LogEntry source() {
       return source;
    }

    public double cosineSimilarity(TermVector other) {
        return 1 - dotProduct(other) / (magnitude() * other.magnitude());
    }

    public static TermVector of(LogEntry entry, Tokenizer tokenizer) {
        var allTerms = new ArrayList<String>();
        if (entry.body() != null) {
            tokenizer.tokenize(entry.body()).forEach(allTerms::add);
        }
        if (entry.exception() != null) {
            tokenizer.tokenize(entry.exception()).forEach(allTerms::add);
        }
        for (var m : entry.metadata().entrySet()) {
            if (m.getKey().equals("Timestamp") || m.getKey().equals("EntryIndex")) {
                continue;
            }
            allTerms.add(m.getKey());
            tokenizer.tokenize(m.getValue()).forEach(allTerms::add);
        }
        return new TermVector(allTerms, entry);
    }

}
