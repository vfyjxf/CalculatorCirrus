package dev.vfyjxf.cirrus.ui.widget.calculator.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchSuggestionsTest {

    @Test
    void syntaxSuggestionsDoNotInventDatabaseValuesForPrefixOnlyToken() {
        List<SearchSuggestion> suggestions = SearchSuggestions.suggest("@", 1, 6);

        // JEI prefix @ is now a valid syntax suggestion, but no database values
        assertTrue(suggestions.stream().anyMatch(s -> s.replacement().equals("@")));
        assertFalse(suggestions.stream().anyMatch(s -> s.replacement().contains("minecraft")));
    }

    @Test
    void appliesSuggestionWithoutExpandingTheWholeQuery() {
        SearchSuggestion suggestion = SearchSuggestions.suggest("oak out", 7, 6).getFirst();

        assertEquals("oak out:", suggestion.applyTo("oak out"));
    }

    @Test
    void byproductSuggestionUsesShortPredicateAlias() {
        List<SearchSuggestion> suggestions = SearchSuggestions.suggest("by", 2, 8);

        assertTrue(suggestions.stream().anyMatch(suggestion -> "bp:".equals(suggestion.replacement())));
    }

    @Test
    void syntaxSuggestionsPreserveLeadingNegation() {
        SearchSuggestion suggestion = SearchSuggestions.suggest("-out", 4, 6).getFirst();

        assertEquals("-out:", suggestion.applyTo("-out"));
    }

    @Test
    void emptyQueryShowsShortSyntaxStarters() {
        List<SearchSuggestion> suggestions = SearchSuggestions.suggest("", 0, 24);

        // With expanded entries, all fields+prefixes (27) fit in limit 24 after sort
        assertTrue(suggestions.stream().anyMatch(s -> "in:".equals(s.replacement())));
        assertTrue(suggestions.stream().anyMatch(s -> "out:".equals(s.replacement())));
        assertTrue(suggestions.stream().anyMatch(s -> "has:".equals(s.replacement())));
        assertTrue(suggestions.stream().anyMatch(s -> "mod:".equals(s.replacement())));
        assertTrue(suggestions.stream().anyMatch(s -> "machine:".equals(s.replacement())));
        assertFalse(suggestions.stream().anyMatch(s -> s.replacement().contains("minecraft")));
    }
}
