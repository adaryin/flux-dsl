import json
import sys
from .lexer import Lexer, LexerError
from .parser import Parser, ParserError, parse


def format_ast(node, indent=0):
    pad = "  " * indent
    if isinstance(node, list):
        if not node:
            return "[]"
        lines = [f"{pad}["]
        for item in node:
            lines.append(f"{pad}  {format_ast(item, indent + 1)},")
        lines.append(f"{pad}]")
        return "\n".join(lines)
    if isinstance(node, dict):
        t = node.get("type", "unknown")
        if t == "pair":
            key = node["key"]
            val = format_ast(node["value"], indent)
            return f"pair {key!r}: {val}"
        if t == "include":
            return f"include {node['path']!r}"
        if t == "schema":
            return f"@schema {node['path']!r}"
        if t in ("string", "number", "boolean", "null"):
            return json.dumps(node.get("value"))
        if t == "list":
            elems = node.get("elements", [])
            if not elems:
                return "[]"
            lines = [f"{pad}["]
            for e in elems:
                lines.append(f"{pad}  {format_ast(e, indent + 1)},")
            lines.append(f"{pad}]")
            return "\n".join(lines)
        if t == "object":
            entries = node.get("entries", [])
            if not entries:
                return "{}"
            lines = [f"{pad}{{"]
            for e in entries:
                if "key" in e:
                    val = format_ast(e["value"], indent + 1)
                    lines.append(f"{pad}  {e['key']!r}: {val}")
                elif "type" in e:
                    lines.append(f"{pad}  {format_ast(e, indent + 1)}")
            lines.append(f"{pad}}}")
            return "\n".join(lines)
        return json.dumps(node)
    return json.dumps(node)


def main():
    if len(sys.argv) < 2:
        print("Usage: python -m fluxdsl <file.fx>", file=sys.stderr)
        sys.exit(1)

    path = sys.argv[1]
    try:
        with open(path) as f:
            text = f.read()
    except FileNotFoundError:
        print(f"Error: file not found: {path}", file=sys.stderr)
        sys.exit(1)

    try:
        result = parse(text)
    except (LexerError, ParserError) as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

    print(format_ast({"type": "document", "items": result}))


if __name__ == "__main__":
    main()
