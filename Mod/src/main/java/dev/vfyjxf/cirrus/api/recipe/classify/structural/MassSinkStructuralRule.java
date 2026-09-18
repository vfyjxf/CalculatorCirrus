package dev.vfyjxf.cirrus.api.recipe.classify.structural;

import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationContext;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationRule;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationSink;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Global rule tagging "mass sink" recipe groups: one system containing many
 * recipes that all map distinct, unrelated inputs onto the same output
 * (composting, nutritional liquifier, food canning). These invert the economy
 * for planning purposes and are tagged {@link ClassificationTags#RECYCLING}.
 *
 * <p>A group (system, primary output id) qualifies when it has at least
 * {@value #minGroupSize} recipes with mostly distinct inputs.
 */
public final class MassSinkStructuralRule implements ClassificationRule {

    public static final String ID = "cirrus:structural/mass_sink";

    private static final int minGroupSize = 64;

    private MassSinkStructuralRule() {
    }

    public static MassSinkStructuralRule create() {
        return new MassSinkStructuralRule();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean global() {
        return true;
    }

    /**
     * This rule works on whole groups, not single recipes; the per-recipe
     * entry point is a no-op. Group tagging happens in
     * {@link #classifyAll(ClassificationContext, GroupSink)} which the
     * classifier invokes once per run.
     */
    @Override
    public void classify(RecipeClassifyView recipe, ClassificationContext context, ClassificationSink sink) {
        // group rule - see classifyAll
    }

    /**
     * Runs the group analysis. The sink receives (recipeIndex, tag) pairs,
     * decoupled from the per-recipe pass.
     */
    public void classifyAll(ClassificationContext context, GroupSink sink) {
        Map<String, List<RecipeClassifyView>> groups = new HashMap<>();
        for (RecipeClassifyView view : context.recipes()) {
            String primary = view.primaryOutputId();
            if (primary == null || view.inputs().isEmpty()) {
                continue;
            }
            groups.computeIfAbsent(view.systemId() + " | " + primary, k -> new ArrayList<>()).add(view);
        }
        for (List<RecipeClassifyView> group : groups.values()) {
            if (group.size() < minGroupSize) {
                continue;
            }
            // Variant-unification exemption: when most recipes consume their input
            // through tag slots (e.g. sawmill "#logs -> one product" families), the
            // group unifies variants instead of sinking unrelated mass — not a
            // mass sink.
            int tagInputRecipes = 0;
            for (RecipeClassifyView view : group) {
                for (var input : view.inputs()) {
                    if (input.kind() == dev.vfyjxf.cirrus.api.recipe.classify.ClassifyContent.Kind.tag) {
                        tagInputRecipes++;
                        break;
                    }
                }
            }
            if (tagInputRecipes * 2 >= group.size()) {
                continue;
            }
            Set<String> distinctInputs = new HashSet<>();
            for (RecipeClassifyView view : group) {
                distinctInputs.addAll(view.allInputIds());
            }
            // qualify only when inputs are mostly distinct (a true mass sink,
            // not one recipe repeated per tag variant)
            if (distinctInputs.size() < group.size()) {
                continue;
            }
            for (RecipeClassifyView view : group) {
                sink.tag(view.index(), ClassificationTags.RECYCLING);
            }
        }
    }

    /**
     * Sink for group rules, addressed by recipe index rather than by the
     * recipe currently under classification.
     */
    @FunctionalInterface
    public interface GroupSink {
        void tag(int recipeIndex, String tag);
    }
}
