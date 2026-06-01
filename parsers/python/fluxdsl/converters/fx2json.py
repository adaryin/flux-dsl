import json

from ..flux_mapper import to_dto
from ..parser import parse
from .clean_json import to_clean_root


def fx_to_json(path: str) -> str:
    """Parse a .fx file and return clean JSON (no type discriminators)."""
    with open(path) as f:
        text = f.read()
    ast = parse(text)
    root = to_dto(ast)
    clean = to_clean_root(root)
    return json.dumps(clean, indent=2, ensure_ascii=False)
