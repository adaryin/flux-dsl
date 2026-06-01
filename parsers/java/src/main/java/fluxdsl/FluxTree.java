package fluxdsl;

import java.util.List;

/** AST types for FluxDSL documents. */
public final class FluxTree {
    private FluxTree() {
    }

    public sealed interface Ast {
        record Root(List<Item> items) implements Ast {
        }
    }

    public sealed interface Item {
        record Include(String path) implements Item {
        }

        record Schema(String path) implements Item {
        }

        record Pair(String key, Value value) implements Item {
        }
    }

    public sealed interface Value {
        record Str(String value) implements Value {
        }

        record Num(double value) implements Value {
        }

        record Bool(boolean value) implements Value {
        }

        record Null() implements Value {
        }

        record Obj(List<ObjEntry> entries) implements Value {
        }

        record Lst(List<Value> elements) implements Value {
        }
    }

    public sealed interface ObjEntry {
        record ObjPair(String key, Value value) implements ObjEntry {
        }

        record ObjInclude(String path) implements ObjEntry {
        }

        record ObjSchema(String path) implements ObjEntry {
        }
    }
}
