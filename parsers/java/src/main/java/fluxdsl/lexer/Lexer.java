package fluxdsl.lexer;

import fluxdsl.token.Token;
import fluxdsl.token.TokenKind;

public class Lexer {
    private final String text;
    private int pos;
    private int line = 1, col = 1;
    private Token peeked;

    public Lexer(String text) {
        this.text = text;
    }

    public int pos() {
        return pos;
    }

    public void pos(int p) {
        pos = p;
    }

    public int line() {
        return line;
    }

    public void line(int l) {
        line = l;
    }

    public int col() {
        return col;
    }

    public void col(int c) {
        col = c;
    }

    public String text() {
        return text;
    }

    public Token next() {
        if (peeked != null) {
            var t = peeked;
            peeked = null;
            return t;
        }
        return scan();
    }

    public Token peek() {
        if (peeked == null) peeked = scan();
        return peeked;
    }

    public void resetPeeked() {
        peeked = null;
    }

    private char ch() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private char advance() {
        var c = text.charAt(pos++);
        if (c == '\n') {
            line++;
            col = 1;
        } else col++;
        return c;
    }

    private void skipLine() {
        while (pos < text.length() && ch() != '\n' && ch() != '\r') advance();
    }

    private String readString() {
        var sb = new StringBuilder();
        advance();
        while (pos < text.length()) {
            var c = ch();
            if (c == '"') {
                advance();
                return sb.toString();
            }
            if (c == '\\') {
                advance();
                if (pos >= text.length()) throw err("Unterminated escape");
                var escaped = advance();
                switch (escaped) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'u' -> {
                        var hex = new StringBuilder();
                        for (int i = 0; i < 4; i++) {
                            if (pos >= text.length()) throw err("Unterminated \\u");
                            hex.append(advance());
                        }
                        var hexStr = hex.toString();
                        if (!hexStr.matches("[0-9a-fA-F]{4}"))
                            throw err("Invalid \\u escape sequence: \\u" + hexStr);
                        sb.append((char) Integer.parseInt(hexStr, 16));
                    }
                    default -> sb.append(escaped);
                }
                continue;
            }
            if (c == '\n' || c == '\r') throw err("Unterminated string");
            sb.append(advance());
        }
        throw err("Unterminated string");
    }

    private Token readBare(char start) {
        var sb = new StringBuilder();
        sb.append(start);
        while (pos < text.length()) {
            var c = ch();
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') sb.append(advance());
            else if (c == '.' || c == 'e' || c == 'E' || c == '+') sb.append(advance());
            else break;
        }
        var s = sb.toString();
        return switch (s) {
            case "true", "false" -> new Token(TokenKind.BOOLEAN, s, line, col);
            case "null" -> new Token(TokenKind.NULL, s, line, col);
            default -> {
                if (s.matches("-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?"))
                    yield new Token(TokenKind.NUMBER, s, line, col);
                yield new Token(TokenKind.BARE_STRING, s, line, col);
            }
        };
    }

    private Token scan() {
        while (pos < text.length()) {
            var c = ch();
            if (c == '#') {
                skipLine();
                continue;
            }
            if (c == ' ' || c == '\t') {
                advance();
                continue;
            }
            if (c == '\n' || c == '\r') {
                var tok = new Token(TokenKind.NEWLINE, String.valueOf(c), line, col);
                advance();
                if (c == '\r' && ch() == '\n') advance();
                return tok;
            }
            return switch (c) {
                case '{' -> {
                    advance();
                    yield tok(TokenKind.LBRACE, "{");
                }
                case '}' -> {
                    advance();
                    yield tok(TokenKind.RBRACE, "}");
                }
                case '[' -> {
                    advance();
                    yield tok(TokenKind.LBRACKET, "[");
                }
                case ']' -> {
                    advance();
                    yield tok(TokenKind.RBRACKET, "]");
                }
                case ':' -> {
                    advance();
                    yield tok(TokenKind.COLON, ":");
                }
                case ',' -> {
                    advance();
                    yield tok(TokenKind.COMMA, ",");
                }
                case '|' -> {
                    advance();
                    yield tok(TokenKind.PIPE, "|");
                }
                case '@' -> {
                    advance();
                    if (pos + 6 <= text.length() && text.startsWith("schema", pos)) {
                        pos += 6;
                        yield tok(TokenKind.BARE_STRING, "@schema");
                    }
                    throw err("Unexpected '@' — use @schema or quote it");
                }
                case '"' -> new Token(TokenKind.STRING, readString(), line, col);
                default -> {
                    if (Character.isLetter(c) || c == '_') yield readBare(advance());
                    if (Character.isDigit(c) || c == '-') yield readBare(advance());
                    throw err("Unexpected character '" + c + "'");
                }
            };
        }
        return new Token(TokenKind.EOF, "", line, col);
    }

    private Token tok(TokenKind k, String v) {
        return new Token(k, v, line, col);
    }

    private LexerException err(String msg) {
        return new LexerException(msg + " at " + line + ":" + col);
    }
}
