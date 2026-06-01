package fluxdsl.parser;

import fluxdsl.FluxTree.Ast.Root;
import fluxdsl.FluxTree.Item;
import fluxdsl.FluxTree.Item.Include;
import fluxdsl.FluxTree.Item.Pair;
import fluxdsl.FluxTree.Item.Schema;
import fluxdsl.FluxTree.ObjEntry;
import fluxdsl.FluxTree.ObjEntry.ObjInclude;
import fluxdsl.FluxTree.ObjEntry.ObjPair;
import fluxdsl.FluxTree.ObjEntry.ObjSchema;
import fluxdsl.FluxTree.Value;
import fluxdsl.FluxTree.Value.*;
import fluxdsl.lexer.Lexer;
import fluxdsl.token.Token;
import fluxdsl.token.TokenKind;

import java.util.ArrayList;

/** Recursive-descent parser for FluxDSL source text. */
public class Parser {
    private final Lexer lex;
    private final boolean strict;

    public Parser(Lexer lex) {
        this(lex, false);
    }

    public Parser(Lexer lex, boolean strict) {
        this.lex = lex;
        this.strict = strict;
    }

    private Token peek() {
        return lex.peek();
    }

    private Token next() {
        return lex.next();
    }

    private ParserException err(String msg) {
        var t = peek();
        return new ParserException(msg + " at " + t.line() + ":" + t.col());
    }

    private Token expect(TokenKind kind) {
        var t = next();
        if (t.kind() != kind)
            throw err("Expected " + kind + ", got " + t.kind() + " (" + t.value() + ")");
        return t;
    }

    private void skipNewlines() {
        while (peek().kind() == TokenKind.NEWLINE) next();
    }

    /** Parses the full document and returns the AST root. */
    public Root parseDocument() {
        var items = new ArrayList<Item>();
        skipNewlines();
        while (peek().kind() != TokenKind.EOF) {
            var tok = peek();
            if (tok.kind() == TokenKind.BARE_STRING && tok.value().equals("include")) {
                if (strict) throw err("include directive is not allowed in strict mode");
                next();
                items.add(new Include(expect(TokenKind.STRING).value()));
            } else if (tok.kind() == TokenKind.BARE_STRING && tok.value().equals("@schema")) {
                if (strict) throw err("@schema directive is not allowed in strict mode");
                next();
                items.add(new Schema(expect(TokenKind.STRING).value()));
            } else if (tok.kind() == TokenKind.BARE_STRING || tok.kind() == TokenKind.STRING) {
                items.add(parsePair());
            } else {
                next();
            }
            skipNewlines();
        }
        return new Root(items);
    }

    private Pair parsePair() {
        var keyTok = next();
        if (keyTok.kind() == TokenKind.BARE_STRING && strict)
            throw err("Bare string keys are not allowed in strict mode; use quoted strings");
        var key = keyTok.value();
        if (peek().kind() == TokenKind.COLON) next();
        return new Pair(key, parseValueOrBlock());
    }

    private Value parseValueOrBlock() {
        if (peek().kind() == TokenKind.LBRACE) return parseBraced();
        return parseValue();
    }

    private Value parseBraced() {
        do next();
        while (peek().kind() == TokenKind.NEWLINE);
        if (peek().kind() == TokenKind.PIPE) {
            next();
            expect(TokenKind.NEWLINE);
            return finishBlockString();
        }
        return parseObjectBody();
    }

