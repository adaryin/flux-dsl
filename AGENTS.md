# FluxDSL — AGENTS.md

## What this is

A declarative configuration DSL (`.fx` files). The spec at `fluxdsl_spec_en.md` is the primary deliverable. Reference parsers live in `parsers/` but are secondary.

## Key syntax

- `{}` for objects, `[]` for lists — **no YAML-style `-` list markers**
- `{ | ... }` for multi-line literal string blocks (auto-dedent, no escaping)
- `include "..."` for modular includes (deep-merge)
- `@schema "..."` / `_schema { ... }` for external / inline validation schemas
- Validation modes: normal (injects defaults), strict (rejects unknown fields), loose (preserves them)

## What to edit

- **Spec edits:** `fluxdsl_spec_en.md` only
- **Remarks:** `remarks.md` — when resolved, update the spec and remove the entry
- **Samples:** `samples/` — keep in sync with spec changes
- **Parsers:** `parsers/java/` (Maven, Java 17) and `parsers/python/` (no deps) — keep in sync with spec changes

## Build & run

- **Java parser:** `mvn -f parsers/java/pom.xml package` → `parsers/java/target/fluxdsl-parser.jar`
- **Java tests:** `mvn -f parsers/java/pom.xml test` (JUnit 5, 98 tests in 5 suites)
- **Python parser:** `python -m fluxdsl <file.fx>` (run from `parsers/python/`)
- **Python tests:** `python -m unittest discover -s tests` (run from `parsers/python/`) or `PYTHONPATH=parsers/python python3 -m unittest discover -s parsers/python/tests` (from repo root)
- **CLI:** `./fx validate|parse|fmt|json|to-json|to-yaml|from-yaml|query <file>` (run from repo root)
- **VS Code extension:** `editors/vscode/` — install locally or publish
- **Pre-commit hook:** `git config core.hooksPath .githooks` — validates staged `.fx` files before commit
- **CI:** `.github/workflows/ci.yml` — matrix Java 17/21 + Python 3.10–3.13 on push/PR

## JSON serialization

Both reference parsers support JSON serialization/deserialization with a common DTO layer that uses `str`/`num`/`bool`/`null`/`obj`/`list` type discriminators.

**Java:** `fluxdsl.json.FluxMapper` via Jackson (`jackson-databind` 2.16.1, in pom.xml). DTOs in `JsonAst.java` use `@JsonProperty`, `@JsonTypeInfo`, `@JsonSubTypes`.

```java
var json  = FluxMapper.toJson(root);        // Root → String
var root  = FluxMapper.fromJson(json);      // String → Root
var dto   = FluxMapper.toDto(root);         // Root → JsonAst.Root
var root2 = FluxMapper.fromDto(dto);        // JsonAst.Root → Root
var mapper = FluxMapper.mapper();           // preconfigured ObjectMapper
```

**Python:** `fluxdsl.flux_mapper` (stdlib `json`, no deps). DTOs in `fluxdsl.json_ast` use dataclasses.

```python
from fluxdsl import to_json, from_json, to_dto, from_dto

json_str = to_json(root)            # Root → String
root     = from_json(json_str)      # String → Root
dto      = to_dto(parser_ast)       # list → Root
ast      = from_dto(dto)            # Root → list
```

## Converters (fx2yaml, yaml2fx, clean JSON)

Both reference parsers support conversion between `.fx` and clean JSON/YAML formats (no type discriminators). Special keys `$include` / `$schema` handle includes/schema references.

**Java:** `fluxdsl.converter` package. Requires `jackson-dataformat-yaml` (2.16.1, in pom.xml).
```java
var yaml = Fx2Yaml.convert(path);      // .fx → YAML String
var fx   = Yaml2Fx.convert(path);      // YAML → .fx String
var clean = CleanJson.rootToClean(dto); // JsonAst.Root → ObjectNode
var dto   = CleanJson.cleanToRoot(obj); // ObjectNode → JsonAst.Root
```

