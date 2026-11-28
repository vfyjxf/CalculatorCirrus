package dev.vfyjxf.cirrus.api.recipe.projection.pipeline;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.recipe.content.ComplexContent;
import dev.vfyjxf.cirrus.api.recipe.content.EmptyRecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.content.complex.DurabilityContent;
import org.jspecify.annotations.Nullable;

/**
 * Exact, allocation-free content comparison used by projection transactions and diffing
 * (replaces legacy {@code toString()} comparison).
 */
final class ProjectionContentEquality {

    private ProjectionContentEquality() {}

    static boolean sameContent(@Nullable RecipeContent a, @Nullable RecipeContent b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.getClass() != b.getClass()) {
            return false;
        }
        if (a.amount() != b.amount() || Float.compare(a.chance(), b.chance()) != 0) {
            return false;
        }
        return sameIdentityAndComponents(a, b);
    }

    static boolean sameIdentity(@Nullable RecipeContent a, @Nullable RecipeContent b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.getClass() != b.getClass()) {
            return false;
        }
        if (a instanceof StackContent<?, ?> sa && b instanceof StackContent<?, ?> sb) {
            return sa.content().matches(sb.content(), MatchStrategy.identity);
        }
        if (a instanceof DurabilityContent da && b instanceof DurabilityContent db) {
            return sameIdentity(da.ingredient(), db.ingredient());
        }
        return sameIdentityAndComponents(a, b);
    }

    private static boolean sameIdentityAndComponents(RecipeContent a, RecipeContent b) {
        if (a instanceof EmptyRecipeContent) {
            return true;
        }
        if (a instanceof StackContent<?, ?> sa && b instanceof StackContent<?, ?> sb) {
            return sa.matchStrategy().equals(sb.matchStrategy())
                    && sa.content().matches(sb.content(), MatchStrategy.identityAndComponent);
        }
        if (a instanceof TagStackContent<?> ta && b instanceof TagStackContent<?> tb) {
            return ta.type() == tb.type() && ta.tagKey().equals(tb.tagKey());
        }
        if (a instanceof StackListContent || a instanceof TypedStackListContent<?, ?>) {
            return a.mergeable(b);
        }
        if (a instanceof ValueContent<?> va && b instanceof ValueContent<?> vb) {
            return va.type() == vb.type() && va.amount() == vb.amount();
        }
        if (a instanceof DurabilityContent da && b instanceof DurabilityContent db) {
            return da.durabilityCost() == db.durabilityCost()
                    && sameIdentityAndComponents(da.ingredient(), db.ingredient());
        }
        if (a instanceof ComplexContent) {
            return a.equals(b);
        }
        return a.equals(b);
    }

    static RecipeContent snapshot(RecipeContent content) {
        return content.copyWithAmountAndChance(content.amount(), content.chance());
    }
}
