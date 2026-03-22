package dev.vfyjxf.cirrus.ui.calc.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SearchParser {
    public SearchExpression parse(String input) {
        List<SearchTokenizer.Token> tokens = SearchTokenizer.tokenize(input);
        if (tokens.isEmpty()) {
            return new SearchExpression.And(List.of());
        }
        return parseOr(tokens);
    }

    private SearchExpression parseOr(List<SearchTokenizer.Token> tokens) {
        List<SearchExpression> parts = new ArrayList<>();
        List<SearchTokenizer.Token> current = new ArrayList<>();
        for (SearchTokenizer.Token token : tokens) {
            if (isOrToken(token)) {
                parts.add(parseAnd(current));
                current = new ArrayList<>();
            } else {
                current.add(token);
            }
        }
        parts.add(parseAnd(current));
        return parts.size() == 1 ? parts.getFirst() : new SearchExpression.Or(parts);
    }

    private SearchExpression parseAnd(List<SearchTokenizer.Token> tokens) {
        List<SearchExpression> parts = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            SearchTokenizer.Token token = tokens.get(i);
            if (isAndToken(token)) {
                continue;
            }
            if (isNotToken(token)) {
                if (i + 1 < tokens.size()) {
                    parts.add(new SearchExpression.Not(parseTerm(tokens.get(i + 1))));
                    i++;
                    continue;
                }
                parts.add(parseTerm(token));
                continue;
            }
            parts.add(parseNot(token));
        }
        return parts.size() == 1 ? parts.getFirst() : new SearchExpression.And(parts);
    }

    private SearchExpression parseNot(SearchTokenizer.Token token) {
        if (!token.quoted() && (token.text().startsWith("!") || token.text().startsWith("-"))) {
            return new SearchExpression.Not(parseTerm(new SearchTokenizer.Token(token.text().substring(1), false)));
        }
        return parseTerm(token);
    }

    private SearchExpression parseTerm(SearchTokenizer.Token token) {
        String fieldPart = "output";
        String raw = token.text();
        int idx = token.text().indexOf(':');
        if (idx > 0) {
            fieldPart = token.text().substring(0, idx);
            raw = token.text().substring(idx + 1);
        }
        SearchField field = SearchField.fromPrefix(fieldPart);
        SearchOperator op;
        if ("*".equals(raw)) {
            op = SearchOperator.exists;
        } else if ("!".equals(raw)) {
            op = SearchOperator.notExists;
        } else if (raw.contains("..") || raw.contains(">") || raw.contains("<")) {
            op = SearchOperator.range;
        } else {
            op = SearchOperator.text;
        }
        return new SearchExpression.Term(new SearchTerm(field, op, raw));
    }

    private boolean isAndToken(SearchTokenizer.Token token) {
        return !token.quoted() && "and".equals(token.text().toLowerCase(Locale.ROOT));
    }

    private boolean isOrToken(SearchTokenizer.Token token) {
        if (token.quoted()) {
            return false;
        }
        String raw = token.text().toLowerCase(Locale.ROOT);
        return "|".equals(raw) || "or".equals(raw);
    }

    private boolean isNotToken(SearchTokenizer.Token token) {
        return !token.quoted() && "not".equals(token.text().toLowerCase(Locale.ROOT));
    }
}
