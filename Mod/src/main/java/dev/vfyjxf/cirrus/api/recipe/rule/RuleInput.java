package dev.vfyjxf.cirrus.api.recipe.rule;

import dev.vfyjxf.cirrus.api.recipe.screening.RecipeUsePolicy;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Neutral, registry-free view of one recipe as seen by {@link RuleCondition}
 * evaluation. The same condition tree runs against both evaluation domains:
 *
 * <ul>
 *     <li><b>catalog stage</b> — plain {@code RecipeClassifyView} data while
 *         the routing catalog is built (classification, exclusion, hints);</li>
 *     <li><b>candidate stage</b> — a live planner candidate (score
 *         adjustments, rejection).</li>
 * </ul>
 *
 * <p>Domain-specific accessors have safe defaults so a condition leaf that is
 * meaningless in one domain simply evaluates to {@code false} there instead of
 * failing: candidate-only leaves ({@link RuleFlag}, {@link RuleScalar},
 * {@link #hintPolicy()}) see {@code hasCandidateFields() == false} at the
 * catalog stage, and vice versa.
 */
public interface RuleInput {

    /** The owning recipe system id ({@code namespace:path}); never null. */
    String systemId();

    /** The recipe id, or an empty string when unresolved. */
    String recipeId();

    /** The mod namespace of the recipe id ("" when unknown). */
    default String recipeMod() {
        String id = recipeId();
        int colon = id.indexOf(':');
        return colon > 0 ? id.substring(0, colon) : "";
    }

    /** The path part of the recipe id. */
    default String recipePath() {
        String id = recipeId();
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    /** Machine item ids able to execute the recipe. */
    List<String> machines();

    /** Number of slots on the given side. */
    int slotCount(boolean inputSide);

    /** The slot at {@code index} on the given side. */
    Slot slot(boolean inputSide, int index);

    /** Union of concrete content ids over all input slots. */
    Set<String> inputIds();

    /** Union of concrete content ids over all output slots. */
    Set<String> outputIds();

    /**
     * Classification tags already attached to this recipe (catalog stage);
     * empty when the domain does not carry tags.
     */
    default Set<String> tags() {
        return Set.of();
    }

    /** Whether candidate-stage scalars/flags/hints are available. */
    default boolean hasCandidateFields() {
        return false;
    }

    /** Candidate-stage boolean flag; {@code false} outside the candidate domain. */
    default boolean flag(RuleFlag flag) {
        return false;
    }

    /** Candidate-stage scalar; {@link Double#NaN} outside the candidate domain. */
    default double scalar(RuleScalar scalar) {
        return Double.NaN;
    }

    /** Candidate-stage stock coverage in {@code [0,1]}; {@link Double#NaN} when absent. */
    default double coverage() {
        return Double.NaN;
    }

    /** The routing hint policy set at the catalog stage; {@code null} when none. */
    default @Nullable RecipeUsePolicy hintPolicy() {
        return null;
    }

    /** One input or output slot in plain-data form. */
    interface Slot {

        /** Concrete content ids (item/fluid registry form); empty for opaque slots. */
        Set<String> ids();

        /** The declared tag id, when this slot is a tag slot; otherwise {@code null}. */
        @Nullable String tagId();

        /** Demanded/produced amount (best effort). */
        double amount();

        /** Output chance in [0,1]; 1 means guaranteed. */
        double chance();
    }
}
