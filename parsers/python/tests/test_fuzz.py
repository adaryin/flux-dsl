import random
import string
import unittest

from fluxdsl.lexer import LexerError
from fluxdsl.parser import ParserError, parse


class TestFuzz(unittest.TestCase):

    def test_random_ascii_strings(self):
        for _ in range(500):
            length = random.randint(0, 200)
            text = "".join(random.choice(string.printable) for _ in range(length))
            try:
                parse(text)
            except (LexerError, ParserError):
                pass
            except Exception as e:
                self.fail(f"Unexpected exception for text {text!r}: {e}")

    def test_random_bytes_as_utf8(self):
        for _ in range(500):
            length = random.randint(0, 200)
            data = bytes(random.randint(0, 255) for _ in range(length))
            text = data.decode("utf-8", errors="replace")
            try:
                parse(text)
            except (LexerError, ParserError):
                pass
            except Exception as e:
                self.fail(f"Unexpected exception for text (length={len(text)}): {e}")

    def test_repeated_characters(self):
        chars = string.printable
        for _ in range(200):
            ch = random.choice(chars)
            length = random.randint(0, 100)
            text = ch * length
            try:
                parse(text)
            except (LexerError, ParserError):
                pass
            except Exception as e:
                self.fail(f"Unexpected exception for {ch!r}*{length}: {e}")

    def test_unclosed_constructs(self):
        texts = [
            'key: "unclosed string',
            'key: {',
            'key: [',
            '{|',
            'key: {|',
            '{| abc',
            'include "no close',
            '@schema "no close',
        ]
        for text in texts:
            try:
                parse(text)
            except (LexerError, ParserError):
                pass
            except Exception as e:
                self.fail(f"Unexpected exception for {text!r}: {e}")

    def test_nested_constructs_deep(self):
        depth = 50
        text = "a " * depth + "{" * depth + "}" * depth + "\n"
        try:
            parse(text)
        except (LexerError, ParserError):
            pass
        except Exception as e:
            self.fail(f"Unexpected exception for deeply nested text: {e}")

    def test_very_long_lines(self):
        text = "key: " + "x" * 10000 + "\n"
        try:
            parse(text)
        except (LexerError, ParserError):
            pass
        except Exception as e:
            self.fail(f"Unexpected exception for long line: {e}")

    def test_unicode_snowflake(self):
        for _ in range(100):
            length = random.randint(0, 50)
            text = "".join(chr(random.randint(0, 0x10FFFF)) for _ in range(length))
            try:
                parse(text)
            except (LexerError, ParserError):
                pass
            except (ValueError, UnicodeEncodeError):
                pass
            except Exception as e:
                self.fail(f"Unexpected exception for unicode text (length={len(text)}): {e}")
