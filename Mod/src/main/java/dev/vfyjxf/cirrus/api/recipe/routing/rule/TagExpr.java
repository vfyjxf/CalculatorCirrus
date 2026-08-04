package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.vfyjxf.cirrus.api.recipe.routing.tag.TagSnapshot;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.list.ImmutableList;

import java.util.BitSet;

/**
 * Declarative boolean expression over recipe tags, compiled to
 * {@link BitSet} operations.
 *
 * <p>{@code TagExpr} is a sealed interface with five variants:
 * <ul>
 *     <li>{@link Has} — matches recipes carrying a specific tag.</li>
 *     <li>{@link AllOf} — intersection of all sub-expressions (AND).</li>
 *     <li>{@link AnyOf} — union of all sub-expressions (OR).</li>
 *     <li>{@link NoneOf} — recipes matching none of the sub-expressions.</li>
 *     <li>{@link Not} — complement of a sub-expression.</li>
 * </ul>
 *
 * <p>Use the static factory methods ({@link #has(String)},
 * {@link #allOf(ImmutableList)}, etc.) to construct expressions.
 * Evaluation against a {@link TagSnapshot} produces a recipe bitset.
 *
 * @see SourcePolicyRuleSpec#when()
 */
public sealed interface TagExpr permits
    TagExpr.Has,
    TagExpr.AllOf,
    TagExpr.AnyOf,
    TagExpr.NoneOf,
    TagExpr.Not {
    Codec<TagExpr> CODEC = Codec.recursive(
        "cirrus_tag_expr",
        self -> Codec.STRING.dispatch("type", TagExpr::codecType, type -> codecForType(type, self))
    );

    private static MapCodec<? extends TagExpr> codecForType(String type, Codec<TagExpr> self) {
        return switch (type) {
            case "has" -> Codec.STRING.fieldOf("tag").xmap(Has::new, Has::tag);
            case "all_of" -> dev.vfyjxf.cloudlib.api.util.Codecs.immutableList(self).fieldOf("expressions").xmap(AllOf::new, AllOf::expressions);
            case "any_of" -> dev.vfyjxf.cloudlib.api.util.Codecs.immutableList(self).fieldOf("expressions").xmap(AnyOf::new, AnyOf::expressions);
            case "none_of" -> dev.vfyjxf.cloudlib.api.util.Codecs.immutableList(self).fieldOf("expressions").xmap(NoneOf::new, NoneOf::expressions);
            case "not" -> self.fieldOf("expression").xmap(Not::new, Not::expression);
            default -> throw new IllegalArgumentException("Unknown tag expression type: " + type);
        };
    }

    default String codecType() {
        return switch (this) {
            case Has ignored -> "has";
            case AllOf ignored -> "all_of";
            case AnyOf ignored -> "any_of";
            case NoneOf ignored -> "none_of";
            case Not ignored -> "not";
        };
    }

    /**
     * Evaluates this expression against the given tag snapshot.
     *
     * @param snapshot the current tag assignments
     * @return a mutable bitset of matching recipe indices
     */
    BitSet evaluate(TagSnapshot snapshot);

    /** Creates an expression matching recipes that carry the given tag. */
    static TagExpr has(String tag) {
        return new Has(tag);
    }

    /** Creates an expression matching recipes that satisfy <b>all</b> sub-expressions. */
    static TagExpr allOf(ImmutableList<TagExpr> expressions) {
        return new AllOf(expressions);
    }

    /** Creates an expression matching recipes that satisfy <b>any</b> sub-expression. */
    static TagExpr anyOf(ImmutableList<TagExpr> expressions) {
        return new AnyOf(expressions);
    }

    /** Creates an expression matching recipes that satisfy <b>none</b> of the sub-expressions. */
    static TagExpr noneOf(ImmutableList<TagExpr> expressions) {
        return new NoneOf(expressions);
    }

    /** Creates an expression matching recipes that do <b>not</b> match the sub-expression. */
    static TagExpr not(TagExpr expression) {
        return new Not(expression);
    }

    /** Matches recipes carrying a specific tag. */
    record Has(String tag) implements TagExpr {
        public Has {
            Checks.checkNotNull(tag, "tag");
        }

        @Override
        public BitSet evaluate(TagSnapshot snapshot) {
            return snapshot.recipesForTag(tag);
        }
    }

    /** Intersection: matches recipes satisfying all sub-expressions. */
    record AllOf(ImmutableList<TagExpr> expressions) implements TagExpr {
        public AllOf {
            Checks.checkNotNull(expressions, "expressions");
        }

        @Override
        public BitSet evaluate(TagSnapshot snapshot) {
            BitSet merged = fullSet(snapshot.recipeCount());
            for (TagExpr expression : expressions) {
                merged.and(expression.evaluate(snapshot));
            }
            return merged;
        }
    }

    /** Union: matches recipes satisfying any sub-expression. */
    record AnyOf(ImmutableList<TagExpr> expressions) implements TagExpr {
        public AnyOf {
            Checks.checkNotNull(expressions, "expressions");
        }

        @Override
        public BitSet evaluate(TagSnapshot snapshot) {
            BitSet merged = new BitSet(snapshot.recipeCount());
            for (TagExpr expression : expressions) {
                merged.or(expression.evaluate(snapshot));
            }
            return merged;
        }
    }

    /** Exclusion: matches recipes satisfying none of the sub-expressions. */
    record NoneOf(ImmutableList<TagExpr> expressions) implements TagExpr {
        public NoneOf {
            Checks.checkNotNull(expressions, "expressions");
        }

        @Override
        public BitSet evaluate(TagSnapshot snapshot) {
            BitSet merged = fullSet(snapshot.recipeCount());
            for (TagExpr expression : expressions) {
                merged.andNot(expression.evaluate(snapshot));
            }
            return merged;
        }
    }

    /** Complement: matches recipes not matching the sub-expression. */
    record Not(TagExpr expression) implements TagExpr {
        public Not {
            Checks.checkNotNull(expression, "expression");
        }

        @Override
        public BitSet evaluate(TagSnapshot snapshot) {
            BitSet bitSet = fullSet(snapshot.recipeCount());
            bitSet.andNot(expression.evaluate(snapshot));
            return bitSet;
        }
    }

    private static BitSet fullSet(int size) {
        BitSet bitSet = new BitSet(size);
        bitSet.set(0, size);
        return bitSet;
    }
}

