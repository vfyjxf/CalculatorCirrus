package dev.vfyjxf.cirrus.ui.widget.calculator.search;

import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SearchSuggestions {
    private static final List<Entry> entries = List.of(
        // Field entries
        new Entry(List.of("has", "contains"), "has:", true, CirrusLangs.Calculator.Database.searchPredicateHasDesc),
        new Entry(List.of("text", "q", "search"), "text:", true, CirrusLangs.Calculator.Database.searchPredicateTextDesc),
        new Entry(List.of("mod", "@mod"), "mod:", true, CirrusLangs.Calculator.Database.searchPredicateModDesc),
        new Entry(List.of("tag", "$tag", "tags"), "tag:", true, CirrusLangs.Calculator.Database.searchPredicateTagDesc),
        new Entry(List.of("tooltip", "#tooltip", "tip"), "tooltip:", true, CirrusLangs.Calculator.Database.searchPredicateTooltipDesc),
        new Entry(List.of("id", "&id", "registry", "rl"), "id:", true, CirrusLangs.Calculator.Database.searchPredicateIdDesc),
        new Entry(List.of("name", "n"), "name:", true, CirrusLangs.Calculator.Database.searchPredicateNameDesc),
        new Entry(List.of("out", "o", "result", "results"), "out:", true, CirrusLangs.Calculator.Database.searchPredicateOutDesc),
        new Entry(List.of("in", "i", "ingredient", "ingredients"), "in:", true, CirrusLangs.Calculator.Database.searchPredicateInDesc),
        new Entry(List.of("system", "recipe"), "system:", true, CirrusLangs.Calculator.Database.searchPredicateSystemDesc),
        new Entry(List.of("db", "database"), "db:", true, CirrusLangs.Calculator.Database.searchPredicateDbDesc),
        new Entry(List.of("machine"), "machine:", true, CirrusLangs.Calculator.Database.searchPredicateMachineDesc),
        new Entry(List.of("time"), "time>=", true, CirrusLangs.Calculator.Database.searchPredicateTimeDesc),
        new Entry(List.of("energy"), "energy<=", true, CirrusLangs.Calculator.Database.searchPredicateEnergyDesc),
        new Entry(List.of("cat"), "cat:", true, CirrusLangs.Calculator.Database.searchPredicateCatDesc),
        new Entry(List.of("bp", "by", "byproduct"), "bp:", true, CirrusLangs.Calculator.Database.searchPredicateByproductDesc),
        new Entry(List.of("chance"), "chance>=", true, CirrusLangs.Calculator.Database.searchPredicateChanceDesc),
        new Entry(List.of("tier"), "tier>=", true, CirrusLangs.Calculator.Database.searchPredicateTierDesc),
        new Entry(List.of("level"), "level>=", true, CirrusLangs.Calculator.Database.searchPredicateLevelDesc),
        new Entry(List.of("context", "%context"), "context:", true, CirrusLangs.Calculator.Database.searchPredicateContextDesc),
        new Entry(List.of("plan"), "plan:", true, CirrusLangs.Calculator.Database.searchPredicatePlanDesc),
        new Entry(List.of("profile"), "profile:", true, CirrusLangs.Calculator.Database.searchPredicateProfileDesc),
        // JEI prefix entries
        new Entry(List.of("@"), "@", true, CirrusLangs.Calculator.Database.searchPredicateModDesc),
        new Entry(List.of("$"), "$", true, CirrusLangs.Calculator.Database.searchPredicateTagDesc),
        new Entry(List.of("#"), "#", true, CirrusLangs.Calculator.Database.searchPredicateTooltipDesc),
        new Entry(List.of("&"), "&", true, CirrusLangs.Calculator.Database.searchPredicateIdDesc),
        new Entry(List.of("%"), "%", true, CirrusLangs.Calculator.Database.searchPredicateContextDesc),
        // Operator entries
        new Entry(List.of(">="), ">=", false, CirrusLangs.Calculator.Database.searchPredicateCompareDesc),
        new Entry(List.of("<="), "<=", false, CirrusLangs.Calculator.Database.searchPredicateCompareDesc),
        new Entry(List.of(">"), ">", false, CirrusLangs.Calculator.Database.searchPredicateCompareDesc),
        new Entry(List.of("<"), "<", false, CirrusLangs.Calculator.Database.searchPredicateCompareDesc),
        new Entry(List.of("="), "=", false, CirrusLangs.Calculator.Database.searchPredicateExactDesc),
        new Entry(List.of("~"), "~", false, CirrusLangs.Calculator.Database.searchPredicateWildcardDesc),
        new Entry(List.of("!="), "!=", false, CirrusLangs.Calculator.Database.searchPredicateNotDesc),
        // Quote entry
        new Entry(List.of("\""), "\"", false, CirrusLangs.Calculator.Database.searchPredicateQuoteDesc),
        // Logical connectors
        new Entry(List.of("|"), "|", false, CirrusLangs.Calculator.Database.searchPredicateOrDesc),
        new Entry(List.of("-"), "-", false, CirrusLangs.Calculator.Database.searchPredicateNotDesc),
        new Entry(List.of("("), "(", false, CirrusLangs.Calculator.Database.searchPredicateGroupDesc)
    );

    private SearchSuggestions() {
    }

    public static List<SearchSuggestion> suggest(String query, int cursor, int limit) {
        return suggestSyntax(query, cursor, limit);
    }

    public static List<SearchSuggestion> suggestSyntax(String query, int cursor, int limit) {
        String input = query == null ? "" : query;
        int clampedCursor = Math.max(0, Math.min(cursor, input.length()));
        Token token = tokenAt(input, clampedCursor);
        String prefix = token.completionText().toLowerCase(Locale.ROOT);
        ArrayList<SearchSuggestion> out = new ArrayList<>();

        for (Entry entry : entries) {
            if (!matches(entry, prefix)) {
                continue;
            }
            out.add(new SearchSuggestion(
                entry.replacement,
                entry.replacement,
                token.completionStart(),
                token.end(),
                entry.description().component()
            ));
        }

        if (!prefix.isEmpty()) {
            out.sort(Comparator
                .comparingInt((SearchSuggestion suggestion) -> rank(suggestion, prefix))
                .thenComparing(SearchSuggestion::token));
        }

        int max = Math.max(0, limit);
        if (out.size() <= max) {
            return List.copyOf(out);
        }
        return List.copyOf(out.subList(0, max));
    }

    private static boolean matches(Entry entry, String prefix) {
        if (prefix.isEmpty()) return entry.visibleOnEmpty();
        String p = prefix.toLowerCase(Locale.ROOT);
        for (String key : entry.matchKeys()) {
            if (key.toLowerCase(Locale.ROOT).startsWith(p)) return true;
        }
        return entry.replacement().toLowerCase(Locale.ROOT).startsWith(p);
    }

    private static int rank(SearchSuggestion suggestion, String prefix) {
        String r = suggestion.replacement().toLowerCase(Locale.ROOT);
        String p = prefix.toLowerCase(Locale.ROOT);
        if (p.isEmpty()) return 0;
        if (r.equals(p)) return 0;
        if (r.startsWith(p)) return 1;
        return 2;
    }

    private static Token tokenAt(String input, int cursor) {
        if (input.isEmpty()) {
            return new Token(0, 0, "", 0);
        }
        int start = cursor;
        while (start > 0 && !Character.isWhitespace(input.charAt(start - 1)) && input.charAt(start - 1) != '(') {
            start--;
        }
        int end = cursor;
        while (end < input.length() && !Character.isWhitespace(input.charAt(end)) && input.charAt(end) != ')') {
            end++;
        }
        String text = input.substring(start, end);
        int negationLength = (text.startsWith("-") || text.startsWith("!")) ? 1 : 0;
        return new Token(start, end, text, negationLength);
    }

    private record Entry(List<String> matchKeys, String replacement, boolean visibleOnEmpty, LangEntry description) {
    }

    private record Token(int start, int end, String text, int negationLength) {
        int completionStart() {
            return start + Math.min(negationLength, text.length());
        }

        String completionText() {
            return text.substring(Math.min(negationLength, text.length()));
        }
    }
}
