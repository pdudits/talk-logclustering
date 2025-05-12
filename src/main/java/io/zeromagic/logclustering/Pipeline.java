package io.zeromagic.logclustering;

import io.zeromagic.logclustering.input.InputProducer;
import io.zeromagic.logclustering.input.LogEntry;
import io.zeromagic.logclustering.naivecluster.NaiveClustering;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

record Pipeline<T>(VectorAdapter<T> adapter, int batchSize) {
    void run(InputProducer input, Path output) throws IOException, InterruptedException, ExecutionException {
        var clustering = new NaiveClustering<>(adapter::distance, adapter.threshold());
        var entryQueue = new ArrayBlockingQueue<LogEntry>(batchSize);
        var processQueue = new ArrayBlockingQueue<List<T>>(batchSize);
        var executors = Executors.newVirtualThreadPerTaskExecutor();
        var start = Instant.now();

        var parseTask = executors.submit(() -> input.produceTo(entryQueue::put));

        // feed input into a queue for possible parallel or batch processing
        var batchTask = executors.submit(() -> {
            var buffer = new ArrayList<LogEntry>(batchSize);
            int items = 0;
            while (true) {
                var entry = entryQueue.poll(40, TimeUnit.MILLISECONDS);
                if (entry != null) {
                    buffer.add(entry);
                }
                if ((entry == null && !buffer.isEmpty()) || buffer.size() == batchSize) {
                    items += buffer.size();
                    processQueue.put(adapter.vectorizeBatch(buffer));
                    buffer.clear();
                }
                if (entry == null && parseTask.isDone() && entryQueue.isEmpty()) {
                    return items;
                }
            }
        });

        // collect batches and submit to processor
        // then cluster results in single thread
        var clusterTask = executors.submit(() -> {
            int items = 0;
            while (true) {
                var batch = processQueue.poll(40, TimeUnit.MILLISECONDS);
                if (batch == null) {
                    if (batchTask.isDone() && processQueue.isEmpty()) {
                        return items;
                    }
                } else {
                    items += batch.size();
                    batch.forEach(clustering::add);
                }
            }
        });

        var clusteredItems = clusterTask.get();
        var end = Instant.now();
        System.out.format("Clustering took %s\n", Duration.between(start, end));
        System.out.println("Total messages: " + parseTask.get());
        System.out.println("Total batched messages: " + batchTask.get());
        System.out.println("Total clustered messages: " + clusteredItems);

        // refining didn't prove to improve the results that much
        //clustering.refine(3000, 1.1);

        var report = new Report<>(clustering.getClusters(), adapter::entry, adapter::distance);
        report.report(output, 20, 0.2);
        report.outputClusterMappings(output);
    }
}
