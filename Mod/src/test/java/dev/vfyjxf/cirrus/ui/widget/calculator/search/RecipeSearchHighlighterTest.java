package dev.vfyjxf.cirrus.ui.widget.calculator.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeSearchHighlighterTest {

    @Test
    void highlightsJeiPrefixesFieldsOperatorsAndSyntax() {
        List<SearchHighlightSegment> segments = RecipeSearchHighlighter.highlight("@minecraft in:oak time>=10s | -#burn");

        assertKind(segments, "@", SearchHighlightKind.prefix);
        assertKind(segments, "in", SearchHighlightKind.field);
        assertKind(segments, ":", SearchHighlightKind.operator);
        assertKind(segments, "time", SearchHighlightKind.field);
        assertKind(segments, ">=", SearchHighlightKind.operator);
        assertKind(segments, "|", SearchHighlightKind.keyword);
        assertKind(segments, "-", SearchHighlightKind.negation);
        assertKind(segments, "#", SearchHighlightKind.prefix);
    }

    @Test
    void marksTheOffendingTokenWhenTheExpressionIsInvalid() {
        List<SearchHighlightSegment> segments = RecipeSearchHighlighter.highlight("oak |");

        assertKind(segments, "|", SearchHighlightKind.error);
    }

    @Test
    void highlightsRangeAndWildcardOperatorsInsideValues() {
        List<SearchHighlightSegment> segments = RecipeSearchHighlighter.highlight("out:smoker * 1..64 has:smok* cat:bucket");

        assertKind(segments, "out", SearchHighlightKind.field);
        assertKind(segments, "*", SearchHighlightKind.operator);
        assertKind(segments, "..", SearchHighlightKind.operator);
        assertKind(segments, "has", SearchHighlightKind.field);
        assertKind(segments, "cat", SearchHighlightKind.field);
    }

    @Test
    void keepsPartialQuotedExpressionsReadable() {
        List<SearchHighlightSegment> segments = RecipeSearchHighlighter.highlight("name:\"oak plank");

        assertKind(segments, "name", SearchHighlightKind.field);
        assertKind(segments, ":", SearchHighlightKind.operator);
        assertKind(segments, "\"", SearchHighlightKind.error);
        assertKind(segments, "oak plank", SearchHighlightKind.text);
    }

    private static void assertKind(List<SearchHighlightSegment> segments, String text, SearchHighlightKind kind) {
        assertTrue(
            segments.stream().anyMatch(segment -> text.equals(segment.text()) && segment.kind() == kind),
            () -> "Expected segment '" + text + "' to be " + kind + " but got " + segments
        );
    }
}
