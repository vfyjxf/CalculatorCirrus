package dev.vfyjxf.cirrus.api.recipe.search;

import dev.vfyjxf.cirrus.api.search.SearchCodecs;
import dev.vfyjxf.cirrus.api.search.SearchField;
import dev.vfyjxf.cirrus.api.search.SearchFieldSet;
import dev.vfyjxf.cirrus.api.search.SearchFieldSpec;
import dev.vfyjxf.cirrus.api.search.SearchParser;

import java.util.List;

/**
 * Cirrus' declared search vocabulary: fields are scenario definitions bound
 * into one set — the shared library provides the constraints and machinery,
 * not a registry. All cirrus search code parses, indexes and matches against
 * {@link #set}.
 */
public final class CirrusSearchFields {
    /** Virtual aggregate of the content channels; bare words parse to {@link #text}. */
    public static final SearchField contains = SearchField.of(
        "contains",
        new SearchFieldSpec(30, false, true, null, SearchFieldSpec.Unit.none, List.of("has"), true)
    );
    public static final SearchField text = SearchField
            .of("text", new SearchFieldSpec(60, false, false, null, "q", "search"));
    public static final SearchField mod = SearchField.of("mod", new SearchFieldSpec(70, false, false, '@', "@mod"));
    public static final SearchField tag = SearchField
            .of("tag", new SearchFieldSpec(80, false, false, '$', "$tag", "tags"));
    public static final SearchField tooltip = SearchField
            .of("tooltip", new SearchFieldSpec(80, false, false, '#', "#tooltip", "tip"));
    public static final SearchField name = SearchField.of("name", new SearchFieldSpec(120, false, false, null, "n"));
    public static final SearchField id = SearchField
            .of("id", new SearchFieldSpec(100, false, false, '&', "&id", "registry", "rl"));
    public static final SearchField out = SearchField
            .of("out", new SearchFieldSpec(110, false, true, null, "o", "result", "results"));
    public static final SearchField outId = SearchField
            .of("out_id", new SearchFieldSpec(110, false, true, null, "out.id", "o.id", "result.id", "results.id"));
    public static final SearchField in = SearchField
            .of("in", new SearchFieldSpec(90, false, true, null, "i", "ingredient", "ingredients"));
    public static final SearchField inId = SearchField.of(
        "in_id",
        new SearchFieldSpec(90, false, true, null, "in.id", "i.id", "ingredient.id", "ingredients.id")
    );
    public static final SearchField system = SearchField
            .of("system", new SearchFieldSpec(50, false, false, null, "recipe"));
    public static final SearchField db = SearchField.of("db", new SearchFieldSpec(40, false, false, null, "database"));
    public static final SearchField machine = SearchField.of("machine", new SearchFieldSpec(50, false, false, null));
    public static final SearchField time = SearchField
            .of("time", new SearchFieldSpec(30, true, false, null, SearchFieldSpec.Unit.time, List.of()));
    public static final SearchField energy = SearchField
            .of("energy", new SearchFieldSpec(30, true, false, null, SearchFieldSpec.Unit.energy, List.of()));
    public static final SearchField cat = SearchField.of("cat", new SearchFieldSpec(85, false, true, null));
    public static final SearchField catId = SearchField
            .of("cat_id", new SearchFieldSpec(85, false, true, null, "cat.id"));
    public static final SearchField byproduct = SearchField
            .of("byproduct", new SearchFieldSpec(30, false, true, null, "bp", "by"));
    public static final SearchField byproductId = SearchField
            .of("byproduct_id", new SearchFieldSpec(30, false, true, null, "bp.id", "by.id", "byproduct.id"));
    public static final SearchField chance = SearchField
            .of("chance", new SearchFieldSpec(30, true, false, null, SearchFieldSpec.Unit.chance, List.of()));
    public static final SearchField tier = SearchField.of("tier", new SearchFieldSpec(30, true, false, null));
    public static final SearchField level = SearchField.of("level", new SearchFieldSpec(30, true, false, null));
    public static final SearchField context = SearchField.of("context", new SearchFieldSpec(40, false, false, '%'));
    public static final SearchField plan = SearchField.of("plan", new SearchFieldSpec(40, false, false, null));
    public static final SearchField profile = SearchField.of("profile", new SearchFieldSpec(40, false, false, null));

    public static final SearchFieldSet set = SearchFieldSet.of(
        contains,
        text,
        mod,
        tag,
        tooltip,
        name,
        id,
        out,
        outId,
        in,
        inId,
        system,
        db,
        machine,
        time,
        energy,
        cat,
        catId,
        byproduct,
        byproductId,
        chance,
        tier,
        level,
        context,
        plan,
        profile
    );

    public static SearchParser parser() {
        return new SearchParser(set, text);
    }

    public static SearchCodecs codecs() {
        return SearchCodecs.of(set);
    }

    private CirrusSearchFields() {}
}
