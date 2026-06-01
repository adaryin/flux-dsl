# FluxDSL Specification (v3.2)

> **Name:** FluxDSL  
> **Version:** 3.2  
> **Type:** Declarative Configuration Domain-Specific Language (DSL)  
> **File Extension:** `.fx`  
> **Status:** Stable  
> **Author:** Alexey Daryin

---

## 1. Philosophy and Principles

**FluxDSL** is a declarative Domain-Specific Language (DSL) designed specifically for describing complex configurations and structured data. It combines the readability of YAML, the strictness of TOML, and the modularity of modern build systems, eliminating their key shortcomings.

**Core Principles:**
1.  **Declarative Nature:** The language describes *state* (what should be), not *logic* (how to do it). There are no loops, functions, or executable control flow statements.
2.  **Explicit Structure:** Nesting is defined by curly `{}` and square `[]` braces. Indentation is used solely for visual clarity and does not affect parsing.
3.  **Modularity:** The built-in `include` directive allows splitting configurations into reusable modules with support for deep merging.
4.  **String Safety:** Special blocks for multi-line text allow including any code (SQL, JSON, scripts) without escaping special characters.
5.  **Built-in Validation:** Native schema support for type checking, required fields, and data normalization (default values).

---

## 2. Lexical Structure and Syntax

### 2.1. Comments
*   Start with the `#` symbol.
*   Extend to the end of the line.
*   Can appear after values or on separate lines.

### 2.2. Keys and Values
*   **Keys:**
    *   **Simple:** Must start with a letter or underscore `_`, followed by letters, digits, underscores, or hyphens `-`. Quotes are not required (e.g., `my_key`, `_internal`).
    *   **Complex:** If a key contains spaces or special characters, it must be enclosed in double quotes `"my key"`.
*   **Data Types:**
    *   **String:**
        *   **Quoted:** `"text"`. Supports escape sequences (`\n`, `\"`, `\uXXXX`).
        *   **Bare (Unquoted):** `hello`, `007`, `my-value`. An unquoted token that is not a valid `number`, `boolean`, or `null`. No escape sequences are processed.
    *   **Number:** `42`, `3.14`, `1e5`. Written without quotes. Leading zeros are prohibited — a value like `007` is **not** a valid number literal and is interpreted as a string.
    *   **Boolean:** `true`, `false` (case-sensitive).
    *   **Null:** `null`.
*   **Colon:** The colon `:` between a key and its value is **optional** for all value types. Both `key: "value"` and `key "value"` are valid.
*   **No Logic:** Programming keywords (`if`, `for`, `func`) are not supported as language constructs and are treated as regular string keys if used.
*   **Reserved Keys:** The key name `_schema` is **reserved** for inline schema definitions (see §2.5.2). It is intercepted during processing and not treated as regular data.

### 2.3. Objects and Lists
*   **Objects:**
    *   **Block form (multi-line):** `key { ... }` or `key: { ... }`. Pairs inside are separated by newlines. Commas between pairs are **not used**.
    *   **Inline form (single-line):** `{ a: 1, b: 2 }`. Used as a value directly, pairs are separated by commas. A trailing comma before `}` is **optional**.
    *   **Blank lines** between pairs are allowed and ignored.
    *   **Duplicate keys:** If the same key appears more than once at the same level, the **last** occurrence wins. Implementations must not error on duplicates.
*   **Lists:**
    *   Always enclosed in square brackets `[ ... ]`.
    *   Elements are separated by commas `,` or newlines.
    *   A trailing comma after the last element is **optional**.
    *   **Important:** The list marker `-` (as used in YAML) is **prohibited**.
    *   Elements can be primitives or objects `{ ... }`.

### 2.4. Multi-line Strings (String Blocks)
Ideal for SQL queries, JSON inside strings, scripts, or large text blocks.
*   **Syntax:** `key: { | ... }`
*   The pipe symbol `|` inside `{}` followed by newline enables literal mode.
*   **Dedentation:** The parser automatically calculates the minimum indentation among all lines in the block and trims that number of spaces from each line.
*   Inside the block, **any characters** (`{}`, `#`, `:`, `"`) are allowed **without escaping**.

