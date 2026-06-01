package fluxdsl.query;

import fluxdsl.FluxTree.Ast.Root;
import fluxdsl.FluxTree.Item;
import fluxdsl.FluxTree.Item.Pair;
import fluxdsl.FluxTree.Value;
import fluxdsl.FluxTree.Value.*;
import fluxdsl.FluxTree.ObjEntry;
import fluxdsl.FluxTree.ObjEntry.ObjPair;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Path-based query/projection for FluxDSL AST.
 *
 * <p>Path syntax:
 * <pre>
 *   .key         — access top-level key
 *   .key.subkey  — nested object access
 *   [0]          — list index
 *   .key[0].sub  — mixed
 * </pre>
 */
public final class FluxQuery {
    private FluxQuery() {}

    private static final Pattern SEGMENT = Pattern.compile(
        "\\.(?<key>[a-zA-Z_][a-zA-Z0-9_-]*)|\\[(?<index>\\d+)]"
    );

    /** Query a Root AST using a path expression. Returns null if not found. */
    public static Value query(Root root, String path) {
        if (!path.startsWith(".") && !path.startsWith("["))
            path = "." + path;

        var current = rootToValue(root);
        var m = SEGMENT.matcher(path);
        int pos = 0;

        while (pos < path.length()) {
            m.region(pos, path.length());
            if (!m.find() || m.start() != pos)
                throw new IllegalArgumentException(
                    "Invalid path segment at position " + pos + ": " + path.substring(pos));

            if (m.group("key") != null) {
                var key = m.group("key");
                if (!(current instanceof Obj obj))
                    return null;
                current = findEntry(obj.entries(), key);
                if (current == null)
                    return null;
            } else {
                var idx = Integer.parseInt(m.group("index"));
                if (!(current instanceof Lst lst))
                    return null;
                var elements = lst.elements();
                if (idx < 0 || idx >= elements.size())
                    return null;
                current = elements.get(idx);
            }
            pos = m.end();
        }
        return current;
    }

    private static Value findEntry(List<ObjEntry> entries, String key) {
        for (var entry : entries) {
            if (entry instanceof ObjPair p && p.key().equals(key))
                return p.value();
        }
        return null;
    }

    private static Value rootToValue(Root root) {
        var items = root.items();
        if (items.isEmpty())
            return null;
        if (items.size() == 1 && items.get(0) instanceof Pair p)
            return p.value();
        // wrap multiple top-level pairs into an object
        var entries = new java.util.ArrayList<ObjEntry>();
        for (var item : items) {
            if (item instanceof Pair p)
                entries.add(new ObjPair(p.key(), p.value()));
        }
        return new Obj(entries);
    }
}
