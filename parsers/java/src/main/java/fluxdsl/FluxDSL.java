package fluxdsl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import fluxdsl.FluxTree.*;
import fluxdsl.FluxTree.Ast.*;
import fluxdsl.FluxTree.Item.*;
import fluxdsl.FluxTree.Value.*;
import fluxdsl.FluxTree.ObjEntry.*;
import fluxdsl.lexer.Lexer;
import fluxdsl.lexer.LexerException;
import fluxdsl.parser.Parser;
import fluxdsl.parser.ParserException;

/**
 * Main CLI entry point for FluxDSL.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>{@code parse [--strict] <file>} — parse and pretty-print
 *   <li>{@code query <file> <path>} — query with a path expression
 * </ul>
 */
public class FluxDSL {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java fluxdsl.FluxDSL <subcommand> [options] <file>");
            System.err.println("Subcommands: parse [--strict], query");
            System.exit(1);
        }
        var cmd = args[0];
        switch (cmd) {
            case "parse" -> cmdParse(args);
            case "query" -> cmdQuery(args);
            default -> {
                System.err.println("Unknown subcommand: " + cmd);
                System.exit(1);
            }
        }
    }

    private static void cmdParse(String[] args) throws IOException {
        boolean strict = false;
        String file = null;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--strict")) strict = true;
            else file = args[i];
        }
        if (file == null) {
            System.err.println("Usage: java fluxdsl.FluxDSL parse [--strict] <file.fx>");
            System.exit(1);
        }
        var text = Files.readString(Path.of(file));
        try {
            var root = new Parser(new Lexer(text), strict).parseDocument();
            System.out.println(format(root, 0));
        } catch (LexerException | ParserException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void cmdQuery(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Usage: java fluxdsl.FluxDSL query <file.fx> <path>");
            System.exit(1);
        }
        var text = Files.readString(Path.of(args[1]));
        try {
            var root = new Parser(new Lexer(text)).parseDocument();
            var result = fluxdsl.query.FluxQuery.query(root, args[2]);
            if (result == null) {
                System.out.println("null");
            } else {
                System.out.println(formatValue(result, 0));
            }
        } catch (LexerException | ParserException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    static String format(Ast node, int indent) {
        if (node instanceof Root) {
            var r = (Root) node;
            var sb = new StringBuilder("document");
            for (var item : r.items()) sb.append("\n").append(formatItem(item, indent + 1));
            return sb.toString();
        }
        return node.toString();
    }

    static String formatItem(Item item, int indent) {
        var pad = "  ".repeat(indent);
        if (item instanceof Include) {
            var i = (Include) item;
            return pad + "include " + i.path();
        }
        if (item instanceof Schema) {
            var s = (Schema) item;
            return pad + "@schema " + s.path();
        }
        var p = (Pair) item;
        return pad + p.key() + ": " + formatValue(p.value(), indent);
    }

    static String formatValue(Value v, int indent) {
        if (v instanceof Str) {
            var s = (Str) v;
            return quote(s.value());
        }
        if (v instanceof Num) {
            var n = (Num) v;
            return n.value() == Math.floor(n.value()) && !Double.isInfinite(n.value())
                    ? String.valueOf((long) n.value())
                    : String.valueOf(n.value());
        }
        if (v instanceof Bool) {
            var b = (Bool) v;
            return String.valueOf(b.value());
        }
        if (v instanceof Null) return "null";
        if (v instanceof Obj) {
            var o = (Obj) v;
            return formatObj(o, indent);
        }
        var l = (Lst) v;
        return formatList(l, indent);
    }

    static String formatObj(Obj o, int indent) {
        if (o.entries().isEmpty()) return "{}";
        var pad = "  ".repeat(indent);
        var sb = new StringBuilder("{");
        for (var e : o.entries()) {
            sb.append("\n").append(pad).append("  ");
            if (e instanceof ObjPair) {
                var p = (ObjPair) e;
                sb.append(p.key()).append(": ").append(formatValue(p.value(), indent + 1));
            } else if (e instanceof ObjInclude) {
                var i = (ObjInclude) e;
                sb.append("include ").append(i.path());
            } else {
                var s = (ObjSchema) e;
                sb.append("@schema ").append(s.path());
            }
        }
        sb.append("\n").append(pad).append("}");
        return sb.toString();
    }

    static String formatList(Lst l, int indent) {
        if (l.elements().isEmpty()) return "[]";
        var pad = "  ".repeat(indent);
        var sb = new StringBuilder("[");
        for (var e : l.elements()) {
            sb.append("\n").append(pad).append("  ").append(formatValue(e, indent + 1));
        }
        sb.append("\n").append(pad).append("]");
        return sb.toString();
    }

    static String quote(String s) {
        if (s.isEmpty()) return "\"\"";
        if (s.chars().noneMatch(c -> c < 32 || c == '"' || c == '\\')
                && s.matches("[a-zA-Z_][a-zA-Z0-9_-]*")) return s;
        var sb = new StringBuilder("\"");
        for (var c : s.toCharArray()) {
            switch (c) {
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> sb.append("\\r");
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> sb.append(c);
            }
        }
        return sb.append("\"").toString();
    }
}
