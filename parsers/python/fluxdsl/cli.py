import argparse
import json
import sys
from pathlib import Path

from .lexer import Lexer, LexerError
from .parser import Parser, ParserError, parse
from .validator import validate
from .flux_mapper import to_dto, to_json
from .converters.fx2json import fx_to_json
from .converters.fx2yaml import fx_to_yaml
from .converters.yaml2fx import yaml_to_fx
from .query import query


def cmd_parse(args):
    """Parse and pretty-print a .fx file."""
    try:
        text = args.path.read_text()
        result = parse(text, strict=args.strict)
        _print_as_tree(result)
    except (LexerError, ParserError) as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


def cmd_query(args):
    """Query a .fx file using a path expression."""
    try:
        text = args.path.read_text()
        ast = parse(text)
        result = query(ast, args.path_expr)
        if result is None:
            print("null")
        else:
            _print_as_tree(result)
    except (LexerError, ParserError, ValueError) as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


def cmd_validate(args):
    """Validate a .fx file against its schema."""
    try:
        result, errors = validate(str(args.path), strict=args.strict)
        for e in errors:
            print(e, file=sys.stderr)
        if errors:
            sys.exit(1)
    except (LexerError, ParserError) as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


def cmd_fmt(args):
    """Pretty-print a .fx file as formatted JSON."""
    try:
        text = args.path.read_text()
        ast = parse(text)
        root = to_dto(ast)
        print(json.dumps(root, indent=2, ensure_ascii=False))
    except (LexerError, ParserError) as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


def cmd_json(args):
    """Convert .fx to DTO JSON round-trip format."""
    try:
        text = args.path.read_text()
        ast = parse(text)
        root = to_dto(ast)
        print(to_json(root))
    except (LexerError, ParserError) as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


def cmd_clean_json(args):
    """Convert .fx to clean JSON (no type discriminators)."""
    try:
        print(fx_to_json(str(args.path)))
    except (LexerError, ParserError) as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


def cmd_yaml(args):
    """Convert .fx to YAML."""
    try:
        print(fx_to_yaml(str(args.path)))
    except (LexerError, ParserError) as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)
    except RuntimeError as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


def cmd_yaml2fx(args):
    """Convert YAML to .fx."""
    try:
        print(yaml_to_fx(str(args.path)))
    except ValueError as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)
    except RuntimeError as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


def _print_as_tree(node, indent=0):
    pad = "  " * indent
    if isinstance(node, list):
        for item in node:
            _print_as_tree(item, indent)
        return
    if not isinstance(node, dict):
        print(f"{pad}{json.dumps(node)}")
        return
    t = node.get("type", "unknown")
    if t == "pair":
        val = node["value"]
        if isinstance(val, dict) and val.get("type") in ("object", "list"):
            print(f"{pad}{node['key']}:")
            _print_as_tree(val, indent + 1)
        else:
            print(f"{pad}{node['key']}: {json.dumps(val.get('value', val))}")
    elif t == "include":
        print(f'{pad}include {node["path"]!r}')
    elif t == "schema":
        print(f'{pad}@schema {node["path"]!r}')
    elif t == "string":
        print(f"{pad}{json.dumps(node['value'])}")
    elif t == "number":
        print(f"{pad}{node['value']}")
    elif t == "boolean":
        print(f"{pad}{'true' if node['value'] else 'false'}")
    elif t == "null":
        print(f"{pad}null")
    elif t == "object":
        print(f"{pad}{{")
        for e in node.get("entries", []):
            _print_entry(e, indent + 1)
        print(f"{pad}}}")
    elif t == "list":
        print(f"{pad}[")
        for e in node.get("elements", []):
            print(f"{pad}  ", end="")
            _print_as_tree(e, indent + 1)
        print(f"{pad}]")
    else:
        print(f"{pad}{json.dumps(node)}")


def _print_entry(entry, indent):
    pad = "  " * indent
    t = entry.get("type")
    if t == "include":
        print(f'{pad}include {entry["path"]!r}')
    elif t == "schema":
        print(f'{pad}@schema {entry["path"]!r}')
    else:
        val = entry["value"]
        if isinstance(val, dict) and val.get("type") in ("object", "list"):
            print(f'{pad}{entry["key"]}:')
            _print_as_tree(val, indent)
        else:
            print(f'{pad}{entry["key"]}: {json.dumps(val.get("value", val))}')


def main():
    """Entry point for the FluxDSL CLI (fx)."""
    parser = argparse.ArgumentParser(prog="fx", description="FluxDSL CLI")
    sub = parser.add_subparsers(dest="command")
    sub.required = True

    p_parse = sub.add_parser("parse", help="Parse and pretty-print")
    p_parse.add_argument("path", type=Path)
    p_parse.add_argument("--strict", action="store_true", help="Strict mode (require quoted strings, disable includes)")
    p_parse.set_defaults(func=cmd_parse)

    p_query = sub.add_parser("query", help="Query with a path expression")
    p_query.add_argument("path", type=Path)
    p_query.add_argument("path_expr", type=str, help="Path expression, e.g. .key.subkey[0]")
    p_query.set_defaults(func=cmd_query)

    p_val = sub.add_parser("validate", help="Validate against schema")
    p_val.add_argument("path", type=Path)
    p_val.add_argument("--strict", action="store_true", help="Reject unknown fields")
    p_val.set_defaults(func=cmd_validate)

    p_fmt = sub.add_parser("fmt", help="Format to pretty JSON round-trip")
    p_fmt.add_argument("path", type=Path)
    p_fmt.set_defaults(func=cmd_fmt)

    p_json = sub.add_parser("json", help="Convert to JSON (DTO format)")
    p_json.add_argument("path", type=Path)
    p_json.set_defaults(func=cmd_json)

    p_clean = sub.add_parser("to-json", help="Convert to clean JSON (no type discriminators)")
    p_clean.add_argument("path", type=Path)
    p_clean.set_defaults(func=cmd_clean_json)

    p_yaml = sub.add_parser("to-yaml", help="Convert to YAML")
    p_yaml.add_argument("path", type=Path)
    p_yaml.set_defaults(func=cmd_yaml)

    p_yaml2fx = sub.add_parser("from-yaml", help="Convert YAML to .fx")
    p_yaml2fx.add_argument("path", type=Path)
    p_yaml2fx.set_defaults(func=cmd_yaml2fx)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
