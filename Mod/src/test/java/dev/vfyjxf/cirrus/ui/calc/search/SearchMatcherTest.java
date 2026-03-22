package dev.vfyjxf.cirrus.ui.calc.search;

import dev.vfyjxf.cirrus.ui.calc.database.DatabaseDefinition;
import dev.vfyjxf.cirrus.ui.calc.database.DatabaseKind;
import dev.vfyjxf.cirrus.ui.calc.target.RecipeCandidate;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchMatcherTest {
    @Test
    void matchesDefaultOutput() {
        DatabaseDefinition db = new DatabaseDefinition("db", "DB", DatabaseKind.system, true, 0, List.of(Namespace.of("core", "craft")));
        RecipeCandidate candidate = new RecipeCandidate(
            "core:craft", "r1", "Stick", db, List.of(db), null, null,
            List.of("stick"), List.of("plank")
        );

        SearchExpression expr = new SearchParser().parse("stick");
        assertTrue(SearchMatcher.matches(expr, candidate));
    }
}
