package fluxdsl;

import fluxdsl.validator.FluxValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FluxValidatorTest {

    @Test
    void validType(@TempDir Path tmp) throws IOException {
        var f = write(tmp, "test.fx", """
                _schema {
                  x: { type: "string" }
                }
                x: "hello"
                """);
        var r = FluxValidator.validate(f);
        assertFalse(r.hasErrors(), r.errors()::toString);
    }

    @Test
    void invalidType(@TempDir Path tmp) throws IOException {
        var f = write(tmp, "test.fx", """
                _schema {
                  x: { type: "number" }
                }
                x: "hello"
                """);
        var r = FluxValidator.validate(f);
        assertTrue(r.hasErrors());
        assertTrue(r.errors().get(0).contains("expected type number"));
    }

    @Test
    void requiredFieldPresent(@TempDir Path tmp) throws IOException {
        var f = write(tmp, "test.fx", """
                _schema {
                  x: { type: "string", required: true }
                }
                x: "hello"
                """);
        var r = FluxValidator.validate(f);
        assertFalse(r.hasErrors(), r.errors()::toString);
    }

    @Test
    void requiredFieldMissing(@TempDir Path tmp) throws IOException {
        var f = write(tmp, "test.fx", """
                _schema {
                  x: { type: "string", required: true }
                }
                """);
        var r = FluxValidator.validate(f);
        assertTrue(r.hasErrors());
        assertEquals("x: required field missing", r.errors().get(0));
    }

    @Test
    void defaultInjected(@TempDir Path tmp) throws IOException {
        var f = write(tmp, "test.fx", """
                _schema {
                  x: { type: "string", default: "default_val" }
                }
                """);
        var r = FluxValidator.validate(f);
        assertFalse(r.hasErrors(), r.errors()::toString);
        var root = r.root();
        var items = root.items();
        assertEquals(1, items.size());
        var pair = (FluxTree.Item.Pair) items.get(0);
        assertEquals("x", pair.key());
        var val = (FluxTree.Value.Str) pair.value();
        assertEquals("default_val", val.value());
    }

    @Test
    void defaultValueTypeMismatch(@TempDir Path tmp) throws IOException {
        var f = write(tmp, "test.fx", """
                _schema {
                  x: { type: "number", default: "not_a_number" }
                }
                """);
        var r = FluxValidator.validate(f);
        assertTrue(r.hasErrors());
        assertTrue(r.errors().get(0).contains("default value type mismatch"));
    }

    @Test
    void enumValid(@TempDir Path tmp) throws IOException {
        var f = write(tmp, "test.fx", """
                _schema {
                  x: { type: "string", enum: ["a", "b", "c"] }
                }
                x: "b"
                """);
        var r = FluxValidator.validate(f);
        assertFalse(r.hasErrors(), r.errors()::toString);
    }

    @Test
    void enumInvalid(@TempDir Path tmp) throws IOException {
        var f = write(tmp, "test.fx", """
                _schema {
                  x: { type: "string", enum: ["a", "b"] }
                }
                x: "z"
                """);
        var r = FluxValidator.validate(f);
        assertTrue(r.hasErrors());
        assertTrue(r.errors().get(0).contains("invalid enum value"));
    }

    @Test
    void patternMatch(@TempDir Path tmp) throws IOException {
        var f = write(tmp, "test.fx", """
                _schema {
                  x: { type: "string", pattern: "^[a-z]+$" }
                }
                x: "hello"
                """);
        var r = FluxValidator.validate(f);
        assertFalse(r.hasErrors(), r.errors()::toString);
    }

    @Test
    void patternNoMatch(@TempDir Path tmp) throws IOException {
        var f = write(tmp, "test.fx", """
                _schema {
                  x: { type: "string", pattern: "^[a-z]+$" }
                }
                x: "Hello123"
                """);
        var r = FluxValidator.validate(f);
        assertTrue(r.hasErrors());
        assertTrue(r.errors().get(0).contains("does not match pattern"));
    }

    @Test
    void minMaxNumberPass(@TempDir Path tmp) throws IOException {
        var f = write(tmp, "test.fx", """
                _schema {
                  x: { type: "number", min: 0, max: 100 }
                }
                x: 50
                """);
        var r = FluxValidator.validate(f);
        assertFalse(r.hasErrors(), r.errors()::toString);
    }

    @Test
    void minMaxNumberFail(@TempDir Path tmp) throws IOException {
        var f = write(tmp, "test.fx", """
                _schema {
                  x: { type: "number", min: 0, max: 100 }
                }
                x: -1
                """);
        var r = FluxValidator.validate(f);
        assertTrue(r.hasErrors());
    }

    @Test
    void minMaxListLength(@TempDir Path tmp) throws IOException {
        var f = write(tmp, "test.fx", """
                _schema {
                  x: { type: "list", min: 2, max: 5 }
                }
                x: [1]
                """);
        var r = FluxValidator.validate(f);
        assertTrue(r.hasErrors());
        assertTrue(r.errors().get(0).contains("list length"));
    }

    @Test
    void itemsValidation(@TempDir Path tmp) throws IOException {
        var f = write(tmp, "test.fx", """
                _schema {
                  x: { type: "list", items: { type: "number" } }
                }
                x: [1, "bad", 3]
                """);
        var r = FluxValidator.validate(f);
        assertTrue(r.hasErrors());
        assertEquals(1, r.errors().size());
        assertTrue(r.errors().get(0).contains("expected type number"));
    }

    @Test
    void nestedSchemaValid(@TempDir Path tmp) throws IOException {
        var f = write(tmp, "test.fx", """
                _schema {
                  cfg: {
                    type: "object"
                    properties: {
                      host: { type: "string", required: true }
                      port: { type: "number", default: 8080 }
                    }
                  }
                }
                cfg {
                  host: "localhost"
                }
                """);
        var r = FluxValidator.validate(f);
        assertFalse(r.hasErrors(), r.errors()::toString);
    }

    @Test
    void nestedSchemaMissingRequired(@TempDir Path tmp) throws IOException {
        var f = write(tmp, "test.fx", """
                _schema {
                  cfg: {
                    type: "object"
                    properties: {
                      host: { type: "string", required: true }
                    }
                  }
                }
                cfg {
                  port: 8080
                }
                """);
        var r = FluxValidator.validate(f);
        assertTrue(r.hasErrors());
        assertTrue(r.errors().get(0).contains("required field missing"));
    }

    @Test
    void strictModeRejectsUnknown(@TempDir Path tmp) throws IOException {
        var f = write(tmp, "test.fx", """
                _schema {
                  x: { type: "string" }
                }
                x: "ok"
                y: 1
                """);
        var r = FluxValidator.validate(f, true);
        assertTrue(r.hasErrors());
        assertTrue(r.errors().get(0).contains("unknown field"));
    }

    @Test
    void looseModePreservesUnknown(@TempDir Path tmp) throws IOException {
        var f = write(tmp, "test.fx", """
                _schema {
                  x: { type: "string" }
                }
                x: "ok"
                y: 1
                """);
        var r = FluxValidator.validate(f);
        assertFalse(r.hasErrors(), r.errors()::toString);
    }

    @Test
    void includeResolvesAndMerges(@TempDir Path tmp) throws IOException {
        write(tmp, "base.fx", """
                x: "from_base"
                y: 1
                """);
        var f = write(tmp, "test.fx", """
                _schema {
                  x: { type: "string" }
                  y: { type: "number" }
                }
                include "base.fx"
                y: 2
                """);
        var r = FluxValidator.validate(f);
        assertFalse(r.hasErrors(), r.errors()::toString);
        var items = r.root().items();
        var xPair = findPair(items, "x");
        assertNotNull(xPair);
        assertEquals("from_base", ((FluxTree.Value.Str) xPair.value()).value());
        var yPair = findPair(items, "y");
        assertNotNull(yPair);
        assertEquals(2.0, ((FluxTree.Value.Num) yPair.value()).value());
    }

    @Test
    void includeChain(@TempDir Path tmp) throws IOException {
        write(tmp, "a.fx", """
                x: "from_a"
                """);
        write(tmp, "b.fx", """
                include "a.fx"
                y: 2
                """);
        var f = write(tmp, "test.fx", """
                _schema {
                  x: { type: "string" }
                  y: { type: "number" }
                }
                include "b.fx"
                """);
        var r = FluxValidator.validate(f);
        assertFalse(r.hasErrors(), r.errors()::toString);
    }

    @Test
    void externalSchema(@TempDir Path tmp) throws IOException {
        write(tmp, "schema.fx", """
                x: { type: "string", required: true }
                """);
        var f = write(tmp, "test.fx", """
                @schema "schema.fx"
                x: "hello"
                """);
        var r = FluxValidator.validate(f);
        assertFalse(r.hasErrors(), r.errors()::toString);
    }

    @Test
    void externalSchemaMissingField(@TempDir Path tmp) throws IOException {
        write(tmp, "schema.fx", """
                x: { type: "string", required: true }
                """);
        var f = write(tmp, "test.fx", """
                @schema "schema.fx"
                """);
        var r = FluxValidator.validate(f);
        assertTrue(r.hasErrors());
        assertEquals("x: required field missing", r.errors().get(0));
    }

    @Test
    void inlineSchemaOverridesExternal(@TempDir Path tmp) throws IOException {
        write(tmp, "schema.fx", """
                x: { type: "string", default: "from_ext" }
                """);
        var f = write(tmp, "test.fx", """
                @schema "schema.fx"
                _schema {
                  x: { type: "string", default: "from_inline" }
                }
                """);
        var r = FluxValidator.validate(f);
        assertFalse(r.hasErrors(), r.errors()::toString);
        var xPair = findPair(r.root().items(), "x");
        assertNotNull(xPair);
        assertEquals("from_inline", ((FluxTree.Value.Str) xPair.value()).value());
    }

    @Test
    void databaseDotFxKnownErrors() throws IOException {
        var samplesDir = findSamplesDir();
        assertNotNull(samplesDir, "Cannot find samples directory");
        var f = samplesDir.resolve("database.fx");
        var r = FluxValidator.validate(f);
        assertTrue(r.hasErrors());
        assertEquals(1, r.errors().size());
        assertEquals("connection.password: required field missing", r.errors().get(0));
    }

    @Test
    void envDevDotFxValid() throws IOException {
        var samplesDir = findSamplesDir();
        assertNotNull(samplesDir);
        var f = samplesDir.resolve("env/dev.fx");
        var r = FluxValidator.validate(f);
        assertFalse(r.hasErrors(), r.errors()::toString);
    }

    @Test
    void ciPipelineDotFxValid() throws IOException {
        var samplesDir = findSamplesDir();
        assertNotNull(samplesDir);
        var f = samplesDir.resolve("ci_pipeline.fx");
        var r = FluxValidator.validate(f);
        assertFalse(r.hasErrors(), r.errors()::toString);
    }

    private static Path write(Path dir, String name, String content) throws IOException {
        var f = dir.resolve(name);
        Files.writeString(f, content);
        return f;
    }

    private static FluxTree.Item.Pair findPair(java.util.List<FluxTree.Item> items, String key) {
        for (var item : items) {
            if (item instanceof FluxTree.Item.Pair p && p.key().equals(key))
                return p;
        }
        return null;
    }

    private static Path findSamplesDir() {
        var cwd = Path.of("").toAbsolutePath();
        for (var candidate : java.util.List.of(
                cwd.resolve("samples"),
                cwd.resolve("../../samples"),
                cwd.resolve("../../../samples")
        )) {
            if (Files.isDirectory(candidate)) return candidate;
        }
        return null;
    }
}