**Python:** `fluxdsl.converters` (stdlib `json`, PyYAML optional for YAML).
```bash
./fx to-json samples/database.fx      # .fx → clean JSON
./fx to-yaml samples/database.fx      # .fx → YAML (needs pyyaml)
./fx from-yaml config.yaml            # YAML → .fx (needs pyyaml)
```
```python
from fluxdsl.converters.fx2json import fx_to_json
from fluxdsl.converters.fx2yaml import fx_to_yaml  # needs pyyaml
from fluxdsl.converters.yaml2fx import yaml_to_fx  # needs pyyaml
from fluxdsl.converters.clean_json import to_clean_root, from_clean_root

json_str = fx_to_json("file.fx")
yaml_str = fx_to_yaml("file.fx")      # needs pyyaml
fx_str   = yaml_to_fx("file.yaml")    # needs pyyaml
```

Top-level includes/schemas map to `$include` / `$schema` keys (or arrays if multiple). Inside objects, the same convention applies.

Both reference parsers have validators that resolve includes, load schemas, validate data against `@schema` / `_schema` rules, and inject defaults. Supports strict mode (rejects unknown fields) and loose mode (preserves them).

**Java:** `fluxdsl.validator.FluxValidator`
```java
var result = FluxValidator.validate(path);           // loose (default)
var result = FluxValidator.validate(path, true);      // strict
// result.root()   → normalized Root with defaults injected
// result.errors() → List<String>
// result.hasErrors()
```

**Python:** `fluxdsl.validator.validate`
```python
from fluxdsl import validate
result, errors = validate(path)         # loose (default)
result, errors = validate(path, strict=True)  # strict
```

Rules supported in both: `type`, `required`, `default`, `enum`, `pattern`, `min`, `max`, `items`, `properties`.

## Samples

Every sample in `samples/` should parse correctly with both reference parsers. The `env/` and `defaults/` subdirectories demonstrate `include` with deep-merge.

The `fluxdsl.samples.FluxSamples` demo processes all `.fx` files: parse → JSON round-trip → verify AST equality + validate. Run from the repo root:

```bash
CP=$(mvn -f parsers/java/pom.xml dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout)
java -cp "parsers/java/target/fluxdsl-parser-1.0.0.jar:$CP" fluxdsl.samples.FluxSamples
```

The `samples` package in `parsers/python/` does the same: parse → JSON round-trip → validate. Run from either the repo root or `parsers/python/`:

```bash
PYTHONPATH=parsers/python python3 -m samples    # from repo root
python3 -m samples                               # from parsers/python/
```

## Robustness

**Java:** 98 tests total (94 unit + 4 property-based via jqwik). Fuzzing: random text input → parser never crashes (always returns lexer/parser error). Benchmarks: `fluxdsl.Benchmark` — times parsing of all 12 sample files.
```bash
java -cp "parsers/java/target/fluxdsl-parser-1.0.0.jar:$CP" fluxdsl.Benchmark
```

**Python:** 103 tests total (94 unit + 1 fuzz (7 sub-tests) + 1 benchmark + 1 skipped property). Fuzz generates random ASCII, UTF-8, deep nesting, long lines, unicode. Benchmark with `test_benchmark.py`:
```bash
PYTHONPATH=parsers/python python3 -m unittest parsers/python/tests/test_benchmark
```

Property-based testing with Hypothesis (optional, skipped if not installed):
```bash
pip install hypothesis
PYTHONPATH=parsers/python python3 -m unittest parsers/python/tests/test_property
```

## API docs

**Java:** Javadoc on all public classes + `mvn javadoc:javadoc` generates `target/site/apidocs/`.
```bash
mvn -f parsers/java/pom.xml javadoc:javadoc
```

**Python:** Sphinx configuration in `docs/sphinx/`. Generate with:
```bash
cd docs/sphinx && sphinx-build -b html . _build
```

## Onboarding

- `docs/quickstart.md` — 5-minute tutorial with CLI examples
- `docs/cheatsheet.md` — FX compared to YAML/JSON/TOML/HCL

## Strict mode

Both parsers support a strict mode that requires quoted strings and disables `include`/`@schema` directives.

**Java:** `new Parser(lex, true)` — rejects bare string keys/values and include/schema directives.
**Python:** `parse(text, strict=True)` or `Parser(lexer, strict=True)`.

CLI: `fx parse --strict <file>.fx`

## Query

Both parsers support path-based queries (jq-like). Path syntax: `.key.subkey[0].field`.

**Java:** `fluxdsl.query.FluxQuery.query(root, ".key.subkey")`
**Python:** `fluxdsl.query.query(ast, ".key.subkey")`

CLI: `fx query <file>.fx .services.api.replicas`
