package fluxdsl.token;

public record Token(TokenKind kind, String value, int line, int col) {
}
