package fluxdsl;

import fluxdsl.lexer.Lexer;
import fluxdsl.parser.Parser;
import fluxdsl.parser.ParserException;
import fluxdsl.FluxTree.Ast;
import fluxdsl.FluxTree.Ast.Root;
import fluxdsl.FluxTree.Item;
import fluxdsl.FluxTree.Item.Pair;
import fluxdsl.FluxTree.Item.Include;
import fluxdsl.FluxTree.Item.Schema;
import fluxdsl.FluxTree.Value;
import fluxdsl.FluxTree.Value.Str;
import fluxdsl.FluxTree.Value.Num;
import fluxdsl.FluxTree.Value.Bool;
import fluxdsl.FluxTree.Value.Null;
import fluxdsl.FluxTree.Value.Obj;
import fluxdsl.FluxTree.Value.Lst;
import fluxdsl.FluxTree.ObjEntry;
import fluxdsl.FluxTree.ObjEntry.ObjPair;
import fluxdsl.FluxTree.ObjEntry.ObjInclude;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    private Root parse(String text) {
        return new Parser(new Lexer(text)).parseDocument();
    }

    @Test
    void parseEmpty() {
        var root = parse("");
        assertTrue(root.items().isEmpty());
    }

    @Test
    void parseStringValue() {
        var root = parse("name: \"hello\"");
        assertPair(root, 0, "name", new Str("hello"));
    }

    @Test
    void parseNumberInt() {
        var root = parse("count: 42");
        assertPair(root, 0, "count", new Num(42));
    }

    @Test
    void parseNumberFloat() {
        var root = parse("pi: 3.14");
        assertPair(root, 0, "pi", new Num(3.14));
    }

    @Test
    void parseBoolTrue() {
        var root = parse("active: true");
        assertPair(root, 0, "active", new Bool(true));
    }

    @Test
    void parseBoolFalse() {
        var root = parse("active: false");
        assertPair(root, 0, "active", new Bool(false));
    }

    @Test
    void parseNull() {
        var root = parse("data: null");
        assertPair(root, 0, "data", new Null());
    }

    @Test
    void parseBareStringValue() {
        var root = parse("key: bare_value");
        assertPair(root, 0, "key", new Str("bare_value"));
    }

    @Test
    void parseNestedObject() {
        var root = parse("db {\n  host: \"localhost\"\n  port: 5432\n}");
        var obj = assertPairType(root, 0, "db", Obj.class);
        var entries = obj.entries();
        assertEquals(2, entries.size());
        assertObjPair(entries.get(0), "host", new Str("localhost"));
        assertObjPair(entries.get(1), "port", new Num(5432));
    }

    @Test
    void parseList() {
        var root = parse("items: [1, 2, 3]");
        var lst = assertPairType(root, 0, "items", Lst.class);
        assertEquals(3, lst.elements().size());
        assertEquals(new Num(1), lst.elements().get(0));
        assertEquals(new Num(2), lst.elements().get(1));
        assertEquals(new Num(3), lst.elements().get(2));
    }

    @Test
    void parseListMixedTypes() {
        var root = parse("mix: [\"a\", 1, true, null]");
        var lst = assertPairType(root, 0, "mix", Lst.class);
        assertEquals(4, lst.elements().size());
        assertEquals(new Str("a"), lst.elements().get(0));
        assertEquals(new Num(1), lst.elements().get(1));
        assertEquals(new Bool(true), lst.elements().get(2));
        assertEquals(new Null(), lst.elements().get(3));
    }

    @Test
    void parseListOfObjects() {
        var root = parse("items: [\n  { name: \"a\" }\n  { name: \"b\" }\n]");
        var lst = assertPairType(root, 0, "items", Lst.class);
        assertEquals(2, lst.elements().size());
    }

    @Test
    void parseInclude() {
        var root = parse("include \"base.fx\"");
        assertEquals(1, root.items().size());
        assertInstanceOf(Include.class, root.items().get(0));
        assertEquals("base.fx", ((Include) root.items().get(0)).path());
    }

    @Test
    void parseSchema() {
        var root = parse("@schema \"db.fx\"");
        assertEquals(1, root.items().size());
        assertInstanceOf(Schema.class, root.items().get(0));
        assertEquals("db.fx", ((Schema) root.items().get(0)).path());
    }

    @Test
    void parseMultipleTopLevelItems() {
        var root = parse("include \"base.fx\"\nkey: \"val\"\n@schema \"s.fx\"");
        assertEquals(3, root.items().size());
        assertInstanceOf(Include.class, root.items().get(0));
        assertInstanceOf(Pair.class, root.items().get(1));
        assertInstanceOf(Schema.class, root.items().get(2));
    }

    @Test
    void parseObjectWithInclude() {
        var root = parse("cfg {\n  include \"ext.fx\"\n  name: \"x\"\n}");
        var obj = assertPairType(root, 0, "cfg", Obj.class);
        assertEquals(2, obj.entries().size());
        assertInstanceOf(ObjInclude.class, obj.entries().get(0));
        assertEquals("ext.fx", ((ObjInclude) obj.entries().get(0)).path());
        assertObjPair(obj.entries().get(1), "name", new Str("x"));
    }

    @Test
    void parseBlockString() {
        var root = parse("data {|\n  hello\n  world\n}");
        assertPair(root, 0, "data", new Str("hello\nworld"));
    }

    @Test
    void parseExplicitColon() {
        var root = parse("key: \"val\"");
        assertPair(root, 0, "key", new Str("val"));
    }

    @Test
    void parseImplicitColon() {
        var root = parse("key \"val\"");
        assertPair(root, 0, "key", new Str("val"));
    }

    @Test
    void errorOnMissingValue() {
        assertThrows(ParserException.class, () -> parse("key:"));
    }

    @Test
    void errorOnExpectedKey() {
        assertThrows(ParserException.class, () -> parse(": \"val\""));
    }

    @Test
    void emptyBracesParsedAsValue() {
        // a single brace pair is consumed as a value context — not an error
        assertDoesNotThrow(() -> parse("{}"));
    }

    private static void assertPair(Root root, int index, String key, Value value) {
        var item = root.items().get(index);
        assertInstanceOf(Pair.class, item);
        var pair = (Pair) item;
        assertEquals(key, pair.key());
        assertEquals(value, pair.value());
    }

    private static <T extends Value> T assertPairType(Root root, int index, String key, Class<T> type) {
        var item = root.items().get(index);
        assertInstanceOf(Pair.class, item);
        var pair = (Pair) item;
        assertEquals(key, pair.key());
        assertInstanceOf(type, pair.value());
        return type.cast(pair.value());
    }

    private static void assertObjPair(ObjEntry entry, String key, Value value) {
        assertInstanceOf(ObjPair.class, entry);
        var p = (ObjPair) entry;
        assertEquals(key, p.key());
        assertEquals(value, p.value());
    }
}
