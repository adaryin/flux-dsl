package fluxdsl.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import fluxdsl.FluxTree.Ast;
import fluxdsl.FluxTree.Item;
import fluxdsl.FluxTree.Value;
import fluxdsl.FluxTree.ObjEntry;
import java.util.ArrayList;

/** JSON serialization/deserialization bridge between {@code FluxTree} AST and {@code JsonAst} DTOs. */
public final class FluxMapper {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .findAndRegisterModules();

    private FluxMapper() {}

    /** Returns the preconfigured Jackson {@link ObjectMapper}. */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** Serializes an AST root to a JSON string. */
    public static String toJson(Ast.Root root) {
        try {
            return MAPPER.writeValueAsString(toDto(root));
        } catch (Exception e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }

    /** Deserializes a JSON string back to an AST root. */
    public static Ast.Root fromJson(String json) {
        try {
            return fromDto(MAPPER.readValue(json, JsonAst.Root.class));
        } catch (Exception e) {
            throw new RuntimeException("Deserialization failed", e);
        }
    }

    /** Converts an AST root to a {@code JsonAst} DTO root. */
    public static JsonAst.Root toDto(Ast.Root root) {
        var items = new ArrayList<JsonAst.Item>();
        for (var item : root.items()) items.add(toDto(item));
        return new JsonAst.Root(items);
    }

    static JsonAst.Item toDto(Item item) {
        if (item instanceof Item.Include i) return new JsonAst.Item.Include(i.path());
        if (item instanceof Item.Schema s) return new JsonAst.Item.Schema(s.path());
        var p = (Item.Pair) item;
        return new JsonAst.Item.Pair(p.key(), toDto(p.value()));
    }

    static JsonAst.Value toDto(Value value) {
        if (value instanceof Value.Str s) return new JsonAst.Value.Str(s.value());
        if (value instanceof Value.Num n) return new JsonAst.Value.Num(n.value());
        if (value instanceof Value.Bool b) return new JsonAst.Value.Bool(b.value());
        if (value instanceof Value.Null) return new JsonAst.Value.Null();
        if (value instanceof Value.Obj o) {
            var entries = new ArrayList<JsonAst.ObjEntry>();
            for (var e : o.entries()) entries.add(toDto(e));
            return new JsonAst.Value.Obj(entries);
        }
        var l = (Value.Lst) value;
        var elements = new ArrayList<JsonAst.Value>();
        for (var e : l.elements()) elements.add(toDto(e));
        return new JsonAst.Value.Lst(elements);
    }

    static JsonAst.ObjEntry toDto(ObjEntry entry) {
        if (entry instanceof ObjEntry.ObjPair p)
            return new JsonAst.ObjEntry.Pair(p.key(), toDto(p.value()));
        if (entry instanceof ObjEntry.ObjInclude i) return new JsonAst.ObjEntry.Include(i.path());
        var s = (ObjEntry.ObjSchema) entry;
        return new JsonAst.ObjEntry.Schema(s.path());
    }

    /** Converts a {@code JsonAst} DTO root back to an AST root. */
    public static Ast.Root fromDto(JsonAst.Root dto) {
        var items = new ArrayList<Item>();
        for (var item : dto.items()) items.add(fromDto(item));
        return new Ast.Root(items);
    }

    static Item fromDto(JsonAst.Item dto) {
        if (dto instanceof JsonAst.Item.Include i) return new Item.Include(i.path());
        if (dto instanceof JsonAst.Item.Schema s) return new Item.Schema(s.path());
        var p = (JsonAst.Item.Pair) dto;
        return new Item.Pair(p.key(), fromDto(p.value()));
    }

    static Value fromDto(JsonAst.Value dto) {
        if (dto instanceof JsonAst.Value.Str s) return new Value.Str(s.value());
        if (dto instanceof JsonAst.Value.Num n) return new Value.Num(n.value());
        if (dto instanceof JsonAst.Value.Bool b) return new Value.Bool(b.value());
        if (dto instanceof JsonAst.Value.Null) return new Value.Null();
        if (dto instanceof JsonAst.Value.Obj o) {
            var entries = new ArrayList<ObjEntry>();
            for (var e : o.entries()) entries.add(fromDto(e));
            return new Value.Obj(entries);
        }
        var l = (JsonAst.Value.Lst) dto;
        var elements = new ArrayList<Value>();
        for (var e : l.elements()) elements.add(fromDto(e));
        return new Value.Lst(elements);
    }

    static ObjEntry fromDto(JsonAst.ObjEntry dto) {
        if (dto instanceof JsonAst.ObjEntry.Pair p)
            return new ObjEntry.ObjPair(p.key(), fromDto(p.value()));
        if (dto instanceof JsonAst.ObjEntry.Include i) return new ObjEntry.ObjInclude(i.path());
        var s = (JsonAst.ObjEntry.Schema) dto;
        return new ObjEntry.ObjSchema(s.path());
    }
}
