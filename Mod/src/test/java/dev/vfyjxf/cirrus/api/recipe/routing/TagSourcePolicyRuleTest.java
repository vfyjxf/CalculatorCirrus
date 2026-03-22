package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicyAction;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicyRuleSpec;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.TagExpr;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.TagSourcePolicyRule;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRecipeCatalog;
import dev.vfyjxf.cirrus.api.recipe.routing.tag.TagIndex;
import dev.vfyjxf.cirrus.api.recipe.routing.tag.UserTagWorkspace;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class TagSourcePolicyRuleTest {

    private static final Namespace testPolicyId = Namespace.of("cirrus", "routing/test/source_policy/tag_rule");
    private static final Namespace excludeJunkRuleId = Namespace.of("cirrus", "routing/test/source_policy_rule/exclude_junk");

    @Test
    void excludeTaggedRecipesWithDeterministicRuleOrder(MinecraftServer server) {
        var r1 = new RoutingTestFixtures.FakeRecipe(
            "r1",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        var r2 = new RoutingTestFixtures.FakeRecipe(
            "r2",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_PLANKS, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 4))
        );
        RecipeGraph graph = RoutingTestFixtures.graph(r1, r2);
        RoutingRecipeCatalog catalog = RoutingRecipeCatalog.fromGraph(graph);
        TagIndex index = new TagIndex(catalog);
        var recipe2 = RoutingTestFixtures.findRecipeById(graph, "r2");
        assertNotNull(recipe2);
        index.add("cirrus:quality/junk", recipe2);

        SourcePolicy policy = new SourcePolicy(
            testPolicyId,
            1,
            Lists.immutable.of(
                new SourcePolicyRuleSpec(excludeJunkRuleId, true, TagExpr.has("cirrus:quality/junk"), SourcePolicyAction.excludeMatch, false)
            )
        );

        TagSourcePolicyRule rule = new TagSourcePolicyRule();
        var decision = rule.apply(index.snapshot(), policy);

        int idx2 = index.recipeIndex(recipe2);
        assertFalse(decision.allowedRecipes().get(idx2));
        assertEquals(1, decision.explain().ruleTraces().size());
    }

    @Test
    void workspaceOverlayMatchesUnionMinusRemoveFormula(MinecraftServer server) {
        var r1 = new RoutingTestFixtures.FakeRecipe(
            "r1",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        var r2 = new RoutingTestFixtures.FakeRecipe(
            "r2",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_PLANKS, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 4))
        );
        RecipeGraph graph = RoutingTestFixtures.graph(r1, r2);
        RoutingRecipeCatalog catalog = RoutingRecipeCatalog.fromGraph(graph);
        TagIndex index = new TagIndex(catalog);
        var recipe1 = RoutingTestFixtures.findRecipeById(graph, "r1");
        var recipe2 = RoutingTestFixtures.findRecipeById(graph, "r2");
        assertNotNull(recipe1);
        assertNotNull(recipe2);

        index.add("cirrus:test/tag", recipe1);
        UserTagWorkspace workspace = new UserTagWorkspace(index);
        workspace.addToTag("cirrus:test/tag", index.recipeIndex(recipe2));
        workspace.removeFromTag("cirrus:test/tag", index.recipeIndex(recipe1));

        var snapshot = workspace.snapshot();
        var set = snapshot.recipesForTag("cirrus:test/tag");
        assertTrue(set.get(index.recipeIndex(recipe2)));
        assertFalse(set.get(index.recipeIndex(recipe1)));
    }
}
