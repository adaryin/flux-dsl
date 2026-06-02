# FluxDSL

A declarative configuration DSL (`.fx` files). Think YAML without the indentation tax — uses `{}` for objects and `[]` for lists, with built-in modular includes and schema validation.

## Quick start

```bash
# Parse & validate
./fx parse samples/database.fx
./fx validate samples/database.fx

# Convert formats
./fx to-json samples/database.fx
./fx to-yaml samples/database.fx    # needs pyyaml
./fx from-yaml config.yaml          # needs pyyaml

# Query (jq-like)
./fx query samples/database.fx .database.host

# Strict mode (quoted strings only, no includes)
./fx parse --strict samples/database.fx
```

## Syntax at a glance

| Feature | FX | YAML equivalent |
|---|---|---|
| String | `key: "value"` | `key: value` |
| Number | `port: 8080` | `port: 8080` |
| Boolean | `active: true` | `active: true` |
| Object | `app { name: "x" }` | `app:\n  name: x` |
| List | `ports: [ 80 443 ]` | `ports:\n  - 80\n  - 443` |
| Block string | `sql: { \| ... }` (content on next line) | `sql: \|` (literal) |
| Comment | `# comment` | `# comment` |
| Include | `include "base.fx"` | N/A (deep-merge) |
| Schema | `@schema "schema.fx"` | N/A |

## Example

```fx
# database.fx
database {
  host: "localhost"
  port: 5432
  credentials {
    user: "admin"
    password: {
      |
      SECRET_BLOCK
      multi-line ok
    }
  }
  pools: [ main_cache analytics ]
}
```

## Features

- **No significant indentation** — nesting uses `{}` / `[]`, indentation is cosmetic
- **No commas** — whitespace-separated list elements
- **No YAML `-` list markers** — `[ item1 item2 ]`
- **Includes** — `include "file.fx"` with deep-merge
- **Schema validation** — `@schema` (external) or `_schema { }` (inline) with type checking, defaults, enums, patterns
- **Multi-line strings** — `{ | ... }` auto-dedent, no escaping
- **Clean JSON / YAML conversion** — round-trip with `.fx`
- **Path queries** — jq-like `.key.subkey[0].field`
- **Validation modes** — normal (inject defaults), strict (reject unknown fields), loose (preserve them)
- **CLI** — `parse`, `validate`, `fmt`, `json`, `to-json`, `to-yaml`, `from-yaml`, `query`
- **VS Code extension** — syntax highlighting in `editors/vscode/`

## Reference parsers

- **Python** — `parsers/python/` (stdlib, no deps)
- **Java 17** — `parsers/java/` (Maven, JUnit 5, jqwik property tests, Jackson for JSON)

## CLI

```bash
./fx parse <file.fx>               # parse and pretty-print AST
./fx validate <file.fx>            # validate against schema
./fx fmt <file.fx>                 # pretty-print as formatted JSON
./fx json <file.fx>                # DTO JSON round-trip
./fx to-json <file.fx>             # clean JSON
./fx to-yaml <file.fx>             # YAML output
./fx from-yaml <file.yaml>         # YAML → .fx
./fx query <file.fx> .path         # path query
```

## Documentation

- [`fluxdsl_spec_en.md`](fluxdsl_spec_en.md) — full language specification
- [`docs/quickstart.md`](docs/quickstart.md) — 5-minute tutorial
- [`docs/cheatsheet.md`](docs/cheatsheet.md) — FX vs YAML/JSON/TOML/HCL
- [`samples/`](samples/) — example `.fx` files

## License

MIT
