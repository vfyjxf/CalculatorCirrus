package dev.vfyjxf.cirrus.ui.widget.calculator.page;

import dev.vfyjxf.cirrus.api.recipe.search.SearchExpression;
import dev.vfyjxf.cirrus.api.recipe.search.SearchField;
import dev.vfyjxf.cirrus.api.recipe.search.SearchParseException;
import dev.vfyjxf.cirrus.api.recipe.search.SearchParser;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchHighlightKind;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchHighlightSegment;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchSuggestion;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchSuggestionSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

final class RuleMaterialSearch {
    private static final EnumSet<SearchField> supportedFields = EnumSet.of(
        SearchField.contains,
        SearchField.text,
        SearchField.mod,
        SearchField.tag,
        SearchField.name,
        SearchField.id
    );
    private static final List<SyntaxEntry> syntaxEntries = List.of(
        new SyntaxEntry("id", "id:"),
        new SyntaxEntry("name", "name:"),
        new SyntaxEntry("mod", "mod:"),
        new SyntaxEntry("tag", "tag:"),
        new SyntaxEntry("@", "@"),
        new SyntaxEntry("$", "$"),
        new SyntaxEntry("&", "&"),
        new SyntaxEntry("-", "-")
    );

    private RuleMaterialSearch() {
    }

    static SearchExpression parse(String query) {
        if (query == null || query.isBlank()) {
            return new SearchExpression.All();
        }
        try {
            SearchExpression expression = new SearchParser().parse(query);
            return supports(expression) ? expression : null;
        } catch (SearchParseException ignored) {
            return null;
        }
    }

