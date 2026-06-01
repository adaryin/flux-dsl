package fluxdsl.validator;

import fluxdsl.FluxTree;
import fluxdsl.lexer.Lexer;
import fluxdsl.parser.Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

class FileProcessor {
    private final boolean strict;
    private final List<String> errors = new ArrayList<>();

    FileProcessor(boolean strict) {
        this.strict = strict;
    }

    FluxValidator.ValidationResult process(Path path) throws IOException {
        var root = readAndParse(path);
        return new FluxValidator.ValidationResult(root, errors);
    }

    FluxTree.Ast.Root readAndParse(Path path) throws IOException {
        var text = Files.readString(path);
        var root = new Parser(new Lexer(text)).parseDocument();
        return processNode(root, path);
    }

    FluxTree.Ast.Root processNode(FluxTree.Ast.Root root, Path currentPath) throws IOException {
        var data = new LinkedHashMap<String, FluxTree.Value>();
        var schema = new LinkedHashMap<String, FluxTree.Value>();
        String schemaPath = null;

        for (var item : root.items()) {
            if (item instanceof FluxTree.Item.Include inc) {
                var incPath = resolve(currentPath, inc.path());
                var incRoot = readAndParse(incPath);
                mergeData(data, itemsToMap(incRoot.items()));
            } else if (item instanceof FluxTree.Item.Schema s) {
                schemaPath = s.path();
            } else if (item instanceof FluxTree.Item.Pair p && p.key().equals("_schema")) {
                mergeSchema(schema, p.value());
            } else if (item instanceof FluxTree.Item.Pair p) {
                data.put(p.key(), p.value());
            }
        }

        var mergedSchema = new LinkedHashMap<String, FluxTree.Value>();
        if (schemaPath != null) {
            var extPath = resolve(currentPath, schemaPath);
            var extText = Files.readString(extPath);
            var extRoot = new Parser(new Lexer(extText)).parseDocument();
            for (var item : extRoot.items()) {
                if (item instanceof FluxTree.Item.Pair p) {
                    mergedSchema.put(p.key(), p.value());
                }
            }
        }
        mergedSchema.putAll(schema);

        if (mergedSchema.isEmpty()) {
            return new FluxTree.Ast.Root(itemsFromMap(data));
        }

        var normalized = validateData(data, mergedSchema, "");
        return new FluxTree.Ast.Root(itemsFromMap(normalized));
    }

    void mergeData(Map<String, FluxTree.Value> target, Map<String, FluxTree.Value> source) {
        for (var entry : source.entrySet()) {
            var k = entry.getKey();
            var v = entry.getValue();
            if (v instanceof FluxTree.Value.Obj srcObj && target.get(k) instanceof FluxTree.Value.Obj tgtObj) {
                var merged = entriesToMap(tgtObj);
                var srcMap = entriesToMap(srcObj);
                for (var se : srcMap.entrySet()) {
                    if (se.getValue() instanceof FluxTree.Value.Obj subSrc
                            && merged.get(se.getKey()) instanceof FluxTree.Value.Obj subTgt) {
                        var deep = entriesToMap(subTgt);
                        mergeData(deep, entriesToMap(subSrc));
                        merged.put(se.getKey(), objFromMap(deep));
                    } else {
                        merged.put(se.getKey(), se.getValue());
                    }
                }
                target.put(k, objFromMap(merged));
            } else {
                target.put(k, v);
            }
        }
    }

    void mergeSchema(Map<String, FluxTree.Value> target, FluxTree.Value schemaValue) {
        if (schemaValue instanceof FluxTree.Value.Obj o) {
            for (var e : o.entries()) {
                if (e instanceof FluxTree.ObjEntry.ObjPair p) {
                    target.put(p.key(), p.value());
                }
            }
        }
    }

    Map<String, FluxTree.Value> itemsToMap(List<FluxTree.Item> items) {
        var map = new LinkedHashMap<String, FluxTree.Value>();
        for (var item : items) {
            if (item instanceof FluxTree.Item.Pair p) {
                map.put(p.key(), p.value());
            }
        }
        return map;
    }

    List<FluxTree.Item> itemsFromMap(Map<String, FluxTree.Value> data) {
        var items = new ArrayList<FluxTree.Item>();
        for (var entry : data.entrySet()) {
            items.add(new FluxTree.Item.Pair(entry.getKey(), entry.getValue()));
        }
        return items;
    }

