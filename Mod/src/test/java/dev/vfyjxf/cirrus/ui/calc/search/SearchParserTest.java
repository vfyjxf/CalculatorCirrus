package dev.vfyjxf.cirrus.ui.calc.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchParserTest {
    @Test
    void parsesOrAndNot() {
        SearchParser parser = new SearchParser();
        SearchExpression expr = parser.parse("output:iron | -@mod:jei");
        assertTrue(expr instanceof SearchExpression.Or);
    }
}
