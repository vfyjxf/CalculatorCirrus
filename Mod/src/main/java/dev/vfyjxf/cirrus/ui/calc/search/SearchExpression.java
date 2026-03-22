package dev.vfyjxf.cirrus.ui.calc.search;

import java.util.List;

public sealed interface SearchExpression permits SearchExpression.And, SearchExpression.Or, SearchExpression.Not, SearchExpression.Term {
    record And(List<SearchExpression> terms) implements SearchExpression {
    }

    record Or(List<SearchExpression> terms) implements SearchExpression {
    }

    record Not(SearchExpression term) implements SearchExpression {
    }

    record Term(SearchTerm term) implements SearchExpression {
    }
}
