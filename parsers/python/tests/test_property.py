import unittest

try:
    from hypothesis import given, strategies as st
    HAS_HYPOTHESIS = True
except ImportError:
    HAS_HYPOTHESIS = False

from fluxdsl.lexer import Lexer, LexerError
from fluxdsl.parser import Parser, ParserError, parse
from fluxdsl.flux_mapper import to_dto, from_dto, to_json, from_json
from fluxdsl.json_ast import (
    Root, Pair, Include, Schema,
    Str, Num, Bool, Null, Obj, Lst,
    ObjPair, ObjInclude, ObjSchema,
)


def _make_root(items):
    return Root(items=items)


def _assert_equal(a, b):
    assert type(a) == type(b), f"Type mismatch: {type(a)} vs {type(b)}"
    if isinstance(a, Root):
        assert len(a.items) == len(b.items), f"Item count: {len(a.items)} vs {len(b.items)}"
        for ai, bi in zip(a.items, b.items):
            _assert_equal(ai, bi)
    elif isinstance(a, (Pair, ObjPair)):
        assert a.key == b.key, f"Key: {a.key!r} vs {b.key!r}"
        _assert_equal(a.value, b.value)
    elif isinstance(a, (Include, ObjInclude, Schema, ObjSchema)):
        assert a.path == b.path, f"Path: {a.path!r} vs {b.path!r}"
    elif isinstance(a, Str):
        assert a.value == b.value, f"Str: {a.value!r} vs {b.value!r}"
    elif isinstance(a, Num):
        assert a.value == b.value, f"Num: {a.value} vs {b.value}"
    elif isinstance(a, Bool):
        assert a.value == b.value, f"Bool: {a.value} vs {b.value}"
    elif isinstance(a, Null):
        pass
    elif isinstance(a, Obj):
        assert len(a.entries) == len(b.entries), f"Obj entry count: {len(a.entries)} vs {len(b.entries)}"
        for ae, be in zip(a.entries, b.entries):
            _assert_equal(ae, be)
    elif isinstance(a, Lst):
        assert len(a.elements) == len(b.elements), f"Lst count: {len(a.elements)} vs {len(b.elements)}"
        for ae, be in zip(a.elements, b.elements):
            _assert_equal(ae, be)
    else:
        raise AssertionError(f"Unknown type: {type(a)}")


def _random_value(draw):
    import random
    choice = random.random()
    if choice < 0.2:
        return Str(value=draw(st.text(max_size=20)))
    if choice < 0.35:
        return Num(value=draw(st.floats(allow_nan=False, allow_infinity=False)))
    if choice < 0.5:
        return Bool(value=draw(st.booleans()))
    if choice < 0.55:
        return Null()
    if choice < 0.75:
        entries = draw(st.lists(_random_obj_entry(draw), max_size=5))
        return Obj(entries=entries)
    elements = draw(st.lists(_random_value(draw), max_size=5))
    return Lst(elements=elements)


def _random_obj_entry(draw):
    import random
    keys = st.text(min_size=1, max_size=10, alphabet=st.characters(whitelist_categories=('L',)))
    choice = random.random()
    if choice < 0.15:
        return ObjInclude(path=draw(st.text(max_size=20)))
    if choice < 0.3:
        return ObjSchema(path=draw(st.text(max_size=20)))
    return ObjPair(key=draw(keys), value=draw(_random_value(draw)))


def _random_item(draw):
    import random
    keys = st.text(min_size=1, max_size=10, alphabet=st.characters(whitelist_categories=('L',)))
    choice = random.random()
    if choice < 0.1:
        return Include(path=draw(st.text(max_size=20)))
    if choice < 0.2:
        return Schema(path=draw(st.text(max_size=20)))
    return Pair(key=draw(keys), value=draw(_random_value(draw)))


def _random_root(draw):
    items = draw(st.lists(_random_item(draw), max_size=10))
    return Root(items=items)


if HAS_HYPOTHESIS:

    class TestPropertyBased(unittest.TestCase):

        @given(st.lists(st.text(max_size=20)), st.lists(st.integers()))
        def test_to_dto_from_dto_roundtrip(self, strings, ints):
            for s in strings:
                root = to_dto([{"type": "pair", "key": "k", "value": {"type": "string", "value": s}}])
                back = from_dto(root)
                assert len(back) == 1
                assert back[0]["type"] == "pair"
                assert back[0]["value"]["type"] == "string"
                assert back[0]["value"]["value"] == s

        @given(st.text())
        def test_parse_does_not_crash(self, text):
            try:
                parse(text)
            except (LexerError, ParserError):
                pass

        @given(st.binary(max_size=200))
        def test_parse_random_bytes_does_not_crash(self, data):
            try:
                text = data.decode("utf-8", errors="replace")
                parse(text)
            except (LexerError, ParserError):
                pass

else:

    class TestPropertyBased(unittest.TestCase):
        def test_skip(self):
            self.skipTest("hypothesis not installed; pip install hypothesis")


if __name__ == "__main__":
    unittest.main()