### 2.5. Management Directives

#### 2.5.1. Include Modules (`include`)
*   **Syntax:** `include "path/to/file.fx"`
*   **Logic:** Merges file content into the current context (Deep Merge). Later values override earlier ones.
*   **Path Resolution:** Paths are resolved relative to the directory of the current file.

#### 2.5.2. Attach Schema (`@schema`)
*   **Syntax:** `@schema "path/to/file.fx"`
*   **Logic:** Points to an external file containing validation rules. Schema content is **not merged** with data; it serves as a validation blueprint.
*   **Path Resolution:** Paths are resolved relative to the directory of the current file.
*   **Inline Schema:** Alternatively, use the reserved `_schema { ... }` key inside a data file for local rules. The `_schema` key is intercepted during parsing and is not included in the output data.
*   **Restriction:** The `@schema` directive is **prohibited** inside schema files. An implementation must raise an error if a schema file references another schema.

---

## 3. Validation and Normalization System

FluxDSL supports data validation against a schema and automatic default value injection.

**Validation Rules (Descriptors):**
In a schema (external file or `_schema` block), values are replaced by rule objects containing:
*   `type`: `"string"`, `"number"`, `"bool"`, `"list"`, `"object"`.
*   `required`: `true`/`false`.
*   `default`: Default value (injected during normalization if the key is missing).
*   `enum`: List of allowed values (e.g., `["dev", "prod"]`).
*   `pattern`: Regex pattern for strings.
*   `min` / `max`: Ranges for numbers or collection lengths.
*   `items`: Schema for list elements.
*   `properties`: Schema for nested objects.

**Modes of Operation:**
1.  **Normalization:** Returns data with `default` values injected and types verified.
2.  **Strict Mode:** Errors are raised for any fields not defined in the schema.
3.  **Loose Mode (Default):** Extra fields not defined in the schema are ignored by the validator but preserved in the output.

---

## 4. Formal Grammar (EBNF)

```ebnf
document     = { whitespace | comment | directive | pair | newline } ;

(* Directives *)
directive    = include_stmt | schema_stmt ;
include_stmt = "include" , whitespace , string , { whitespace | comment } , newline ;
schema_stmt  = "@" , "schema" , whitespace , string , { whitespace | comment } , newline ;

(* Key-Value Pairs *)
pair         = key [ ":" ] ( value | block_object | block_string ) ;
key          = simple_key | quoted_key ;
simple_key   = ( letter | "_" ) , { letter | digit | "_" | "-" } ;
quoted_key   = "\"" , { char_except_quote | escape_seq } , "\"" ;

(* Values *)
value        = string | number | boolean | "null" | bare_string | inline_list | inline_object ;
string       = "\"" , { char_except_quote | escape_seq } , "\"" ;
bare_string  = ( letter | digit ) , { letter | digit | "_" | "-" } ;
number       = [ "-" ] , ( "0" | ( non_zero_digit , { digit } ) ) , [ "." , digit , { digit } ] , [ "e" , [ "+" | "-" ] , digit , { digit } ] ;
non_zero_digit = "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" ;
boolean      = "true" | "false" ;

(* Structures *)
block_object = "{" , gap , { entry , gap } , gap , "}" ;
entry        = ( pair | directive ) , [ comment ] | comment ;
inline_object= "{" , whitespace , [ pair , { whitespace , "," , whitespace , pair } , [ whitespace , "," ] ] , whitespace , "}" ;
block_string = "{" , gap , "|" , newline , text_block , gap , "}" ;
text_block   = { line } ; (* Dedent logic applies here *)

inline_list  = "[" , gap , [ list_elements ] , gap , "]" ;
list_elements = ( value | block_object ) , { gap , ( "," | newline ) , gap , ( value | block_object ) } , [ gap , ( "," | newline ) ] ;

(* Character Classes *)
letter       = "a" | "b" | "c" | "d" | "e" | "f" | "g" | "h" | "i" | "j" | "k" | "l" | "m"
             | "n" | "o" | "p" | "q" | "r" | "s" | "t" | "u" | "v" | "w" | "x" | "y" | "z"
             | "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H" | "I" | "J" | "K" | "L" | "M"
             | "N" | "O" | "P" | "Q" | "R" | "S" | "T" | "U" | "V" | "W" | "X" | "Y" | "Z" ;
digit        = "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" ;

(* Special Sequences — informal descriptions *)
char_except_newline = ? any character except "\n" and "\r" ? ;
char_except_quote   = ? any character except '"', "\n", and "\r" ? ;
line                = ? any sequence of characters terminated by a newline ? ;

(* Auxiliary Tokens *)
whitespace   = " " | "\t" ;
newline      = "\n" | "\r\n" ;
gap          = { whitespace | newline } ;
comment      = "#" , { char_except_newline } ;
escape_seq   = "\" , ( "n" | "t" | "r" | "\" | "\"" | "u" , hex_digit , hex_digit , hex_digit , hex_digit ) ;
hex_digit    = digit | "a" | "b" | "c" | "d" | "e" | "f" | "A" | "B" | "C" | "D" | "E" | "F" ;
```

