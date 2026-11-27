package dev.vfyjxf.cirrus.ui.widget.calculator.search;

import dev.vfyjxf.cirrus.api.recipe.search.CirrusSearchFields;
import dev.vfyjxf.cirrus.api.search.SearchField;
import dev.vfyjxf.cirrus.api.search.SearchParseException;
import dev.vfyjxf.cirrus.api.search.SearchParser;
import dev.vfyjxf.cirrus.api.search.SearchSyntaxKind;
import dev.vfyjxf.cirrus.api.search.SearchSyntaxSpan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class RecipeSearchHighlighter {
    private RecipeSearchHighlighter() {}

    public static List<SearchHighlightSegment> highlight(String query) {
        String input = query == null ? "" : query;
        if (input.isEmpty()) {
            return List.of();
        }

        SearchParser parser = CirrusSearchFields.parser();
        List<SearchSyntaxSpan> spans;
        int errorPosition = input.length();
        try {
            spans = parser.parseWithSyntax(input).syntaxSpans();
        } catch (SearchParseException ex) {
            errorPosition = Math.max(0, Math.min(input.length(), ex.position()));
            spans = fillMissingSyntax(input, parser.partialSyntaxSpans());
            if (errorPosition == input.length() && !spans.isEmpty()) {
                errorPosition = spans.getLast().start();
            }
        }

        ArrayList<SearchSyntaxSpan> sorted = new ArrayList<>(spans);
        sorted.sort(Comparator.comparingInt(SearchSyntaxSpan::start).thenComparingInt(SearchSyntaxSpan::end));

        ArrayList<SearchHighlightSegment> out = new ArrayList<>();
        int index = 0;
        for (SearchSyntaxSpan span : sorted) {
            int start = Math.max(0, Math.min(span.start(), input.length()));
            int end = Math.max(start, Math.min(span.end(), input.length()));
            if (start > index) {
                add(out, input, index, start, SearchHighlightKind.text);
            }
            if (end > index) {
                add(out, input, start, end, map(span.kind()));
                index = end;
            }
        }
        if (index < input.length()) {
            add(out, input, index, input.length(), SearchHighlightKind.text);
        }
        if (errorPosition < input.length()) {
            markError(out, input, errorPosition);
        }
        return List.copyOf(out);
    }

    private static List<SearchSyntaxSpan> fillMissingSyntax(String input, List<SearchSyntaxSpan> parsed) {
        ArrayList<SearchSyntaxSpan> sorted = new ArrayList<>(parsed);
        sorted.sort(Comparator.comparingInt(SearchSyntaxSpan::start).thenComparingInt(SearchSyntaxSpan::end));

        ArrayList<SearchSyntaxSpan> out = new ArrayList<>();
        int index = 0;
        for (SearchSyntaxSpan span : sorted) {
            int start = Math.max(0, Math.min(span.start(), input.length()));
            int end = Math.max(start, Math.min(span.end(), input.length()));
            if (start > index) {
                out.addAll(lexicalSpans(input, index, start));
            }
            if (end > index) {
                out.add(new SearchSyntaxSpan(start, end, span.kind()));
                index = end;
            }
        }
        if (index < input.length()) {
            out.addAll(lexicalSpans(input, index, input.length()));
        }
        return List.copyOf(out);
    }

    private static List<SearchSyntaxSpan> lexicalSpans(String input, int start, int end) {
        ArrayList<SearchSyntaxSpan> out = new ArrayList<>();
        int index = start;
        while (index < end) {
            char c = input.charAt(index);
            if (Character.isWhitespace(c)) {
                index++;
                continue;
            }
            switch (c) {
                case '(', ')', '|' -> {
                    out.add(new SearchSyntaxSpan(index, index + 1, SearchSyntaxKind.keyword));
                    index++;
                }
                case '&' -> {
                    if (index + 1 < end && !Character.isWhitespace(input.charAt(index + 1))) {
                        index = readLexicalWord(input, index, end, out);
                    } else {
                        out.add(new SearchSyntaxSpan(index, index + 1, SearchSyntaxKind.keyword));
                        index++;
                    }
                }
                case '-' -> {
                    out.add(new SearchSyntaxSpan(index, index + 1, SearchSyntaxKind.negation));
                    index++;
                }
                case '!' -> {
                    if (index + 1 < end && (input.charAt(index + 1) == '=' || input.charAt(index + 1) == '~')) {
                        out.add(new SearchSyntaxSpan(index, index + 2, SearchSyntaxKind.operator));
                        index += 2;
                    } else {
                        out.add(new SearchSyntaxSpan(index, index + 1, SearchSyntaxKind.negation));
                        index++;
                    }
                }
                case '>', '<', '=', '~', ':' -> {
                    int operatorEnd = (c == '>' || c == '<') && index + 1 < end && input.charAt(index + 1) == '='
                            ? index + 2
                            : index + 1;
                    out.add(new SearchSyntaxSpan(index, operatorEnd, SearchSyntaxKind.operator));
                    index = operatorEnd;
                }
                case '"' -> index = readLexicalQuote(input, index, end, out);
                default -> index = readLexicalWord(input, index, end, out);
            }
        }
        return out;
    }

    private static int readLexicalQuote(String input, int quoteIndex, int end, ArrayList<SearchSyntaxSpan> out) {
        out.add(new SearchSyntaxSpan(quoteIndex, quoteIndex + 1, SearchSyntaxKind.quote));
        int index = quoteIndex + 1;
        boolean escaped = false;
        while (index < end) {
            char c = input.charAt(index);
            if (escaped) {
                escaped = false;
                index++;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                index++;
                continue;
            }
            if (c == '"') {
                if (quoteIndex + 1 < index) {
                    out.add(new SearchSyntaxSpan(quoteIndex + 1, index, SearchSyntaxKind.text));
                }
                out.add(new SearchSyntaxSpan(index, index + 1, SearchSyntaxKind.quote));
                return index + 1;
            }
            index++;
        }
        if (quoteIndex + 1 < end) {
            out.add(new SearchSyntaxSpan(quoteIndex + 1, end, SearchSyntaxKind.text));
        }
        return end;
    }

    private static int readLexicalWord(String input, int start, int end, ArrayList<SearchSyntaxSpan> out) {
        int index = start;
        while (index < end) {
            char c = input.charAt(index);
            if (Character.isWhitespace(c)
                    || c == '('
                    || c == ')'
                    || c == '|'
                    || c == '"'
                    || isStandaloneOperator(input, start, index)) {
                break;
            }
            index++;
        }
        if (index == start) {
            out.add(new SearchSyntaxSpan(start, start + 1, SearchSyntaxKind.text));
            return start + 1;
        }

        String word = input.substring(start, index);
        if (word.equalsIgnoreCase("and") || word.equalsIgnoreCase("or")) {
            out.add(new SearchSyntaxSpan(start, index, SearchSyntaxKind.keyword));
            return index;
        }
        if (word.equalsIgnoreCase("not")) {
            out.add(new SearchSyntaxSpan(start, index, SearchSyntaxKind.negation));
            return index;
        }

        SearchField prefixField = word.length() > 1 ? CirrusSearchFields.set.byJeiPrefix(word.charAt(0)) : null;
        if (prefixField != null) {
            out.add(new SearchSyntaxSpan(start, start + 1, SearchSyntaxKind.prefix));
            addLexicalValueSpans(out, start + 1, word.substring(1), prefixField);
            return index;
        }

        int colon = word.indexOf(':');
        if (colon > 0) {
            String alias = word.substring(0, colon);
            if (CirrusSearchFields.set.isKnownAlias(alias)) {
                SearchField field = Objects.requireNonNull(CirrusSearchFields.set.byAlias(alias));
                out.add(new SearchSyntaxSpan(start, start + colon, SearchSyntaxKind.field));
                out.add(new SearchSyntaxSpan(start + colon, start + colon + 1, SearchSyntaxKind.operator));
                addLexicalValueSpans(out, start + colon + 1, word.substring(colon + 1), field);
                return index;
            }
        }

        out.add(
            new SearchSyntaxSpan(
                start,
                index,
                CirrusSearchFields.set.isKnownAlias(word) ? SearchSyntaxKind.field : SearchSyntaxKind.text
            )
        );
        return index;
    }

    private static boolean isStandaloneOperator(String input, int wordStart, int index) {
        char c = input.charAt(index);
        if (c == '>' || c == '<' || c == '=' || c == '~') {
            return true;
        }
        if (c == '!') {
            return index + 1 < input.length() && (input.charAt(index + 1) == '=' || input.charAt(index + 1) == '~');
        }
        return c == ':' && index == wordStart;
    }

    private static void addLexicalValueSpans(
        ArrayList<SearchSyntaxSpan> out,
        int start,
        String value,
        SearchField field
    ) {
        if (value.isEmpty()) {
            return;
        }
        if (field.numeric()) {
            int range = value.indexOf("..");
            if (range >= 0) {
                addLexicalSpan(out, start, start + range, SearchSyntaxKind.text);
                addLexicalSpan(out, start + range, start + range + 2, SearchSyntaxKind.operator);
                addLexicalValueSpans(out, start + range + 2, value.substring(range + 2), field);
                return;
            }
        }

        int segmentStart = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '*' && c != '?') {
                continue;
            }
            addLexicalSpan(out, start + segmentStart, start + i, SearchSyntaxKind.text);
            addLexicalSpan(out, start + i, start + i + 1, SearchSyntaxKind.operator);
            segmentStart = i + 1;
        }
        addLexicalSpan(out, start + segmentStart, start + value.length(), SearchSyntaxKind.text);
    }

    private static void addLexicalSpan(ArrayList<SearchSyntaxSpan> out, int start, int end, SearchSyntaxKind kind) {
        if (start < end) {
            out.add(new SearchSyntaxSpan(start, end, kind));
        }
    }

    private static void markError(ArrayList<SearchHighlightSegment> segments, String input, int errorPosition) {
        for (int i = 0; i < segments.size(); i++) {
            SearchHighlightSegment segment = segments.get(i);
            if (errorPosition >= segment.start() && errorPosition < segment.end()) {
                segments.set(
                    i,
                    new SearchHighlightSegment(
                        segment.text(),
                        SearchHighlightKind.error,
                        segment.start(),
                        segment.end()
                    )
                );
                return;
            }
        }
        int start = Math.max(0, Math.min(errorPosition, input.length() - 1));
        int end = Math.min(input.length(), start + 1);
        if (start >= end) {
            return;
        }
        for (int i = 0; i < segments.size(); i++) {
            SearchHighlightSegment segment = segments.get(i);
            if (segment.start() <= start && segment.end() >= end) {
                segments.remove(i);
                if (segment.start() < start) {
                    segments.add(
                        i++,
                        new SearchHighlightSegment(
                            input.substring(segment.start(), start),
                            segment.kind(),
                            segment.start(),
                            start
                        )
                    );
                }
                segments.add(
                    i++,
                    new SearchHighlightSegment(input.substring(start, end), SearchHighlightKind.error, start, end)
                );
                if (end < segment.end()) {
                    segments.add(
                        i,
                        new SearchHighlightSegment(
                            input.substring(end, segment.end()),
                            segment.kind(),
                            end,
                            segment.end()
                        )
                    );
                }
                return;
            }
        }
    }

    private static SearchHighlightKind map(SearchSyntaxKind kind) {
        return switch (kind) {
            case text -> SearchHighlightKind.text;
            case field -> SearchHighlightKind.field;
            case prefix -> SearchHighlightKind.prefix;
            case operator -> SearchHighlightKind.operator;
            case keyword -> SearchHighlightKind.keyword;
            case negation -> SearchHighlightKind.negation;
            case quote -> SearchHighlightKind.quote;
            case error -> SearchHighlightKind.error;
        };
    }

    private static void add(
        ArrayList<SearchHighlightSegment> out,
        String input,
        int start,
        int end,
        SearchHighlightKind kind
    ) {
        if (start >= end) {
            return;
        }
        out.add(new SearchHighlightSegment(input.substring(start, end), kind, start, end));
    }
}
