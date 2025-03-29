package io.zeromagic.logclustering.naivecluster;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public record Cluster<T>(T leader, List<T> members) {
    public static <T> Cluster<T> of(T leader) {
        return new Cluster<>(leader, new CopyOnWriteArrayList<>(List.of(leader)));
    }
}