    static List<SearchHighlightSegment> highlight(String query) {
        String input = query == null ? "" : query;
        if (input.isEmpty()) {
            return List.of();
        }
        ArrayList<SearchHighlightSegment> out = new ArrayList<>();
        int index = 0;
        while (index < input.length()) {
            char c = input.charAt(index);
            if (Character.isWhitespace(c)) {
                int start = index++;
                while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                    index++;
                }
                add(out, input, start, index, SearchHighlightKind.text);
                continue;
            }
            int start = index;
            while (index < input.length() && !Character.isWhitespace(input.charAt(index))) {
                index++;
            }
            highlightToken(out, input, start, index);
        }
        return List.copyOf(out);
    }

    static SearchSuggestionSource suggestionSource(List<RuleIngredientCatalog.Entry> entries) {
        return new MaterialSuggestionSource(entries);
    }

    private static boolean supports(SearchExpression expression) {
        return switch (expression) {
            case SearchExpression.All ignored -> true;
            case SearchExpression.And and -> and.terms().stream().allMatch(RuleMaterialSearch::supports);
            case SearchExpression.Or or -> or.terms().stream().allMatch(RuleMaterialSearch::supports);
            case SearchExpression.Not not -> supports(not.term());
            case SearchExpression.Term term -> supportedFields.contains(term.term().field()) && !term.term().hasAmount();
        };
    }

    private static void highlightToken(ArrayList<SearchHighlightSegment> out, String input, int start, int end) {
        int cursor = start;
        char first = input.charAt(start);
        if (first == '-' || first == '!') {
            add(out, input, start, start + 1, SearchHighlightKind.negation);
            cursor = start + 1;
            if (cursor >= end) {
                return;
            }
        }
        char prefix = input.charAt(cursor);
        SearchField prefixed = SearchField.fromJeiPrefix(prefix);
        if (prefixed != null && supportedFields.contains(prefixed)) {
            add(out, input, cursor, cursor + 1, SearchHighlightKind.prefix);
            add(out, input, cursor + 1, end, SearchHighlightKind.text);
            return;
        }
        int colon = input.indexOf(':', cursor);
        if (colon > cursor && colon < end) {
            String alias = input.substring(cursor, colon);
            SearchField field = SearchField.isKnownAlias(alias) ? SearchField.fromAlias(alias) : null;
            if (field != null && supportedFields.contains(field)) {
                add(out, input, cursor, colon, SearchHighlightKind.field);
                add(out, input, colon, colon + 1, SearchHighlightKind.operator);
                add(out, input, colon + 1, end, SearchHighlightKind.text);
                return;
            }
        }
        add(out, input, cursor, end, SearchHighlightKind.text);
    }

    private static void add(ArrayList<SearchHighlightSegment> out, String input, int start, int end, SearchHighlightKind kind) {
        if (start < end) {
            out.add(new SearchHighlightSegment(input.substring(start, end), kind, start, end));
        }
    }

    private static final class MaterialSuggestionSource implements SearchSuggestionSource {
        private final List<RuleIngredientCatalog.Entry> entries;

        private MaterialSuggestionSource(List<RuleIngredientCatalog.Entry> entries) {
            this.entries = entries == null ? List.of() : List.copyOf(entries);
        }

        @Override
        public List<SearchSuggestion> suggest(String query, int cursor, int limit) {
            String input = query == null ? "" : query;
            int max = Math.max(0, limit);
            if (max == 0) {
                return List.of();
            }
            Token token = tokenAt(input, cursor);
            ArrayList<SearchSuggestion> out = new ArrayList<>();
            addSyntax(out, token, max);
            SearchField field = context(token);
            List<RuleIngredientCatalog.Entry> candidates = filteredBefore(input, token.start());
            if (field != null) {
                addFieldValues(out, candidates, token, field, max * 3);
            } else if (!token.body().isBlank()) {
                addGeneralValues(out, candidates, token, max * 3);
            }
            String prefix = token.valuePrefix().toLowerCase(Locale.ROOT);
            out.sort(Comparator
                .comparingInt((SearchSuggestion suggestion) -> rank(suggestion, prefix))
                .thenComparing(SearchSuggestion::token));
            return out.stream().distinct().limit(max).toList();
        }

        private void addSyntax(ArrayList<SearchSuggestion> out, Token token, int limit) {
            String prefix = token.body().toLowerCase(Locale.ROOT);
            for (SyntaxEntry entry : syntaxEntries) {
                if (!prefix.isBlank() && !entry.trigger().startsWith(prefix) && !entry.replacement().startsWith(prefix)) {
                    continue;
                }
                out.add(new SearchSuggestion(entry.replacement(), entry.replacement(), token.valueStart(), token.end()));
                if (out.size() >= limit) {
                    return;
                }
            }
        }

        private void addFieldValues(ArrayList<SearchSuggestion> out, List<RuleIngredientCatalog.Entry> candidates, Token token, SearchField field, int limit) {
            String prefix = token.leading() + token.body().substring(0, Math.max(0, token.valueStart() - token.start() - token.leading().length()));
            String valuePrefix = token.valuePrefix().toLowerCase(Locale.ROOT);
            for (String value : values(candidates, field, limit)) {
                if (!valuePrefix.isEmpty() && !value.toLowerCase(Locale.ROOT).contains(valuePrefix)) {
                    continue;
                }
                out.add(new SearchSuggestion(prefix + value, prefix + value, token.start(), token.end()));
                if (out.size() >= limit) {
                    return;
                }
            }
        }

        private void addGeneralValues(ArrayList<SearchSuggestion> out, List<RuleIngredientCatalog.Entry> candidates, Token token, int limit) {
            String valuePrefix = token.body().toLowerCase(Locale.ROOT);
            for (String value : values(candidates, SearchField.id, limit * 2)) {
                if (value.toLowerCase(Locale.ROOT).contains(valuePrefix)) {
                    String replacement = token.leading() + value;
                    out.add(new SearchSuggestion(replacement, replacement, token.start(), token.end()));
                }
                if (out.size() >= limit) {
                    return;
                }
            }
            for (String value : values(candidates, SearchField.name, limit * 2)) {
                if (value.indexOf(' ') >= 0 || !value.toLowerCase(Locale.ROOT).contains(valuePrefix)) {
                    continue;
                }
                String replacement = token.leading() + value;
                out.add(new SearchSuggestion(replacement, replacement, token.start(), token.end()));
                if (out.size() >= limit) {
                    return;
                }
            }
        }

        private List<RuleIngredientCatalog.Entry> filteredBefore(String input, int tokenStart) {
            SearchExpression expression = parse(completedQueryBefore(input, tokenStart));
            if (expression == null) {
                return entries;
            }
            return entries.stream().filter(entry -> entry.matches(expression)).toList();
        }

        private static List<String> values(List<RuleIngredientCatalog.Entry> candidates, SearchField field, int limit) {
            LinkedHashSet<String> values = new LinkedHashSet<>();
            for (RuleIngredientCatalog.Entry entry : candidates) {
                switch (field) {
                    case id -> values.add(entry.material());
                    case name, text, contains -> values.add(entry.label());
                    case mod -> values.add(entry.namespace());
                    case tag -> values.addAll(entry.tags());
                    default -> {
                    }
                }
                if (values.size() >= limit) {
                    break;
                }
            }
            return List.copyOf(values);
        }

        private static SearchField context(Token token) {
            String body = token.body();
            if (body.length() > 1) {
                SearchField prefixed = SearchField.fromJeiPrefix(body.charAt(0));
                if (prefixed != null && supportedFields.contains(prefixed)) {
                    return prefixed;
                }
            }
            int colon = body.indexOf(':');
            if (colon > 0) {
                String alias = body.substring(0, colon);
                SearchField field = SearchField.isKnownAlias(alias) ? SearchField.fromAlias(alias) : null;
                if (field != null && supportedFields.contains(field)) {
                    return field;
                }
            }
            return null;
        }

        private static String completedQueryBefore(String input, int tokenStart) {
            int end = Math.max(0, Math.min(tokenStart, input.length()));
            return input.substring(0, end).stripTrailing();
        }

        private static int rank(SearchSuggestion suggestion, String prefix) {
            String replacement = suggestion.replacement().toLowerCase(Locale.ROOT);
            if (!prefix.isEmpty() && replacement.equals(prefix)) {
                return 0;
            }
            if (!prefix.isEmpty() && replacement.startsWith(prefix)) {
                return 1;
            }
            if (replacement.endsWith(":")) {
                return 2;
            }
            return 3;
        }

        private static Token tokenAt(String input, int cursor) {
            int clamped = Math.max(0, Math.min(cursor, input.length()));
            int start = clamped;
            while (start > 0 && !Character.isWhitespace(input.charAt(start - 1)) && input.charAt(start - 1) != '(') {
                start--;
            }
            int end = clamped;
            while (end < input.length() && !Character.isWhitespace(input.charAt(end)) && input.charAt(end) != ')') {
                end++;
            }
            String text = input.substring(start, end);
            int negationLength = (text.startsWith("-") || text.startsWith("!")) ? 1 : 0;
            String body = text.substring(Math.min(negationLength, text.length()));
            int valueStart = start + negationLength;
            int colon = body.indexOf(':');
            if (colon >= 0) {
                valueStart = start + negationLength + colon + 1;
            } else if (!body.isEmpty() && SearchField.fromJeiPrefix(body.charAt(0)) != null) {
                valueStart = start + negationLength + 1;
            }
            return new Token(start, end, text, negationLength, Math.min(end, valueStart));
        }
    }

    private record SyntaxEntry(String trigger, String replacement) {
    }

    private record Token(int start, int end, String text, int negationLength, int valueStart) {
        String leading() {
            return text.substring(0, Math.min(negationLength, text.length()));
        }

        String body() {
            return text.substring(Math.min(negationLength, text.length()));
        }

        String valuePrefix() {
            return text.substring(Math.max(0, Math.min(text.length(), valueStart - start)));
        }
    }
}
