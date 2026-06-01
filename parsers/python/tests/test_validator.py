from pathlib import Path
import sys
import tempfile
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import unittest
from fluxdsl.validator import validate


class FluxValidatorTest(unittest.TestCase):

    def _write(self, dir_path, name, content):
        f = dir_path / name
        f.write_text(content)
        return f

    def test_valid_type(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = self._write(Path(tmp), "test.fx", """
_schema {
  x: { type: "string" }
}
x: "hello"
""")
            result, errors = validate(str(f))
            self.assertEqual([], errors, errors)

    def test_invalid_type(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = self._write(Path(tmp), "test.fx", """
_schema {
  x: { type: "number" }
}
x: "hello"
""")
            result, errors = validate(str(f))
            self.assertGreater(len(errors), 0)
            self.assertIn("expected type number", errors[0])

    def test_required_field_present(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = self._write(Path(tmp), "test.fx", """
_schema {
  x: { type: "string", required: true }
}
x: "hello"
""")
            result, errors = validate(str(f))
            self.assertEqual([], errors, errors)

    def test_required_field_missing(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = self._write(Path(tmp), "test.fx", """
_schema {
  x: { type: "string", required: true }
}
""")
            result, errors = validate(str(f))
            self.assertGreater(len(errors), 0)
            self.assertEqual("x: required field missing", errors[0])

    def test_default_injected(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = self._write(Path(tmp), "test.fx", """
_schema {
  x: { type: "string", default: "default_val" }
}
""")
            result, errors = validate(str(f))
            self.assertEqual([], errors, errors)
            items = result
            pair = [n for n in items if n.get("type") == "pair" and n["key"] == "x"]
            self.assertEqual(1, len(pair))
            self.assertEqual("default_val", pair[0]["value"]["value"])

    def test_default_value_type_mismatch(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = self._write(Path(tmp), "test.fx", """
_schema {
  x: { type: "number", default: "not_a_number" }
}
""")
            result, errors = validate(str(f))
            self.assertGreater(len(errors), 0)
            self.assertIn("default value type mismatch", errors[0])

    def test_enum_valid(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = self._write(Path(tmp), "test.fx", """
_schema {
  x: { type: "string", enum: ["a", "b", "c"] }
}
x: "b"
""")
            result, errors = validate(str(f))
            self.assertEqual([], errors, errors)

    def test_enum_invalid(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = self._write(Path(tmp), "test.fx", """
_schema {
  x: { type: "string", enum: ["a", "b"] }
}
x: "z"
""")
            result, errors = validate(str(f))
            self.assertGreater(len(errors), 0)
            self.assertIn("invalid enum value", errors[0])

    def test_pattern_match(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = self._write(Path(tmp), "test.fx", """
_schema {
  x: { type: "string", pattern: "^[a-z]+$" }
}
x: "hello"
""")
            result, errors = validate(str(f))
            self.assertEqual([], errors, errors)

    def test_pattern_no_match(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = self._write(Path(tmp), "test.fx", """
_schema {
  x: { type: "string", pattern: "^[a-z]+$" }
}
x: "Hello123"
""")
            result, errors = validate(str(f))
            self.assertGreater(len(errors), 0)
            self.assertIn("does not match pattern", errors[0])

    def test_min_max_number_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = self._write(Path(tmp), "test.fx", """
_schema {
  x: { type: "number", min: 0, max: 100 }
}
x: 50
""")
            result, errors = validate(str(f))
            self.assertEqual([], errors, errors)

    def test_min_max_number_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = self._write(Path(tmp), "test.fx", """
_schema {
  x: { type: "number", min: 0, max: 100 }
}
x: -1
""")
            result, errors = validate(str(f))
            self.assertGreater(len(errors), 0)

    def test_min_max_list_length(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = self._write(Path(tmp), "test.fx", """
_schema {
  x: { type: "list", min: 2, max: 5 }
}
x: [1]
""")
            result, errors = validate(str(f))
            self.assertGreater(len(errors), 0)
            self.assertIn("list length", errors[0])

    def test_items_validation(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = self._write(Path(tmp), "test.fx", """
_schema {
  x: { type: "list", items: { type: "number" } }
}
x: [1, "bad", 3]
""")
            result, errors = validate(str(f))
            self.assertEqual(1, len(errors))
            self.assertIn("expected type number", errors[0])

    def test_nested_schema_valid(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = self._write(Path(tmp), "test.fx", """
_schema {
  cfg: {
    type: "object"
    properties: {
      host: { type: "string", required: true }
      port: { type: "number", default: 8080 }
    }
  }
}
cfg {
  host: "localhost"
}
""")
            result, errors = validate(str(f))
            self.assertEqual([], errors, errors)

    def test_nested_schema_missing_required(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = self._write(Path(tmp), "test.fx", """
_schema {
  cfg: {
    type: "object"
    properties: {
      host: { type: "string", required: true }
    }
  }
}
cfg {
  port: 8080
}
""")
            result, errors = validate(str(f))
            self.assertGreater(len(errors), 0)
            self.assertIn("required field missing", errors[0])

    def test_strict_mode_rejects_unknown(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = self._write(Path(tmp), "test.fx", """
_schema {
  x: { type: "string" }
}
x: "ok"
y: 1
""")
            result, errors = validate(str(f), strict=True)
            self.assertGreater(len(errors), 0)
            self.assertIn("unknown field", errors[0])

    def test_loose_mode_preserves_unknown(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = self._write(Path(tmp), "test.fx", """
_schema {
  x: { type: "string" }
}
x: "ok"
y: 1
""")
            result, errors = validate(str(f))
            self.assertEqual([], errors, errors)

    def test_include_resolves_and_merges(self):
        with tempfile.TemporaryDirectory() as tmp:
            self._write(Path(tmp), "base.fx", """
x: "from_base"
y: 1
""")
            f = self._write(Path(tmp), "test.fx", """
_schema {
  x: { type: "string" }
  y: { type: "number" }
}
include "base.fx"
y: 2
""")
            result, errors = validate(str(f))
            self.assertEqual([], errors, errors)
            items = result
            for node in items:
                if node.get("type") == "pair" and node["key"] == "x":
                    self.assertEqual("from_base", node["value"]["value"])
                if node.get("type") == "pair" and node["key"] == "y":
                    self.assertEqual(2, node["value"]["value"])

    def test_include_chain(self):
        with tempfile.TemporaryDirectory() as tmp:
            self._write(Path(tmp), "a.fx", 'x: "from_a"\n')
            self._write(Path(tmp), "b.fx", 'include "a.fx"\ny: 2\n')
            f = self._write(Path(tmp), "test.fx", """
_schema {
  x: { type: "string" }
  y: { type: "number" }
}
include "b.fx"
""")
            result, errors = validate(str(f))
            self.assertEqual([], errors, errors)

    def test_external_schema(self):
        with tempfile.TemporaryDirectory() as tmp:
            self._write(Path(tmp), "schema.fx", 'x: { type: "string", required: true }\n')
            f = self._write(Path(tmp), "test.fx", """
@schema "schema.fx"
x: "hello"
""")
            result, errors = validate(str(f))
            self.assertEqual([], errors, errors)

    def test_external_schema_missing_field(self):
        with tempfile.TemporaryDirectory() as tmp:
            self._write(Path(tmp), "schema.fx", 'x: { type: "string", required: true }\n')
            f = self._write(Path(tmp), "test.fx", """
@schema "schema.fx"
""")
            result, errors = validate(str(f))
            self.assertGreater(len(errors), 0)
            self.assertEqual("x: required field missing", errors[0])

    def test_inline_schema_overrides_external(self):
        with tempfile.TemporaryDirectory() as tmp:
            self._write(Path(tmp), "schema.fx", 'x: { type: "string", default: "from_ext" }\n')
            f = self._write(Path(tmp), "test.fx", """
@schema "schema.fx"
_schema {
  x: { type: "string", default: "from_inline" }
}
""")
            result, errors = validate(str(f))
            self.assertEqual([], errors, errors)
            for node in result:
                if node.get("type") == "pair" and node["key"] == "x":
                    self.assertEqual("from_inline", node["value"]["value"])

    def test_database_dot_fx_known_errors(self):
        samples_dir = self._find_samples_dir()
        if samples_dir is None:
            self.skipTest("Cannot find samples directory")
        f = samples_dir / "database.fx"
        result, errors = validate(str(f))
        self.assertGreater(len(errors), 0)
        self.assertIn("connection.password: required field missing", errors)

    def test_env_dev_dot_fx_valid(self):
        samples_dir = self._find_samples_dir()
        if samples_dir is None:
            self.skipTest("Cannot find samples directory")
        f = samples_dir / "env/dev.fx"
        result, errors = validate(str(f))
        self.assertEqual([], errors, errors)

    def test_ci_pipeline_dot_fx_valid(self):
        samples_dir = self._find_samples_dir()
        if samples_dir is None:
            self.skipTest("Cannot find samples directory")
        f = samples_dir / "ci_pipeline.fx"
        result, errors = validate(str(f))
        self.assertEqual([], errors, errors)

    def _find_samples_dir(self):
        script = Path(__file__).resolve()
        for parent in [script, *script.parents]:
            candidate = parent / "samples"
            if candidate.is_dir() and list(candidate.rglob("*.fx")):
                return candidate
        return None


if __name__ == "__main__":
    unittest.main()