    private Value finishBlockString() {
        var lines = new ArrayList<String>();
        Integer refIndent = null;

        boolean eof = false;
        while (lex.pos() < lex.text().length()) {
            int startPos = lex.pos();
            var line = new StringBuilder();
            while (lex.pos() < lex.text().length()) {
                var c = lex.text().charAt(lex.pos());
                if (c == '\n' || c == '\r') {
                    if (c == '\r' && lex.pos() + 1 < lex.text().length()
                            && lex.text().charAt(lex.pos() + 1) == '\n')
                        lex.pos(lex.pos() + 1);
                    lex.pos(lex.pos() + 1);
                    break;
                }
                line.append(c);
                lex.pos(lex.pos() + 1);
            }
            if (lex.pos() >= lex.text().length()) eof = true;

            var content = line.toString();
            var stripped = content.stripLeading();
            var wsCount = content.length() - stripped.length();

            if (stripped.equals("}") && refIndent != null && wsCount < refIndent) {
                lex.pos(startPos + wsCount);
                break;
            }
            if (!stripped.isEmpty()) {
                if (refIndent == null || wsCount < refIndent) refIndent = wsCount;
                lines.add(content);
            }
            if (eof) break;
        }

        lex.resetPeeked();
        while (lex.pos() < lex.text().length()
                && " \t\n\r".indexOf(lex.text().charAt(lex.pos())) >= 0)
            lex.pos(lex.pos() + 1);
        if (lex.pos() < lex.text().length() && lex.text().charAt(lex.pos()) == '}') {
            lex.pos(lex.pos() + 1);
        } else {
            expect(TokenKind.RBRACE);
        }
        return new Str(dedent(lines));
    }

    private String dedent(java.util.List<String> lines) {
        if (lines.isEmpty()) return "";
        var min = lines.stream()
                .filter(l -> !l.isBlank())
                .mapToInt(l -> l.length() - l.stripLeading().length())
                .min().orElse(0);
        return lines.stream()
                .map(l -> l.substring(Math.min(min, l.length())))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private Value parseObjectBody() {
        var entries = new ArrayList<ObjEntry>();
        skipGap();
        while (peek().kind() != TokenKind.RBRACE) {
            if (peek().kind() == TokenKind.EOF) throw err("Unterminated object");
            if (peek().kind() == TokenKind.NEWLINE || peek().kind() == TokenKind.COMMA) {
                next();
                continue;
            }
            var keyTok = peek();
            if (keyTok.kind() == TokenKind.BARE_STRING && keyTok.value().equals("include")) {
                if (strict) throw err("include directive is not allowed in strict mode");
                next();
                entries.add(new ObjInclude(expect(TokenKind.STRING).value()));
            } else if (keyTok.kind() == TokenKind.BARE_STRING && keyTok.value().equals("@schema")) {
                if (strict) throw err("@schema directive is not allowed in strict mode");
                next();
                entries.add(new ObjSchema(expect(TokenKind.STRING).value()));
            } else if (keyTok.kind() == TokenKind.BARE_STRING || keyTok.kind() == TokenKind.STRING) {
                var pair = parsePair();
                entries.add(new ObjPair(pair.key(), pair.value()));
            } else {
                throw err("Unexpected token inside object: "
                        + keyTok.kind() + " (" + keyTok.value() + ")");
            }
            skipGap();
        }
        next();
        return new Obj(entries);
    }

    private void skipGap() {
        while (peek().kind() == TokenKind.NEWLINE || peek().kind() == TokenKind.COMMA) next();
    }

    private Value parseValue() {
        var tok = peek();
        return switch (tok.kind()) {
            case STRING -> {
                next();
                yield new Str(tok.value());
            }
            case BARE_STRING -> {
                if (strict)
                    throw err("Bare string values are not allowed in strict mode; use quoted strings");
                next();
                yield new Str(tok.value());
            }
            case NUMBER -> {
                next();
                var raw = tok.value();
                if (raw.contains(".") || raw.contains("e") || raw.contains("E"))
                    yield new Num(Double.parseDouble(raw));
                yield new Num(Integer.parseInt(raw));
            }
            case BOOLEAN -> {
                next();
                yield new Bool(tok.value().equals("true"));
            }
            case NULL -> {
                next();
                yield new Null();
            }
            case LBRACKET -> parseList();
            case LBRACE -> parseBraced();
            default -> throw err("Expected value, got " + tok.kind() + " (" + tok.value() + ")");
        };
    }

    private Value parseList() {
        next();
        var elements = new ArrayList<Value>();
        skipGap();
        while (peek().kind() != TokenKind.RBRACKET) {
            if (peek().kind() == TokenKind.EOF) throw err("Unterminated list");
            if (peek().kind() == TokenKind.NEWLINE || peek().kind() == TokenKind.COMMA) {
                next();
                continue;
            }
            elements.add(parseValueOrBlock());
            while (peek().kind() == TokenKind.COMMA || peek().kind() == TokenKind.NEWLINE) next();
        }
        expect(TokenKind.RBRACKET);
        return new Lst(elements);
    }
}
