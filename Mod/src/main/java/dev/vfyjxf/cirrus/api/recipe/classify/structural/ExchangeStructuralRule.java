package dev.vfyjxf.cirrus.api.recipe.classify.structural;

import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationContext;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationRule;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationSink;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassifyContent;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;

/**
 * Tags recipes whose output is simultaneously part of their own input:
 * chisel-style variant swaps (output ∈ input tag members), list conversions
 * (output ∈ input variants) and self-referential recipes (repair, recharge).
 *
 * <p>These recipes form tight dependency loops and are tagged
 * {@link ClassificationTags#EXCHANGE} (advisory, never excluded) so planning
 * penalizes them while keeping them available for items they uniquely produce.
 */
public final class ExchangeStructuralRule implements ClassificationRule {

    public static final ExchangeStructuralRule instance = new ExchangeStructuralRule();
    public static final String id = "cirrus:structural/exchange";

    private ExchangeStructuralRule() {}

    @Override
    public String id() {
        return id;
    }

    @Override
    public void classify(RecipeClassifyView recipe, ClassificationContext context, ClassificationSink sink) {
        if (recipe.outputs().isEmpty() || recipe.inputs().isEmpty()) {
            return;
        }
        var outputIds = recipe.allOutputIds();
        if (outputIds.isEmpty()) {
            return;
        }
        for (ClassifyContent input : recipe.inputs()) {
            if (input.kind() == ClassifyContent.Kind.value || input.kind() == ClassifyContent.Kind.empty) {
                continue;
            }
            for (String id : input.ids()) {
                if (outputIds.contains(id)) {
                    sink.tag(ClassificationTags.exchange);
                    return;
                }
            }
        }
    }
}