---

## 5. Usage Examples

**File: `schemas/base_schema.fx` (External Schema)**
```flux
# Rules only, no _schema key needed
app {
  name: { type: "string", required: true, pattern: "^[a-z]+$" }
  version: { type: "string", default: "0.0.1" }
  replicas: { type: "number", min: 1, max: 10, default: 1 }
}
database {
  host: { type: "string", required: true }
  port: { type: "number", default: 5432 }
}
```

**File: `defaults/base.fx` (Default Data)**
```flux
# Base FluxDSL module
app {
  name: "MyService"
  version: "1.0.0"
  logging {
    level: "info"
    format: "json"
  }
}
database {
  host: "localhost"
  port: 5432
  user: "admin"
}
```

**File: `production.fx` (Main Configuration)**
```flux
# Attach external schema for validation
@schema "schemas/base_schema.fx"

# Include base values (data)
include "defaults/base.fx"

# Production overrides
app {
  name: "myapp"
  replicas: 5
}

database {
  host: "db.prod.internal"
  # port defaults to 5432 from schema if not specified
  
  # Multi-line script
  init_script: {
    |
    CREATE TABLE IF NOT EXISTS logs (id SERIAL);
    # SQL is safe inside the block
  }
}
```

## 6. Implementation Logic (Pseudocode)

