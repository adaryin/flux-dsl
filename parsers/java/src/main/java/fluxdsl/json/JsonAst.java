package fluxdsl.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/** Jackson-serializable DTO types for FluxDSL AST round-tripping via JSON. */
public final class JsonAst {
    private JsonAst() {}

    public record Root(@JsonProperty("items") List<Item> items) {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Item.Include.class, name = "include"),
            @JsonSubTypes.Type(value = Item.Schema.class, name = "schema"),
            @JsonSubTypes.Type(value = Item.Pair.class, name = "pair"),
    })
    public sealed interface Item {
        record Include(@JsonProperty("path") String path) implements Item {}

        record Schema(@JsonProperty("path") String path) implements Item {}

        record Pair(@JsonProperty("key") String key,
                     @JsonProperty("value") Value value) implements Item {}
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Value.Str.class, name = "str"),
            @JsonSubTypes.Type(value = Value.Num.class, name = "num"),
            @JsonSubTypes.Type(value = Value.Bool.class, name = "bool"),
            @JsonSubTypes.Type(value = Value.Null.class, name = "null"),
            @JsonSubTypes.Type(value = Value.Obj.class, name = "obj"),
            @JsonSubTypes.Type(value = Value.Lst.class, name = "list"),
    })
    public sealed interface Value {
        record Str(@JsonProperty("value") String value) implements Value {}

        record Num(@JsonProperty("value") double value) implements Value {}

        record Bool(@JsonProperty("value") boolean value) implements Value {}

        record Null() implements Value {}

        record Obj(@JsonProperty("entries") List<ObjEntry> entries) implements Value {}

        record Lst(@JsonProperty("elements") List<Value> elements) implements Value {}
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = ObjEntry.Pair.class, name = "pair"),
            @JsonSubTypes.Type(value = ObjEntry.Include.class, name = "include"),
            @JsonSubTypes.Type(value = ObjEntry.Schema.class, name = "schema"),
    })
    public sealed interface ObjEntry {
        record Pair(@JsonProperty("key") String key,
                     @JsonProperty("value") Value value) implements ObjEntry {}

        record Include(@JsonProperty("path") String path) implements ObjEntry {}

        record Schema(@JsonProperty("path") String path) implements ObjEntry {}
    }
}
