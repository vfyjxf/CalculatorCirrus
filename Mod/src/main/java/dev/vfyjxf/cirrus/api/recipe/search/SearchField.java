package dev.vfyjxf.cirrus.api.recipe.search;

import com.mojang.serialization.Codec;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum SearchField {
    contains,
    text,
    mod,
    tag,
    tooltip,
    name,
    id,
    out,
    out_id,
    in,
    in_id,
    system,
    db,
    machine,
    time,
    energy,
    cat,
    cat_id,
    byproduct,
    byproduct_id,
    chance,
    tier,
    level,
    context,
    plan,
    profile;

    public static final Codec<SearchField> CODEC = dev.vfyjxf.cloudlib.api.util.Codecs.lowerCaseEnum(SearchField.class);

    public int weight() {
        return switch (this) {
            case name -> 120;
            case out, out_id -> 110;
            case id -> 100;
            case in, in_id -> 90;
            case cat, cat_id -> 85;
            case tag, tooltip -> 80;
            case mod -> 70;
            case text -> 60;
            case machine, system -> 50;
            case db, profile, context, plan -> 40;
            default -> 30;
        };
    }

    public boolean numeric() {
        return switch (this) {
            case time, energy, chance, tier, level -> true;
            default -> false;
        };
    }

    public static boolean isKnownAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            return false;
        }
        return switch (alias.toLowerCase(Locale.ROOT)) {
            case "has", "contains",
                 "text", "q", "search",
                 "@mod", "mod",
                 "$tag", "tag", "tags",
                 "#tooltip", "tooltip", "tip",
                 "&id", "id", "registry", "rl",
                 "name", "n",
                 "out.id", "o.id", "result.id", "results.id",
                 "out", "o", "result", "results",
                 "in.id", "i.id", "ingredient.id", "ingredients.id",
                 "in", "i", "ingredient", "ingredients",
                 "system", "recipe",
                 "db", "database",
                 "machine", "time", "energy",
                 "cat.id",
                 "cat", "bp", "by", "byproduct", "chance",
                 "bp.id", "by.id", "byproduct.id",
                 "tier", "level", "context", "plan", "profile" -> true;
            default -> false;
        };
    }

    public static SearchField fromAlias(String alias) {
        String value = alias.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "has", "contains" -> contains;
            case "text", "q", "search" -> text;
            case "@mod", "mod" -> mod;
            case "$tag", "tag", "tags" -> tag;
            case "#tooltip", "tooltip", "tip" -> tooltip;
            case "&id", "id", "registry", "rl" -> id;
            case "name", "n" -> name;
            case "out.id", "o.id", "result.id", "results.id" -> out_id;
            case "out", "o", "result", "results" -> out;
            case "in.id", "i.id", "ingredient.id", "ingredients.id" -> in_id;
            case "in", "i", "ingredient", "ingredients" -> in;
            case "system", "recipe" -> system;
            case "db", "database" -> db;
            case "machine" -> machine;
            case "time" -> time;
            case "energy" -> energy;
            case "cat.id" -> cat_id;
            case "cat" -> cat;
            case "bp.id", "by.id", "byproduct.id" -> byproduct_id;
            case "bp", "by", "byproduct" -> byproduct;
            case "chance" -> chance;
            case "tier" -> tier;
            case "level" -> level;
            case "context" -> context;
            case "plan" -> plan;
            case "profile" -> profile;
            default -> text;
        };
    }

    public static @Nullable SearchField fromJeiPrefix(char prefix) {
        return switch (prefix) {
            case '@' -> mod;
            case '$' -> tag;
            case '#' -> tooltip;
            case '&' -> id;
            case '%' -> context;
            default -> null;
        };
    }
}
