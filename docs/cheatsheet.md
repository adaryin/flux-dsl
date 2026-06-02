# FluxDSL Cheatsheet — FX vs YAML / JSON / TOML / HCL

## Scalar values

| FX                  | YAML              | JSON            | TOML            | HCL              |
|---------------------|-------------------|-----------------|-----------------|------------------|
| `key: "hello"`      | `key: hello`      | `"key": "hello"`| `key = "hello"` | `key = "hello"`  |
| `count: 42`         | `count: 42`       | `"count": 42`   | `count = 42`    | `count = 42`     |
| `active: true`      | `active: true`    | `"active": true`| `active = true` | `active = true`  |
| `extra: null`       | `extra: ~`        | `"extra": null` | —               | `extra = null`   |

## Objects

| FX                              | YAML                         | JSON                            |
|----------------------------------|------------------------------|---------------------------------|
| `app { name: "x" }`             | `app:\n  name: x`            | `"app": {"name": "x"}`          |
| `app: { name: "x" }`            | (same)                       | (same)                          |

## Lists

| FX                        | YAML                        | JSON                        |
|---------------------------|-----------------------------|-----------------------------|
| `ports: [ 80 443 ]`       | `ports:\n  - 80\n  - 443`   | `"ports": [80, 443]`        |
| `items: [ "a" "b" ]`      | `items:\n  - a\n  - b`      | `"items": ["a", "b"]`       |

## Comments

| FX           | YAML         | JSON      | TOML       | HCL           |
|--------------|--------------|-----------|------------|---------------|
| `# comment`  | `# comment`  | ❌        | `# comment`| `// comment`  |
|              |              |           |            | `/* block */` |

## Multi-line strings

| FX                          | YAML               | JSON      | TOML               | HCL            |
|-----------------------------|---------------------|-----------|--------------------|----------------|
| `text: { \| ... }`           | `text: \|`          | `"..."\n` | `text = """..."""` | `<<EOT...EOT`  |
| auto-dedent                 | literal block      | escape    | literal            | heredoc        |

## Includes / imports

| Language | Syntax                           |
|----------|----------------------------------|
| **FX**   | `include "base.fx"` (deep-merge) |
| YAML     | `!include` (custom, not native)  |
| JSON     | `$ref` (JSON Schema)             |
| TOML     | ❌ (no includes)                  |
| HCL      | `import` (native)                |

## Schemas / validation

| Language | Built-in?                  |
|----------|----------------------------|
| **FX**   | `@schema`, `_schema { }`   |
| YAML     | JSON Schema (external)     |
| JSON     | JSON Schema (external)     |
| TOML     | ❌                          |
| HCL      | `validation` block         |

## What FX eliminates

| Pain point                | YAML                            | FX solution                |
|---------------------------|---------------------------------|----------------------------|
| Indentation errors        | `key:\n  sub:\n    value`       | `key { sub: "value" }`    |
| Flow-style ambiguity      | `{key: val}` vs `key: val`      | Always `key: value` pairs |
| List marker noise         | `- item`                        | `[ item1 item2 ]`         |
| Block string quirks       | `\|`, `\|-`, `>`, `>-`          | `{ \| ... \| }`            |
| No includes               | tools like `yq` or preprocessor | `include "file.fx"`        |
| No native validation      | JSON Schema (external)          | `_schema { ... }`          |
