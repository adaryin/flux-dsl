import sys
from pathlib import Path

from fluxdsl import parse, validate, to_dto, to_json


def _find_samples_dir():
    script = Path(__file__).resolve()
    for parent in [script.parent, *script.parents]:
        candidate = parent / "samples"
        if candidate.is_dir() and list(candidate.rglob("*.fx")):
            return candidate
    return None


def process_file(path):
    try:
        text = path.read_text()
        root = parse(text)

        json_str = to_json(to_dto(root))
        print(f"=== {path} ===")
        print(json_str)

        result, errors = validate(str(path.resolve()))
        if errors:
            print("Validation errors:")
            for err in errors:
                print(f"  {err}")
        else:
            print("Validation: \u2713")
        print()

    except Exception as e:
        print(f"=== {path} ===")
        print(f"Error: {e}", file=sys.stderr)
        print()


def main():
    if len(sys.argv) > 1:
        for arg in sys.argv[1:]:
            process_file(Path(arg))
        return

    samples_dir = _find_samples_dir()
    if samples_dir is None:
        print("Usage: python -m samples <file.fx> ...")
        sys.exit(1)

    for path in sorted(samples_dir.rglob("*.fx")):
        process_file(path)


if __name__ == "__main__":
    main()