    FluxTree.Value objFromMap(Map<String, FluxTree.Value> entries) {
        var list = new ArrayList<FluxTree.ObjEntry>();
        for (var entry : entries.entrySet()) {
            list.add(new FluxTree.ObjEntry.ObjPair(entry.getKey(), entry.getValue()));
        }
        return new FluxTree.Value.Obj(list);
    }

    Map<String, FluxTree.Value> entriesToMap(FluxTree.Value.Obj o) {
        var map = new LinkedHashMap<String, FluxTree.Value>();
        for (var e : o.entries()) {
            if (e instanceof FluxTree.ObjEntry.ObjPair p) {
                map.put(p.key(), p.value());
            }
        }
        return map;
    }

    Path resolve(Path currentFile, String ref) {
        return currentFile.getParent().resolve(ref).normalize();
    }

    Map<String, FluxTree.Value> validateData(Map<String, FluxTree.Value> data,
                                             Map<String, FluxTree.Value> schema, String path) {
        var result = new LinkedHashMap<String, FluxTree.Value>();

        for (var schemaEntry : schema.entrySet()) {
            var key = schemaEntry.getKey();
            var rules = schemaEntry.getValue();
            var val = data.get(key);
            var fieldPath = path.isEmpty() ? key : path + "." + key;

            if (isNestedSchema(rules)) {
                var nestedSchema = extractNestedSchema(rules);
                Map<String, FluxTree.Value> nestedData = val instanceof FluxTree.Value.Obj o
                        ? entriesToMap(o) : new LinkedHashMap<>();
                var validated = validateData(nestedData, nestedSchema, fieldPath);
                result.put(key, objFromMap(validated));
                continue;
            }

            if (val == null) {
                if (hasDefault(rules)) {
                    var def = getDefault(rules);
                    if (checkType(def, getType(rules))) {
                        errors.add(fieldPath + ": default value type mismatch");
                    }
                    result.put(key, def);
                } else if (isRequired(rules)) {
                    errors.add(fieldPath + ": required field missing");
                }
                continue;
            }

            var type = getType(rules);
            if (type != null && !type.isEmpty() && checkType(val, type)) {
                errors.add(fieldPath + ": expected type " + type
                        + ", got " + typeName(val));
            }

            if (val instanceof FluxTree.Value.Str s) {
                checkStringConstraints(s.value(), rules, fieldPath);
            } else if (val instanceof FluxTree.Value.Num n) {
                checkNumberConstraints(n.value(), rules, fieldPath);
            } else if (val instanceof FluxTree.Value.Lst lst) {
                checkListConstraints(lst, rules, fieldPath);
            }

            result.put(key, val);
        }

        for (var entry : data.entrySet()) {
            if (!schema.containsKey(entry.getKey())) {
                if (strict) {
                    errors.add(path + "." + entry.getKey()
                            + ": unknown field in strict mode");
                } else {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return result;
    }

    boolean isNestedSchema(FluxTree.Value rules) {
        if (!(rules instanceof FluxTree.Value.Obj o)) return false;
        for (var e : o.entries()) {
            if (e instanceof FluxTree.ObjEntry.ObjPair p && FluxValidator.RULE_KEYS.contains(p.key())) {
                return p.key().equals("properties")
                        || "object".equals(stringValue(p.value()));
            }
        }
        for (var e : o.entries()) {
            if (e instanceof FluxTree.ObjEntry.ObjPair p && !FluxValidator.RULE_KEYS.contains(p.key())) {
                return true;
            }
        }
        return false;
    }

    String stringValue(FluxTree.Value v) {
        return v instanceof FluxTree.Value.Str s ? s.value() : null;
    }

    Map<String, FluxTree.Value> extractNestedSchema(FluxTree.Value rules) {
        if (rules instanceof FluxTree.Value.Obj o) {
            for (var e : o.entries()) {
                if (e instanceof FluxTree.ObjEntry.ObjPair p && p.key().equals("properties")
                        && p.value() instanceof FluxTree.Value.Obj props) {
                    return entriesToMap(props);
                }
            }
        }
        return rules instanceof FluxTree.Value.Obj o ? entriesToMap(o) : new LinkedHashMap<>();
    }

    String getType(FluxTree.Value rules) {
        return stringValue(getRule(rules, "type"));
    }

    boolean isRequired(FluxTree.Value rules) {
        var v = getRule(rules, "required");
        return v instanceof FluxTree.Value.Bool b && b.value();
    }

    boolean hasDefault(FluxTree.Value rules) {
        return getRule(rules, "default") != null;
    }

    FluxTree.Value getDefault(FluxTree.Value rules) {
        return getRule(rules, "default");
    }

    FluxTree.Value getRule(FluxTree.Value rules, String ruleName) {
        if (rules instanceof FluxTree.Value.Obj o) {
            for (var e : o.entries()) {
                if (e instanceof FluxTree.ObjEntry.ObjPair p && p.key().equals(ruleName)) {
                    return p.value();
                }
            }
        }
        return null;
    }

    boolean checkType(FluxTree.Value val, String type) {
        if (type == null || type.isEmpty()) return false;
        return !switch (type) {
            case "string" -> val instanceof FluxTree.Value.Str;
            case "number" -> val instanceof FluxTree.Value.Num;
            case "bool" -> val instanceof FluxTree.Value.Bool;
            case "null" -> val instanceof FluxTree.Value.Null;
            case "object" -> val instanceof FluxTree.Value.Obj;
            case "list" -> val instanceof FluxTree.Value.Lst;
            default -> true;
        };
    }

    String typeName(FluxTree.Value val) {
        if (val instanceof FluxTree.Value.Str) return "string";
        if (val instanceof FluxTree.Value.Num) return "number";
        if (val instanceof FluxTree.Value.Bool) return "bool";
        if (val instanceof FluxTree.Value.Null) return "null";
        if (val instanceof FluxTree.Value.Obj) return "object";
        if (val instanceof FluxTree.Value.Lst) return "list";
        return "unknown";
    }

    void checkStringConstraints(String val, FluxTree.Value rules, String path) {
        var enumVals = enumValues(rules);
        if (enumVals != null && !enumVals.contains(val)) {
            errors.add(path + ": invalid enum value \"" + val + "\"");
        }
        var pat = pattern(rules);
        if (pat != null && !pat.matcher(val).matches()) {
            errors.add(path + ": does not match pattern " + pat.pattern());
        }
    }

    void checkNumberConstraints(double val, FluxTree.Value rules, String path) {
        var min = minValue(rules);
        var max = maxValue(rules);
        if (min != null && val < min) {
            errors.add(path + ": value " + val + " less than min " + min);
        }
        if (max != null && val > max) {
            errors.add(path + ": value " + val + " greater than max " + max);
        }
    }

    void checkListConstraints(FluxTree.Value.Lst lst, FluxTree.Value rules, String path) {
        var min = minValue(rules);
        var max = maxValue(rules);
        if (min != null && lst.elements().size() < min) {
            errors.add(path + ": list length " + lst.elements().size()
                    + " less than min " + min);
        }
        if (max != null && lst.elements().size() > max) {
            errors.add(path + ": list length " + lst.elements().size()
                    + " greater than max " + max);
        }
        var itemsRule = getRule(rules, "items");
        if (itemsRule != null) {
            for (int i = 0; i < lst.elements().size(); i++) {
                validateItem(lst.elements().get(i), itemsRule, path + "[" + i + "]");
            }
        }
    }

    void validateItem(FluxTree.Value val, FluxTree.Value itemsRule, String path) {
        if (isNestedSchema(itemsRule)) {
            var nestedSchema = extractNestedSchema(itemsRule);
            Map<String, FluxTree.Value> nestedData = val instanceof FluxTree.Value.Obj o
                    ? entriesToMap(o) : new LinkedHashMap<>();
            validateData(nestedData, nestedSchema, path);
        } else {
            var type = getType(itemsRule);
            if (type != null && !type.isEmpty() && checkType(val, type)) {
                errors.add(path + ": expected type " + type
                        + ", got " + typeName(val));
            }
            if (val instanceof FluxTree.Value.Str s) {
                checkStringConstraints(s.value(), itemsRule, path);
            } else if (val instanceof FluxTree.Value.Num n) {
                checkNumberConstraints(n.value(), itemsRule, path);
            }
        }
    }

    List<String> enumValues(FluxTree.Value rules) {
        var v = getRule(rules, "enum");
        if (v instanceof FluxTree.Value.Lst lst) {
            var result = new ArrayList<String>();
            for (var e : lst.elements()) {
                if (e instanceof FluxTree.Value.Str s) result.add(s.value());
            }
            return result;
        }
        return null;
    }

    Pattern pattern(FluxTree.Value rules) {
        var v = getRule(rules, "pattern");
        return v instanceof FluxTree.Value.Str s ? Pattern.compile(s.value()) : null;
    }

    Double minValue(FluxTree.Value rules) {
        var v = getRule(rules, "min");
        return v instanceof FluxTree.Value.Num n ? n.value() : null;
    }

    Double maxValue(FluxTree.Value rules) {
        var v = getRule(rules, "max");
        return v instanceof FluxTree.Value.Num n ? n.value() : null;
    }
}
