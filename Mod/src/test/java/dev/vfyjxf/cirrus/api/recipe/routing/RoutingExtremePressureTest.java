package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicyAction;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicyRuleSpec;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.TagExpr;
import dev.vfyjxf.cirrus.api.recipe.routing.service.LoopBudget;
import dev.vfyjxf.cirrus.api.recipe.routing.service.OutputSelectionPolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingBudget;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.api.recipe.routing.tag.TagSnapshot;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class RoutingExtremePressureTest {

    private static final Namespace hardExclusionPolicyId = Namespace.of("cirrus", "routing/test/source_policy/hard_exclusion");
    private static final Namespace hardExclusionWithPlayerBanPolicyId = Namespace.of("cirrus", "routing/test/source_policy/hard_exclusion_with_player_ban");
    private static final Namespace excludeJunkClusterRuleId = Namespace.of("cirrus", "routing/test/source_policy_rule/exclude_junk_cluster");
    private static final Namespace excludeDecoyClusterRuleId = Namespace.of("cirrus", "routing/test/source_policy_rule/exclude_decoy_cluster");
    private static final Namespace excludePlayerBanRuleId = Namespace.of("cirrus", "routing/test/source_policy_rule/exclude_player_ban");

    @Test
    void fuzzMatrixPressureAcrossDozensOfHighDensityScenarios(MinecraftServer server) {
        for (int seed = 200; seed < 232; seed++) {
            int caseSeed = seed;
            RoutingComplexDataset.Scenario scenario = RoutingComplexDataset.generatedScenario(seed, 24, 120, 24);
            RoutingService service = RoutingTestFixtures.service(scenario.recipes());
            installHardExclusionPolicies(service);

            RecipeEntry<?> targetRecipe = RoutingTestFixtures.findRecipeById(service.graph(), scenario.targetRecipeId());
            assertNotNull(targetRecipe, "target recipe must exist for pressure seed " + seed);

            PlannerMode mode = seed % 2 == 0 ? PlannerMode.assuredBounded : PlannerMode.standard;
            RoutingRequest request = RoutingRequest.builder()
                .plannerMode(mode)
                .sourcePolicyId(hardExclusionPolicyId)
                .stock(scenario.stock().copy())
                .budget(new RoutingBudget(10_000, 120_000, 120, 24_000, 0.08, LoopBudget.assuredDefault()))
                .targetRecipe(targetRecipe, scenario.targetTimes())
                .build();

            var outcome = service.route(request);
            assertTrue(outcome.success() || outcome.partial(), "pressure seed should generate plan: " + seed);
            assertNotNull(outcome.plan(), "plan missing for pressure seed " + seed);
            assertTrue(outcome.metrics().expandedNodes() <= request.budget().maxExpandedNodes(), "expanded overflow in seed " + seed);
            assertNoHardExcludedExecution(outcome.plan(), Set.of());

            var report = RoutingPlanReverseVerifier.verify(request, outcome, service.graph());
            assertTrue(report.valid(), () -> "reverse verifier failed in pressure seed " + caseSeed + ": " + report.errors());

            if ((seed % 4) == 0) {
                ContentResource adapterTarget = firstOutputOfRecipe(service.graph(), targetRecipe);
                RoutingRequest adapterRequest = RoutingRequest.builder()
                    .plannerMode(PlannerMode.assuredBounded)
                    .sourcePolicyId(hardExclusionPolicyId)
                    .stock(scenario.stock().copy())
                    .adapterSelectionPolicy(OutputSelectionPolicy.bestEffortSingleRecipe)
                    .maxProducerCandidates(16)
                    .budget(RoutingBudget.assuredDefault())
                    .targetOutput(adapterTarget, Math.max(1, scenario.targetTimes() / 2))
                    .build();
                var adapterOutcome = service.route(adapterRequest);
                assertTrue(adapterOutcome.success() || adapterOutcome.partial(), "adapter path should return plan on seed " + seed);
                assertNotNull(adapterOutcome.plan(), "adapter plan missing on seed " + seed);
                assertTrue(adapterOutcome.metrics().adapterAttemptCount() > 0, "adapter should record attempts on seed " + seed);
                assertNoHardExcludedExecution(adapterOutcome.plan(), Set.of());
                var adapterReport = RoutingPlanReverseVerifier.verify(adapterRequest, adapterOutcome, service.graph());
                assertTrue(adapterReport.valid(), () -> "adapter reverse verifier failed on seed " + caseSeed + ": " + adapterReport.errors());
            }
        }
    }

    @Test
    void megaScenarioBatchTargetsStayReplayableUnderHeavyRequestVolume(MinecraftServer server) {
        RoutingComplexDataset.Scenario scenario = RoutingComplexDataset.megaScenario();
        RoutingService service = RoutingTestFixtures.service(scenario.recipes());
        installHardExclusionPolicies(service);

        List<RecipeEntry<?>> targets = new ArrayList<>();
        for (RecipeGraph.Node node : service.graph().nodes()) {
            if (node.recipe().id() != null && node.recipe().id().getPath().startsWith("mega_core_")) {
                targets.add(node.recipe());
            }
        }
        assertFalse(targets.isEmpty(), "mega scenario must include core targets");

        int baseTimes = 2;
        for (int i = 0; i < targets.size(); i++) {
            RecipeEntry<?> target = targets.get(i);
            int times = baseTimes + (i % 7);
            RoutingRequest request = RoutingRequest.builder()
                .plannerMode((i % 3) == 0 ? PlannerMode.assuredBounded : PlannerMode.standard)
                .sourcePolicyId(hardExclusionPolicyId)
                .stock(scenario.stock().copy())
                .budget(new RoutingBudget(18_000, 180_000, 200, 36_000, 0.10, LoopBudget.assuredDefault()))
                .targetRecipe(target, times)
                .build();

            var outcome = service.route(request);
            assertTrue(outcome.success() || outcome.partial(), "batch target should return plan: " + target.id());
            assertNotNull(outcome.plan(), "missing batch plan for target: " + target.id());
            assertNoHardExcludedExecution(outcome.plan(), Set.of());

            var report = RoutingPlanReverseVerifier.verify(request, outcome, service.graph());
            assertTrue(report.valid(), () -> "batch reverse verifier failed for " + target.id() + ": " + report.errors());
        }
    }

    @Test
    void playerOverlayChurnPressurePreservesBanIsolation(MinecraftServer server) {
        RoutingComplexDataset.Scenario scenario = RoutingComplexDataset.generatedScenario(778899L, 22, 100, 18);
        RoutingService service = RoutingTestFixtures.service(scenario.recipes());
        installHardExclusionPolicies(service);
        registerPlayerBanPolicy(service);

        RecipeEntry<?> targetRecipe = RoutingTestFixtures.findRecipeById(service.graph(), scenario.targetRecipeId());
        assertNotNull(targetRecipe);

        Map<Integer, String> altRecipeIds = collectAltRecipeIds(service);
        assertFalse(altRecipeIds.isEmpty(), "pressure dataset should include alt recipes");

        Random random = new Random(778899L);
        List<Integer> altIndices = new ArrayList<>(altRecipeIds.keySet());
        for (int iteration = 0; iteration < 80; iteration++) {
            int caseIteration = iteration;
            int pick = altIndices.get(random.nextInt(altIndices.size()));
            if (random.nextBoolean()) {
                service.userWorkspace().addToTag("cirrus:player/ban", pick);
            } else {
                service.userWorkspace().removeFromTag("cirrus:player/ban", pick);
            }

            TagSnapshot snapshot = service.userWorkspace().snapshot();
            Set<String> bannedIds = bannedIdsFromSnapshot(snapshot, altRecipeIds);

            RoutingRequest request = RoutingRequest.builder()
                .plannerMode((iteration % 2) == 0 ? PlannerMode.standard : PlannerMode.assuredBounded)
                .sourcePolicyId(hardExclusionWithPlayerBanPolicyId)
                .stock(scenario.stock().copy())
                .tagSnapshot(snapshot)
                .budget(new RoutingBudget(9_000, 90_000, 120, 22_000, 0.08, LoopBudget.assuredDefault()))
                .targetRecipe(targetRecipe, scenario.targetTimes())
                .build();

            var outcome = service.route(request);
            assertTrue(outcome.success() || outcome.partial(), "overlay churn should still route, iteration=" + iteration);
            assertNotNull(outcome.plan(), "overlay churn missing plan, iteration=" + iteration);
            assertNoHardExcludedExecution(outcome.plan(), bannedIds);

            var report = RoutingPlanReverseVerifier.verify(request, outcome, service.graph());
            assertTrue(report.valid(), () -> "overlay churn reverse verifier failed at iteration " + caseIteration + ": " + report.errors());
        }
    }

    private static void installHardExclusionPolicies(RoutingService service) {
        for (RecipeGraph.Node node : service.graph().nodes()) {
            if (node.recipe().id() == null) {
                continue;
            }
            String path = node.recipe().id().getPath();
            if (path.startsWith("junk_") || path.contains("_junk_")) {
                service.tagIndex().add("cirrus:quality/junk", node.recipe());
            }
            if (path.startsWith("mega_decoy_")) {
                service.tagIndex().add("cirrus:quality/decoy", node.recipe());
            }
        }

        SourcePolicy policy = new SourcePolicy(
            hardExclusionPolicyId,
            100,
            Lists.immutable.of(
                new SourcePolicyRuleSpec(excludeJunkClusterRuleId, true, TagExpr.has("cirrus:quality/junk"), SourcePolicyAction.excludeMatch, false),
                new SourcePolicyRuleSpec(excludeDecoyClusterRuleId, true, TagExpr.has("cirrus:quality/decoy"), SourcePolicyAction.excludeMatch, false)
            )
        );
        service.registerSourcePolicy(policy);
    }

    private static void registerPlayerBanPolicy(RoutingService service) {
        SourcePolicy policy = new SourcePolicy(
            hardExclusionWithPlayerBanPolicyId,
            200,
            Lists.immutable.of(
                new SourcePolicyRuleSpec(excludeJunkClusterRuleId, true, TagExpr.has("cirrus:quality/junk"), SourcePolicyAction.excludeMatch, false),
                new SourcePolicyRuleSpec(excludeDecoyClusterRuleId, true, TagExpr.has("cirrus:quality/decoy"), SourcePolicyAction.excludeMatch, false),
                new SourcePolicyRuleSpec(excludePlayerBanRuleId, true, TagExpr.has("cirrus:player/ban"), SourcePolicyAction.excludeMatch, false)
            )
        );
        service.registerSourcePolicy(policy);
    }

    private static Map<Integer, String> collectAltRecipeIds(RoutingService service) {
        Map<Integer, String> alt = new HashMap<>();
        for (RecipeGraph.Node node : service.graph().nodes()) {
            if (node.recipe().id() != null && node.recipe().id().getPath().contains("_alt_")) {
                int index = service.tagIndex().recipeIndex(node.recipe());
                if (index >= 0) {
                    alt.put(index, node.recipe().id().getPath());
                }
            }
        }
        return alt;
    }

    private static Set<String> bannedIdsFromSnapshot(TagSnapshot snapshot, Map<Integer, String> altRecipeIds) {
        Set<String> banned = new HashSet<>();
        BitSet bannedBits = snapshot.recipesForTag("cirrus:player/ban");
        for (var entry : altRecipeIds.entrySet()) {
            if (bannedBits.get(entry.getKey())) {
                banned.add(entry.getValue());
            }
        }
        return banned;
    }

    private static ContentResource firstOutputOfRecipe(RecipeGraph graph, RecipeEntry<?> recipe) {
        for (RecipeGraph.Node node : graph.nodes()) {
            if (!node.recipe().equals(recipe)) {
                continue;
            }
            for (var output : node.mergedOutputs()) {
                return output.asResource();
            }
        }
        fail("recipe has no outputs in graph: " + recipe);
        throw new IllegalStateException("unreachable");
    }

    private static void assertNoHardExcludedExecution(dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan plan, Set<String> bannedIds) {
        for (var action : plan.actions()) {
            if (!(action instanceof PlanAction.ExecuteRecipeAction exec) || exec.recipe().id() == null) {
                continue;
            }
            String path = exec.recipe().id().getPath();
            if (path.startsWith("junk_") || path.contains("_junk_")) {
                fail("plan executed junk recipe: " + exec.recipe().id());
            }
            if (path.startsWith("mega_decoy_")) {
                fail("plan executed decoy recipe: " + exec.recipe().id());
            }
            if (bannedIds.contains(path)) {
                fail("plan executed player-banned recipe: " + exec.recipe().id());
            }
        }
    }
}
