from __future__ import annotations

from dataclasses import dataclass, field
from typing import List


@dataclass
class Root:
    """DTO root containing a list of top-level items."""
    items: List["Item"] = field(default_factory=list)


class Item:
    """Base type for top-level DTO items."""


@dataclass
class Pair(Item):
    key: str
    value: "Value"


@dataclass
class Include(Item):
    path: str


@dataclass
class Schema(Item):
    path: str


class Value:
    """Base type for DTO value nodes."""


@dataclass
class Str(Value):
    value: str


@dataclass
class Num(Value):
    value: float | int


@dataclass
class Bool(Value):
    value: bool


@dataclass
class Null(Value):
    pass


@dataclass
class Obj(Value):
    entries: List["ObjEntry"] = field(default_factory=list)


@dataclass
class Lst(Value):
    elements: List[Value] = field(default_factory=list)


class ObjEntry:
    """Base type for DTO object entries."""


@dataclass
class ObjPair(ObjEntry):
    key: str
    value: Value


@dataclass
class ObjInclude(ObjEntry):
    path: str


@dataclass
class ObjSchema(ObjEntry):
    path: str


_TYPE_TAG: dict[type, str] = {
    Root: "",
    Str: "str",
    Num: "num",
    Bool: "bool",
    Null: "null",
    Obj: "obj",
    Lst: "list",
    Pair: "pair",
    Include: "include",
    Schema: "schema",
    ObjPair: "pair",
    ObjInclude: "include",
    ObjSchema: "schema",
}


def _to_dict(obj) -> dict:
    """Serialize a DTO object to a JSON-serializable dict."""
    cls = type(obj)

    if isinstance(obj, Root):
        return {"items": [_to_dict(i) for i in obj.items]}

    tag = _TYPE_TAG.get(cls)
    if tag is None:
        raise TypeError(f"Unknown type: {cls}")

    d = {"type": tag}

    if isinstance(obj, (Pair, ObjPair)):
        d["key"] = obj.key
        d["value"] = _to_dict(obj.value)
    elif isinstance(obj, (Include, ObjInclude, Schema, ObjSchema)):
        d["path"] = obj.path
    elif isinstance(obj, Str):
        d["value"] = obj.value
    elif isinstance(obj, Num):
        d["value"] = obj.value
    elif isinstance(obj, Bool):
        d["value"] = obj.value
    elif isinstance(obj, Obj):
        d["entries"] = [_to_dict(e) for e in obj.entries]
    elif isinstance(obj, Lst):
        d["elements"] = [_to_dict(e) for e in obj.elements]

    return d


def item_from_dict(d: dict) -> Item:
    """Deserialize a dict to an Item DTO."""
    t = d["type"]
    if t == "pair":
        return Pair(key=d["key"], value=value_from_dict(d["value"]))
    if t == "include":
        return Include(path=d["path"])
    if t == "schema":
        return Schema(path=d["path"])
    raise ValueError(f"Unknown item type: {t}")


def value_from_dict(d: dict) -> Value:
    """Deserialize a dict to a Value DTO."""
    t = d["type"]
    if t == "str":
        return Str(value=d["value"])
    if t == "num":
        return Num(value=d["value"])
    if t == "bool":
        return Bool(value=d["value"])
    if t == "null":
        return Null()
    if t == "obj":
        return Obj(entries=[entry_from_dict(e) for e in d.get("entries", [])])
    if t == "list":
        return Lst(elements=[value_from_dict(e) for e in d.get("elements", [])])
    raise ValueError(f"Unknown value type: {t}")


def entry_from_dict(d: dict) -> ObjEntry:
    """Deserialize a dict to an ObjEntry DTO."""
    t = d["type"]
    if t == "pair":
        return ObjPair(key=d["key"], value=value_from_dict(d["value"]))
    if t == "include":
        return ObjInclude(path=d["path"])
    if t == "schema":
        return ObjSchema(path=d["path"])
    raise ValueError(f"Unknown entry type: {t}")


def root_from_dict(d: dict) -> Root:
    """Deserialize a dict to a Root DTO."""
    return Root(items=[item_from_dict(i) for i in d["items"]])
