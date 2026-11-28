package dev.vfyjxf.cirrus.planning;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.BaseBoundarySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.BaseContract;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventoryTier;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.graph.CatalystSlot;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.CostOverrideRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.human.OverrideStockFeedback;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.SlotBinding;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningSession;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultAcquisitionOracle;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultCostMetadataProvider;
import dev.vfyjxf.cirrus.planning.core.cost.PlanCosts;
import dev.vfyjxf.cirrus.planning.core.cost.StepCosts;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import dev.vfyjxf.cirrus.planning.core.human.LayeredHumanGuidanceStore;
import dev.vfyjxf.cirrus.planning.core.service.DefaultPlanningService;
import dev.vfyjxf.cirrus.planning.core.verify.DefaultPlanVerifier;
import dev.vfyjxf.cirrus.planning.core.verify.ErrorCodes;
import dev.vfyjxf.cirrus.testutil.Reflect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.capability;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.compile;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.durabilityCatalyst;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.fac;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.generousBudget;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.item;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.machine;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.out;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.owned;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.ownedCraftingTable;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.rec;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.request;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningAuditSessionAndVerifierRegressionTest {

    private static final VerificationCertificate dummyCert = new VerificationCertificate(
        false,
        false,
        false,
        false,
        false,
        false,
        List.of()
    );

    @Test
    void r41RefinePreservesFourTierInventoryAndBaseBoundary() {
        ComponentStackKey netOre = item("mod:net_ore");
        ComponentStackKey gear = item("mod:gear");
        ComponentStackKey cobble = item("minecraft:cobblestone");
        ComponentStackKey sand = item("minecraft:sand");
        ComponentStackKey dust = item("mod:nearby_dust");
        ComponentStackKey remoteCrystal = item("mod:remote_crystal");
        ComponentStackKey rareCrystal = item("mod:rare_crystal");
        ComponentStackKey fresh = item("mod:fresh_override");

        CompiledRecipeGraph graph = compile(
            List.of(
                rec(
                    "minecraft:crafting",
                    "gear_from_net_ore",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(netOre, 2)),
                    List.of(out(gear, 1))
                )
            )
        );

        Map<InventoryTier, StockLedger> available = Map.of(
            InventoryTier.hand, new StockLedger(Map.of(cobble, 4L)),
            InventoryTier.network, new StockLedger(Map.of(netOre, 3L)),
            InventoryTier.nearby, new StockLedger(Map.of(dust, 5L)),
            InventoryTier.remote, new StockLedger(Map.of(remoteCrystal, 7L))
        );
        Map<InventoryTier, StockLedger> reserved = Map.of(
            InventoryTier.hand, new StockLedger(Map.of(cobble, 1L)),
            InventoryTier.network, new StockLedger(Map.of(netOre, 1L)),
            InventoryTier.nearby, new StockLedger(Map.of(dust, 2L)),
            InventoryTier.remote, new StockLedger(Map.of(remoteCrystal, 1L))
        );
        Set<InventoryTier> openTiers = EnumSet.of(
            InventoryTier.hand,
            InventoryTier.network,
            InventoryTier.nearby
        );
        Set<ComponentStackKey> protectedTags = Set.of(netOre, remoteCrystal);
        InventorySnapshot inventory = InventorySnapshot.fromTiers(available, reserved, openTiers, protectedTags);

        BaseBoundarySnapshot boundary = new BaseBoundarySnapshot(
            Map.of(cobble, new BaseContract(cobble, 1.5, 0.5, 0.25, 0.0, 64L, false, "test:contract")),
            Set.of(sand),
            Set.of(rareCrystal),
            Set.of(dust),
            List.of(new BaseBoundarySnapshot.PartialOrderConstraint(cobble, sand, 0.5))
        );

        HumanGuidanceView guidance = DefaultHumanGuidanceView.empty();
        PlanningContext context = new PlanningContext(
            inventory,
            fac(ownedCraftingTable()),
            new DefaultAcquisitionOracle(graph, boundary, guidance),
            guidance,
            ProgressionState.overworldDefault
        ).withBaseBoundary(boundary);
        PlanningRequest initialRequest = new PlanningRequest(
            List.of(new TargetDemand(gear, 1L)),
            context,
            PlanningProfile.defaultBalanced(),
            generousBudget()
        );

        try (PlanningSession session = new DefaultPlanningService(graph).openSession(initialRequest)) {
            PlanningResult before = session.currentResult();
            assertEquals(PlanningStatus.feasible, before.status());
            assertFalse(before.paretoPlans().isEmpty());
            ProductionPlan originalPlan = before.paretoPlans().get(0);
            assertEquals(2L, originalPlan.preparation().consumedFromInventory().amountOf(netOre));
            assertEquals(0L, originalPlan.preparation().toAcquireFromWorld().amountOf(netOre));

            PlanningResult after = session.refine(new OverrideStockFeedback(fresh, 5L));
            assertEquals(PlanningStatus.feasible, after.status());
            assertFalse(after.paretoPlans().isEmpty());
            ProductionPlan refinedPlan = after.paretoPlans().get(0);

            Map<InventoryTier, StockLedger> expectedAvailable = Map.of(
                InventoryTier.hand, new StockLedger(Map.of(cobble, 4L, fresh, 5L)),
                InventoryTier.network, new StockLedger(Map.of(netOre, 3L)),
                InventoryTier.nearby, new StockLedger(Map.of(dust, 5L)),
                InventoryTier.remote, new StockLedger(Map.of(remoteCrystal, 7L))
            );
            Map<InventoryTier, StockLedger> expectedReserved = Map.of(
                InventoryTier.hand, new StockLedger(Map.of(cobble, 1L)),
                InventoryTier.network, new StockLedger(Map.of(netOre, 1L)),
                InventoryTier.nearby, new StockLedger(Map.of(dust, 2L)),
                InventoryTier.remote, new StockLedger(Map.of(remoteCrystal, 1L))
            );
            InventorySnapshot expected = InventorySnapshot.fromTiers(
                expectedAvailable,
                expectedReserved,
                openTiers,
                protectedTags
            );

            PlanningRequest refinedRequest = currentRequest(session);
            InventorySnapshot refined = refinedRequest.context().inventory();
            assertEquals(expected, refined);
            assertEquals(4, refined.availableByTier().size());
            assertEquals(4, refined.reservedByTier().size());
            assertEquals(2L, refined.usableAmountInTier(netOre, InventoryTier.network));
            assertEquals(3L, refined.usableAmountInTier(dust, InventoryTier.nearby));
            assertEquals(6L, refined.usableAmountInTier(remoteCrystal, InventoryTier.remote));
            assertEquals(5L, refined.usableAmountInTier(fresh, InventoryTier.hand));
            assertEquals(openTiers, refined.alreadyOpenTiers());
            assertEquals(protectedTags, refined.protectedFromGenericTags());
            assertTrue(refined.isProtectedFromGenericTag(netOre));
            assertTrue(refined.hasNetworkStorage());

            BaseBoundarySnapshot refinedBoundary = refinedRequest.context().baseBoundary();
            assertEquals(boundary, refinedBoundary);
            assertEquals(Set.of(sand), refinedBoundary.forceExpandResources());
            assertEquals(Set.of(rareCrystal), refinedBoundary.protectedRareResources());
            assertEquals(Set.of(dust), refinedBoundary.certifiedNaturalResources());
            assertEquals(1, refinedBoundary.pricePartialOrders().size());
            Object oracle = refinedRequest.context().acquisitionOracle();
            assertTrue(oracle instanceof DefaultAcquisitionOracle);
            assertEquals(boundary, ((DefaultAcquisitionOracle) oracle).baseBoundary());

            assertEquals(originalPlan.steps(), refinedPlan.steps());
            assertEquals(originalPlan.preparation(), refinedPlan.preparation());
            assertEquals(2L, refinedPlan.preparation().consumedFromInventory().amountOf(netOre));
            assertEquals(0L, refinedPlan.preparation().toAcquireFromWorld().amountOf(netOre));
            assertTrue(refinedPlan.certificate().valid());
        }
    }

    @Test
    void r42UndoRedoReplaysWorldSaveGuidanceMutations() {
        ComponentStackKey cobble = item("minecraft:cobblestone");
        ComponentStackKey gear = item("mod:world_gear");
        CompiledRecipeGraph graph = compile(
            List.of(
                rec(
                    "minecraft:crafting",
                    "world_gear",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(cobble, 1)),
                    List.of(out(gear, 1))
                )
            )
        );

        List<GuidanceRule> persisted = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        LayeredHumanGuidanceStore store = new LayeredHumanGuidanceStore(persisted::add, removed::add);
        DefaultPlanningService service = new DefaultPlanningService(
            graph,
            DefaultCostMetadataProvider.instance,
            store,
            null
        );
        PlanningRequest planningRequest = request(
            graph,
            List.of(new TargetDemand(gear, 1L)),
            InventorySnapshot.empty,
            fac(ownedCraftingTable()),
            DefaultHumanGuidanceView.empty(),
            PlanningProfile.defaultBalanced(),
            generousBudget()
        );
        CostOverrideRule override = new CostOverrideRule(
            "world:override_cobble",
            GuidanceScope.session,
            RuleStrength.hardPin,
            cobble,
            new CostVector(0.5, 0.0, 0.0, 0.0, 0.0)
        );

        try (PlanningSession session = service.openSession(planningRequest)) {
            PlanningResult initial = session.currentResult();
            assertFalse(initial.paretoPlans().isEmpty());
            assertEquals(1L, initial.paretoPlans().get(0).preparation().toAcquireFromWorld().amountOf(cobble));
            PlanningResult refined = session.refine(new ApplyRulesFeedback(List.of(override), true));
            assertFalse(refined.paretoPlans().isEmpty());
            assertFalse(refined.paretoPlans().get(0).totalCost().equals(initial.paretoPlans().get(0).totalCost()));

            assertEquals(1, persisted.size());
            assertEquals("world:override_cobble", persisted.get(0).ruleId());
            assertEquals(GuidanceScope.worldSave, persisted.get(0).scope());
            assertTrue(removed.isEmpty());
            assertEquals(
                List.of("world:override_cobble"),
                store.rulesInScope(GuidanceScope.worldSave).stream().map(GuidanceRule::ruleId).toList()
            );

            PlanningResult undone = session.undo();
            assertEquals(initial.paretoPlans(), undone.paretoPlans());
            assertEquals(List.of("world:override_cobble"), removed);
            assertTrue(store.rulesInScope(GuidanceScope.worldSave).isEmpty());

            PlanningResult redone = session.redo();
            assertEquals(refined.paretoPlans(), redone.paretoPlans());
            assertEquals(2, persisted.size());
            assertEquals(persisted.get(0), persisted.get(1));
            assertEquals(1, store.rulesInScope(GuidanceScope.worldSave).size());
            assertEquals("world:override_cobble", store.rulesInScope(GuidanceScope.worldSave).get(0).ruleId());
        }
    }

    @Test
    void r7aCumulativeCatalystDurabilityAcrossCraftSteps() {
        ComponentStackKey cobble = item("minecraft:cobblestone");
        ComponentStackKey plate = item("mod:plate");
        ComponentStackKey hammer = item("mod:hammer");
        CompiledRecipeGraph graph = compile(
            List.of(
                rec(
                    "minecraft:crafting",
                    "plate_single",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(cobble, 1), durabilityCatalyst(hammer, 1, 10)),
                    List.of(out(plate, 1))
                ),
                rec(
                    "minecraft:crafting",
                    "plate_bulk",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(cobble, 1), durabilityCatalyst(hammer, 1, 10)),
                    List.of(out(plate, 1))
                )
            )
        );
        DefaultPlanVerifier verifier = new DefaultPlanVerifier(graph);
        ProductionPlan plan = twoStepDurabilityPlan(graph, cobble, plate, hammer);

        VerificationCertificate stockOne = verifier.verify(
            plan,
            durabilityRequest(graph, plate, 11L, Map.of(cobble, 11L, hammer, 1L))
        );
        assertFalse(stockOne.valid());
        assertEquals(1, stockOne.violations().size());
        assertTrue(stockOne.violations().stream().anyMatch(v -> v.startsWith(ErrorCodes.errMissingCatalyst)));

        VerificationCertificate stockTwo = verifier.verify(
            plan,
            durabilityRequest(graph, plate, 11L, Map.of(cobble, 11L, hammer, 2L))
        );
        assertTrue(stockTwo.valid());

        List<PlanStep> bulkOnlySteps = List.of(
            craftStep("bulk", recipeByPath(graph, plate, "plate_bulk"), 10L, "minecraft:crafting_table")
        );
        ProductionPlan bulkOnly = new ProductionPlan(
            "p_bulk_only",
            "test",
            bulkOnlySteps,
            new PreparationSummary(
                new StockLedger(Map.of(cobble, 10L)),
                StockLedger.empty,
                Map.of(),
                List.of(),
                List.of(hammer)
            ),
            List.of(),
            PlanCosts.aggregate(bulkOnlySteps),
            dummyCert
        );
        assertTrue(
            verifier.verify(bulkOnly, durabilityRequest(graph, plate, 10L, Map.of(cobble, 10L, hammer, 1L))).valid()
        );
    }

    @Test
    void r7aCycleLoopIterationsAmplifyCatalystDurability() {
        ComponentStackKey seed = item("mod:cycle_seed");
        ComponentStackKey carrier = item("mod:cycle_carrier");
        ComponentStackKey reagent = item("mod:cycle_reagent");
        ComponentStackKey product = item("mod:cycle_product");
        ComponentStackKey file = item("mod:durability_file");
        CompiledRecipeGraph graph = compile(
            List.of(
                rec(
                    "mod:cycle",
                    "drain",
                    List.of("mod:cycle_vat"),
                    List.of(slot(carrier, 1), slot(reagent, 1), durabilityCatalyst(file, 1, 10)),
                    List.of(out(seed, 1), out(product, 1))
                ),
                rec(
                    "mod:cycle",
                    "regen",
                    List.of("mod:cycle_vat"),
                    List.of(slot(seed, 1)),
                    List.of(out(carrier, 1))
                )
            )
        );
        assertFalse(graph.cycleMacrosProducing(product).isEmpty());
        CompiledCycleMacro macro = graph.cycleMacrosProducing(product).get(0);
        DefaultPlanVerifier verifier = new DefaultPlanVerifier(graph);

        VerificationCertificate tenIterations = verifier.verify(
            cyclePlan(macro, 10L, reagent, file),
            cycleRequest(graph, macro, product, 10L, reagent)
        );
        assertTrue(tenIterations.valid());

        VerificationCertificate elevenIterations = verifier.verify(
            cyclePlan(macro, 11L, reagent, file),
            cycleRequest(graph, macro, product, 11L, reagent)
        );
        assertFalse(elevenIterations.valid());
        assertEquals(2, elevenIterations.violations().size());
        assertTrue(elevenIterations.violations().contains(ErrorCodes.errPreparationMismatch));
        assertTrue(
            elevenIterations.violations().stream().anyMatch(v -> v.startsWith(ErrorCodes.errMissingCatalyst))
        );
    }

    @Test
    void r7bTripSecondsTamperFailsCostDimensionOnly() {
        ComponentStackKey cobble = item("minecraft:cobblestone");
        ComponentStackKey plate = item("mod:plate");
        ComponentStackKey hammer = item("mod:hammer");
        CompiledRecipeGraph graph = compile(
            List.of(
                rec(
                    "minecraft:crafting",
                    "plate_single",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(cobble, 1), durabilityCatalyst(hammer, 1, 10)),
                    List.of(out(plate, 1))
                ),
                rec(
                    "minecraft:crafting",
                    "plate_bulk",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(cobble, 1), durabilityCatalyst(hammer, 1, 10)),
                    List.of(out(plate, 1))
                )
            )
        );
        DefaultPlanVerifier verifier = new DefaultPlanVerifier(graph);
        ProductionPlan plan = twoStepDurabilityPlan(graph, cobble, plate, hammer);
        PlanningRequest planningRequest = durabilityRequest(
            graph,
            plate,
            11L,
            Map.of(cobble, 11L, hammer, 2L)
        );

        assertTrue(verifier.verify(plan, planningRequest).valid());

        CostVector validCost = plan.totalCost();
        CostVector tamperedCost = new CostVector(
            validCost.humanActiveSeconds(),
            0.25,
            validCost.passiveWaitSeconds(),
            validCost.machineSetupCapital(),
            validCost.materialScarcity(),
            validCost.cognitiveFriction()
        );
        ProductionPlan tampered = new ProductionPlan(
            plan.planId(),
            plan.strategyLabelKey(),
            plan.steps(),
            plan.preparation(),
            plan.surplusByproducts(),
            tamperedCost,
            plan.certificate()
        );
        VerificationCertificate tamperedCertificate = verifier.verify(tampered, planningRequest);
        assertFalse(tamperedCertificate.valid());
        assertEquals(List.of(ErrorCodes.errCostMismatch + ":total"), tamperedCertificate.violations());
    }

    private static PlanningRequest currentRequest(PlanningSession session) {
        // Session state is private; Reflect is the repository's sanctioned field entry point.
        Object state = Reflect.field(session, "current", Object.class);
        return Reflect.field(state, "request", PlanningRequest.class);
    }

    private static CompiledRecipe recipeByPath(CompiledRecipeGraph graph, ComponentStackKey output, String recipePath) {
        return graph.producersOf(output)
            .stream()
            .filter(recipe -> recipe.key().explicitRecipeId().map(id -> id.getPath().equals(recipePath)).orElse(false))
            .findFirst()
            .orElseThrow();
    }

    private static CraftStep craftStep(
        String stepId,
        CompiledRecipe recipe,
        long batches,
        String machineId
    ) {
        Map<Integer, SlotBinding> bindings = new TreeMap<>();
        for (InputSlot input : recipe.inputs()) {
            bindings.put(input.slotIndex(), new SlotBinding(input.candidates().get(0), List.of()));
        }
        Map<Integer, ComponentStackKey> catalysts = new TreeMap<>();
        for (CatalystSlot catalyst : recipe.catalysts()) {
            catalysts.put(catalyst.slotIndex(), catalyst.candidates().get(0));
        }
        return new CraftStep(
            stepId,
            List.of(),
            recipe.key(),
            batches,
            Optional.of(machine(machineId)),
            false,
            bindings,
            catalysts,
            StepCosts.freeBaseCost
        );
    }

    private static ProductionPlan twoStepDurabilityPlan(
        CompiledRecipeGraph graph,
        ComponentStackKey cobble,
        ComponentStackKey plate,
        ComponentStackKey hammer
    ) {
        List<PlanStep> steps = List.of(
            craftStep("single", recipeByPath(graph, plate, "plate_single"), 1L, "minecraft:crafting_table"),
            craftStep("bulk", recipeByPath(graph, plate, "plate_bulk"), 10L, "minecraft:crafting_table")
        );
        PreparationSummary preparation = new PreparationSummary(
            new StockLedger(Map.of(cobble, 11L)),
            StockLedger.empty,
            Map.of(),
            List.of(),
            List.of(hammer)
        );
        return new ProductionPlan(
            "p_durability",
            "test",
            steps,
            preparation,
            List.of(),
            PlanCosts.aggregate(steps),
            dummyCert
        );
    }

    private static PlanningRequest durabilityRequest(
        CompiledRecipeGraph graph,
        ComponentStackKey target,
        long amount,
        Map<ComponentStackKey, Long> stock
    ) {
        InventorySnapshot inventory = new InventorySnapshot(new StockLedger(stock), StockLedger.empty);
        return request(
            graph,
            List.of(new TargetDemand(target, amount)),
            inventory,
            fac(ownedCraftingTable()),
            DefaultHumanGuidanceView.empty(),
            PlanningProfile.defaultBalanced(),
            generousBudget()
        );
    }

    private static ProductionPlan cyclePlan(
        CompiledCycleMacro macro,
        long iterations,
        ComponentStackKey reagent,
        ComponentStackKey catalyst
    ) {
        List<CraftStep> template = new ArrayList<>(macro.innerRecipes().size());
        for (int i = 0; i < macro.innerRecipes().size(); i++) {
            // Macro order and multipliers are authoritative: the verifier matches the template positionally.
            template.add(
                craftStep(
                    "inner_" + i,
                    macro.innerRecipes().get(i),
                    macro.innerMultipliers().get(i),
                    "mod:cycle_vat"
                )
            );
        }
        CycleLoopStep loop = new CycleLoopStep(
            "loop",
            List.of(),
            macro.macroKey(),
            iterations,
            macro.workingSeeds(),
            template,
            macro.netDeltaPerIter(),
            StepCosts.freeBaseCost
        );
        List<PlanStep> steps = List.of(loop);
        PreparationSummary preparation = new PreparationSummary(
            new StockLedger(Map.of(reagent, iterations)),
            StockLedger.empty,
            Map.of(),
            List.of(),
            List.of(catalyst)
        );
        return new ProductionPlan(
            "p_cycle_" + iterations,
            "test",
            steps,
            preparation,
            List.of(),
            PlanCosts.aggregate(steps),
            dummyCert
        );
    }

    private static PlanningRequest cycleRequest(
        CompiledRecipeGraph graph,
        CompiledCycleMacro macro,
        ComponentStackKey product,
        long iterations,
        ComponentStackKey reagent
    ) {
        Map<ComponentStackKey, Long> stock = new TreeMap<>(macro.workingSeeds());
        stock.put(reagent, iterations);
        InventorySnapshot inventory = new InventorySnapshot(new StockLedger(stock), StockLedger.empty);
        return request(
            graph,
            List.of(new TargetDemand(product, iterations)),
            inventory,
            fac(owned("mod:cycle_vat", capability("mod:cycle"), 1)),
            DefaultHumanGuidanceView.empty(),
            PlanningProfile.defaultBalanced(),
            generousBudget()
        );
    }
}
