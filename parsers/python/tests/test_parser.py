from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import unittest
from fluxdsl.lexer import Lexer, TokenKind
from fluxdsl.parser import Parser, ParserError


class ParserTest(unittest.TestCase):

    def _parse(self, text):
        return Parser(Lexer(text)).parse_document()

    def _assert_pair(self, items, index, key, expected_type, expected_value=None):
        node = items[index]
        self.assertEqual("pair", node["type"])
        self.assertEqual(key, node["key"])
        val = node["value"]
        self.assertEqual(expected_type, val["type"])
        if expected_value is not None:
            self.assertEqual(expected_value, val.get("value"))

    def test_empty(self):
        root = self._parse("")
        self.assertEqual([], root)

    def test_string_value(self):
        root = self._parse('name: "hello"')
        self._assert_pair(root, 0, "name", "string", "hello")

    def test_number_int(self):
        root = self._parse("count: 42")
        self._assert_pair(root, 0, "count", "number", 42)

    def test_number_float(self):
        root = self._parse("pi: 3.14")
        self._assert_pair(root, 0, "pi", "number", 3.14)

    def test_bool_true(self):
        root = self._parse("active: true")
        self._assert_pair(root, 0, "active", "boolean", True)

    def test_bool_false(self):
        root = self._parse("active: false")
        self._assert_pair(root, 0, "active", "boolean", False)

    def test_null(self):
        root = self._parse("data: null")
        self._assert_pair(root, 0, "data", "null")

    def test_bare_string_value(self):
        root = self._parse("key: bare_value")
        self._assert_pair(root, 0, "key", "string", "bare_value")

    def test_nested_object(self):
        root = self._parse("db {\n  host: \"localhost\"\n  port: 5432\n}")
        node = root[0]
        self.assertEqual("pair", node["type"])
        self.assertEqual("db", node["key"])
        obj = node["value"]
        self.assertEqual("object", obj["type"])
        entries = obj["entries"]
        self.assertEqual(2, len(entries))
        self.assertEqual("host", entries[0]["key"])
        self.assertEqual("localhost", entries[0]["value"]["value"])
        self.assertEqual("port", entries[1]["key"])
        self.assertEqual(5432, entries[1]["value"]["value"])

    def test_list(self):
        root = self._parse("items: [1, 2, 3]")
        self._assert_pair(root, 0, "items", "list")
        lst = root[0]["value"]
        self.assertEqual(3, len(lst["elements"]))
        self.assertEqual(1, lst["elements"][0]["value"])
        self.assertEqual(2, lst["elements"][1]["value"])
        self.assertEqual(3, lst["elements"][2]["value"])

    def test_list_mixed_types(self):
        root = self._parse('mix: ["a", 1, true, null]')
        lst = root[0]["value"]
        self.assertEqual(4, len(lst["elements"]))
        self.assertEqual("string", lst["elements"][0]["type"])
        self.assertEqual("number", lst["elements"][1]["type"])
        self.assertEqual("boolean", lst["elements"][2]["type"])
        self.assertEqual("null", lst["elements"][3]["type"])

    def test_list_of_objects(self):
        root = self._parse("items: [\n  { name: \"a\" }\n  { name: \"b\" }\n]")
        lst = root[0]["value"]
        self.assertEqual(2, len(lst["elements"]))
        self.assertEqual("object", lst["elements"][0]["type"])
        self.assertEqual("object", lst["elements"][1]["type"])

    def test_include(self):
        root = self._parse('include "base.fx"')
        self.assertEqual(1, len(root))
        self.assertEqual("include", root[0]["type"])
        self.assertEqual("base.fx", root[0]["path"])

    def test_schema(self):
        root = self._parse('@schema "db.fx"')
        self.assertEqual(1, len(root))
        self.assertEqual("schema", root[0]["type"])
        self.assertEqual("db.fx", root[0]["path"])

    def test_multiple_top_level_items(self):
        root = self._parse('include "base.fx"\nkey: "val"\n@schema "s.fx"')
        self.assertEqual(3, len(root))
        self.assertEqual("include", root[0]["type"])
        self.assertEqual("pair", root[1]["type"])
        self.assertEqual("schema", root[2]["type"])

    def test_object_with_include(self):
        root = self._parse('cfg {\n  include "ext.fx"\n  name: "x"\n}')
        obj = root[0]["value"]
        entries = obj["entries"]
        self.assertEqual(2, len(entries))
        self.assertEqual("include", entries[0]["type"])
        self.assertEqual("ext.fx", entries[0]["path"])
        self.assertEqual("name", entries[1]["key"])
        self.assertEqual("x", entries[1]["value"]["value"])

    def test_block_string(self):
        root = self._parse("data {|\n  hello\n  world\n}")
        self._assert_pair(root, 0, "data", "string", "hello\nworld")

    def test_explicit_colon(self):
        root = self._parse('key: "val"')
        self._assert_pair(root, 0, "key", "string", "val")

    def test_implicit_colon(self):
        root = self._parse('key "val"')
        self._assert_pair(root, 0, "key", "string", "val")

    def test_error_on_missing_value(self):
        with self.assertRaises(ParserError):
            self._parse("key:")

    def test_error_on_expected_key(self):
        with self.assertRaises(ParserError):
            self._parse(': "val"')

    def test_empty_braces_parsed(self):
        try:
            self._parse("{}")
        except ParserError:
            self.fail("Empty braces raised ParserError")


if __name__ == "__main__":
    unittest.main()
