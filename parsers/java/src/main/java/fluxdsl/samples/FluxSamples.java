package fluxdsl.samples;

import fluxdsl.json.FluxMapper;
import fluxdsl.lexer.Lexer;
import fluxdsl.lexer.LexerException;
import fluxdsl.parser.Parser;
import fluxdsl.parser.ParserException;
import fluxdsl.validator.FluxValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FluxSamples {
    private FluxSamples() {}

    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            for (var arg : args) processFile(Path.of(arg));
            return;
        }

        var samplesDir = Path.of("samples");
        if (!Files.isDirectory(samplesDir))
            samplesDir = Path.of("../../../../samples");
        if (!Files.isDirectory(samplesDir)) {
            System.err.println("Usage: java fluxdsl.samples.FluxSamples <file.fx> ...");
            System.exit(1);
        }

        try (var files = Files.walk(samplesDir)) {
            files.filter(f -> f.toString().endsWith(".fx"))
                 .sorted()
                 .forEach(FluxSamples::processFile);
        }
    }

    static void processFile(Path path) {
        try {
            var text = Files.readString(path);
            var root = new Parser(new Lexer(text)).parseDocument();

            var json = FluxMapper.toJson(root);
            var restored = FluxMapper.fromJson(json);

            var ok = root.equals(restored);
            System.out.println("=== " + path + (ok ? " \u2713" : " \u2717 ROUND-TRIP FAILED") + " ===");
            System.out.println(json);

            var result = FluxValidator.validate(path);
            if (result.hasErrors()) {
                System.out.println("Validation errors:");
                for (var err : result.errors()) {
                    System.out.println("  " + err);
                }
            } else {
                System.out.println("Validation: \u2713");
            }
            System.out.println();
        } catch (IOException e) {
            System.err.println("Error reading " + path + ": " + e.getMessage());
        } catch (LexerException | ParserException e) {
            System.err.println("Parse error in " + path + ": " + e.getMessage());
        }
    }
}
