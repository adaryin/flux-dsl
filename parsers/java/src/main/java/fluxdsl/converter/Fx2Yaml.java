package fluxdsl.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import fluxdsl.json.FluxMapper;
import fluxdsl.parser.Parser;
import fluxdsl.lexer.Lexer;
import java.nio.file.Path;

/** Converts FluxDSL (.fx) files to YAML strings. */
public final class Fx2Yaml {
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(
            new YAMLFactory()
                    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                    .enable(YAMLGenerator.Feature.INDENT_ARRAYS)
                    .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)
    );

    private Fx2Yaml() {}

    /** Parses a .fx file and returns the equivalent YAML string. */
    public static String convert(Path path) {
        try {
            var text = java.nio.file.Files.readString(path);
            var root = new Parser(new Lexer(text)).parseDocument();
            var dto = FluxMapper.toDto(root);
            var clean = CleanJson.rootToClean(dto);
            return YAML_MAPPER.writeValueAsString(clean);
        } catch (Exception e) {
            throw new RuntimeException("YAML conversion failed", e);
        }
    }
}
