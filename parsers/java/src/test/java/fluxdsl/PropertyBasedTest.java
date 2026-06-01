package fluxdsl;

import fluxdsl.FluxTree.*;
import fluxdsl.json.FluxMapper;
import fluxdsl.json.JsonAst;
import net.jqwik.api.*;
import java.util.*;

class PropertyBasedTest {

    @Provide
    Arbitrary<Item> anyItem() {
        var pairs = Combinators.combine(
                Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(8),
                simpleValue()
        ).as(Item.Pair::new);
        var includes = Arbitraries.strings().ascii().ofMaxLength(15).map(Item.Include::new);
        var schemas = Arbitraries.strings().ascii().ofMaxLength(15).map(Item.Schema::new);
        return Arbitraries.oneOf(pairs, includes, schemas);
    }

    @Provide
    Arbitrary<Value> simpleValue() {
        var strings = Arbitraries.strings().ofMaxLength(10).map(Value.Str::new);
        var nums = Arbitraries.doubles()
                .filter(d -> !Double.isNaN(d) && !Double.isInfinite(d))
                .map(Value.Num::new);
        var bools = Arbitraries.of(true, false).map(Value.Bool::new);
        var nulls = Arbitraries.just(new Value.Null());
        return Arbitraries.oneOf(strings, nums, bools, nulls);
    }

    @Property(tries = 500)
    void roundTripThroughDto(@ForAll("anyItem") Item i1,
                             @ForAll("anyItem") Item i2,
                             @ForAll("anyItem") Item i3) {
        var items = new ArrayList<Item>();
        items.add(i1); items.add(i2); items.add(i3);
        var root = new Ast.Root(items);
        var dto = FluxMapper.toDto(root);
        var back = FluxMapper.fromDto(dto);
        assertEqual(root, back);
    }

    @Property(tries = 500)
    void jsonRoundTrip(@ForAll("anyItem") Item i1,
                       @ForAll("anyItem") Item i2) {
        var items = new ArrayList<Item>();
        items.add(i1); items.add(i2);
        var root = new Ast.Root(items);
        var json = FluxMapper.toJson(root);
        var back = FluxMapper.fromJson(json);
        assertEqual(root, back);
    }

    @Property(tries = 500)
    void dtoRoundTrip(@ForAll("anyItem") Item item) throws Exception {
        var items = new ArrayList<Item>();
        items.add(item);
        var root = new Ast.Root(items);
        var dto = FluxMapper.toDto(root);
        var dtoJson = FluxMapper.mapper().valueToTree(dto);
        var dtoBack = FluxMapper.mapper().treeToValue(dtoJson, JsonAst.Root.class);
        var back = FluxMapper.fromDto(dtoBack);
        assertEqual(root, back);
    }

    @Provide
    Arbitrary<String> randomText() {
        return Arbitraries.strings().ascii().ofMinLength(0).ofMaxLength(100);
    }

    @Property(tries = 500)
    void parserDoesNotCrash(@ForAll("randomText") String text) {
        try {
            var lexer = new fluxdsl.lexer.Lexer(text);
            var parser = new fluxdsl.parser.Parser(lexer);
            parser.parseDocument();
        } catch (fluxdsl.lexer.LexerException | fluxdsl.parser.ParserException e) {
            // Expected
        }
    }

    // ---- helpers ----

    static void assertEqual(Ast.Root a, Ast.Root b) {
        var aItems = a.items();
        var bItems = b.items();
        if (aItems.size() != bItems.size())
            throw new AssertionError("Item count: " + aItems.size() + " vs " + bItems.size());
        for (int i = 0; i < aItems.size(); i++) assertItemEqual(aItems.get(i), bItems.get(i));
    }

    static void assertItemEqual(Item a, Item b) {
        if (a instanceof Item.Include ia && b instanceof Item.Include ib) {
            if (!ia.path().equals(ib.path()))
                throw new AssertionError("Include path: " + ia.path() + " vs " + ib.path());
        } else if (a instanceof Item.Schema sa && b instanceof Item.Schema sb) {
            if (!sa.path().equals(sb.path()))
                throw new AssertionError("Schema path: " + sa.path() + " vs " + sb.path());
        } else if (a instanceof Item.Pair pa && b instanceof Item.Pair pb) {
            if (!pa.key().equals(pb.key()))
                throw new AssertionError("Key: " + pa.key() + " vs " + pb.key());
            assertValueEqual(pa.value(), pb.value());
        } else {
            throw new AssertionError("Item type mismatch: " + a.getClass() + " vs " + b.getClass());
        }
    }

    static void assertValueEqual(Value a, Value b) {
        if (a instanceof Value.Str sa && b instanceof Value.Str sb) {
            if (!sa.value().equals(sb.value()))
                throw new AssertionError("Str: " + sa.value() + " vs " + sb.value());
        } else if (a instanceof Value.Num na && b instanceof Value.Num nb) {
            if (Double.compare(na.value(), nb.value()) != 0)
                throw new AssertionError("Num: " + na.value() + " vs " + nb.value());
        } else if (a instanceof Value.Bool ba && b instanceof Value.Bool bb) {
            if (ba.value() != bb.value())
                throw new AssertionError("Bool: " + ba.value() + " vs " + bb.value());
        } else if (a instanceof Value.Null && b instanceof Value.Null) {
        } else if (a instanceof Value.Obj oa && b instanceof Value.Obj ob) {
            var ea = oa.entries();
            var eb = ob.entries();
            if (ea.size() != eb.size())
                throw new AssertionError("Obj entries: " + ea.size() + " vs " + eb.size());
            for (int i = 0; i < ea.size(); i++) assertEntryEqual(ea.get(i), eb.get(i));
        } else if (a instanceof Value.Lst la && b instanceof Value.Lst lb) {
            var ea = la.elements();
            var eb = lb.elements();
            if (ea.size() != eb.size())
                throw new AssertionError("Lst size: " + ea.size() + " vs " + eb.size());
            for (int i = 0; i < ea.size(); i++) assertValueEqual(ea.get(i), eb.get(i));
        } else {
            throw new AssertionError("Value type mismatch: " + a.getClass() + " vs " + b.getClass());
        }
    }

    static void assertEntryEqual(ObjEntry a, ObjEntry b) {
        if (a instanceof ObjEntry.ObjPair pa && b instanceof ObjEntry.ObjPair pb) {
            if (!pa.key().equals(pb.key()))
                throw new AssertionError("ObjPair key: " + pa.key() + " vs " + pb.key());
            assertValueEqual(pa.value(), pb.value());
        } else if (a instanceof ObjEntry.ObjInclude ia && b instanceof ObjEntry.ObjInclude ib) {
            if (!ia.path().equals(ib.path()))
                throw new AssertionError("ObjInclude path: " + ia.path() + " vs " + ib.path());
        } else if (a instanceof ObjEntry.ObjSchema sa && b instanceof ObjEntry.ObjSchema sb) {
            if (!sa.path().equals(sb.path()))
                throw new AssertionError("ObjSchema path: " + sa.path() + " vs " + sb.path());
        } else {
            throw new AssertionError("Entry type mismatch: " + a.getClass() + " vs " + b.getClass());
        }
    }
}
