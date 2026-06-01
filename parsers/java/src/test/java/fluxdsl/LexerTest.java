package fluxdsl;

import fluxdsl.lexer.Lexer;
import fluxdsl.lexer.LexerException;
import fluxdsl.token.Token;
import fluxdsl.token.TokenKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class LexerTest {

    private Token single(String text) {
        return new Lexer(text).next();
    }

    @Test
    void tokenizeString() {
        var t = single("\"hello\"");
        assertEquals(TokenKind.STRING, t.kind());
        assertEquals("hello", t.value());
    }

    @Test
    void tokenizeStringWithEscapes() {
        assertEquals("a\nb", single("\"a\\nb\"").value());
        assertEquals("a\tb", single("\"a\\tb\"").value());
        assertEquals("a\rb", single("\"a\\rb\"").value());
        assertEquals("a\"b", single("\"a\\\"b\"").value());
        assertEquals("a\\b", single("\"a\\\\b\"").value());
    }

    @Test
    void tokenizeUnicodeEscape() {
        assertEquals("©", single("\"\\u00a9\"").value());
        assertEquals("A", single("\"\\u0041\"").value());
    }

    @Test
    void tokenizeInteger() {
        var t = single("42");
        assertEquals(TokenKind.NUMBER, t.kind());
        assertEquals("42", t.value());
    }

    @Test
    void tokenizeNegativeInteger() {
        var t = single("-1");
        assertEquals(TokenKind.NUMBER, t.kind());
        assertEquals("-1", t.value());
    }

    @Test
    void tokenizeFloat() {
        var t = single("3.14");
        assertEquals(TokenKind.NUMBER, t.kind());
        assertEquals("3.14", t.value());
    }

    @Test
    void tokenizeScientificNotation() {
        var t = single("1.5e10");
        assertEquals(TokenKind.NUMBER, t.kind());
        assertEquals("1.5e10", t.value());
    }

    @Test
    void tokenizeBoolTrue() {
        var t = single("true");
        assertEquals(TokenKind.BOOLEAN, t.kind());
        assertEquals("true", t.value());
    }

    @Test
    void tokenizeBoolFalse() {
        var t = single("false");
        assertEquals(TokenKind.BOOLEAN, t.kind());
        assertEquals("false", t.value());
    }

    @Test
    void tokenizeNull() {
        var t = single("null");
        assertEquals(TokenKind.NULL, t.kind());
        assertEquals("null", t.value());
    }

    @Test
    void tokenizeBareString() {
        var t = single("foobar");
        assertEquals(TokenKind.BARE_STRING, t.kind());
        assertEquals("foobar", t.value());
    }

    @Test
    void tokenizeAtSchema() {
        var t = single("@schema");
        assertEquals(TokenKind.BARE_STRING, t.kind());
        assertEquals("@schema", t.value());
    }

    @Test
    void tokenizeBraces() {
        var lex = new Lexer("{}");
        assertEquals(TokenKind.LBRACE, lex.next().kind());
        assertEquals(TokenKind.RBRACE, lex.next().kind());
    }

    @Test
    void tokenizeBrackets() {
        var lex = new Lexer("[]");
        assertEquals(TokenKind.LBRACKET, lex.next().kind());
        assertEquals(TokenKind.RBRACKET, lex.next().kind());
    }

    @Test
    void tokenizeColon() {
        assertEquals(TokenKind.COLON, single(":").kind());
    }

    @Test
    void tokenizeComma() {
        assertEquals(TokenKind.COMMA, single(",").kind());
    }

    @Test
    void tokenizePipe() {
        assertEquals(TokenKind.PIPE, single("|").kind());
    }

    @Test
    void tokenizeNewline() {
        var t = single("\n");
        assertEquals(TokenKind.NEWLINE, t.kind());
    }

    @Test
    void tokenizeComment() {
        var lex = new Lexer("# this is a comment\nkey");
        var t1 = lex.next();
        assertEquals(TokenKind.NEWLINE, t1.kind());
        var t2 = lex.next();
        assertEquals(TokenKind.BARE_STRING, t2.kind());
        assertEquals("key", t2.value());
    }

    @Test
    void eofOnEmpty() {
        assertEquals(TokenKind.EOF, new Lexer("").next().kind());
    }

    @Test
    void eofAfterTokens() {
        var lex = new Lexer("42");
        assertEquals(TokenKind.NUMBER, lex.next().kind());
        assertEquals(TokenKind.EOF, lex.next().kind());
    }

    @Test
    void peekDoesNotAdvance() {
        var lex = new Lexer("foo");
        var p1 = lex.peek();
        var p2 = lex.peek();
        assertSame(p1, p2);
    }

    @Test
    void nextAfterPeekReturnsSameToken() {
        var lex = new Lexer("foo");
        var p = lex.peek();
        var n = lex.next();
        assertEquals(p, n);
    }

    @Test
    void multipleTokens() {
        var lex = new Lexer("name: \"hello\"\n");
        assertTok(lex.next(), TokenKind.BARE_STRING, "name");
        assertTok(lex.next(), TokenKind.COLON, ":");
        assertTok(lex.next(), TokenKind.STRING, "hello");
        assertTok(lex.next(), TokenKind.NEWLINE, "\n");
        assertEquals(TokenKind.EOF, lex.next().kind());
    }

    @Test
    void errorOnUnterminatedString() {
        assertThrows(LexerException.class, () -> single("\"hello"));
    }

    @Test
    void errorOnUnterminatedEscape() {
        assertThrows(LexerException.class, () -> single("\"hello\\"));
    }

    @Test
    void errorOnUnexpectedAt() {
        assertThrows(LexerException.class, () -> single("@invalid"));
    }

    @Test
    void errorOnBadCharacter() {
        assertThrows(LexerException.class, () -> single("~"));
    }

    private static void assertTok(Token t, TokenKind kind, String value) {
        assertEquals(kind, t.kind());
        assertEquals(value, t.value());
    }
}
