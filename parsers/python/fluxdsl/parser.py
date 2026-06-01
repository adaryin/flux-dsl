from .lexer import Token, TokenKind, Lexer, LexerError


class ParserError(Exception):
    pass


class Parser:
    """Recursive-descent parser for FluxDSL source text."""
    def __init__(self, lexer: Lexer, strict: bool = False):
        self.lexer = lexer
        self.strict = strict

    def _error(self, msg: str) -> ParserError:
        tok = self.lexer.peek_token()
        return ParserError(f"{msg} at {tok.line}:{tok.col}")

    def _peek(self) -> Token:
        return self.lexer.peek_token()

    def _next(self) -> Token:
        return self.lexer.next_token()

    def _skip_newlines(self) -> None:
        while self._peek().kind == TokenKind.NEWLINE:
            self._next()

    def _expect(self, kind: TokenKind, value: str | None = None) -> Token:
        tok = self._next()
        if tok.kind != kind or (value is not None and tok.value != value):
            expected = kind.name
            if value is not None:
                expected += f" ({value!r})"
            raise self._error(f"Expected {expected}, got {tok.kind.name} ({tok.value!r})")
        return tok

    def parse_document(self) -> list:
        items = []
        self._skip_newlines()
        while self._peek().kind != TokenKind.EOF:
            tok = self._peek()

            if tok.kind == TokenKind.BARE_STRING and tok.value == "include":
                if self.strict:
                    raise self._error("include directive is not allowed in strict mode")
                items.append(self._parse_include())
            elif tok.kind == TokenKind.BARE_STRING and tok.value == "@schema":
                if self.strict:
                    raise self._error("@schema directive is not allowed in strict mode")
                items.append(self._parse_schema())
            elif tok.kind in (TokenKind.BARE_STRING, TokenKind.STRING):
                items.append(self._parse_pair(top_level=True))
            else:
                self._next()
                continue

            self._skip_newlines()
        return items

    def _parse_include(self) -> dict:
        self._next()
        path_tok = self._expect(TokenKind.STRING)
        return {"type": "include", "path": path_tok.value}

    def _parse_schema(self) -> dict:
        self._next()
        path_tok = self._expect(TokenKind.STRING)
        return {"type": "schema", "path": path_tok.value}

    def _parse_pair(self, top_level: bool = False) -> dict:
        key_tok = self._next()
        if key_tok.kind == TokenKind.BARE_STRING and self.strict:
            raise self._error("Bare string keys are not allowed in strict mode; use quoted strings")
        if key_tok.kind in (TokenKind.STRING, TokenKind.BARE_STRING):
            key = key_tok.value
        else:
            raise self._error(f"Expected key, got {key_tok.kind.name} ({key_tok.value!r})")

        if self._peek().kind == TokenKind.COLON:
            self._next()

        value = self._parse_value_or_block()
        return {"type": "pair", "key": key, "value": value}

    def _parse_value_or_block(self):
        tok = self._peek()

        if tok.kind == TokenKind.LBRACE:
            return self._parse_braced()

        return self._parse_value()

    # ---- Braced content: block_string, block_object, inline_object ----

    def _parse_braced(self):
        self._next()
        while self._peek().kind == TokenKind.NEWLINE:
            self._next()
        if self._peek().kind == TokenKind.PIPE:
            self._next()
            self._expect(TokenKind.NEWLINE)
            return self._finish_block_string()
        return self._parse_object_body()

    def _finish_block_string(self):
        ref_indent = None
        lines = []
        while self.lexer.pos < len(self.lexer.text):
            start = self.lexer.pos
            line = ""
            reached_eof = False
            while self.lexer.pos < len(self.lexer.text):
                ch = self.lexer.text[self.lexer.pos]
                if ch in "\n\r":
                    if ch == "\r" and self.lexer.pos + 1 < len(self.lexer.text) and self.lexer.text[self.lexer.pos + 1] == "\n":
                        self.lexer.pos += 1
                    self.lexer.pos += 1
                    self.lexer.line += 1
                    self.lexer.col = 1
                    break
                line += ch
                self.lexer.pos += 1
            else:
                reached_eof = True

            trimmed = line.lstrip()
            indent = len(line) - len(trimmed)

            if trimmed == "}" and ref_indent is not None and indent < ref_indent:
                self.lexer.pos = start + indent
                self.lexer.col = indent + 1
                break

            if trimmed:
                if ref_indent is None or indent < ref_indent:
                    ref_indent = indent
                lines.append(line)

            if reached_eof:
                break

        self.lexer._peeked = None

        gap_end = self.lexer.pos
        while gap_end < len(self.lexer.text) and self.lexer.text[gap_end] in (" ", "\t", "\n", "\r"):
            gap_end += 1

        if gap_end < len(self.lexer.text) and self.lexer.text[gap_end] == "}":
            self.lexer.pos = gap_end + 1
        else:
            self.lexer.pos = gap_end
            self._expect(TokenKind.RBRACE)

        return {"type": "string", "value": self._dedent(lines)}

    def _dedent(self, lines: list[str]) -> str:
        if not lines:
            return ""
        min_indent = min(
            (len(line) - len(line.lstrip()) for line in lines if line.strip()),
            default=0,
        )
        return "\n".join(line[min_indent:] for line in lines)

    def _parse_object_body(self):
        entries = []
        self._skip_gap_in_braces()
        while self._peek().kind != TokenKind.RBRACE:
            if self._peek().kind == TokenKind.EOF:
                raise self._error("Unterminated object")
            tok = self._peek()
            if tok.kind in (TokenKind.NEWLINE, TokenKind.COMMA):
                self._next()
                continue

            key_tok = self._peek()
            if key_tok.kind in (TokenKind.BARE_STRING, TokenKind.STRING):
                if key_tok.kind == TokenKind.BARE_STRING and key_tok.value == "include":
                    if self.strict:
                        raise self._error("include directive is not allowed in strict mode")
                    self._next()
                    path_tok = self._expect(TokenKind.STRING)
                    entries.append({"type": "include", "path": path_tok.value})
                elif key_tok.kind == TokenKind.BARE_STRING and key_tok.value == "@schema":
                    if self.strict:
                        raise self._error("@schema directive is not allowed in strict mode")
                    self._next()
                    path_tok = self._expect(TokenKind.STRING)
                    entries.append({"type": "schema", "path": path_tok.value})
                else:
                    pair = self._parse_pair()
                    entries.append({"key": pair["key"], "value": pair["value"]})
            else:
                raise self._error(f"Unexpected token inside object: {key_tok.kind.name} ({key_tok.value!r})")
            self._skip_gap_in_braces()
        self._next()
        return {"type": "object", "entries": entries}

    def _skip_gap_in_braces(self) -> None:
        while self._peek().kind in (TokenKind.NEWLINE, TokenKind.COMMA):
            self._next()

    # ---- Value parsing ----

    def _parse_value(self):
        tok = self._peek()

        if tok.kind == TokenKind.STRING:
            self._next()
            return {"type": "string", "value": tok.value}

        if tok.kind == TokenKind.NUMBER:
            self._next()
            raw = tok.value
            if "." in raw or "e" in raw or "E" in raw:
                return {"type": "number", "value": float(raw)}
            return {"type": "number", "value": int(raw)}

        if tok.kind == TokenKind.BOOLEAN:
            self._next()
            return {"type": "boolean", "value": tok.value == "true"}

        if tok.kind == TokenKind.NULL:
            self._next()
            return {"type": "null"}

        if tok.kind == TokenKind.BARE_STRING:
            if self.strict:
                raise self._error("Bare string values are not allowed in strict mode; use quoted strings")
            self._next()
            return {"type": "string", "value": tok.value}

        if tok.kind == TokenKind.LBRACKET:
            return self._parse_list()

        if tok.kind == TokenKind.LBRACE:
            return self._parse_braced()

        raise self._error(f"Expected value, got {tok.kind.name} ({tok.value!r})")

    def _parse_list(self):
        self._next()
        elements = []
        self._skip_gap_in_braces()
        while self._peek().kind != TokenKind.RBRACKET:
            if self._peek().kind == TokenKind.EOF:
                raise self._error("Unterminated list")
            if self._peek().kind in (TokenKind.NEWLINE, TokenKind.COMMA):
                self._next()
                continue
            elements.append(self._parse_value_or_block())
            while self._peek().kind in (TokenKind.COMMA, TokenKind.NEWLINE):
                self._next()
        self._expect(TokenKind.RBRACKET)
        return {"type": "list", "elements": elements}


def parse(text: str, strict: bool = False) -> list:
    """Parse FluxDSL source text into a list of AST nodes."""
    lexer = Lexer(text)
    parser = Parser(lexer, strict=strict)
    return parser.parse_document()
