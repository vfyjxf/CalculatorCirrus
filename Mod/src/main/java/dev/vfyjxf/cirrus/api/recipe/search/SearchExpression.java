package dev.vfyjxf.cirrus.api.recipe.search;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.List;

public sealed interface SearchExpression permits SearchExpression.All,
                                                 SearchExpression.And,
                                                 SearchExpression.Or,
                                                 SearchExpression.Not,
                                                 SearchExpression.Term {
    Codec<SearchExpression> CODEC = Codec.recursive(
        "cirrus_search_expression",
        self -> Codec.STRING.dispatch("type", SearchExpression::codecType, type -> codecForType(type, self))
    );

    private static MapCodec<? extends SearchExpression> codecForType(String type, Codec<SearchExpression> self) {
        return switch (type) {
            case "all" -> MapCodec.unit(new All());
            case "and" -> self.listOf().fieldOf("terms").xmap(And::new, And::terms);
            case "or" -> self.listOf().fieldOf("terms").xmap(Or::new, Or::terms);
            case "not" -> self.fieldOf("term").xmap(Not::new, Not::term);
            case "term" -> SearchTerm.CODEC.fieldOf("term").xmap(Term::new, Term::term);
            default -> throw new IllegalArgumentException("Unknown search expression type: " + type);
        };
    }

    default String codecType() {
        return switch (this) {
            case All ignored -> "all";
            case And ignored -> "and";
            case Or ignored -> "or";
            case Not ignored -> "not";
            case Term ignored -> "term";
        };
    }

    record All() implements SearchExpression {
    }

    record And(List<SearchExpression> terms) implements SearchExpression {
    }

    record Or(List<SearchExpression> terms) implements SearchExpression {
    }

    record Not(SearchExpression term) implements SearchExpression {
    }

    record Term(SearchTerm term) implements SearchExpression {
    }
}
