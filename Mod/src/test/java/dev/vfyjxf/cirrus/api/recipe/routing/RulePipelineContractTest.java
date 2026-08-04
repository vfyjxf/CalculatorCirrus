package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.recipe.routing.rule.CandidateContext;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleProfiles;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.HardRuleResult;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class RulePipelineContractTest {

    @Test
    void hardRulesRunBeforeScoreAndRank(MinecraftServer server) {
        var r1 = new RoutingTestFixtures.FakeRecipe(
            "r1",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        RecipeGraph graph = RoutingTestFixtures.graph(r1);
        RecipeGraph.Node node = graph.nodes().iterator().next();

        RuleProfile profile = DefaultRuleProfiles.standardDefault();
        CandidateContext context = new CandidateContext(
            node,
            RoutingTestFixtures.in(Items.OAK_LOG, 1),
            1,
            1,
            0,
            false,
            0.1,
            1.0
        );

        for (var hardRule : profile.hardRules()) {
            HardRuleResult result = hardRule.evaluate(context);
            assertTrue(result.accepted(), "hard rule should accept deterministic recipe");
        }

        double[] score = profile.newScoreVector();
        var accumulator = profile.newAccumulator(score);
        for (var scoreRule : profile.scoreRules()) {
            scoreRule.contribute(context, accumulator);
        }
        assertTrue(score[0] > 0, "craft score should be populated");
    }

    @Test
    void rankAndTieBreakAreDeterministic(MinecraftServer server) {
        var r1 = new RoutingTestFixtures.FakeRecipe(
            "a_recipe",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        var r2 = new RoutingTestFixtures.FakeRecipe(
            "b_recipe",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        RecipeGraph graph = RoutingTestFixtures.graph(r1, r2);
        RecipeGraph.Node n1 = null;
        RecipeGraph.Node n2 = null;
        for (RecipeGraph.Node node : graph.nodes()) {
            if (node.recipe().id() != null && "a_recipe".equals(node.recipe().id().getPath())) {
                n1 = node;
            }
            if (node.recipe().id() != null && "b_recipe".equals(node.recipe().id().getPath())) {
                n2 = node;
            }
        }
        assertNotNull(n1);
        assertNotNull(n2);

        RuleProfile profile = DefaultRuleProfiles.standardDefault();
        CandidateContext c1 = new CandidateContext(n1, RoutingTestFixtures.in(Items.OAK_LOG, 1), 1, 1, 0, false, 0.1, 1.0);
        CandidateContext c2 = new CandidateContext(n2, RoutingTestFixtures.in(Items.OAK_LOG, 1), 1, 1, 0, false, 0.1, 1.0);
        double[] s1 = profile.newScoreVector();
        double[] s2 = profile.newScoreVector();

        assertEquals(0, profile.rankPolicy().compare(s1, s2));
        int tieCmp1 = profile.tieBreakPolicy().compare(c1, c2);
        int tieCmp2 = profile.tieBreakPolicy().compare(c1, c2);
        assertEquals(tieCmp1, tieCmp2);
    }
}
