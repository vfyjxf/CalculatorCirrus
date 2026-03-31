package dev.vfyjxf.cirrus.ui.widget.calculator.search;

import java.util.List;

@FunctionalInterface
public interface SearchSuggestionSource {
    List<SearchSuggestion> suggest(String query, int cursor, int limit);

    static SearchSuggestionSource syntaxOnly() {
        return SearchSuggestions::suggestSyntax;
    }
}
