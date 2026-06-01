from ..json_ast import (
    Root, Pair, Include, Schema,
    Str, Num, Bool, Null, Obj, Lst,
    ObjPair, ObjInclude, ObjSchema, ObjEntry,
)
from ..flux_mapper import to_dto, from_dto
from ..parser import parse


def to_clean_value(val) -> object:
    """Convert a DTO value to a plain Python object (no type tags)."""
    if isinstance(val, Str):
        return val.value
    if isinstance(val, Num):
        return val.value
    if isinstance(val, Bool):
        return val.value
    if isinstance(val, Null):
        return None
    if isinstance(val, Obj):
        return _to_clean_obj(val)
    if isinstance(val, Lst):
        return [to_clean_value(e) for e in val.elements]
    raise TypeError(f"Unknown value type: {type(val)}")


def _to_clean_obj(obj: Obj) -> dict:
    result = {}
    for entry in obj.entries:
        if isinstance(entry, ObjPair):
            result[entry.key] = to_clean_value(entry.value)
        elif isinstance(entry, ObjInclude):
            _add_special(result, "$include", entry.path)
        elif isinstance(entry, ObjSchema):
            _add_special(result, "$schema", entry.path)
    return result


def _add_special(d: dict, key: str, value: str):
    if key in d:
        existing = d[key]
        if isinstance(existing, list):
            existing.append(value)
        else:
            d[key] = [existing, value]
    else:
        d[key] = value


def to_clean_root(root: Root) -> dict:
    """Convert a DTO Root to a plain dict (no type tags)."""
    result = {}
    for item in root.items:
        if isinstance(item, Pair):
            result[item.key] = to_clean_value(item.value)
        elif isinstance(item, Include):
            _add_special(result, "$include", item.path)
        elif isinstance(item, Schema):
            _add_special(result, "$schema", item.path)
    return result


def from_clean_value(value) -> object:
    """Convert a plain Python object back to a DTO value."""
    if value is None:
        return Null()
    if isinstance(value, bool):
        return Bool(value=value)
    if isinstance(value, (int, float)):
        return Num(value=value)
    if isinstance(value, str):
        return Str(value=value)
    if isinstance(value, list):
        return Lst(elements=[from_clean_value(v) for v in value])
    if isinstance(value, dict):
        return _from_clean_obj(value)
    raise TypeError(f"Unsupported value type: {type(value)}")


def _from_clean_obj(d: dict) -> Obj:
    entries: list[ObjEntry] = []
    for k, v in d.items():
        if k == "$include":
            _add_entries_special(entries, v, True)
        elif k == "$schema":
            _add_entries_special(entries, v, False)
        else:
            entries.append(ObjPair(key=k, value=from_clean_value(v)))
    return Obj(entries=entries)


def _add_entries_special(entries: list[ObjEntry], value, is_include: bool):
    cls = ObjInclude if is_include else ObjSchema
    if isinstance(value, list):
        for p in value:
            entries.append(cls(path=p))
    else:
        entries.append(cls(path=value))


def from_clean_root(d: dict) -> Root:
    """Convert a plain dict back to a DTO Root."""
    items = []
    for k, v in d.items():
        if k == "$include":
            if isinstance(v, list):
                for p in v:
                    items.append(Include(path=p))
            else:
                items.append(Include(path=v))
        elif k == "$schema":
            if isinstance(v, list):
                for p in v:
                    items.append(Schema(path=p))
            else:
                items.append(Schema(path=v))
        else:
            items.append(Pair(key=k, value=from_clean_value(v)))
    return Root(items=items)
