from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import unittest
from fluxdsl.lexer import Lexer, LexerError, TokenKind, Token


class LexerTest(unittest.TestCase):

    def _single(self, text):
        return Lexer(text).next_token()

    def test_string(self):
        t = self._single('"hello"')
        self.assertEqual(TokenKind.STRING, t.kind)
        self.assertEqual("hello", t.value)

    def test_string_with_escapes(self):
        self.assertEqual("a\nb", self._single('"a\\nb"').value)
        self.assertEqual("a\tb", self._single('"a\\tb"').value)
        self.assertEqual("a\rb", self._single('"a\\rb"').value)
        self.assertEqual('a"b', self._single('"a\\"b"').value)
        self.assertEqual("a\\b", self._single('"a\\\\b"').value)

    def test_unicode_escape(self):
        self.assertEqual("\u00a9", self._single('"\\u00a9"').value)
        self.assertEqual("A", self._single('"\\u0041"').value)

    def test_integer(self):
        t = self._single("42")
        self.assertEqual(TokenKind.NUMBER, t.kind)
        self.assertEqual("42", t.value)

    def test_negative_integer(self):
        t = self._single("-1")
        self.assertEqual(TokenKind.NUMBER, t.kind)
        self.assertEqual("-1", t.value)

    def test_float(self):
        t = self._single("3.14")
        self.assertEqual(TokenKind.NUMBER, t.kind)
        self.assertEqual("3.14", t.value)

    def test_scientific_notation(self):
        t = self._single("1.5e10")
        self.assertEqual(TokenKind.NUMBER, t.kind)
        self.assertEqual("1.5e10", t.value)

    def test_bool_true(self):
        t = self._single("true")
        self.assertEqual(TokenKind.BOOLEAN, t.kind)
        self.assertEqual("true", t.value)

    def test_bool_false(self):
        t = self._single("false")
        self.assertEqual(TokenKind.BOOLEAN, t.kind)
        self.assertEqual("false", t.value)

    def test_null(self):
        t = self._single("null")
        self.assertEqual(TokenKind.NULL, t.kind)
        self.assertEqual("null", t.value)

    def test_bare_string(self):
        t = self._single("foobar")
        self.assertEqual(TokenKind.BARE_STRING, t.kind)
        self.assertEqual("foobar", t.value)

    def test_at_schema(self):
        t = self._single("@schema")
        self.assertEqual(TokenKind.BARE_STRING, t.kind)
        self.assertEqual("@schema", t.value)

    def test_braces(self):
        lex = Lexer("{}")
        self.assertEqual(TokenKind.LBRACE, lex.next_token().kind)
        self.assertEqual(TokenKind.RBRACE, lex.next_token().kind)

    def test_brackets(self):
        lex = Lexer("[]")
        self.assertEqual(TokenKind.LBRACKET, lex.next_token().kind)
        self.assertEqual(TokenKind.RBRACKET, lex.next_token().kind)

    def test_colon(self):
        self.assertEqual(TokenKind.COLON, self._single(":").kind)

    def test_comma(self):
        self.assertEqual(TokenKind.COMMA, self._single(",").kind)

    def test_pipe(self):
        self.assertEqual(TokenKind.PIPE, self._single("|").kind)

    def test_newline(self):
        t = self._single("\n")
        self.assertEqual(TokenKind.NEWLINE, t.kind)

    def test_comment(self):
        lex = Lexer("# this is a comment\nkey")
        t1 = lex.next_token()
        self.assertEqual(TokenKind.NEWLINE, t1.kind)
        t2 = lex.next_token()
        self.assertEqual(TokenKind.BARE_STRING, t2.kind)
        self.assertEqual("key", t2.value)

    def test_eof_on_empty(self):
        self.assertEqual(TokenKind.EOF, Lexer("").next_token().kind)

    def test_eof_after_tokens(self):
        lex = Lexer("42")
        self.assertEqual(TokenKind.NUMBER, lex.next_token().kind)
        self.assertEqual(TokenKind.EOF, lex.next_token().kind)

    def test_peek_does_not_advance(self):
        lex = Lexer("foo")
        p1 = lex.peek_token()
        p2 = lex.peek_token()
        self.assertIs(p1, p2)

    def test_next_after_peek_returns_same(self):
        lex = Lexer("foo")
        p = lex.peek_token()
        n = lex.next_token()
        self.assertEqual(p, n)

    def test_multiple_tokens(self):
        lex = Lexer('name: "hello"\n')
        self.assertTok(lex.next_token(), TokenKind.BARE_STRING, "name")
        self.assertTok(lex.next_token(), TokenKind.COLON, ":")
        self.assertTok(lex.next_token(), TokenKind.STRING, "hello")
        self.assertTok(lex.next_token(), TokenKind.NEWLINE, "\n")
        self.assertEqual(TokenKind.EOF, lex.next_token().kind)

    def test_error_on_unterminated_string(self):
        with self.assertRaises(LexerError):
            self._single('"hello')

    def test_error_on_unterminated_escape(self):
        with self.assertRaises(LexerError):
            self._single('"hello\\')

    def test_error_on_unexpected_at(self):
        with self.assertRaises(LexerError):
            self._single("@invalid")

    def test_error_on_bad_character(self):
        with self.assertRaises(LexerError):
            self._single("~")

    def assertTok(self, t, kind, value):
        self.assertEqual(kind, t.kind)
        self.assertEqual(value, t.value)


if __name__ == "__main__":
    unittest.main()
