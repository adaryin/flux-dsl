import re
from enum import Enum, auto
from dataclasses import dataclass


class TokenKind(Enum):
    STRING = auto()
    NUMBER = auto()
    BOOLEAN = auto()
    NULL = auto()
    BARE_STRING = auto()
    LBRACE = auto()
    RBRACE = auto()
    LBRACKET = auto()
    RBRACKET = auto()
    COLON = auto()
    COMMA = auto()
    PIPE = auto()
    NEWLINE = auto()
    EOF = auto()


@dataclass
class Token:
    kind: TokenKind
    value: str = ""
    line: int = 0
    col: int = 0


class LexerError(Exception):
    pass


_BOOLEAN_VALUES = {"true", "false"}
_NUMBER_RE = re.compile(r"-?(0|[1-9]\d*)(\.\d+)?([eE][+-]?\d+)?")


class Lexer:
    """Tokenizes FluxDSL source text into a stream of tokens."""
    def __init__(self, text: str):
        self.text = text
        self.pos = 0
        self.line = 1
        self.col = 1
        self._peeked: Token | None = None

    def _error(self, msg: str) -> LexerError:
        return LexerError(f"{msg} at {self.line}:{self.col}")

    def _peek(self) -> str:
        return self.text[self.pos] if self.pos < len(self.text) else "\0"

    def _advance(self) -> str:
        ch = self.text[self.pos]
        self.pos += 1
        if ch == "\n":
            self.line += 1
            self.col = 1
        else:
            self.col += 1
        return ch

    def _skip_line(self) -> None:
        while self.pos < len(self.text) and self._peek() not in "\n\r":
            self._advance()

    def _read_string(self) -> str:
        raw = []
        self._advance()
        while self.pos < len(self.text):
            ch = self._peek()
            if ch == '"':
                self._advance()
                return "".join(raw)
            if ch == "\\":
                self._advance()
                if self.pos >= len(self.text):
                    raise self._error("Unterminated escape in string")
                esc = self._advance()
                if esc == "n":
                    raw.append("\n")
                elif esc == "t":
                    raw.append("\t")
                elif esc == "r":
                    raw.append("\r")
                elif esc == "u":
                    hex_str = ""
                    for _ in range(4):
                        if self.pos >= len(self.text):
                            raise self._error("Unterminated \\u escape")
                        hex_str += self._advance()
                    if not all(c in "0123456789abcdefABCDEF" for c in hex_str):
                        raise self._error(f"Invalid \\u escape sequence: \\u{hex_str}")
                    raw.append(chr(int(hex_str, 16)))
                else:
                    raw.append(esc)
                continue
            if ch in "\n\r":
                raise self._error("Unterminated string")
            raw.append(self._advance())
        raise self._error("Unterminated string")

    def _read_bare(self, start: str) -> Token:
        raw = start
        while self.pos < len(self.text):
            ch = self._peek()
            if ch.isalnum() or ch in ("_", "-", ".", "e", "E", "+"):
                raw += self._advance()
            else:
                break
        if raw in _BOOLEAN_VALUES:
            return Token(TokenKind.BOOLEAN, raw, self.line, self.col)
        if raw == "null":
            return Token(TokenKind.NULL, raw, self.line, self.col)
        if _NUMBER_RE.fullmatch(raw):
            return Token(TokenKind.NUMBER, raw, self.line, self.col)
        return Token(TokenKind.BARE_STRING, raw, self.line, self.col)

    def next_token(self) -> Token:
        """Return the next token from the input, consuming it."""
        if self._peeked:
            tok = self._peeked
            self._peeked = None
            return tok
        return self._scan()

    def peek_token(self) -> Token:
        """Return the next token without consuming it."""
        if not self._peeked:
            self._peeked = self._scan()
        return self._peeked

    def _scan(self) -> Token:
        while self.pos < len(self.text):
            ch = self._peek()

            if ch == "#":
                self._skip_line()
                continue

            if ch in " \t":
                self._advance()
                continue

            if ch in "\n\r":
                tok = Token(TokenKind.NEWLINE, self._advance(), self.line, self.col)
                if ch == "\r" and self._peek() == "\n":
                    self._advance()
                    tok.value = "\r\n"
                return tok

            if ch == "{":
                self._advance()
                return Token(TokenKind.LBRACE, "{", self.line, self.col)
            if ch == "}":
                self._advance()
                return Token(TokenKind.RBRACE, "}", self.line, self.col)
            if ch == "[":
                self._advance()
                return Token(TokenKind.LBRACKET, "[", self.line, self.col)
            if ch == "]":
                self._advance()
                return Token(TokenKind.RBRACKET, "]", self.line, self.col)
            if ch == ":":
                self._advance()
                return Token(TokenKind.COLON, ":", self.line, self.col)
            if ch == ",":
                self._advance()
                return Token(TokenKind.COMMA, ",", self.line, self.col)

            if ch == "|":
                self._advance()
                return Token(TokenKind.PIPE, "|", self.line, self.col)

            if ch == "@":
                start = self._advance()
                if self.text[self.pos:self.pos + 6] == "schema":
                    for _ in range(6):
                        self._advance()
                    return Token(TokenKind.BARE_STRING, "@schema", self.line, self.col)
                raise self._error("Unexpected '@' — use @schema or quote it")

            if ch == '"':
                val = self._read_string()
                return Token(TokenKind.STRING, val, self.line, self.col)

            if ch.isalpha() or ch == "_":
                return self._read_bare(self._advance())

            if ch.isdigit() or ch == "-":
                start = self._advance()
                return self._read_bare(start)

            raise self._error(f"Unexpected character {ch!r}")

        return Token(TokenKind.EOF, "", self.line, self.col)
