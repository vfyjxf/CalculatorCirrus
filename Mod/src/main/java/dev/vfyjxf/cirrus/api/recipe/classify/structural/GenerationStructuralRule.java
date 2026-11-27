package dev.vfyjxf.cirrus.api.recipe.classify.structural;

import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationContext;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationRule;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationSink;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassifyContent;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;

/**
 * Tags resource-generation recipes: concrete outputs with no inputs at all,
 * or with only numeric value inputs (energy/time) — cobblegen, laser drills,
 * quarries.
 *
 * <p>Complex (non-enumerable) inputs disqualify: their contents are unknown,
 * so the recipe may well consume items. The {@link ClassificationTags#GENERATION}
 * tag is mark-only: these recipes are legitimate raw-material sources, but
 * situational filtering may treat them specially (e.g. "accept
 * self-sustaining sources" toggles).
 */
public final class GenerationStructuralRule implements ClassificationRule {

    public static final GenerationStructuralRule instance = new GenerationStructuralRule();
    public static final String id = "cirrus:structural/generation";

    private GenerationStructuralRule() {}

    @Override
    public String id() {
        return id;
    }

    @Override
    public void classify(RecipeClassifyView recipe, ClassificationContext context, ClassificationSink sink) {
        for (ClassifyContent input : recipe.inputs()) {
            if (input.kind() != ClassifyContent.Kind.value && input.kind() != ClassifyContent.Kind.empty) {
                return;
            }
        }
        for (ClassifyContent output : recipe.outputs()) {
            if (output.hasIds()) {
                sink.tag(ClassificationTags.generation);
                return;
            }
        }
    }
}
