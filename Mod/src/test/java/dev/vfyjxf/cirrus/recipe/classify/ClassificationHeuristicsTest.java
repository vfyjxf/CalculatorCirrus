package dev.vfyjxf.cirrus.recipe.classify;

import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHeuristicRule;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHeuristics;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

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

    private static Set<String> registeredIds() {
        Set<String> ids = new HashSet<>();
        for (RecipeRoutingHeuristicRule rule : RecipeRoutingHeuristics.snapshot()) {
            ids.add(rule.ruleId().toString());
        }
        return ids;
    }
}
