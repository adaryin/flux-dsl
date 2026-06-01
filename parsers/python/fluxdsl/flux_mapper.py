from __future__ import annotations

import json

from .json_ast import (
    Root, Item, Pair, Include, Schema,
    Value, Str, Num, Bool, Null, Obj, Lst,
    ObjEntry, ObjPair, ObjInclude, ObjSchema,
    _to_dict, item_from_dict, value_from_dict, entry_from_dict, root_from_dict,
)

_PARSER_VALUE_TYPE: dict[str, str] = {
    "str": "string",
    "num": "number",
    "bool": "boolean",
    "null": "null",
    "obj": "object",
    "list": "list",
}

_DTO_VALUE_TYPE: dict[str, str] = {v: k for k, v in _PARSER_VALUE_TYPE.items()}


def _parser_value_to_dto(parser_val: dict) -> dict:
    t = parser_val["type"]
    dto_t = _DTO_VALUE_TYPE.get(t)
    if dto_t is None:
        raise ValueError(f"Unknown parser value type: {t}")
    d = {"type": dto_t}
    if t == "null":
        pass
    elif t == "object":
        entries = parser_val.get("entries", [])
        d["entries"] = [_parser_entry_to_dto(e) for e in entries]
    elif t == "list":
        elements = parser_val.get("elements", [])
        d["elements"] = [_parser_value_to_dto(e) for e in elements]
    else:
        d["value"] = parser_val["value"]
    return d


def _parser_entry_to_dto(parser_entry: dict) -> dict:
    t = parser_entry.get("type")
    if t == "include":
        return {"type": "include", "path": parser_entry["path"]}
    if t == "schema":
        return {"type": "schema", "path": parser_entry["path"]}
    return {"type": "pair", "key": parser_entry["key"], "value": _parser_value_to_dto(parser_entry["value"])}


def _dto_value_to_parser(dto_val: dict) -> dict:
    t = dto_val["type"]
    parser_t = _PARSER_VALUE_TYPE.get(t)
    if parser_t is None:
        raise ValueError(f"Unknown DTO value type: {t}")
    d = {"type": parser_t}
    if t == "null":
        pass
    elif t == "obj":
        entries = dto_val.get("entries", [])
        d["entries"] = [_dto_entry_to_parser(e) for e in entries]
    elif t == "list":
        elements = dto_val.get("elements", [])
        d["elements"] = [_dto_value_to_parser(e) for e in elements]
    else:
        d["value"] = dto_val["value"]
    return d


def _dto_entry_to_parser(dto_entry: dict) -> dict:
    t = dto_entry["type"]
    if t == "include":
        return {"type": "include", "path": dto_entry["path"]}
    if t == "schema":
        return {"type": "schema", "path": dto_entry["path"]}
    return {"key": dto_entry["key"], "value": _dto_value_to_parser(dto_entry["value"])}


def to_dto(parser_ast: list) -> Root:
    """Convert a parser AST list to a DTO Root with typed dataclasses."""
    items = []
    for node in parser_ast:
        t = node["type"]
        if t == "pair":
            items.append(Pair(key=node["key"], value=_dto_value_from_ast(node["value"])))
        elif t == "include":
            items.append(Include(path=node["path"]))
        elif t == "schema":
            items.append(Schema(path=node["path"]))
    return Root(items=items)


def _dto_value_from_ast(parser_val: dict) -> Value:
    t = parser_val["type"]
    if t == "string":
        return Str(value=parser_val["value"])
    if t == "number":
        return Num(value=parser_val["value"])
    if t == "boolean":
        return Bool(value=parser_val["value"])
    if t == "null":
        return Null()
    if t == "object":
        entries = [_dto_entry_from_ast(e) for e in parser_val.get("entries", [])]
        return Obj(entries=entries)
    if t == "list":
        elements = [_dto_value_from_ast(e) for e in parser_val.get("elements", [])]
        return Lst(elements=elements)
    raise ValueError(f"Unknown parser value type: {t}")


def _dto_entry_from_ast(parser_entry: dict) -> ObjEntry:
    if parser_entry.get("type") == "include":
        return ObjInclude(path=parser_entry["path"])
    if parser_entry.get("type") == "schema":
        return ObjSchema(path=parser_entry["path"])
    return ObjPair(key=parser_entry["key"], value=_dto_value_from_ast(parser_entry["value"]))


def from_dto(root: Root) -> list:
    """Convert a DTO Root back to a parser AST list."""
    result = []
    for item in root.items:
        if isinstance(item, Pair):
            result.append({"type": "pair", "key": item.key, "value": _ast_value_from_dto(item.value)})
        elif isinstance(item, Include):
            result.append({"type": "include", "path": item.path})
        elif isinstance(item, Schema):
            result.append({"type": "schema", "path": item.path})
    return result


def _ast_value_from_dto(val: Value) -> dict:
    if isinstance(val, Str):
        return {"type": "string", "value": val.value}
    if isinstance(val, Num):
        return {"type": "number", "value": val.value}
    if isinstance(val, Bool):
        return {"type": "boolean", "value": val.value}
    if isinstance(val, Null):
        return {"type": "null"}
    if isinstance(val, Obj):
        entries = [_ast_entry_from_dto(e) for e in val.entries]
        return {"type": "object", "entries": entries}
    if isinstance(val, Lst):
        elements = [_ast_value_from_dto(e) for e in val.elements]
        return {"type": "list", "elements": elements}
    raise ValueError(f"Unknown DTO value type: {type(val)}")


def _ast_entry_from_dto(entry: ObjEntry) -> dict:
    if isinstance(entry, ObjInclude):
        return {"type": "include", "path": entry.path}
    if isinstance(entry, ObjSchema):
        return {"type": "schema", "path": entry.path}
    return {"key": entry.key, "value": _ast_value_from_dto(entry.value)}


def to_json(root: Root) -> str:
    """Serialize a DTO Root to a JSON string."""
    return json.dumps(_to_dict(root), indent=2, ensure_ascii=False)


def from_json(json_str: str) -> Root:
    """Deserialize a JSON string to a DTO Root."""
    d = json.loads(json_str)
    return root_from_dict(d)
