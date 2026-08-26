package dev.vfyjxf.cirrus.recipe.classify;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeHeuristicContext;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHeuristicRule;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHeuristics;
import dev.vfyjxf.cirrus.internal.performance.CalculatorSyntheticScenario;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The classification heuristics register into a global registry that tests
 * may {@code clear()}; registration must be recoverable afterwards, otherwise
 * later graph builds silently lose the advisory penalties.
 */
class ClassificationHeuristicsTest {

    @Test
    void registerRestoresRulesAfterRegistryClear() {
        RecipeRoutingHeuristics.clear();
        try {
            ClassificationHeuristics.register();
            Set<String> firstPass = registeredIds();
            assertTrue(firstPass.contains("cirrus:classification/recolor"));
            assertTrue(firstPass.contains("cirrus:classification/exchange"));
            assertTrue(firstPass.contains("cirrus:classification/recycling"));

            // a second call must not duplicate the rules
            ClassificationHeuristics.register();
            Set<String> secondPass = registeredIds();
            assertTrue(secondPass.contains("cirrus:classification/recolor"));
            int recolorCount = 0;
            for (RecipeRoutingHeuristicRule rule : RecipeRoutingHeuristics.snapshot()) {
                if ("cirrus:classification/recolor".equals(rule.ruleId().toString())) {
                    recolorCount++;
                }
            }
            assertTrue(recolorCount == 1, "register must dedup by rule id, got " + recolorCount);

            // simulated test-side clear, then recovery on next register
            RecipeRoutingHeuristics.clear();
            ClassificationHeuristics.register();
            assertTrue(registeredIds().contains("cirrus:classification/recycling"));
        } finally {
            RecipeRoutingHeuristics.clear();
            ClassificationHeuristics.register();
        }
    }

    @Test
    void tagsAreSharedAcrossContextsOfSameRecipe() {
        ClassificationHeuristics.clearTagsCache();
        try {
            CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.create(0);
            RecipeEntry<?> entry = scenario.entries().get(0);
            // graph building allocates a fresh context per node; two contexts for
            // the same recipe must hit the per-recipe memo, not re-classify
            Set<String> first = ClassificationHeuristics.tagsOf(context(entry, entry.id()));
            Set<String> second = ClassificationHeuristics.tagsOf(context(entry, entry.id()));
            assertSame(first, second, "same recipe id must share one tag set across context instances");
            assertEquals(1, ClassificationHeuristics.tagsByRecipeSize());
        } finally {
            ClassificationHeuristics.clearTagsCache();
        }
    }

    @Test
    void nullRecipeIdBypassesPerRecipeMemo() {
        ClassificationHeuristics.clearTagsCache();
        try {
            CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.create(0);
            RecipeEntry<?> entry = scenario.entries().get(0);
            // distinct id-less recipes would collide on the shared key, so they
            // must stay uncached (mirrors RecipeClassifier.classifyLocal)
            ClassificationHeuristics.tagsOf(context(entry, null));
            ClassificationHeuristics.tagsOf(context(entry, null));
            assertEquals(0, ClassificationHeuristics.tagsByRecipeSize(), "null recipe id must not populate the memo");
        } finally {
            ClassificationHeuristics.clearTagsCache();
        }
    }

    @Test
    void runtimeCacheClearDropsMemo() {
        ClassificationHeuristics.clearTagsCache();
        try {
            CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.create(0);
            RecipeEntry<?> entry = scenario.entries().get(0);
            ClassificationHeuristics.tagsOf(context(entry, entry.id()));
            assertEquals(1, ClassificationHeuristics.tagsByRecipeSize());
            RecipeClassificationService.clearRuntimeCaches();
            assertEquals(0, ClassificationHeuristics.tagsByRecipeSize(), "client unload must drop the per-recipe memo");
        } finally {
            ClassificationHeuristics.clearTagsCache();
        }
    }

    private static RecipeHeuristicContext context(RecipeEntry<?> entry, @Nullable ResourceLocation recipeId) {
        return new RecipeHeuristicContext(
            entry,
            recipeId,
            entry.systemId(),
            MachineEntry.empty,
            Lists.immutable.empty(),
            Lists.immutable.empty()
        );
    }

    private static Set<String> registeredIds() {
        Set<String> ids = new HashSet<>();
        for (RecipeRoutingHeuristicRule rule : RecipeRoutingHeuristics.snapshot()) {
            ids.add(rule.ruleId().toString());
        }
        return ids;
    }
}
