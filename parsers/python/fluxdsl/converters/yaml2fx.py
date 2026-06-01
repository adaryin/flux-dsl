from .clean_json import from_clean_root
from .fx_formatter import format_fx
from ..flux_mapper import from_dto


def yaml_to_fx(path: str) -> str:
    """Parse a YAML file and return .fx source text (requires PyYAML)."""
    try:
        import yaml
    except ImportError:
        raise RuntimeError("PyYAML is required for YAML conversion: pip install pyyaml")

    with open(path) as f:
        data = yaml.safe_load(f)

    if not isinstance(data, dict):
        raise ValueError("YAML root must be a mapping")

    root = from_clean_root(data)
    ast = from_dto(root)
    return format_fx(ast)
