package fluxdsl;

import fluxdsl.lexer.Lexer;
import fluxdsl.parser.Parser;
import java.nio.file.*;
import java.util.*;

/** Benchmarks parse time for all .fx sample files. */
public final class Benchmark {
    private static final int WARMUP = 3;
    private static final int ITERATIONS = 10;

    private Benchmark() {}

    public static void main(String[] args) throws Exception {
        var samplesDir = Paths.get("samples");
        if (!Files.isDirectory(samplesDir)) {
            samplesDir = Paths.get("../..").resolve("samples");
        }
        if (!Files.isDirectory(samplesDir)) {
            System.err.println("samples/ directory not found");
            System.exit(1);
        }

        var files = new ArrayList<Path>();
        try (var stream = Files.walk(samplesDir)) {
            stream.filter(p -> p.toString().endsWith(".fx")).sorted().forEach(files::add);
        }

        System.out.println("Benchmarking " + files.size() + " .fx files\n");

        for (var file : files) {
            var text = Files.readString(file);
            var total = 0.0;

            // Warmup
            for (int i = 0; i < WARMUP; i++) {
                runParse(text);
            }

            // Measure
            for (int i = 0; i < ITERATIONS; i++) {
                long start = System.nanoTime();
                runParse(text);
                long end = System.nanoTime();
                total += (end - start) / 1_000_000.0;
            }

            var avg = total / ITERATIONS;
            System.out.printf("%-40s %8.3f ms%n", relative(file, samplesDir), avg);
        }
    }

    static void runParse(String text) {
        var lexer = new Lexer(text);
        var parser = new Parser(lexer);
        parser.parseDocument();
    }

    static String relative(Path file, Path base) {
        return base.relativize(file).toString();
    }
}
