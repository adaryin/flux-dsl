import re
from pathlib import Path
from .lexer import Lexer, LexerError
from .parser import Parser, ParserError, parse


RULE_KEYS = frozenset({
    "type", "required", "default", "enum",
    "pattern", "min", "max", "items", "properties",
})


def validate(path, strict=False):
    """Validate a FluxDSL file. Returns (normalized_ast, errors)."""
    path = Path(path).resolve()
    result, errors = _process_file(path, strict)
    return result, errors


def _read_and_parse(path):
    text = path.read_text()
    return parse(text)


def _process_file(path, strict, context="data"):
    root = _read_and_parse(path)
    return _process_node(root, path, strict, context)


def _process_node(ast, current_path, strict, context="data"):
    data = {}
    schema = {}
    schema_path = None

    for node in ast:
        nt = node.get("type")
        if nt == "include":
            inc_path = _resolve(current_path, node["path"])
            inc_root, _ = _process_node(
                _read_and_parse(inc_path), inc_path, strict, context,
            )
            _deep_merge(data, _items_to_map(inc_root))
        elif nt == "schema":
            if context == "schema":
                raise ValueError(
                    "@schema is not allowed inside schema files"
                )
            schema_path = node["path"]
        elif nt == "pair":
            if node["key"] == "_schema":
                _merge_schema(schema, node["value"])
            else:
                data[node["key"]] = node["value"]

    merged_schema = {}
    if schema_path is not None:
        ext_path = _resolve(current_path, schema_path)
        ext_ast = _read_and_parse(ext_path)
        for ext_node in ext_ast:
            if ext_node.get("type") == "pair":
                merged_schema[ext_node["key"]] = ext_node["value"]
    merged_schema.update(schema)

    if not merged_schema:
        return _items_from_map(data), []

    errors = []
    normalized = _validate_data(data, merged_schema, "", strict, errors)
    return _items_from_map(normalized), errors


def _resolve(current_file, ref):
    return (current_file.parent / ref).resolve()


def _items_to_map(items):
    result = {}
    for node in items:
        if node.get("type") == "pair":
            result[node["key"]] = node["value"]
    return result


def _items_from_map(data):
    items = []
    for key, value in data.items():
        items.append({"type": "pair", "key": key, "value": value})
    return items


def _deep_merge(target, source):
    for key, value in source.items():
        existing = target.get(key)
        if (
            isinstance(value, dict) and value.get("type") == "object"
            and isinstance(existing, dict) and existing.get("type") == "object"
        ):
            merged = _entries_to_dict(existing)
            src_map = _entries_to_dict(value)
            for sk, sv in src_map.items():
                if (
                    isinstance(sv, dict) and sv.get("type") == "object"
                    and merged.get(sk) is not None
                    and isinstance(merged[sk], dict)
                    and merged[sk].get("type") == "object"
                ):
                    deep = _entries_to_dict(merged[sk])
                    _deep_merge(deep, _entries_to_dict(sv))
                    merged[sk] = _dict_to_obj(deep)
                else:
                    merged[sk] = sv
            target[key] = _dict_to_obj(merged)
        else:
            target[key] = value


def _merge_schema(target, schema_value):
    if isinstance(schema_value, dict) and schema_value.get("type") == "object":
        for entry in schema_value.get("entries", []):
            if "key" in entry:
                target[entry["key"]] = entry["value"]


def _entries_to_dict(obj):
    result = {}
    for entry in obj.get("entries", []):
        if "key" in entry:
            result[entry["key"]] = entry["value"]
    return result


def _dict_to_obj(entries):
    items = []
    for key, value in entries.items():
        items.append({"key": key, "value": value})
    return {"type": "object", "entries": items}


def _validate_data(data, schema, path_str, strict, errors):
    result = {}

    for key, rules in schema.items():
        val = data.get(key)
        field_path = f"{path_str}.{key}" if path_str else key

        if _is_nested_schema(rules):
            nested_schema = _extract_nested_schema(rules)
            nested_data = (
                _entries_to_dict(val)
                if isinstance(val, dict) and val.get("type") == "object"
                else {}
            )
            result[key] = _dict_to_obj(
                _validate_data(nested_data, nested_schema, field_path, strict, errors)
            )
            continue

        if val is None:
            if _has_default(rules):
                default_val = _get_default(rules)
                rule_type = _get_type(rules)
                if rule_type and _check_type(default_val, rule_type):
                    errors.append(f"{field_path}: default value type mismatch")
                result[key] = default_val
            elif _is_required(rules):
                errors.append(f"{field_path}: required field missing")
            continue

        rule_type = _get_type(rules)
        if rule_type and _check_type(val, rule_type):
            errors.append(
                f"{field_path}: expected type {rule_type}, got {_type_name(val)}"
            )

        val_type = val.get("type") if isinstance(val, dict) else None
        if val_type == "string":
            _check_string_constraints(val["value"], rules, field_path, errors)
        elif val_type == "number":
            _check_number_constraints(val["value"], rules, field_path, errors)
        elif val_type == "list":
            _check_list_constraints(val, rules, field_path, strict, errors)

        result[key] = val

    for key, val in data.items():
        if key not in schema:
            if strict:
                errors.append(
                    f"{path_str}.{key}: unknown field in strict mode"
                    if path_str else f"{key}: unknown field in strict mode"
                )
            else:
                result[key] = val

    return result


