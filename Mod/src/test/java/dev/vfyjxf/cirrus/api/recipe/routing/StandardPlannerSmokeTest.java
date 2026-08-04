package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHeuristics;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleDefinitions;
import dev.vfyjxf.cirrus.api.recipe.routing.service.LoopBudget;
import dev.vfyjxf.cirrus.api.recipe.routing.service.LoopPolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.service.OutputSelectionPolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingFailureCode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingVariantKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class StandardPlannerSmokeTest {

    @Test
    void generateRecipeCenteredPlan(MinecraftServer server) {
        var makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        var makeStick = new RoutingTestFixtures.FakeRecipe(
            "make_stick",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_PLANKS, 2)),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 4))
        );
        RoutingService service = RoutingTestFixtures.service(makePlanks, makeStick);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_stick");
        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));

        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(stock)
            .targetRecipe(target, 1)
            .build();

        var outcome = service.route(request);
        assertTrue(outcome.success() || outcome.partial());
        assertNotNull(outcome.plan());
        assertTrue(outcome.plan().actions().size() >= 2, "expected at least consume+execute actions");
    }

    @Test
    void flatHeuristicBuilderCanSuppressBadIntermediateRecipe(MinecraftServer server) {
        RecipeRoutingHeuristics.clear();
        try {
            RecipeRoutingHeuristics.add("test:bad_recycling")
                .recipeIdContains("recycling")
                .recycling()
                .targetOnly()
                .penalty(10_000)
                .register();

            var makeNormalPlanks = new RoutingTestFixtures.FakeRecipe(
                "make_normal_planks",
                java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
                java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
            );
            var makeRecyclingPlanks = new RoutingTestFixtures.FakeRecipe(
                "make_recycling_planks",
                java.util.List.of(RoutingTestFixtures.in(Items.OAK_DOOR, 1)),
                java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
            );
            var makeStick = new RoutingTestFixtures.FakeRecipe(
                "make_stick",
                java.util.List.of(RoutingTestFixtures.in(Items.OAK_PLANKS, 2)),
                java.util.List.of(RoutingTestFixtures.out(Items.STICK, 4))
            );
            RoutingService service = RoutingTestFixtures.service(makeNormalPlanks, makeRecyclingPlanks, makeStick);
            var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_stick");
            assertNotNull(target);

            Stock stock = Stock.empty();
            stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));
            stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_DOOR, 1)));

            RoutingRequest request = RoutingRequest.builder()
                .plannerMode(PlannerMode.standard)
                .stock(stock)
                .targetRecipe(target, 1)
                .build();

            var outcome = service.route(request);
            assertTrue(outcome.success() || outcome.partial());
            assertNotNull(outcome.plan());
            assertTrue(outcome.plan().actions().anySatisfy(action ->
                action instanceof PlanAction.ExecuteRecipe execute
                    && execute.recipe().id() != null
                    && "make_normal_planks".equals(execute.recipe().id().getPath())
            ));
            assertFalse(outcome.plan().actions().anySatisfy(action ->
                action instanceof PlanAction.ExecuteRecipe execute
                    && execute.recipe().id() != null
                    && "make_recycling_planks".equals(execute.recipe().id().getPath())
            ));

            var recyclingTarget = RoutingTestFixtures.findRecipeById(service.graph(), "make_recycling_planks");
            assertNotNull(recyclingTarget);
            var directOutcome = service.route(RoutingRequest.builder()
                .plannerMode(PlannerMode.standard)
                .stock(stock)
                .targetRecipe(recyclingTarget, 1)
                .build());
            assertTrue(directOutcome.success() || directOutcome.partial(), "targetOnly recipe should still be routable as the target");
        } finally {
            RecipeRoutingHeuristics.clear();
        }
    }

    @Test
    void routeTagInputAcrossIntermediateRecipe(MinecraftServer server) {
        var makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        var makeStick = new RoutingTestFixtures.FakeRecipe(
            "make_stick",
            java.util.List.of(InputContent.fromItemTag(ItemTags.PLANKS, 2)),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 4))
        );
        RoutingService service = RoutingTestFixtures.service(makePlanks, makeStick);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_stick");
        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));

        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(stock)
            .targetRecipe(target, 1)
            .build();

        var outcome = service.route(request);
        assertTrue(outcome.success() || outcome.partial());
        assertNotNull(outcome.plan());

        boolean executedPlanks = false;
        boolean executedStick = false;
        for (var action : outcome.plan().actions()) {
            if (!(action instanceof PlanAction.ExecuteRecipe execute) || execute.recipe().id() == null) {
                continue;
            }
            String path = execute.recipe().id().getPath();
            if ("make_planks".equals(path)) {
                executedPlanks = true;
            }
            if ("make_stick".equals(path)) {
                executedStick = true;
            }
        }
        assertTrue(executedPlanks, "expected planner to synthesize tag ingredient via make_planks");
        assertTrue(executedStick, "expected planner to execute target recipe");
    }

    @Test
    void tagBasedRecipeCycleIsBlockedByActivePath(MinecraftServer server) {
        var makeLog = new RoutingTestFixtures.FakeRecipe(
            "make_log",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_PLANKS, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_LOG, 1))
        );
        var makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            java.util.List.of(InputContent.fromItemTag(ItemTags.LOGS, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        RoutingService service = RoutingTestFixtures.service(makeLog, makePlanks);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_log");
        assertNotNull(target);

        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(Stock.empty())
            .targetRecipe(target, 1)
            .build();

        var outcome = service.route(request);
        assertTrue(outcome.failed() || outcome.partial());
        assertFalse(outcome.issues().isEmpty(), "cycle should surface as an unresolved route");
        assertTrue(outcome.metrics().expandedNodes() <= 2, "cycle handling should not expand beyond the active path");
    }

    @Test
    void directRecipeCycleReportsCycleAmplificationBlocked(MinecraftServer server) {
        var makeLog = new RoutingTestFixtures.FakeRecipe(
            "make_log",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_PLANKS, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_LOG, 1))
        );
        var makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        RoutingService service = RoutingTestFixtures.service(makeLog, makePlanks);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_log");
        assertNotNull(target);

        var outcome = service.route(RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(Stock.empty())
            .targetRecipe(target, 1)
            .build());

        assertTrue(outcome.failed() || outcome.partial());
        assertTrue(outcome.issues().anySatisfy(issue -> issue.code() == RoutingFailureCode.cycleAmplificationBlocked));
        assertTrue(outcome.metrics().expandedNodes() <= 2, "direct two-node cycle should stop at active-path re-entry");
    }

    @Test
    void calculatorPreviewCutsReversibleCycleAndContinuesSiblingInputs(MinecraftServer server) {
        var makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        var makeStick = new RoutingTestFixtures.FakeRecipe(
            "make_stick",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_PLANKS, 2)),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 4))
        );
        var compressBirch = new RoutingTestFixtures.FakeRecipe(
            "compress_birch",
            java.util.List.of(RoutingTestFixtures.in(Items.BIRCH_LOG, 9)),
            java.util.List.of(RoutingTestFixtures.out(Items.BIRCH_WOOD, 1))
        );
        var decompressBirch = new RoutingTestFixtures.FakeRecipe(
            "decompress_birch",
            java.util.List.of(RoutingTestFixtures.in(Items.BIRCH_WOOD, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.BIRCH_LOG, 9))
        );
        var makeTarget = new RoutingTestFixtures.FakeRecipe(
            "make_cycle_target",
            java.util.List.of(
                RoutingTestFixtures.in(Items.STICK, 1),
                RoutingTestFixtures.in(Items.BIRCH_WOOD, 1)
            ),
            java.util.List.of(RoutingTestFixtures.out(Items.DIAMOND, 1))
        );
        RoutingService service = RoutingTestFixtures.service(
            makePlanks,
            makeStick,
            compressBirch,
            decompressBirch,
            makeTarget
        );
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_cycle_target");
        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));

        var outcome = service.route(RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .ruleProfileId(DefaultRuleDefinitions.calculatorPreviewProfileId)
            .stock(stock)
            .targetRecipe(target, 1)
            .build());

        assertTrue(outcome.success() || outcome.partial(), () -> describeIssues(outcome));
        assertNotNull(outcome.plan(), () -> describeIssues(outcome));
        assertFalse(outcome.metrics().budgetExhausted(), "cycle cut should not consume the route budget");
        assertTrue(outcome.metrics().expandedNodes() <= 6, "cycle should stop at the first active-path re-entry");
        assertTrue(outcome.issues().anySatisfy(issue ->
            issue.code() == RoutingFailureCode.cycleAmplificationBlocked && !issue.blocking()
        ), () -> describeIssues(outcome));
        assertTrue(outcome.plan().actions().anySatisfy(action -> action instanceof PlanAction.PendingInput),
            () -> describePlan(outcome.plan()));
        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe exec
                && exec.recipe().id() != null
                && "make_stick".equals(exec.recipe().id().getPath())
        ), () -> describePlan(outcome.plan()));
        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe exec
                && exec.recipe().id() != null
                && "make_cycle_target".equals(exec.recipe().id().getPath())
        ), () -> describePlan(outcome.plan()));
    }

    @Test
    void activeDemandCycleRejectsCompressedResourceLoop(MinecraftServer server) {
        var makeOakWoodFromLogs = new RoutingTestFixtures.FakeRecipe(
            "make_oak_wood_from_logs",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 4)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_WOOD, 3))
        );
        var compressOakWood = new RoutingTestFixtures.FakeRecipe(
            "compress_oak_wood",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_WOOD, 9)),
            java.util.List.of(RoutingTestFixtures.out(Items.STRIPPED_OAK_WOOD, 1))
        );
        var decompressOakWood = new RoutingTestFixtures.FakeRecipe(
            "decompress_oak_wood",
            java.util.List.of(RoutingTestFixtures.in(Items.STRIPPED_OAK_WOOD, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_WOOD, 9))
        );
        var makeTarget = new RoutingTestFixtures.FakeRecipe(
            "make_oak_target",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_WOOD, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.DIAMOND, 1))
        );
        RoutingService service = RoutingTestFixtures.service(
            makeOakWoodFromLogs,
            compressOakWood,
            decompressOakWood,
            makeTarget
        );
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_oak_target");
        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 4)));

        var outcome = service.route(RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(stock)
            .targetRecipe(target, 1)
            .build());

        assertTrue(outcome.success() || outcome.partial(), () -> describeIssues(outcome));
        assertNotNull(outcome.plan(), () -> describeIssues(outcome));
        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe exec
                && exec.recipe().id() != null
                && "make_oak_wood_from_logs".equals(exec.recipe().id().getPath())
        ), () -> describePlan(outcome.plan()));
        assertFalse(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe exec
                && exec.recipe().id() != null
                && "decompress_oak_wood".equals(exec.recipe().id().getPath())
        ), () -> describePlan(outcome.plan()));
    }

    @Test
    void activeDemandCycleFiltersIndirectCompressedChainBeforeTryingCandidate(MinecraftServer server) {
        var makeIngotFromOre = new RoutingTestFixtures.FakeRecipe(
            "zz_make_ingot_from_ore",
            java.util.List.of(RoutingTestFixtures.in(Items.RAW_IRON, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.IRON_INGOT, 1))
        );
        var decompressBlock = new RoutingTestFixtures.FakeRecipe(
            "aa_decompress_block",
            java.util.List.of(RoutingTestFixtures.in(Items.IRON_BLOCK, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.IRON_INGOT, 9))
        );
        var decompressCompressedBlock = new RoutingTestFixtures.FakeRecipe(
            "ab_decompress_compressed_block",
            java.util.List.of(RoutingTestFixtures.in(Items.DIAMOND_BLOCK, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.IRON_BLOCK, 9))
        );
        var compressIngot = new RoutingTestFixtures.FakeRecipe(
            "ac_compress_ingot",
            java.util.List.of(RoutingTestFixtures.in(Items.IRON_INGOT, 9)),
            java.util.List.of(RoutingTestFixtures.out(Items.DIAMOND_BLOCK, 1))
        );
        var makeTarget = new RoutingTestFixtures.FakeRecipe(
            "make_indirect_cycle_target",
            java.util.List.of(RoutingTestFixtures.in(Items.IRON_INGOT, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.NETHER_STAR, 1))
        );
        RoutingService service = RoutingTestFixtures.service(
            makeIngotFromOre,
            decompressBlock,
            decompressCompressedBlock,
            compressIngot,
            makeTarget
        );
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_indirect_cycle_target");
        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.RAW_IRON, 1)));

        var outcome = service.route(RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(stock)
            .targetRecipe(target, 1)
            .build());

        assertTrue(outcome.success() || outcome.partial(), () -> describeIssues(outcome));
        assertNotNull(outcome.plan(), () -> describeIssues(outcome));
        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe exec
                && exec.recipe().id() != null
                && "zz_make_ingot_from_ore".equals(exec.recipe().id().getPath())
        ), () -> describePlan(outcome.plan()));
        assertFalse(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe exec
                && exec.recipe().id() != null
                && "aa_decompress_block".equals(exec.recipe().id().getPath())
        ), () -> describePlan(outcome.plan()));
        assertFalse(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe exec
                && exec.recipe().id() != null
                && "ab_decompress_compressed_block".equals(exec.recipe().id().getPath())
        ), () -> describePlan(outcome.plan()));
        assertFalse(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe exec
                && exec.recipe().id() != null
                && "ac_compress_ingot".equals(exec.recipe().id().getPath())
        ), () -> describePlan(outcome.plan()));
        assertFalse(outcome.metrics().budgetExhausted(), () -> describeIssues(outcome));
    }

    @Test
    void calculatorPreviewRootInputBudgetDoesNotStarveSiblingInputs(MinecraftServer server) {
        var makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        var makeStick = new RoutingTestFixtures.FakeRecipe(
            "make_stick",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_PLANKS, 2)),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 4))
        );
        var deepA = new RoutingTestFixtures.FakeRecipe(
            "deep_a",
            java.util.List.of(RoutingTestFixtures.in(Items.COBBLESTONE, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.DIRT, 1))
        );
        var deepB = new RoutingTestFixtures.FakeRecipe(
            "deep_b",
            java.util.List.of(RoutingTestFixtures.in(Items.DIRT, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.GRAVEL, 1))
        );
        var deepC = new RoutingTestFixtures.FakeRecipe(
            "deep_c",
            java.util.List.of(RoutingTestFixtures.in(Items.GRAVEL, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.SAND, 1))
        );
        var makeTarget = new RoutingTestFixtures.FakeRecipe(
            "make_budget_target",
            java.util.List.of(
                RoutingTestFixtures.in(Items.SAND, 1),
                RoutingTestFixtures.in(Items.STICK, 1)
            ),
            java.util.List.of(RoutingTestFixtures.out(Items.DIAMOND, 1))
        );
        RoutingService service = RoutingTestFixtures.service(makePlanks, makeStick, deepA, deepB, deepC, makeTarget);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_budget_target");
        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));

        var outcome = service.route(RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .ruleProfileId(DefaultRuleDefinitions.calculatorPreviewProfileId)
            .budget(new dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingBudget(
                32,
                128,
                30,
                64,
                2,
                16,
                0.05,
                LoopBudget.standardDefault()
            ))
            .stock(stock)
            .targetRecipe(target, 1)
            .build());

        assertTrue(outcome.success() || outcome.partial(), () -> describeIssues(outcome));
        assertNotNull(outcome.plan(), () -> describeIssues(outcome));
        assertTrue(outcome.metrics().budgetExhausted(), "root input branch budget should be recorded");
        assertEquals("maxRootInputBudget", outcome.metrics().budgetStopReason());
        assertTrue(outcome.plan().actions().anySatisfy(action -> action instanceof PlanAction.PendingInput),
            () -> describePlan(outcome.plan()));
        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe exec
                && exec.recipe().id() != null
                && "make_planks".equals(exec.recipe().id().getPath())
        ), () -> describePlan(outcome.plan()));
        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe exec
                && exec.recipe().id() != null
                && "make_stick".equals(exec.recipe().id().getPath())
        ), () -> describePlan(outcome.plan()));
        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe exec
                && exec.recipe().id() != null
                && "make_budget_target".equals(exec.recipe().id().getPath())
        ), () -> describePlan(outcome.plan()));
    }

    @Test
    void boundedRecipeCycleStopsAtRecipeLoopBudget(MinecraftServer server) {
        var echoStick = new RoutingTestFixtures.FakeRecipe(
            "echo_stick",
            java.util.List.of(RoutingTestFixtures.in(Items.STICK, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 1))
        );
        RoutingService service = RoutingTestFixtures.service(echoStick);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "echo_stick");
        assertNotNull(target);

        var outcome = service.route(RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .loopPolicy(LoopPolicy.allowBoundedAmplification)
            .loopBudget(new LoopBudget(1, 10))
            .stock(Stock.empty())
            .targetRecipe(target, 1)
            .build());

        assertTrue(outcome.failed() || outcome.partial());
        assertTrue(outcome.issues().anySatisfy(issue -> issue.code() == RoutingFailureCode.cycleBudgetExhausted));
        assertTrue(outcome.metrics().expandedNodes() <= 1, "budget should reject before expanding the re-entered recipe");
    }

    @Test
    void unrelatedCycleDoesNotAffectAcyclicTargetRoute(MinecraftServer server) {
        var makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        var makeStick = new RoutingTestFixtures.FakeRecipe(
            "make_stick",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_PLANKS, 2)),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 4))
        );
        var unrelatedCycleA = new RoutingTestFixtures.FakeRecipe(
            "unrelated_cycle_a",
            java.util.List.of(RoutingTestFixtures.in(Items.COBBLESTONE, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.DIRT, 1))
        );
        var unrelatedCycleB = new RoutingTestFixtures.FakeRecipe(
            "unrelated_cycle_b",
            java.util.List.of(RoutingTestFixtures.in(Items.DIRT, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.COBBLESTONE, 1))
        );
        RoutingService service = RoutingTestFixtures.service(makePlanks, makeStick, unrelatedCycleA, unrelatedCycleB);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_stick");
        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));

        var outcome = service.route(RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(stock)
            .targetRecipe(target, 1)
            .build());

        assertTrue(outcome.success(), "unrelated cycle should not influence an acyclic target route");
        assertFalse(outcome.issues().anySatisfy(issue -> issue.code() == RoutingFailureCode.cycleAmplificationBlocked));
        assertTrue(outcome.metrics().expandedNodes() <= 2, "planner should only expand the acyclic target path");
    }

    @Test
    void routeTagInputKeepsTagBindingOnExecuteAction(MinecraftServer server) {
        var makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        var makeStick = new RoutingTestFixtures.FakeRecipe(
            "make_stick",
            java.util.List.of(InputContent.fromItemTag(ItemTags.PLANKS, 2)),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 4))
        );
        RoutingService service = RoutingTestFixtures.service(makePlanks, makeStick);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_stick");
        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));

        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(stock)
            .targetRecipe(target, 1)
            .build();

        var outcome = service.route(request);
        assertTrue(outcome.success() || outcome.partial());
        assertNotNull(outcome.plan());

        PlanAction.ExecuteRecipe stickAction = outcome.plan().actions()
													  .selectInstancesOf(PlanAction.ExecuteRecipe.class)
													  .detect(action -> "make_stick".equals(action.recipe().id().getPath()));

        assertNotNull(stickAction);
        assertTrue(stickAction.boundInputs().getFirst().input() instanceof TagStackContent<?>);
        assertEquals(2L, stickAction.boundInputs().getFirst().amount());
    }

    @Test
    void preferredTagVariantReroutesToMatchingProducer(MinecraftServer server) {
        var makeOakPlanks = new RoutingTestFixtures.FakeRecipe(
            "make_oak_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        var makeBirchPlanks = new RoutingTestFixtures.FakeRecipe(
            "make_birch_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.BIRCH_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.BIRCH_PLANKS, 4))
        );
        InputContent planks = InputContent.fromItemTag(ItemTags.PLANKS, 2);
        var makeStick = new RoutingTestFixtures.FakeRecipe(
            "make_stick",
            java.util.List.of(planks),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 4))
        );
        RoutingService service = RoutingTestFixtures.service(makeOakPlanks, makeBirchPlanks, makeStick);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_stick");
        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.BIRCH_LOG, 1)));

        String birchVariantKey = RoutingVariantKeys.variantKey(ContentKind.fromContent(CirrusTypes.Contents.ITEM, Items.BIRCH_PLANKS));
        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(stock)
            .targetRecipe(target, 1)
            .preferVariant(planks, birchVariantKey)
            .build();

        var outcome = service.route(request);
        assertTrue(outcome.success() || outcome.partial());
        assertNotNull(outcome.plan());

        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe execute
                && execute.recipe().id() != null
                && "make_birch_planks".equals(execute.recipe().id().getPath())
        ));
        assertFalse(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe execute
                && execute.recipe().id() != null
                && "make_oak_planks".equals(execute.recipe().id().getPath())
        ));
    }

    @Test
    void preferInventoryCoveredIntermediateRecipe(MinecraftServer server) {
        var cobbleFurnace = new RoutingTestFixtures.FakeRecipe(
            "make_furnace_from_cobble",
            java.util.List.of(RoutingTestFixtures.in(Items.COBBLESTONE, 8)),
            java.util.List.of(RoutingTestFixtures.out(Items.FURNACE, 1))
        );
        var blackstoneFurnace = new RoutingTestFixtures.FakeRecipe(
            "make_furnace_from_blackstone",
            java.util.List.of(RoutingTestFixtures.in(Items.BLACKSTONE, 8)),
            java.util.List.of(RoutingTestFixtures.out(Items.FURNACE, 1))
        );
        var makeSmoker = new RoutingTestFixtures.FakeRecipe(
            "make_smoker",
            java.util.List.of(
                RoutingTestFixtures.in(Items.FURNACE, 1),
                RoutingTestFixtures.in(Items.OAK_LOG, 4)
            ),
            java.util.List.of(RoutingTestFixtures.out(Items.SMOKER, 1))
        );
        RoutingService service = RoutingTestFixtures.service(cobbleFurnace, blackstoneFurnace, makeSmoker);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_smoker");
        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.COBBLESTONE, 64)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 4)));

        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(stock)
            .targetRecipe(target, 1)
            .build();

        var outcome = service.route(request);
        assertTrue(outcome.success() || outcome.partial());
        assertNotNull(outcome.plan());

        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe execute
                && execute.recipe().id() != null
                && "make_furnace_from_cobble".equals(execute.recipe().id().getPath())
        ));
        assertFalse(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe execute
                && execute.recipe().id() != null
                && "make_furnace_from_blackstone".equals(execute.recipe().id().getPath())
        ));
    }

    @Test
    void preferInventoryCoveredProducerForOutputTarget(MinecraftServer server) {
        var cobbleFurnace = new RoutingTestFixtures.FakeRecipe(
            "make_furnace_from_cobble",
            java.util.List.of(RoutingTestFixtures.in(Items.COBBLESTONE, 8)),
            java.util.List.of(RoutingTestFixtures.out(Items.FURNACE, 1))
        );
        var blackstoneFurnace = new RoutingTestFixtures.FakeRecipe(
            "make_furnace_from_blackstone",
            java.util.List.of(RoutingTestFixtures.in(Items.BLACKSTONE, 8)),
            java.util.List.of(RoutingTestFixtures.out(Items.FURNACE, 1))
        );
        RoutingService service = RoutingTestFixtures.service(cobbleFurnace, blackstoneFurnace);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.COBBLESTONE, 64)));

        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(stock)
            .adapterSelectionPolicy(OutputSelectionPolicy.bestEffortSingleRecipe)
            .targetOutput(ContentStack.fromStack(new ItemStack(Items.FURNACE, 1)), 1)
            .build();

        var outcome = service.route(request);
        assertTrue(outcome.success() || outcome.partial());
        assertNotNull(outcome.plan());

        PlanAction.ExecuteRecipe furnaceAction = outcome.plan().actions()
            .selectInstancesOf(PlanAction.ExecuteRecipe.class)
            .getFirst();
        assertNotNull(furnaceAction.recipe().id());
        assertEquals("make_furnace_from_cobble", furnaceAction.recipe().id().getPath());
    }

    private static String describeIssues(RoutingOutcome outcome) {
        StringBuilder builder = new StringBuilder();
        outcome.issues().forEach(issue -> builder.append(issue).append('\n'));
        return builder.toString();
    }

    private static String describePlan(RoutingPlan plan) {
        StringBuilder builder = new StringBuilder();
        plan.actions().forEach(action -> builder.append(action).append('\n'));
        return builder.toString();
    }
}
