package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
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
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class RoutingComplexIntegrationTest {

    private static final Namespace sourceNoJunkPolicyId = Namespace.of("cirrus", "routing/test/source_policy/no_junk");
    private static final Namespace sourceNoJunkAndPlayerBanPolicyId = Namespace.of("cirrus", "routing/test/source_policy/no_junk_and_player_ban");
    private static final Namespace excludeJunkClusterRuleId = Namespace.of("cirrus", "routing/test/source_policy_rule/exclude_junk_cluster");
    private static final Namespace excludeDecoyClusterRuleId = Namespace.of("cirrus", "routing/test/source_policy_rule/exclude_decoy_cluster");
    private static final Namespace excludePlayerBanRuleId = Namespace.of("cirrus", "routing/test/source_policy_rule/exclude_player_ban");

    @Test
    void largeDatasetSupportsRecipeAndOutputTargetWithReverseValidation(MinecraftServer server) {
        RoutingComplexDataset.Scenario scenario = RoutingComplexDataset.largeScenario();
        RoutingService service = RoutingTestFixtures.service(scenario.recipes());
        installJunkExclusionPolicy(service);

        RecipeEntry<?> targetRecipe = RoutingTestFixtures.findRecipeById(service.graph(), scenario.targetRecipeId());
        assertNotNull(targetRecipe, "target recipe must exist");

        RoutingRequest recipeTargetRequest = RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .sourcePolicyId(sourceNoJunkPolicyId)
            .stock(scenario.stock().copy())
            .budget(new RoutingBudget(4_500, 45_000, 120, 12_000, 0.1, LoopBudget.assuredDefault()))
            .targetRecipe(targetRecipe, scenario.targetTimes())
            .build();

        var recipeOutcome = service.route(recipeTargetRequest);
        assertTrue(recipeOutcome.success() || recipeOutcome.partial(), "recipe target routing should produce an executable plan");
        assertNotNull(recipeOutcome.plan());
        assertTrue(recipeOutcome.metrics().expandedNodes() < 4_500, "standard mode should stay within bounded search budget");
        assertNoJunkExecution(recipeOutcome.plan());

        var recipeVerification = RoutingPlanReverseVerifier.verify(recipeTargetRequest, recipeOutcome, service.graph());
        assertTrue(recipeVerification.valid(), () -> "recipe target verification failed: " + recipeVerification.errors());

        RoutingRequest outputTargetRequest = RoutingRequest.builder()
            .plannerMode(PlannerMode.assuredBounded)
            .sourcePolicyId(sourceNoJunkPolicyId)
            .stock(scenario.stock().copy())
            .adapterSelectionPolicy(OutputSelectionPolicy.bestEffortSingleRecipe)
            .maxProducerCandidates(12)
            .budget(RoutingBudget.assuredDefault())
            .targetOutput(ContentStack.fromStack(new ItemStack(Items.NETHER_STAR, 1)), scenario.targetTimes())
            .build();

        var outputOutcome = service.route(outputTargetRequest);
        assertTrue(outputOutcome.success() || outputOutcome.partial(), "output target routing should produce an executable plan");
        assertNotNull(outputOutcome.plan());
        assertTrue(outputOutcome.metrics().adapterAttemptCount() > 0, "adapter path should record producer attempts");
        assertNoJunkExecution(outputOutcome.plan());

        var outputVerification = RoutingPlanReverseVerifier.verify(outputTargetRequest, outputOutcome, service.graph());
        assertTrue(outputVerification.valid(), () -> "output target verification failed: " + outputVerification.errors());
    }

    @Test
    void randomComplexScenariosRemainDeterministicAndReplayable(MinecraftServer server) {
        for (int seed = 1; seed <= 12; seed++) {
            int caseSeed = seed;
            RoutingComplexDataset.Scenario scenario = RoutingComplexDataset.generatedScenario(seed);
            RoutingService service = RoutingTestFixtures.service(scenario.recipes());
            installJunkExclusionPolicy(service);

            RecipeEntry<?> targetRecipe = RoutingTestFixtures.findRecipeById(service.graph(), scenario.targetRecipeId());
            assertNotNull(targetRecipe, "target recipe must exist for seed " + seed);

            RoutingRequest request = RoutingRequest.builder()
                .plannerMode(seed % 2 == 0 ? PlannerMode.assuredBounded : PlannerMode.standard)
                .sourcePolicyId(sourceNoJunkPolicyId)
                .stock(scenario.stock().copy())
                .targetRecipe(targetRecipe, scenario.targetTimes())
                .budget(RoutingBudget.assuredDefault())
                .build();

            var outcome1 = service.route(request);
            var outcome2 = service.route(request);
            assertEquals(outcome1.status(), outcome2.status(), "status must be deterministic for seed " + seed);
            assertNotNull(outcome1.plan(), "plan should exist for seed " + seed);
            assertNotNull(outcome2.plan(), "plan should exist for seed " + seed);
            assertEquals(outcome1.plan().actions().size(), outcome2.plan().actions().size(), "action size must be stable for seed " + seed);

            var report = RoutingPlanReverseVerifier.verify(request, outcome1, service.graph());
            assertTrue(report.valid(), () -> "reverse verification failed for seed " + caseSeed + ": " + report.errors());
            assertTrue(outcome1.metrics().expandedNodes() <= request.budget().maxExpandedNodes(), "expanded nodes exceed budget for seed " + seed);
        }
    }

    @Test
    void megaDatasetSurvivesAdversarialDecoysWithStablePlanFingerprint(MinecraftServer server) {
        RoutingComplexDataset.Scenario scenario = RoutingComplexDataset.megaScenario();
        RoutingService service = RoutingTestFixtures.service(scenario.recipes());
        installJunkExclusionPolicy(service);

        RecipeEntry<?> targetRecipe = RoutingTestFixtures.findRecipeById(service.graph(), scenario.targetRecipeId());
        assertNotNull(targetRecipe);

        RoutingRequest standard = RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .sourcePolicyId(sourceNoJunkPolicyId)
            .stock(scenario.stock().copy())
            .budget(new RoutingBudget(16_000, 160_000, 200, 30_000, 0.05, LoopBudget.assuredDefault()))
            .targetRecipe(targetRecipe, scenario.targetTimes())
            .build();

        var s1 = service.route(standard);
        var s2 = service.route(standard);
        assertTrue(s1.success() || s1.partial(), "mega standard run should still produce a plan");
        assertNotNull(s1.plan());
        assertNotNull(s2.plan());
        assertEquals(planFingerprint(s1.plan()), planFingerprint(s2.plan()), "deterministic request should keep same plan fingerprint");
        assertNoJunkExecution(s1.plan());
        var standardReport = RoutingPlanReverseVerifier.verify(standard, s1, service.graph());
        assertTrue(standardReport.valid(), () -> "mega standard reverse verifier failed: " + standardReport.errors());

        RoutingRequest assured = RoutingRequest.builder()
            .plannerMode(PlannerMode.assuredBounded)
            .sourcePolicyId(sourceNoJunkPolicyId)
            .stock(scenario.stock().copy())
            .budget(RoutingBudget.assuredDefault())
            .targetRecipe(targetRecipe, scenario.targetTimes())
            .build();
        var a1 = service.route(assured);
        assertTrue(a1.success() || a1.partial(), "mega assured run should still produce a plan");
        assertNotNull(a1.plan());
        assertNoJunkExecution(a1.plan());
        var assuredReport = RoutingPlanReverseVerifier.verify(assured, a1, service.graph());
        assertTrue(assuredReport.valid(), () -> "mega assured reverse verifier failed: " + assuredReport.errors());
    }

    @Test
    void fuzzedHighComplexityScenariosWithPlayerOverlayTagsRemainReplayable(MinecraftServer server) {
        for (int seed = 100; seed < 124; seed++) {
            int caseSeed = seed;
            RoutingComplexDataset.Scenario scenario = RoutingComplexDataset.generatedScenario(seed, 20, 80, 16);
            RoutingService service = RoutingTestFixtures.service(scenario.recipes());
            installJunkExclusionPolicy(service);
            java.util.Set<String> bannedIds = installPlayerBanOverlay(service, seed);

            RecipeEntry<?> targetRecipe = RoutingTestFixtures.findRecipeById(service.graph(), scenario.targetRecipeId());
            assertNotNull(targetRecipe);

            RoutingRequest request = RoutingRequest.builder()
                .plannerMode(seed % 3 == 0 ? PlannerMode.assuredBounded : PlannerMode.standard)
                .sourcePolicyId(sourceNoJunkAndPlayerBanPolicyId)
                .stock(scenario.stock().copy())
                .tagSnapshot(service.userWorkspace().snapshot())
                .budget(new RoutingBudget(7_500, 80_000, 120, 20_000, 0.08, LoopBudget.assuredDefault()))
                .targetRecipe(targetRecipe, scenario.targetTimes())
                .build();

            var outcome = service.route(request);
            assertTrue(outcome.success() || outcome.partial(), "fuzz seed " + seed + " must produce plan");
            assertNotNull(outcome.plan());
            assertNoJunkExecution(outcome.plan());
            assertNoPlayerBannedExecution(outcome.plan(), bannedIds);

            var report = RoutingPlanReverseVerifier.verify(request, outcome, service.graph());
            assertTrue(report.valid(), () -> "reverse verification failed for fuzz seed " + caseSeed + ": " + report.errors());
            assertTrue(outcome.metrics().expandedNodes() <= request.budget().maxExpandedNodes(), "expanded nodes overflow on seed " + seed);
        }
    }

    private static void installJunkExclusionPolicy(RoutingService service) {
        for (RecipeGraph.Node node : service.graph().nodes()) {
            if (node.recipe().id() != null && node.recipe().id().getPath().startsWith("junk_")) {
                service.tagIndex().add("cirrus:quality/junk", node.recipe());
            }
            if (node.recipe().id() != null && node.recipe().id().getPath().contains("_junk_")) {
                service.tagIndex().add("cirrus:quality/junk", node.recipe());
            }
            if (node.recipe().id() != null && node.recipe().id().getPath().startsWith("mega_decoy_")) {
                service.tagIndex().add("cirrus:quality/decoy", node.recipe());
            }
        }

        SourcePolicy policy = new SourcePolicy(
            sourceNoJunkPolicyId,
            100,
            Lists.immutable.of(
                new SourcePolicyRuleSpec(
                    excludeJunkClusterRuleId,
                    true,
                    TagExpr.has("cirrus:quality/junk"),
                    SourcePolicyAction.excludeMatch,
                    false
                ),
                new SourcePolicyRuleSpec(
                    excludeDecoyClusterRuleId,
                    true,
                    TagExpr.has("cirrus:quality/decoy"),
                    SourcePolicyAction.excludeMatch,
                    false
                )
            )
        );
        service.registerSourcePolicy(policy);
    }

    private static java.util.Set<String> installPlayerBanOverlay(RoutingService service, int seed) {
        java.util.Random random = new java.util.Random(seed * 17L + 3L);
        java.util.Set<String> bannedIds = new java.util.HashSet<>();
        for (RecipeGraph.Node node : service.graph().nodes()) {
            if (node.recipe().id() == null) {
                continue;
            }
            String path = node.recipe().id().getPath();
            if (path.contains("_alt_") && random.nextBoolean()) {
                int index = service.tagIndex().recipeIndex(node.recipe());
                if (index >= 0) {
                    service.userWorkspace().addToTag("cirrus:player/ban", index);
                    bannedIds.add(path);
                }
            }
        }

        SourcePolicy combinedPolicy = new SourcePolicy(
            sourceNoJunkAndPlayerBanPolicyId,
            200,
            Lists.immutable.of(
                new SourcePolicyRuleSpec(excludeJunkClusterRuleId, true, TagExpr.has("cirrus:quality/junk"), SourcePolicyAction.excludeMatch, false),
                new SourcePolicyRuleSpec(excludePlayerBanRuleId, true, TagExpr.has("cirrus:player/ban"), SourcePolicyAction.excludeMatch, false)
            )
        );
        service.registerSourcePolicy(combinedPolicy);
        return bannedIds;
    }

    private static void assertNoJunkExecution(dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan plan) {
        for (var action : plan.actions()) {
            if (action instanceof PlanAction.ExecuteRecipe exec &&
                exec.recipe().id() != null &&
                exec.recipe().id().getPath().startsWith("junk_")) {
                fail("plan executed junk recipe: " + exec.recipe().id());
            }
            if (action instanceof PlanAction.ExecuteRecipe exec &&
                exec.recipe().id() != null &&
                exec.recipe().id().getPath().contains("_junk_")) {
                fail("plan executed seed junk recipe: " + exec.recipe().id());
            }
            if (action instanceof PlanAction.ExecuteRecipe exec &&
                exec.recipe().id() != null &&
                exec.recipe().id().getPath().startsWith("mega_decoy_")) {
                fail("plan executed decoy recipe: " + exec.recipe().id());
            }
        }
    }

    private static void assertNoPlayerBannedExecution(
        dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan plan,
        java.util.Set<String> bannedIds
    ) {
        for (var action : plan.actions()) {
            if (action instanceof PlanAction.ExecuteRecipe exec &&
                exec.recipe().id() != null &&
                bannedIds.contains(exec.recipe().id().getPath())) {
                fail("plan executed player-banned recipe: " + exec.recipe().id());
            }
        }
    }

    private static String planFingerprint(dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan plan) {
        StringBuilder builder = new StringBuilder();
        for (var action : plan.actions()) {
            builder.append(action.actionId()).append(":").append(action.getClass().getSimpleName());
            if (action instanceof PlanAction.ExecuteRecipe exec) {
                builder.append(":").append(exec.recipe().id());
                builder.append(":").append(exec.times());
                builder.append(":").append(exec.boundInputs().size());
                builder.append(":").append(exec.expectedOutputs().size());
            }
            builder.append("|");
        }
        return builder.toString();
    }
}
