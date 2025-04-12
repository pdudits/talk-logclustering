package io.zeromagic.logclustering.vector;

import io.zeromagic.logclustering.input.LogEntry;
import io.zeromagic.logclustering.input.Tokenizer;

import java.util.ArrayList;
import java.util.TreeMap;

// this is about 20% faster than the original TermVector
public class OptimizedTermVector {
    private final LogEntry source;
    private final String[] terms;
    private final int[] freqs;
    private double magnitude = -1;

    public OptimizedTermVector(Iterable<String> terms, LogEntry source) {
        var tv = new TreeMap<String, Integer>();
        for (String term : terms) {
            tv.compute(term, (k, v) -> v == null ? 1 : v + 1);
        }
        this.terms = new String[tv.size()];
        this.freqs = new int[tv.size()];
        int i = 0;
        for (var entry : tv.entrySet()) {
            this.terms[i] = entry.getKey().intern();
            this.freqs[i] = entry.getValue();
            i++;
        }
        this.source = source;
    }

    public double dotProduct(OptimizedTermVector other) {
        double result = 0;
        int j = 0;
        for (int i = 0; i < terms.length; i++) {
            var term = terms[i];
            while (j < other.terms.length && term != other.terms[j] && term.compareTo(other.terms[j]) > 0) {
                j++;
            }
            if (j == other.terms.length) {
                break;
            }
            // strings are interned therefore == is safe
            if (term == other.terms[j]) {
                result += freqs[i] * other.freqs[j];
            }
        }
        return result;
    }

    public double magnitude() {
        if (magnitude > 0) {
            return magnitude;
        }
        double result = 0;
        for(int i=0; i<terms.length; i++) {
            result += freqs[i] * freqs[i];
        }
        magnitude = Math.sqrt(result);
        return magnitude;
    }

    public LogEntry source() {
       return source;
    }

    public double cosineDistance(OptimizedTermVector other) {
        return 1 - dotProduct(other) / (magnitude() * other.magnitude());
    }

    public static OptimizedTermVector of(LogEntry entry, Tokenizer tokenizer) {
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
        return new OptimizedTermVector(allTerms, entry);
    }

}
