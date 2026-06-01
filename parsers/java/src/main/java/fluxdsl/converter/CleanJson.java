package fluxdsl.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import fluxdsl.json.JsonAst;
import java.util.*;

/** Converts between {@code JsonAst} DTOs and plain JSON/YAML trees (no type discriminators). */
public final class CleanJson {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CleanJson() {}

    /** Converts a DTO root to a clean Jackson {@link ObjectNode}. */
    public static ObjectNode rootToClean(JsonAst.Root root) {
        var out = MAPPER.createObjectNode();
        for (var item : root.items()) {
            if (item instanceof JsonAst.Item.Pair p) {
                out.set(p.key(), valueToClean(p.value()));
            } else if (item instanceof JsonAst.Item.Include i) {
                addSpecial(out, "$include", i.path());
            } else if (item instanceof JsonAst.Item.Schema s) {
                addSpecial(out, "$schema", s.path());
            }
        }
        return out;
    }

    /** Converts a DTO value to a clean Jackson {@link JsonNode}. */
    public static JsonNode valueToClean(JsonAst.Value value) {
        if (value instanceof JsonAst.Value.Str s) return TextNode.valueOf(s.value());
        if (value instanceof JsonAst.Value.Num n) {
            var v = n.value();
            if (v == Math.floor(v) && !Double.isInfinite(v)) return new LongNode((long) v);
            return new DoubleNode(v);
        }
        if (value instanceof JsonAst.Value.Bool b) return BooleanNode.valueOf(b.value());
        if (value instanceof JsonAst.Value.Null) return NullNode.getInstance();
        if (value instanceof JsonAst.Value.Obj o) return objToClean(o);
        var l = (JsonAst.Value.Lst) value;
        var arr = MAPPER.createArrayNode();
        for (var e : l.elements()) arr.add(valueToClean(e));
        return arr;
    }

    /** Converts a DTO object value to a clean Jackson {@link ObjectNode}. */
    public static ObjectNode objToClean(JsonAst.Value.Obj obj) {
        var out = MAPPER.createObjectNode();
        for (var entry : obj.entries()) {
            if (entry instanceof JsonAst.ObjEntry.Pair p) {
                out.set(p.key(), valueToClean(p.value()));
            } else if (entry instanceof JsonAst.ObjEntry.Include i) {
                addSpecial(out, "$include", i.path());
            } else if (entry instanceof JsonAst.ObjEntry.Schema s) {
                addSpecial(out, "$schema", s.path());
            }
        }
        return out;
    }

    private static void addSpecial(ObjectNode node, String key, String value) {
        var existing = node.get(key);
        if (existing != null) {
            if (existing.isArray()) {
                ((ArrayNode) existing).add(value);
            } else {
                var arr = MAPPER.createArrayNode();
                arr.add(existing.asText());
                arr.add(value);
                node.set(key, arr);
            }
        } else {
            node.put(key, value);
        }
    }

    /** Converts a clean Jackson {@link ObjectNode} back to a DTO root. */
    public static JsonAst.Root cleanToRoot(ObjectNode node) {
        var items = new ArrayList<JsonAst.Item>();
        var it = node.fields();
        while (it.hasNext()) {
            var field = it.next();
            var key = field.getKey();
            var val = field.getValue();
            if (key.equals("$include")) {
                if (val.isArray()) {
                    for (var v : val) items.add(new JsonAst.Item.Include(v.asText()));
                } else {
                    items.add(new JsonAst.Item.Include(val.asText()));
                }
            } else if (key.equals("$schema")) {
                if (val.isArray()) {
                    for (var v : val) items.add(new JsonAst.Item.Schema(v.asText()));
                } else {
                    items.add(new JsonAst.Item.Schema(val.asText()));
                }
            } else {
                items.add(new JsonAst.Item.Pair(key, cleanToValue(val)));
            }
        }
        return new JsonAst.Root(items);
    }

    /** Converts a clean Jackson {@link JsonNode} back to a DTO value. */
    public static JsonAst.Value cleanToValue(JsonNode node) {
        if (node.isNull()) return new JsonAst.Value.Null();
        if (node.isBoolean()) return new JsonAst.Value.Bool(node.asBoolean());
        if (node.isNumber()) return new JsonAst.Value.Num(node.asDouble());
        if (node.isTextual()) return new JsonAst.Value.Str(node.asText());
        if (node.isArray()) {
            var elements = new ArrayList<JsonAst.Value>();
            for (var e : node) elements.add(cleanToValue(e));
            return new JsonAst.Value.Lst(elements);
        }
        if (node.isObject()) return cleanToObj((ObjectNode) node);
        throw new IllegalArgumentException("Unsupported node type: " + node.getNodeType());
    }

    /** Converts a clean Jackson {@link ObjectNode} back to a DTO object value. */
    public static JsonAst.Value.Obj cleanToObj(ObjectNode node) {
        var entries = new ArrayList<JsonAst.ObjEntry>();
        var it = node.fields();
        while (it.hasNext()) {
            var field = it.next();
            var key = field.getKey();
            var val = field.getValue();
            if (key.equals("$include")) {
                if (val.isArray()) {
                    for (var v : val) entries.add(new JsonAst.ObjEntry.Include(v.asText()));
                } else {
                    entries.add(new JsonAst.ObjEntry.Include(val.asText()));
                }
            } else if (key.equals("$schema")) {
                if (val.isArray()) {
                    for (var v : val) entries.add(new JsonAst.ObjEntry.Schema(v.asText()));
                } else {
                    entries.add(new JsonAst.ObjEntry.Schema(val.asText()));
                }
            } else {
                entries.add(new JsonAst.ObjEntry.Pair(key, cleanToValue(val)));
            }
        }
        return new JsonAst.Value.Obj(entries);
    }
}
