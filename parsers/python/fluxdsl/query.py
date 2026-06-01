"""Path-based query/projection for FluxDSL AST."""

import re

_SEGMENT_RE = re.compile(
    r"\.(?P<key>[a-zA-Z_][a-zA-Z0-9_-]*)|\[(?P<index>\d+)]"
)


def query(ast, path):
    """Query a FluxDSL AST using a path expression.

    Path syntax:
      .key         — access top-level key
      .key.subkey  — nested object access
      [0]          — list index
      .key[0].sub  — mixed

    Returns the matching node or None if not found.
    """
    if not path:
        return None
    if not path.startswith(".") and not path.startswith("["):
        path = "." + path

    current = _ast_to_value(ast)
    pos = 0

    while pos < len(path):
        m = _SEGMENT_RE.match(path, pos)
        if not m:
            raise ValueError(f"Invalid path segment at position {pos}: {path[pos:]!r}")

        if m.group("key") is not None:
            key = m.group("key")
            if not isinstance(current, dict) or current.get("type") != "object":
                return None
            entries = current.get("entries", [])
            found = None
            for entry in entries:
                if entry.get("key") == key:
                    found = entry.get("value")
                    break
            if found is None:
                return None
            current = found
        else:
            idx = int(m.group("index"))
            if not isinstance(current, dict) or current.get("type") != "list":
                return None
            elements = current.get("elements", [])
            if idx < 0 or idx >= len(elements):
                return None
            current = elements[idx]

        pos = m.end()

    return current


def _ast_to_value(ast):
    """Convert a top-level items list to a single object if possible."""
    if not isinstance(ast, list):
        return ast
    pairs = [item for item in ast if item.get("type") == "pair"]
    if not pairs:
        return ast
    if len(pairs) == 1:
        return pairs[0]["value"]
    return {"type": "object", "entries": [
        {"key": e["key"], "value": e["value"]} for e in pairs
    ]}
