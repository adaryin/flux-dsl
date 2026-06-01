package fluxdsl.validator;

import fluxdsl.FluxTree.Ast.Root;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Validates FluxDSL files against schemas, with optional strict/loose mode. */
public final class FluxValidator {
    private FluxValidator() {}

    /** Validates a FluxDSL file in loose mode (unknown fields preserved). */
    public static ValidationResult validate(Path path) throws IOException {
        return validate(path, false);
    }

    /** Validates a FluxDSL file.
     * @param strict if true, unknown fields produce errors */
    public static ValidationResult validate(Path path, boolean strict) throws IOException {
        var proc = new FileProcessor(strict);
        return proc.process(path.toAbsolutePath().normalize());
    }

    /** Result of a validation pass. */
    public record ValidationResult(Root root, List<String> errors) {
        /** Returns true if validation errors were found. */
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    static final Set<String> RULE_KEYS = Set.of(
            "type", "required", "default", "enum", "pattern", "min", "max", "items", "properties");

}
