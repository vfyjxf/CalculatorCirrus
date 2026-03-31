package dev.vfyjxf.cirrus.api.recipe.search;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchCodecTest {
    @Test
    void searchExpressionRoundTripsThroughCodec() {
        SearchExpression expression = new SearchExpression.And(java.util.List.of(
            new SearchExpression.Term(new SearchTerm(SearchField.text, SearchOperator.contains, "oak")),
            new SearchExpression.Not(new SearchExpression.Term(new SearchTerm(SearchField.tag, SearchOperator.exact, "blocked"))),
            new SearchExpression.Term(new SearchTerm(SearchField.out, SearchOperator.wildcard, "Oak*", "", true))
        ));

        var encoded = SearchExpression.CODEC.encodeStart(JsonOps.INSTANCE, expression).result().orElseThrow();
        SearchExpression decoded = SearchExpression.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();

        assertEquals(expression, decoded);
    }
}
