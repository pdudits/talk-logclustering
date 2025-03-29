package io.zeromagic.logclustering.input;

import java.util.List;

public interface Tokenizer {
    Iterable<String> tokenize(String text);

    Tokenizer SIMPLE = text -> List.of(text.split("\\W+"));
}
