from ..flux_mapper import to_dto
from ..parser import parse
from .clean_json import to_clean_root


def fx_to_yaml(path: str) -> str:
    """Parse a .fx file and return YAML (requires PyYAML)."""
    try:
        import yaml
    except ImportError:
        raise RuntimeError("PyYAML is required for YAML conversion: pip install pyyaml")

    with open(path) as f:
        text = f.read()
    ast = parse(text)
    root = to_dto(ast)
    clean = to_clean_root(root)
    return yaml.dump(clean, default_flow_style=False, allow_unicode=True, sort_keys=False)
