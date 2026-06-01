package fluxdsl;

import fluxdsl.FluxTree.Ast.Root;
import fluxdsl.FluxTree.Item;
import fluxdsl.FluxTree.Item.Pair;
import fluxdsl.FluxTree.Item.Include;
import fluxdsl.FluxTree.Item.Schema;
import fluxdsl.FluxTree.Value.*;
import fluxdsl.FluxTree.ObjEntry.*;
import fluxdsl.json.FluxMapper;
import fluxdsl.json.JsonAst;
import fluxdsl.lexer.Lexer;
import fluxdsl.parser.Parser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FluxMapperTest {

    @Test
    void roundTripEmpty() {
        var root = new Root(List.of());
        assertRoundTrip(root);
    }

    @Test
    void roundTripString() {
        var root = new Root(List.of(new Pair("k", new Str("hello"))));
        assertRoundTrip(root);
    }

    @Test
    void roundTripNumber() {
        var root = new Root(List.of(new Pair("k", new Num(42))));
        assertRoundTrip(root);
    }

    @Test
    void roundTripBool() {
        var root = new Root(List.of(new Pair("k", new Bool(true))));
        assertRoundTrip(root);
    }

    @Test
    void roundTripNull() {
        var root = new Root(List.of(new Pair("k", new Null())));
        assertRoundTrip(root);
    }

    @Test
    void roundTripAllTypes() {
        var root = new Root(List.of(
                new Pair("s", new Str("hi")),
                new Pair("n", new Num(-3.14)),
                new Pair("b", new Bool(false)),
                new Pair("x", new Null())
        ));
        assertRoundTrip(root);
    }

    @Test
    void roundTripInclude() {
        var root = new Root(List.of(new Include("base.fx")));
        assertRoundTrip(root);
    }

    @Test
    void roundTripSchema() {
        var root = new Root(List.of(new Schema("s.fx")));
        assertRoundTrip(root);
    }

    @Test
    void roundTripNestedObject() {
        var inner = new Obj(List.of(new ObjPair("x", new Str("y"))));
        var root = new Root(List.of(new Pair("outer", inner)));
        assertRoundTrip(root);
    }

    @Test
    void roundTripDeeplyNested() {
        var obj = new Obj(List.of(
                new ObjPair("a", new Obj(List.of(
                        new ObjPair("b", new Obj(List.of(
                                new ObjPair("c", new Num(1))
                        )))
                )))
        ));
        var root = new Root(List.of(new Pair("deep", obj)));
        assertRoundTrip(root);
    }

    @Test
    void roundTripList() {
        var lst = new Lst(List.of(new Str("a"), new Num(1), new Bool(true), new Null()));
        var root = new Root(List.of(new Pair("items", lst)));
        assertRoundTrip(root);
    }

    @Test
    void roundTripListOfObjects() {
        var lst = new Lst(List.of(
                new Obj(List.of(new ObjPair("x", new Num(1)))),
                new Obj(List.of(new ObjPair("y", new Num(2))))
        ));
        var root = new Root(List.of(new Pair("items", lst)));
        assertRoundTrip(root);
    }

    @Test
    void roundTripObjectWithIncludeEntry() {
        var obj = new Obj(List.of(
                new ObjInclude("ext.fx"),
                new ObjPair("name", new Str("test"))
        ));
        var root = new Root(List.of(new Pair("cfg", obj)));
        assertRoundTrip(root);
    }

    @Test
    void roundTripObjectWithSchemaEntry() {
        var obj = new Obj(List.of(
                new ObjSchema("s.fx"),
                new ObjPair("x", new Num(1))
        ));
        var root = new Root(List.of(new Pair("cfg", obj)));
        assertRoundTrip(root);
    }

    @Test
    void roundTripComplex() {
        var root = new Root(List.of(
                new Include("base.fx"),
                new Schema("db_schema.fx"),
                new Pair("db", new Obj(List.of(
                        new ObjPair("host", new Str("localhost")),
                        new ObjPair("port", new Num(5432)),
                        new ObjInclude("pool.fx")
                ))),
                new Pair("items", new Lst(List.of(
                        new Str("a"),
                        new Num(2)
                )))
        ));
        assertRoundTrip(root);
    }

    @Test
    void toDtoFromDtoRoundTrip() {
        var root = new Root(List.of(new Pair("k", new Str("v"))));
        var dto = FluxMapper.toDto(root);
        var back = FluxMapper.fromDto(dto);
        assertEquals(root, back);
    }

    @Test
    void jsonUsesShortTypeNames() throws Exception {
        var root = new Root(List.of(
                new Pair("s", new Str("hi")),
                new Pair("n", new Num(1)),
                new Pair("b", new Bool(true)),
                new Pair("x", new Null()),
                new Pair("o", new Obj(List.of())),
                new Pair("l", new Lst(List.of()))
        ));
        var json = FluxMapper.toJson(root);
        assertTrue(json.contains("\"str\""));
        assertTrue(json.contains("\"num\""));
        assertTrue(json.contains("\"bool\""));
        assertTrue(json.contains("\"null\""));
        assertTrue(json.contains("\"obj\""));
        assertTrue(json.contains("\"list\""));
    }

    @Test
    void roundTripAllSampleFiles() throws IOException {
        var samplesDir = findSamplesDir();
        if (samplesDir == null) {
            fail("Cannot find samples directory");
        }
        try (var files = Files.walk(samplesDir)) {
            var fxFiles = files.filter(f -> f.toString().endsWith(".fx")).toList();
            assertFalse(fxFiles.isEmpty(), "No .fx files found in " + samplesDir);
            for (var path : fxFiles) {
                var text = Files.readString(path);
                var root = new Parser(new Lexer(text)).parseDocument();
                assertRoundTrip(root, path.toString());
            }
        }
    }

    private static void assertRoundTrip(Root root) {
        assertRoundTrip(root, null);
    }

    private static void assertRoundTrip(Root root, String label) {
        var json = FluxMapper.toJson(root);
        var restored = FluxMapper.fromJson(json);
        assertEquals(root, restored, label != null ? label : "Round-trip failed");
    }

    private static Path findSamplesDir() {
        var cwd = Path.of("").toAbsolutePath();
        for (var candidate : List.of(
                cwd.resolve("samples"),
                cwd.resolve("../../samples"),
                cwd.resolve("../../../samples")
        )) {
            if (Files.isDirectory(candidate)) return candidate;
        }
        return null;
    }
}
