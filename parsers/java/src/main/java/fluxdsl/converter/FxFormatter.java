package fluxdsl.converter;

import fluxdsl.FluxTree.*;
import java.util.*;

/** Formats a FluxDSL AST back to .fx source text. */
public final class FxFormatter {
    private FxFormatter() {}

    /** Formats an AST root as .fx source text. */
    public static String format(Ast.Root root) {
        var sb = new StringBuilder();
        formatItems(root.items(), 0, sb);
        return sb.toString();
    }

    static void formatItems(List<Item> items, int indent, StringBuilder sb) {
        var pad = "  ".repeat(indent);
        for (var item : items) {
            if (item instanceof Item.Include i) {
                sb.append(pad).append("include \"").append(esc(i.path())).append("\"\n");
            } else if (item instanceof Item.Schema s) {
                sb.append(pad).append("@schema \"").append(esc(s.path())).append("\"\n");
            } else if (item instanceof Item.Pair p) {
                formatPair(p.key(), p.value(), indent, sb);
            }
        }
    }

    static void formatPair(String key, Value value, int indent, StringBuilder sb) {
        var pad = "  ".repeat(indent);
        if (value instanceof Value.Str s && !s.value().contains("\n")) {
            sb.append(pad).append(key).append(": \"").append(esc(s.value())).append("\"\n");
        } else if (value instanceof Value.Obj o) {
            sb.append(pad).append(key).append(": {\n");
            formatObjEntries(o.entries(), indent + 1, sb);
            sb.append(pad).append("}\n");
        } else if (value instanceof Value.Lst l) {
            sb.append(pad).append(key).append(":\n");
            formatList(l, indent, sb);
        } else {
            sb.append(pad).append(key).append(": ").append(formatPrimitive(value)).append("\n");
        }
    }

    static void formatObjEntries(List<ObjEntry> entries, int indent, StringBuilder sb) {
        var pad = "  ".repeat(indent);
        for (var entry : entries) {
            if (entry instanceof ObjEntry.ObjInclude i) {
                sb.append(pad).append("include \"").append(esc(i.path())).append("\"\n");
            } else if (entry instanceof ObjEntry.ObjSchema s) {
                sb.append(pad).append("@schema \"").append(esc(s.path())).append("\"\n");
            } else if (entry instanceof ObjEntry.ObjPair p) {
                formatPair(p.key(), p.value(), indent, sb);
            }
        }
    }

    static void formatList(Value.Lst list, int indent, StringBuilder sb) {
        var pad = "  ".repeat(indent);
        for (var e : list.elements()) {
            sb.append(pad).append("  ").append(formatPrimitive(e)).append("\n");
        }
    }

    static String formatPrimitive(Value value) {
        if (value instanceof Value.Str s) return "\"" + esc(s.value()) + "\"";
        if (value instanceof Value.Num n) {
            var v = n.value();
            if (v == Math.floor(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
            return String.valueOf(v);
        }
        if (value instanceof Value.Bool b) return String.valueOf(b.value());
        if (value instanceof Value.Null) return "null";
        return value.toString();
    }

    static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
