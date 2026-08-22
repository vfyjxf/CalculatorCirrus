package dev.vfyjxf.cirrus.ui.widget.calculator.search;

import dev.vfyjxf.cirrus.api.recipe.search.CirrusSearchFields;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseIndex;
import dev.vfyjxf.cloudlib.api.search.SearchExpression;
import dev.vfyjxf.cloudlib.api.search.SearchField;
import dev.vfyjxf.cloudlib.api.search.SearchParseException;
import dev.vfyjxf.cloudlib.api.search.SearchParser;
import dev.vfyjxf.cloudlib.api.search.SearchSuggestionValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RecipeDatabaseSuggestionSource implements SearchSuggestionSource {
    private final RecipeDatabaseIndex index;

    public RecipeDatabaseSuggestionSource(RecipeDatabaseIndex index) {
        this.index = index == null ? RecipeDatabaseIndex.ofRecords(List.of()) : index;
    }

    @Override
    public List<SearchSuggestion> suggest(String query, int cursor, int limit) {
        String input = query == null ? "" : query;
        int max = Math.max(0, limit);
        if (max == 0) {
            return List.of();
        }
        ArrayList<SearchSuggestion> out = new ArrayList<>(SearchSuggestions.suggestSyntax(input, cursor, max));
        Token token = tokenAt(input, cursor);
        Context context = context(input, token);
        RecipeDatabaseIndex candidateIndex = suggestionIndex(input, token);
        if (isAmountModifierToken(token.body())) {
            SearchField amountField = previousContentField(input, token.start());
            if (amountField != null) {
                addStandaloneAmountValues(out, candidateIndex, token, amountField, max * 3);
            }
        } else if (context.field() != null) {
            addFieldValues(out, candidateIndex, input, token, context.field(), max * 3);
        } else if (isPrefixToken(token.body())) {
            SearchField field = CirrusSearchFields.SET.byJeiPrefix(token.body().charAt(0));
            if (field != null) {
                addPrefixValues(out, candidateIndex, token, field, max * 3);
            }
        } else if (!token.text().isBlank()) {
            addGeneralValues(out, candidateIndex, token, max * 3);
        }
        String prefix = token.valuePrefix().toLowerCase(Locale.ROOT);
        out.sort(Comparator
            .comparingInt((SearchSuggestion suggestion) -> rank(suggestion, prefix))
            .thenComparing(Comparator.comparingInt(SearchSuggestion::count).reversed())
            .thenComparing(SearchSuggestion::token));
        return out.stream().distinct().limit(max).toList();
    }

    private RecipeDatabaseIndex suggestionIndex(String input, Token token) {
        SearchExpression expression = previousFilterExpression(input, token.start());
        return expression == null ? index : index.filtered(expression);
    }

    private void addFieldValues(ArrayList<SearchSuggestion> out, RecipeDatabaseIndex candidateIndex, String input, Token token, SearchField field, int limit) {
        String fieldPrefix = input.substring(token.start(), token.valueStart());
        String valuePrefix = token.valuePrefix().toLowerCase(Locale.ROOT);
        for (String value : candidateIndex.textCandidates(field, limit * 2)) {
            if (isContentField(field) && isNumericTextCandidate(value)) {
                continue;
            }
            if (valuePrefix.isEmpty() || value.toLowerCase(Locale.ROOT).contains(valuePrefix)) {
                out.add(new SearchSuggestion(fieldPrefix + value, fieldPrefix + value, token.start(), token.end()));
                if (out.size() >= limit) {
                    return;
                }
            }
        }
        if (valuePrefix.length() >= 2) {
            // append whole-word completions from the token dictionary after
            // the full-value candidates (words narrow faster than ids)
            for (SearchSuggestionValue value : candidateIndex.tokenSuggestions(field, valuePrefix, limit)) {
                if (isNumericTextCandidate(value.value()) || containsToken(out, value.value())) {
                    continue;
                }
                out.add(new SearchSuggestion(fieldPrefix + value.value(), fieldPrefix + value.value(), token.start(), token.end(), value.count()));
            }
        }
    }

    private static boolean containsToken(List<SearchSuggestion> suggestions, String token) {
        for (SearchSuggestion suggestion : suggestions) {
            if (suggestion.token().equals(token) || suggestion.token().endsWith(":" + token)) {
                return true;
            }
        }
        return false;
    }

    private void addPrefixValues(ArrayList<SearchSuggestion> out, RecipeDatabaseIndex candidateIndex, Token token, SearchField field, int limit) {
        String prefix = token.leading() + token.body().substring(0, 1);
        String valuePrefix = token.body().substring(1).toLowerCase(Locale.ROOT);
        for (String value : candidateIndex.textCandidates(field, limit)) {
            if (valuePrefix.isEmpty() || value.toLowerCase(Locale.ROOT).contains(valuePrefix)) {
                out.add(new SearchSuggestion(prefix + value, prefix + value, token.start(), token.end()));
            }
        }
    }

    private void addGeneralValues(ArrayList<SearchSuggestion> out, RecipeDatabaseIndex candidateIndex, Token token, int limit) {
        String valuePrefix = token.body().toLowerCase(Locale.ROOT);
        Map<SearchField, List<String>> values = candidateIndex.textCandidateMap(Math.max(4, limit));
        for (SearchField field : List.of(CirrusSearchFields.out, CirrusSearchFields.name, CirrusSearchFields.id, CirrusSearchFields.in, CirrusSearchFields.cat, CirrusSearchFields.mod, CirrusSearchFields.tag, CirrusSearchFields.db, CirrusSearchFields.system)) {
            for (String value : values.getOrDefault(field, List.of())) {
                if (isContentField(field) && isNumericTextCandidate(value)) {
                    continue;
                }
                if (value.toLowerCase(Locale.ROOT).contains(valuePrefix)) {
                    String replacement = token.leading() + value;
                    out.add(new SearchSuggestion(replacement, replacement, token.start(), token.end()));
                }
            }
        }
        if (valuePrefix.length() >= 2) {
            // whole-word completions ranked by postings volume, appended
            // after full-value candidates; numbers stay out of text values
            for (SearchSuggestionValue value : candidateIndex.tokenSuggestions(valuePrefix, limit)) {
                if (isNumericTextCandidate(value.value()) || containsToken(out, value.value())) {
                    continue;
                }
                String replacement = token.leading() + value.value();
                out.add(new SearchSuggestion(replacement, replacement, token.start(), token.end(), value.count()));
            }
        }
    }

    private void addStandaloneAmountValues(ArrayList<SearchSuggestion> out, RecipeDatabaseIndex candidateIndex, Token token, SearchField field, int limit) {
        AmountPrefix amount = amountPrefix(token.body());
        for (String value : candidateIndex.numberCandidates(field, limit)) {
            if (!amount.numberPrefix().isEmpty() && !value.startsWith(amount.numberPrefix())) {
                continue;
            }
            String replacement = token.leading() + "* " + amount.operator() + value;
            out.add(new SearchSuggestion(replacement, replacement, token.start(), token.end()));
        }
    }

    private static SearchExpression previousFilterExpression(String input, int tokenStart) {
        String completed = completedQueryBefore(input, tokenStart);
        if (completed.isBlank()) {
            return new SearchExpression.All();
        }
        try {
            return CirrusSearchFields.parser().parse(completed);
        } catch (SearchParseException ignored) {
            return null;
        }
    }

    private static String completedQueryBefore(String input, int tokenStart) {
        int end = Math.max(0, Math.min(tokenStart, input.length()));
        String completed = input.substring(0, end).stripTrailing();
        while (endsWithExplicitAnd(completed)) {
            completed = completed.substring(0, trailingTokenStart(completed)).stripTrailing();
        }
        return completed;
    }

    private static boolean endsWithExplicitAnd(String input) {
        if (input.isBlank()) {
            return false;
        }
        String token = input.substring(trailingTokenStart(input)).toLowerCase(Locale.ROOT);
        return token.equals("and") || token.equals("&");
    }

    private static int trailingTokenStart(String input) {
        int start = input.length();
        while (start > 0 && !Character.isWhitespace(input.charAt(start - 1)) && input.charAt(start - 1) != '(') {
            start--;
        }
        return start;
    }

    private static Context context(String input, Token token) {
        String body = token.body();
        int colon = body.indexOf(':');
        if (colon > 0) {
            String alias = body.substring(0, colon);
            if (CirrusSearchFields.SET.isKnownAlias(alias)) {
                return new Context(CirrusSearchFields.SET.byAlias(alias));
            }
        }
        if (body.length() > 1) {
            SearchField prefixed = CirrusSearchFields.SET.byJeiPrefix(body.charAt(0));
            if (prefixed != null) {
                return new Context(prefixed);
            }
        }
        return new Context(null);
    }

    private static boolean isPrefixToken(String text) {
        return text != null && !text.isBlank() && CirrusSearchFields.SET.byJeiPrefix(text.charAt(0)) != null;
    }

    private static boolean isAmountModifierToken(String text) {
        return text != null && text.startsWith("*");
    }

    private static AmountPrefix amountPrefix(String raw) {
        String suffix = raw == null || raw.isEmpty() ? "" : raw.substring(1).trim();
        for (String operator : List.of(">=", "<=", ">", "<", "=")) {
            if (suffix.startsWith(operator)) {
                return new AmountPrefix(operator, suffix.substring(operator.length()));
            }
        }
        return new AmountPrefix("", suffix);
    }

    private static SearchField previousContentField(String input, int tokenStart) {
        Token previous = previousToken(input, tokenStart);
        if (previous == null) {
            return null;
        }
        SearchField field = context(input, previous).field();
        if (isContentField(field)) {
            return field;
        }
        return field == null && isPlainContentToken(previous.body()) ? CirrusSearchFields.contains : null;
    }

    private static boolean isPlainContentToken(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '>' || c == '<' || c == '=' || c == '~' || c == '!') {
                return false;
            }
        }
        int colon = body.indexOf(':');
        return colon <= 0 || !CirrusSearchFields.SET.isKnownAlias(body.substring(0, colon));
    }

    private static Token previousToken(String input, int tokenStart) {
        int end = Math.max(0, Math.min(tokenStart, input.length()));
        while (end > 0 && Character.isWhitespace(input.charAt(end - 1))) {
            end--;
        }
        if (end <= 0) {
            return null;
        }
        int start = end;
        while (start > 0 && !Character.isWhitespace(input.charAt(start - 1)) && input.charAt(start - 1) != '(') {
            start--;
        }
        return tokenAt(input, end);
    }

    private static boolean isContentField(SearchField field) {
        if (field == null) {
            return false;
        }
        return field.contentChannel();
    }

    private static boolean isNumericTextCandidate(String value) {
        return value != null && value.trim().matches("[+-]?\\d+(?:\\.\\d+)?(?:\\s.*|[xX*])?");
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
        } else if (isPrefixToken(body)) {
            valueStart = start + negationLength + 1;
        }
        return new Token(start, end, text, negationLength, Math.min(end, valueStart));
    }

    private record Context(SearchField field) {
    }

    private record AmountPrefix(String operator, String numberPrefix) {
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
