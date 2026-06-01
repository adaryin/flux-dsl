package fluxdsl.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import fluxdsl.json.FluxMapper;
import java.nio.file.Path;

/** Converts YAML files to FluxDSL (.fx) strings. */
public final class Yaml2Fx {
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private Yaml2Fx() {}

    /** Parses a YAML file and returns the equivalent .fx string. */
    public static String convert(Path path) {
        try {
            var node = YAML_MAPPER.readTree(path.toFile());
            if (!(node instanceof ObjectNode obj)) {
                throw new IllegalArgumentException("YAML root must be a mapping");
            }
            var dto = CleanJson.cleanToRoot(obj);
            var ast = FluxMapper.fromDto(dto);
            return FxFormatter.format(ast);
        } catch (Exception e) {
            throw new RuntimeException("FX conversion failed", e);
        }
    }
}