```python
def process_flux_file(path, context="data", strict=False):
    """
    context: "data" | "schema"
    """
    content = read_file(path)
    ast = parse(content) # Returns list of nodes
    
    data = {}
    schema_rules = {}
    external_schema_path = None
    
    # 1. First pass: Separate directives, data, and inline schema
    for node in ast:
        if node.type == 'INCLUDE':
            # Merge included data immediately
            inc_data = process_flux_file(resolve_path(path, node.arg), context="data")
            data = deep_merge(data, inc_data)
            
        elif node.type == 'SCHEMA_DIRECTIVE': # @schema "..."
            if context == "schema":
                raise Error("@schema is not allowed inside schema files")
            external_schema_path = resolve_path(path, node.arg)
            
        elif node.key == '_schema':
            # Inline schema definition
            schema_rules = deep_merge(schema_rules, node.value)
            
        else:
            # Regular data
            data = deep_merge(data, {node.key: node.value})

    # 2. Load External Schema if specified
    if external_schema_path:
        ext_schema = process_flux_file(external_schema_path, context="schema")
        schema_rules = deep_merge(ext_schema, schema_rules)  # inline _schema wins over external @schema

    # 3. If this was a schema file call, return rules directly
    if context == "schema":
        return data # In schema files, data structure IS the rules

    # 4. Validate and Normalize
    if schema_rules:
        normalized_data, errors = validate_and_normalize(data, schema_rules)
        if errors:
            raise ValidationError(errors)
        return normalized_data
    
    return data

def validate_and_normalize(data, schema, path=""):
    result = {}
    errors = []
    
    # Process fields defined in schema
    for key, rules in schema.items():
        val = data.get(key)
        
        # Handle nested objects
        if rules.get('type') == 'object' or 'properties' in rules:
            nested_schema = rules.get('properties', rules)
            nested_data = val if isinstance(val, dict) else {}
            res, err = validate_and_normalize(nested_data, nested_schema, f"{path}.{key}")
            if err: errors.extend(err)
            result[key] = res
            continue
            
        # Handle defaults
        if val is None:
            if 'default' in rules:
                default_val = rules['default']
                if not check_type(default_val, rules.get('type')):
                    errors.append(f"{path}.{key}: default value type mismatch")
                else:
                    result[key] = default_val
                continue
            elif rules.get('required'):
                errors.append(f"{path}.{key}: required field missing")
                continue
            else:
                continue # Optional and no default
        
        # Type Checking & Constraints (simplified)
        if not check_type(val, rules.get('type')):
            errors.append(f"{path}.{key}: type mismatch")
        elif 'enum' in rules and val not in rules['enum']:
            errors.append(f"{path}.{key}: invalid enum value")
        else:
            result[key] = val

    # Loose mode: keep extra fields not in schema
    for key, val in data.items():
        if key not in schema:
            if strict:
                errors.append(f"{path}.{key}: unknown field in strict mode")
            else:
                result[key] = val
                
    return result, errors
```

---

## 7. Strict Parser Mode

A FluxDSL parser may support a **strict mode** that enforces additional restrictions on the input. This mode is intended for environments where consistency and safety are preferred over flexibility.

### Rules

1. **Quoted strings required.** Bare (unquoted) identifiers are not allowed as keys or values. All strings must be enclosed in double quotes (`"..."`).
   - `key: "value"` — allowed
   - `key: bare_value` — **error** in strict mode
2. **Directives disabled.** The `include` and `@schema` directives are rejected with an error.
   - `include "base.fx"` — **error** in strict mode
   - `@schema "schema.fx"` — **error** in strict mode

### Pseudo-code

```python
def parse_strict(text):
    lexer = Lexer(text)
    parser = Parser(lexer, strict=True)
    return parser.parse_document()

# In parsePair():
def parse_pair():
    key_tok = next()
    if key_tok.kind == TokenKind.BARE_STRING and strict:
        error("Bare string keys not allowed in strict mode")
    ...

# In parseValue():
def parse_value():
    tok = peek()
    if tok.kind == TokenKind.BARE_STRING and strict:
        error("Bare string values not allowed in strict mode")
    ...
```

---

## 8. Query / Projection

A FluxDSL parser may support a **path-based query language** (analogous to `jq` for JSON) for extracting values from parsed AST.

### Path Syntax

| Path | Matches |
|------|---------|
| `.key` | Top-level key `key` |
| `.key.subkey` | Nested object access |
| `[0]` | First element of a list |
| `.key[0].sub` | Mixed: key `key`, then index `0`, then key `sub` |

The path is evaluated left to right against the AST. If any segment does not match (wrong type, missing key, out-of-bounds index), the result is `null`.

### Pseudo-code

```python
def query(ast, path):
    if not path:
        return null
    if not path.startswith(".") and not path.startswith("["):
        path = "." + path
    
    current = ast_to_value(ast)
    pos = 0
    while pos < len(path):
        if match(".key"):
            current = current[key]
        elif match("[N]"):
            current = current[N]
        else:
            error(f"Invalid path at {pos}")
        pos = match.end()
    return current
```

### CLI

```bash
fx query deploy.fx .services.api.replicas   # → 3
fx query deploy.fx .project                 # → "myapp"
fx query deploy.fx .secrets.db_password     # → { from_vault: "prod/db/password" }
```

---

## 9. License

The **FluxDSL** specification is provided as an open concept. You are free to implement parsers, serializers, validators, and tools for this format in your projects.