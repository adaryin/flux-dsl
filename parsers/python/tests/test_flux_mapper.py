from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import unittest
from fluxdsl.lexer import Lexer
from fluxdsl.parser import Parser
from fluxdsl.json_ast import (
    Root, Pair, Include, Schema,
    Str, Num, Bool, Null, Obj, Lst,
    ObjPair, ObjInclude, ObjSchema,
)
from fluxdsl.flux_mapper import to_json, from_json, to_dto, from_dto


class FluxMapperTest(unittest.TestCase):

    def _round_trip(self, root, label=None):
        json_str = to_json(root)
        restored = from_json(json_str)
        self.assertEqual(root, restored, label or "Round-trip failed")

    def _parse(self, text):
        return Parser(Lexer(text)).parse_document()

    def test_round_trip_empty(self):
        self._round_trip(Root(items=[]))

    def test_round_trip_string(self):
        self._round_trip(Root(items=[Pair(key="k", value=Str("hello"))]))

    def test_round_trip_number(self):
        self._round_trip(Root(items=[Pair(key="k", value=Num(42))]))

    def test_round_trip_bool(self):
        self._round_trip(Root(items=[Pair(key="k", value=Bool(True))]))

    def test_round_trip_null(self):
        self._round_trip(Root(items=[Pair(key="k", value=Null())]))

    def test_round_trip_all_types(self):
        self._round_trip(Root(items=[
            Pair(key="s", value=Str("hi")),
            Pair(key="n", value=Num(-3.14)),
            Pair(key="b", value=Bool(False)),
            Pair(key="x", value=Null()),
        ]))

    def test_round_trip_include(self):
        self._round_trip(Root(items=[Include(path="base.fx")]))

    def test_round_trip_schema(self):
        self._round_trip(Root(items=[Schema(path="s.fx")]))

    def test_round_trip_nested_object(self):
        inner = Obj(entries=[ObjPair(key="x", value=Str("y"))])
        self._round_trip(Root(items=[Pair(key="outer", value=inner)]))

    def test_round_trip_deeply_nested(self):
        obj = Obj(entries=[
            ObjPair(key="a", value=Obj(entries=[
                ObjPair(key="b", value=Obj(entries=[
                    ObjPair(key="c", value=Num(1))
                ]))
            ]))
        ])
        self._round_trip(Root(items=[Pair(key="deep", value=obj)]))

    def test_round_trip_list(self):
        lst = Lst(elements=[Str("a"), Num(1), Bool(True), Null()])
        self._round_trip(Root(items=[Pair(key="items", value=lst)]))

    def test_round_trip_list_of_objects(self):
        lst = Lst(elements=[
            Obj(entries=[ObjPair(key="x", value=Num(1))]),
            Obj(entries=[ObjPair(key="y", value=Num(2))]),
        ])
        self._round_trip(Root(items=[Pair(key="items", value=lst)]))

    def test_round_trip_object_with_include_entry(self):
        obj = Obj(entries=[
            ObjInclude(path="ext.fx"),
            ObjPair(key="name", value=Str("test")),
        ])
        self._round_trip(Root(items=[Pair(key="cfg", value=obj)]))

    def test_round_trip_object_with_schema_entry(self):
        obj = Obj(entries=[
            ObjSchema(path="s.fx"),
            ObjPair(key="x", value=Num(1)),
        ])
        self._round_trip(Root(items=[Pair(key="cfg", value=obj)]))

    def test_round_trip_complex(self):
        root = Root(items=[
            Include(path="base.fx"),
            Schema(path="db_schema.fx"),
            Pair(key="db", value=Obj(entries=[
                ObjPair(key="host", value=Str("localhost")),
                ObjPair(key="port", value=Num(5432)),
                ObjInclude(path="pool.fx"),
            ])),
            Pair(key="items", value=Lst(elements=[
                Str("a"),
                Num(2),
            ])),
        ])
        self._round_trip(root)

    def test_to_dto_from_dto(self):
        ast = self._parse('key: "hello"\nnum: 42')
        root = to_dto(ast)
        back = from_dto(root)
        self.assertEqual(ast, back)

    def test_json_uses_short_type_names(self):
        root = Root(items=[
            Pair(key="s", value=Str("hi")),
            Pair(key="n", value=Num(1)),
            Pair(key="b", value=Bool(True)),
            Pair(key="x", value=Null()),
            Pair(key="o", value=Obj(entries=[])),
            Pair(key="l", value=Lst(elements=[])),
        ])
        json_str = to_json(root)
        self.assertIn('"str"', json_str)
        self.assertIn('"num"', json_str)
        self.assertIn('"bool"', json_str)
        self.assertIn('"null"', json_str)
        self.assertIn('"obj"', json_str)
        self.assertIn('"list"', json_str)

    def test_round_trip_all_sample_files(self):
        samples_dir = self._find_samples_dir()
        self.assertIsNotNone(samples_dir, "Cannot find samples directory")
        for path in sorted(samples_dir.rglob("*.fx")):
            text = path.read_text()
            ast = self._parse(text)
            root = to_dto(ast)
            self._round_trip(root, str(path))

    def _find_samples_dir(self):
        script = Path(__file__).resolve()
        for parent in [script, *script.parents]:
            candidate = parent / "samples"
            if candidate.is_dir() and list(candidate.rglob("*.fx")):
                return candidate
        return None


if __name__ == "__main__":
    unittest.main()