def _is_nested_schema(rules):
    if not isinstance(rules, dict) or rules.get("type") != "object":
        return False
    found_rule = False
    for entry in rules.get("entries", []):
        key = entry.get("key")
        if key in RULE_KEYS:
            if key == "properties":
                return True
            entry_val = entry.get("value")
            if (
                key == "type"
                and isinstance(entry_val, dict)
                and entry_val.get("type") == "string"
                and entry_val.get("value") == "object"
            ):
                return True
            found_rule = True
    if found_rule:
        return False
    for entry in rules.get("entries", []):
        if entry.get("key") not in RULE_KEYS:
            return True
    return False


def _extract_nested_schema(rules):
    if not isinstance(rules, dict):
        return {}
    for entry in rules.get("entries", []):
        if entry.get("key") == "properties":
            val = entry.get("value")
            if isinstance(val, dict) and val.get("type") == "object":
                return _entries_to_dict(val)
    return _entries_to_dict(rules)


def _get_value(rules, rule_name):
    if not isinstance(rules, dict):
        return None
    for entry in rules.get("entries", []):
        if entry.get("key") == rule_name:
            return entry.get("value")
    return None


def _get_type(rules):
    v = _get_value(rules, "type")
    if isinstance(v, dict) and v.get("type") == "string":
        return v["value"]
    return None


def _is_required(rules):
    v = _get_value(rules, "required")
    return isinstance(v, dict) and v.get("type") == "boolean" and v["value"] is True


def _has_default(rules):
    return _get_value(rules, "default") is not None


def _get_default(rules):
    return _get_value(rules, "default")


def _check_type(val, type_name):
    if type_name is None or not isinstance(val, dict):
        return False
    val_type = val.get("type")
    mapping = {
        "string": "string",
        "number": "number",
        "bool": "boolean",
        "null": "null",
        "object": "object",
        "list": "list",
    }
    expected = mapping.get(type_name)
    if expected is None:
        return False
    return val_type != expected


def _type_name(val):
    if not isinstance(val, dict):
        return "unknown"
    return val.get("type", "unknown")


def _check_string_constraints(val, rules, field_path, errors):
    enum_vals = _enum_values(rules)
    if enum_vals is not None and val not in enum_vals:
        errors.append(f"{field_path}: invalid enum value {val!r}")

    pat = _pattern(rules)
    if pat is not None and not pat.match(val):
        errors.append(f"{field_path}: does not match pattern {pat.pattern!r}")


def _check_number_constraints(val, rules, field_path, errors):
    mn = _min_value(rules)
    mx = _max_value(rules)
    if mn is not None and val < mn:
        errors.append(f"{field_path}: value {val} less than min {mn}")
    if mx is not None and val > mx:
        errors.append(f"{field_path}: value {val} greater than max {mx}")


def _check_list_constraints(lst, rules, field_path, strict, errors):
    mn = _min_value(rules)
    mx = _max_value(rules)
    length = len(lst.get("elements", []))
    if mn is not None and length < mn:
        errors.append(f"{field_path}: list length {length} less than min {mn}")
    if mx is not None and length > mx:
        errors.append(f"{field_path}: list length {length} greater than max {mx}")

    items_rule = _get_value(rules, "items")
    if items_rule is not None:
        for i, elem in enumerate(lst.get("elements", [])):
            _validate_item(elem, items_rule, f"{field_path}[{i}]", strict, errors)


def _validate_item(val, items_rule, path_str, strict, errors):
    if _is_nested_schema(items_rule):
        nested_schema = _extract_nested_schema(items_rule)
        nested_data = (
            _entries_to_dict(val)
            if isinstance(val, dict) and val.get("type") == "object"
            else {}
        )
        _validate_data(nested_data, nested_schema, path_str, strict, errors)
    else:
        rule_type = _get_type(items_rule)
        if rule_type and _check_type(val, rule_type):
            errors.append(
                f"{path_str}: expected type {rule_type}, got {_type_name(val)}"
            )
        if isinstance(val, dict) and val.get("type") == "string":
            _check_string_constraints(val["value"], items_rule, path_str, errors)
        elif isinstance(val, dict) and val.get("type") == "number":
            _check_number_constraints(val["value"], items_rule, path_str, errors)


def _enum_values(rules):
    v = _get_value(rules, "enum")
    if isinstance(v, dict) and v.get("type") == "list":
        result = []
        for elem in v.get("elements", []):
            if isinstance(elem, dict) and elem.get("type") == "string":
                result.append(elem["value"])
        return result
    return None


def _pattern(rules):
    v = _get_value(rules, "pattern")
    if isinstance(v, dict) and v.get("type") == "string":
        return re.compile(v["value"])
    return None


def _min_value(rules):
    v = _get_value(rules, "min")
    if isinstance(v, dict) and v.get("type") == "number":
        return v["value"]
    return None


def _max_value(rules):
    v = _get_value(rules, "max")
    if isinstance(v, dict) and v.get("type") == "number":
        return v["value"]
    return None
