def format_fx(ast: list) -> str:
    """Format a parser AST list back to .fx source text."""
    lines = _format_items(ast, indent=0)
    return "\n".join(lines)


def _format_items(items: list, indent: int) -> list[str]:
    pad = "  " * indent
    result = []
    for item in items:
        t = item.get("type")
        if t == "include":
            result.append(f'{pad}include "{item["path"]}"')
        elif t == "schema":
            result.append(f'{pad}@schema "{item["path"]}"')
        elif t == "pair":
            result.extend(_format_pair(item, indent))
        elif t == "object":
            result.append(f"{pad}{{")
            for e in item.get("entries", []):
                result.extend(_format_entry(e, indent + 1))
            result.append(f"{pad}}}")
        elif t == "list":
            result.extend(_format_list(item, indent))
        else:
            val = _format_value(item)
            result.append(f"{pad}{val}")
    return result


def _format_pair(item: dict, indent: int) -> list[str]:
    pad = "  " * indent
    val = item.get("value", {})
    vt = val.get("type")
    key = item["key"]

    if vt == "string" and "\n" not in str(val.get("value", "")):
        return [f'{pad}{key}: {_format_value(val)}']
    if vt in ("object", "list"):
        lines = _format_braced_value(val, indent)
        return [f"{pad}{key}: {lines[0]}"] + lines[1:]
    return [f"{pad}{key}: {_format_value(val)}"]


def _format_entry(entry: dict, indent: int) -> list[str]:
    pad = "  " * indent
    t = entry.get("type")
    if t == "include":
        return [f'{pad}include "{entry["path"]}"']
    if t == "schema":
        return [f'{pad}@schema "{entry["path"]}"']
    return _format_pair(entry, indent)


def _format_braced_value(val: dict, indent: int) -> list[str]:
    pad = "  " * indent
    vt = val.get("type")
    if vt == "object":
        lines = [f"{pad}{{"]
        for e in val.get("entries", []):
            lines.extend(_format_entry(e, indent + 1))
        lines.append(f"{pad}}}")
        return lines
    if vt == "list":
        return _format_list(val, indent)
    return [_format_value(val)]


def _format_list(item: dict, indent: int) -> list[str]:
    pad = "  " * indent
    elements = item.get("elements", [])
    lines = [f"{pad}["]
    for e in elements:
        ev = _format_value(e)
        lines.append(f"{pad}  {ev}")
    lines.append(f"{pad}]")
    return lines


def _format_value(val: dict) -> str:
    t = val.get("type")
    if t == "string":
        v = val["value"]
        return f'"{v}"'
    if t == "number":
        v = val["value"]
        if isinstance(v, float) and v == int(v):
            return str(int(v))
        return str(v)
    if t == "boolean":
        return "true" if val["value"] else "false"
    if t == "null":
        return "null"
    return repr(val)
