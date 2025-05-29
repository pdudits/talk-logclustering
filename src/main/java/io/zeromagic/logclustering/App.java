package io.zeromagic.logclustering;

import io.zeromagic.logclustering.input.HadoopInputProducer;
import io.zeromagic.logclustering.input.InputProducer;
import io.zeromagic.logclustering.input.JsonArrayInputProducer;
import io.zeromagic.logclustering.vector.Embedding;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
        InputProducer in = null;
        boolean terms = false;
        boolean embeddings = false;
        Embedding.Model model = Embedding.Model.E5SmallV2Quantized;
        for(int i=0; i<args.length; i++) {
            switch (args[i]) {
                case "--terms" -> terms = true;
                case "--embeddings" -> {
                    embeddings = true;
                    if (i + 1 < args.length) {
                        try {
                            model = Embedding.Model.valueOf(args[i+1]);
                            i++;
                        } catch (IllegalArgumentException iae) {
                            // well it wasn't a model name then.
                        }
                    }
                }
                case "--hadoop" -> in = new HadoopInputProducer(Path.of(args[++i]));
                case "--loganalytics" -> in = new JsonArrayInputProducer(Path.of(args[++i]), s -> s.replaceAll("\\n\\s+at (?!fish.payara.cloud).+", ""));
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    System.exit(1);
                }
            }
        }
        var timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm").format(OffsetDateTime.now());
        if (terms) {
            termVectorPipeline(in, Path.of("target/termvector_" + timestamp + "/"));
        }
        if (embeddings) {
            embeddingPipeline(in, model, Path.of("target/embedding_" + timestamp + "/"));
        }
        if (!terms && !embeddings) {
            System.out.println("""
                    Usage: java -jar logclustering.jar [--terms|--embeddings [model name] < --hadoop <directory> | --loganalytics <json file>>
                    
                    --terms: process log entries into term vectors
                    --embeddings: process log entries into embeddings
                    --hadoop: process log entries from directory Hadoop log files
                    --loganalytics: process log entries from a JSON file in LogAnalytics format
                    
                    The output will be written to target/termvector_<timestamp> or target/embedding_<timestamp>
                    """);
            System.out.println("Valid embedding names: ");
            for (var modelName : Embedding.Model.values()) {
                System.out.println("  - " + modelName.name());
            }
            System.exit(1);
        }
    }

    static void termVectorPipeline(InputProducer input, Path output) throws IOException, InterruptedException, ExecutionException {
        prepareOutputDirectory(output);
        runPipeline(input, output, TermVectorAdapter.INSTANCE);
    }

    static void embeddingPipeline(InputProducer input, Embedding.Model modelKind, Path output) throws IOException, InterruptedException, ExecutionException {
        prepareOutputDirectory(output);
        System.out.println("Using embedding model: " + modelKind.name());
        var model = new Embedding(modelKind);
        try (var embeddingFile = new FileWriter(output.resolve("embeddings.json").toFile());
             var out = new EmbeddingOutput(embeddingFile);
        ) {
            runPipeline(input, output, new EmbeddingVectorAdapter(model, out));
        }
    }

    static <T> void runPipeline(InputProducer input, Path output,
                                VectorAdapter<T> process) throws IOException, InterruptedException, ExecutionException {
        var pipeline = new Pipeline<T>(process, 128);
        pipeline.run(input, output);
    }

    static void prepareOutputDirectory(Path output) throws IOException {

        // creaete files with samples.
        Files.createDirectories(output);
        // delete any existing files
        Files.list(output).forEach(f -> {
            try {
                Files.delete(f);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

}
