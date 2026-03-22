package dev.vfyjxf.cirrus.ui.calc.search;

import java.util.Locale;

public enum SearchField {
    mod, tag,
    name, id, output, input, system, db, machine,
    time, energy, catalyst, byproduct, chance,
    tier, level, context, plan, profile;

    public static SearchField fromPrefix(String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return switch (p) {
            case "@mod", "mod" -> mod;
            case "#tag", "tag" -> tag;
            case "name" -> name;
            case "id" -> id;
            case "output" -> output;
            case "input" -> input;
            case "system" -> system;
            case "db" -> db;
            case "machine" -> machine;
            case "time" -> time;
            case "energy" -> energy;
            case "catalyst" -> catalyst;
            case "byproduct" -> byproduct;
            case "chance" -> chance;
            case "tier" -> tier;
            case "level" -> level;
            case "context" -> context;
            case "plan" -> plan;
            case "profile" -> profile;
            default -> output;
        };
    }
}
