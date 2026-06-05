# FluxDSL — 5-Minute Quickstart

## What is FluxDSL?

A declarative configuration DSL (`.fx` files). Think YAML without the
indentation tax — uses `{}` for objects and `[]` for lists.

## 1. Install

No installation needed. Use the Python reference parser directly:

```bash
git clone https://github.com/adaryin/fluxdsl.git
cd fluxdsl
alias fx='PYTHONPATH=parsers/python python3 -m fluxdsl.cli'
```

Or the Java jar:

```bash
mvn -f parsers/java/pom.xml package
alias fx='java -jar parsers/java/target/fluxdsl-parser-1.0.0.jar'
```

## 2. Your first `.fx` file

Create `hello.fx`:

```
# My first config
app {
  name: "hello-world"
  version: 1.0
  enabled: true
}
```

Parse it:

```bash
fx parse hello.fx
```

## 3. Syntax basics

| Feature        | FX syntax                             | Equivalent YAML          |
|----------------|---------------------------------------|--------------------------|
| Comment        | `# this is a comment`                 | `# comment`              |
| String         | `key: "value"`                        | `key: value`             |
| Number         | `port: 8080`                          | `port: 8080`             |
| Boolean        | `active: true`                        | `active: true`           |
| Null           | `extra: null`                         | `extra: ~`               |
| Object         | `obj { key: "v" }` or `obj: { ... }`  | `obj:\n  key: v`         |
| List           | `list: [ 1 2 3 ]`                     | `list:\n  - 1\n  - 2`    |
| Block string   | `text: { \| ... }`                     | `text: \|`               |
| Include        | `include "base.fx"`                   | N/A                      |
| Schema         | `@schema "schema.fx"`                 | N/A                      |

## 4. Lists and objects

Lists use `[]`, no commas needed (whitespace-separated):

```
fruits: [ apple banana cherry ]
```

Objects use `{}`:

```
server {
  host: "localhost"
  port: 3000
}
```

Shorthand inline: `key: { sub: "value" }`

## 5. Multi-line strings

Block strings auto-dedent, no escaping needed:

```
sql: {
  |
  SELECT *
  FROM users
  WHERE active = true
}
```

## 6. Includes

Split config across files:

```fx
# base.fx
app { name: "myapp" }
```

```fx
# override.fx
include "base.fx"
app { debug: true }
```

Result: `app { name: "myapp", debug: true }` (deep-merge).

## 7. Schemas

Validate your data with external or inline schemas:

```fx
@schema "schemas/app_schema.fx"
```

Or inline:

```
_schema {
  name: { type: "string", required: true }
  port: { type: "number", default: 8080 }
}
app {
  name: "myapp"
}
```

Schema rules: `type`, `required`, `default`, `enum`, `pattern`, `min`, `max`.

## 8. Using CLI

```bash
fx parse file.fx                # parse and pretty-print AST
fx parse --strict file.fx       # strict mode (require quoted strings, no includes)
fx validate file.fx             # validate against schema
fx validate --strict file.fx    # strict validation (rejects unknown fields)
fx fmt file.fx                  # pretty-print as formatted JSON
fx json file.fx                 # DTO JSON round-trip
fx to-json file.fx              # clean JSON (no type tags)
fx to-yaml file.fx              # YAML output
fx from-yaml file.yaml          # YAML → .fx conversion
fx query file.fx .key.sub[0]    # query with a path expression (jq-like)
```

## 9. What's next?

- Browse `samples/` for real examples: `ci_pipeline.fx`, `database.fx`, `k8s_deployment.fx`
- Read the full spec at `fluxdsl_spec_en.md`
- Use the VS Code extension in `editors/vscode/` for syntax highlighting
