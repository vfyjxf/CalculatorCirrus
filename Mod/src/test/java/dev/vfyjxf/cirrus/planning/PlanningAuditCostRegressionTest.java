package dev.vfyjxf.cirrus.planning;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOption;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
import dev.vfyjxf.cirrus.api.planning.context.BaseBoundarySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.BaseContract;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.cost.SystemCostTier;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.RawOutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.api.planning.tag.CanonicalRecipeTag;
import dev.vfyjxf.cirrus.api.planning.tag.RecipeEvidenceTier;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystemTags;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultAcquisitionOracle;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultCostMetadataProvider;
import dev.vfyjxf.cirrus.planning.core.cost.StepCosts;
import dev.vfyjxf.cirrus.planning.core.cost.SystemCostTiers;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import dev.vfyjxf.cirrus.planning.core.service.DefaultPlanningService;
import dev.vfyjxf.cirrus.planning.normalize.DualTagClassifier;
import dev.vfyjxf.cirrus.planning.normalize.JeiRecipeNormalizer;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.compile;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.fac;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.generousBudget;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.item;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.machine;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.machines;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.ns;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.out;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.ownedCraftingTable;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.rec;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the three audit repair points in planning cost & classification:
 * tag-driven system cost tiers, reverse-partner-gated packing classification, and
 * non-double-counted base-contract delivery pricing with a live capacity limit.
 */
class PlanningAuditCostRegressionTest {

    private static final double eps = 1e-9;

    private static final ProgressionState progression = ProgressionState.overworldDefault;

    private record TierSample(
        String label,
        String systemId,
        String sourceKind,
        String machineId,
        Set<Namespace> systemTags,
        SystemCostTier tier,
        double humanSecondsPerBatch,
        double passiveSecondsPerBatch,
        double machineSetupCapital
    ) {}

    private static final List<TierSample> tierSamples = List.of(
        new TierSample(
            "t0 hand workshop",
            "minecraft:crafting",
            "vanilla",
            "minecraft:crafting_table",
            Set.of(RecipeSystemTags.crafting, RecipeSystemTags.vanilla, RecipeSystemTags.handOperable),
            SystemCostTier.t0HandWorkshop,
            3.0,
            0.0,
            2.0
        ),
        new TierSample(
            "t1 basic static machine",
            "minecraft:smelting",
            "vanilla",
            "minecraft:furnace",
            Set.of(RecipeSystemTags.cooking, RecipeSystemTags.vanilla, RecipeSystemTags.automatable),
            SystemCostTier.t1BasicStaticMachine,
            2.0,
            10.0,
            15.0
        ),
        new TierSample(
            "t2 mid tech single machine",
            "bigmod:assembler",
            "mod_native",
            "bigmod:assembler",
            Set.of(RecipeSystemTags.assembly, RecipeSystemTags.modNative),
            SystemCostTier.t2MidTechSingleMachine,
            1.5,
            6.0,
            60.0
        ),
        new TierSample(
            "t3 automated factory line",
            "bigmod:factory",
            "mod_native",
            "bigmod:factory_controller",
            Set.of(RecipeSystemTags.assembly, RecipeSystemTags.automatable),
            SystemCostTier.t3AutomatedFactoryLine,
            1.0,
            4.0,
            180.0
        ),
        new TierSample(
            "t4 heavy multiblock",
            "bigmod:fusion_reactor",
            "mod_native",
            "bigmod:fusion_core",
            Set.of(RecipeSystemTags.oreProcessing, RecipeSystemTags.heavyMultiblock),
            SystemCostTier.t4HeavySpecialized,
            5.0,
            20.0,
            500.0
        )
    );

    @Test
    void r1_recipeSystemTagsDriveTierAndCostFields() {
        List<RawRecipeRecord> records = new ArrayList<>(tierSamples.size());
        for (int i = 0; i < tierSamples.size(); i++) {
            TierSample sample = tierSamples.get(i);
            records.add(
                taggedRecipe(
                    sample.systemId(),
                    sample.sourceKind(),
                    "audit:tier_" + i,
                    sample.machineId(),
                    sample.systemTags(),
                    item("test:tier_" + i + "_in"),
                    item("test:tier_" + i + "_out")
                )
            );
        }
        CompiledRecipeGraph graph = compile(records);
        assertEquals(tierSamples.size(), graph.stats().compiledRecipes());

        TreeSet<Double> distinctCapitals = new TreeSet<>();
        for (int i = 0; i < tierSamples.size(); i++) {
            TierSample sample = tierSamples.get(i);
            CompiledRecipe recipe = graph.producersOf(item("test:tier_" + i + "_out")).get(0);
            assertEquals(sample.systemTags(), recipe.systemTags(), sample.label() + ": tags must survive compilation");
            assertEquals(sample.tier(), SystemCostTiers.tierOfSystemTags(recipe.systemTags()), sample.label());
            assertEquals(
                sample.tier(),
                DefaultCostMetadataProvider.instance.tierOfSystem(recipe.key().systemId(), recipe.systemTags()),
                sample.label()
            );

            MachineEntry usedMachine = recipe.machineRequirement().compatibleMachines().get(0);
            CostVector craft = StepCosts.craftCost(
                recipe,
                1L,
                Optional.of(usedMachine),
                false,
                FacilitySnapshot.empty,
                DefaultCostMetadataProvider.instance
            );
            assertEquals(sample.humanSecondsPerBatch(), craft.humanActiveSeconds(), eps, sample.label());
            assertEquals(sample.passiveSecondsPerBatch(), craft.passiveWaitSeconds(), eps, sample.label());

            CostVector provision = StepCosts.provisionMachineCost(
                usedMachine,
                recipe.key().systemId(),
                recipe.systemTags(),
                1,
                DefaultCostMetadataProvider.instance
            );
            assertEquals(sample.machineSetupCapital(), provision.machineSetupCapital(), eps, sample.label());
            distinctCapitals.add(provision.machineSetupCapital());
        }

        assertEquals(tierSamples.size(), distinctCapitals.size(), "every audited system must land on a different capital tier");
        assertTrue(
            tierSamples.get(0).machineSetupCapital() < tierSamples.get(1).machineSetupCapital()
                && tierSamples.get(1).machineSetupCapital() < tierSamples.get(2).machineSetupCapital()
                && tierSamples.get(2).machineSetupCapital() < tierSamples.get(3).machineSetupCapital()
                && tierSamples.get(3).machineSetupCapital() < tierSamples.get(4).machineSetupCapital()
        );
    }

    @Test
    void r1_tierIsStableForSameSystemAndIgnoresSystemId() {
        Set<Namespace> vanillaCraftingTags = Set.of(
            RecipeSystemTags.crafting,
            RecipeSystemTags.vanilla,
            RecipeSystemTags.handOperable
        );
        DefaultCostMetadataProvider provider = DefaultCostMetadataProvider.instance;

        SystemCostTier crafting = provider.tierOfSystem(ns("minecraft:crafting"), vanillaCraftingTags);
        assertEquals(SystemCostTier.t0HandWorkshop, crafting);
        assertEquals(crafting, provider.tierOfSystem(ns("minecraft:smelting"), vanillaCraftingTags));
        assertEquals(crafting, provider.tierOfSystem(ns("bigmod:unrelated_machine"), vanillaCraftingTags));
        assertEquals(
            provider.estimateMachineSetupCapital(machine("minecraft:crafting_table"), ns("minecraft:crafting"), vanillaCraftingTags),
            provider.estimateMachineSetupCapital(machine("bigmod:unrelated_machine"), ns("bigmod:unrelated_machine"), vanillaCraftingTags),
            eps
        );
        assertEquals(
            provider.estimateHumanSecondsPerBatch(ns("minecraft:smelting"), vanillaCraftingTags, Optional.empty(), false),
            provider.estimateHumanSecondsPerBatch(ns("bigmod:unrelated_machine"), vanillaCraftingTags, Optional.empty(), false),
            eps
        );

        assertEquals(SystemCostTier.t2MidTechSingleMachine, provider.tierOfSystem(ns("minecraft:crafting"), Set.of()));
        assertEquals(
            SystemCostTier.t1BasicStaticMachine,
            provider.tierOfSystem(ns("minecraft:crafting"), Set.of(RecipeSystemTags.vanilla))
        );
        assertEquals(
            SystemCostTier.t2MidTechSingleMachine,
            SystemCostTiers.tierOfSystemTags(Set.of(RecipeSystemTags.crafting)),
            "a process-domain tag alone must not move a system off the T2 default"
        );
        assertEquals(
            SystemCostTier.t3AutomatedFactoryLine,
            SystemCostTiers.tierOfSystemTags(Set.of(RecipeSystemTags.automatable))
        );
        assertEquals(SystemCostTier.t4HeavySpecialized, SystemCostTiers.tierOfSystemTags(Set.of(RecipeSystemTags.optIn)));

        assertEquals(0, SystemCostTiers.systemPriority(Set.of(RecipeSystemTags.handOperable, RecipeSystemTags.vanilla)));
        assertEquals(1, SystemCostTiers.systemPriority(Set.of(RecipeSystemTags.vanilla)));
        assertEquals(2, SystemCostTiers.systemPriority(Set.of(RecipeSystemTags.handOperable)));
        assertEquals(3, SystemCostTiers.systemPriority(Set.of(RecipeSystemTags.crafting)));
    }

    @Test
    void r2_oneWayLogToPlanksStaysNormalProductionNotPacking() {
        ComponentStackKey log = item("minecraft:oak_log");
        ComponentStackKey planks = item("minecraft:oak_planks");
        RawRecipeRecord logToPlanks = rec(
            "minecraft:crafting",
            "minecraft:oak_planks",
            List.of("minecraft:crafting_table"),
            List.of(slot(log, 1L)),
            List.of(out(planks, 4L))
        );

        Set<CanonicalRecipeTag> withoutReversePartner = DualTagClassifier.tagSet(
            DualTagClassifier.classify(logToPlanks, false, false, false)
        );
        assertEquals(Set.of(CanonicalRecipeTag.normalProduction), withoutReversePartner);
        assertEquals(
            Set.of(CanonicalRecipeTag.normalProduction),
            DualTagClassifier.tagSet(DualTagClassifier.classify(logToPlanks, false, false)),
            "the default classification path must not resurrect the structural-only packing guess"
        );

        ComponentStackKey ingot = item("minecraft:iron_ingot");
        ComponentStackKey block = item("minecraft:iron_block");
        RawRecipeRecord ingotsToBlock = rec(
            "minecraft:crafting",
            "minecraft:iron_block",
            List.of("minecraft:crafting_table"),
            List.of(slot(ingot, 9L)),
            List.of(out(block, 1L))
        );
        assertEquals(
            Set.of(CanonicalRecipeTag.normalProduction),
            DualTagClassifier.tagSet(DualTagClassifier.classify(ingotsToBlock, false, false, false)),
            "a 9:1 shape alone is not packing until the reverse recipe exists"
        );

        JeiRecipeNormalizer.NormalizationResult normalized = JeiRecipeNormalizer.normalize(
            List.of(logToPlanks),
            List.of(),
            List.of()
        );
        assertEquals(
            Set.of(CanonicalRecipeTag.normalProduction),
            canonicalTagsProducing(normalized, planks),
            "normalizer must keep the one-way recipe as normal production"
        );

        CompiledRecipeGraph graph = compile(normalized.normalizedRecords());
        assertEquals(1, graph.producersOf(planks).size());
        DefaultAcquisitionOracle oracle = new DefaultAcquisitionOracle(graph);
        assertTrue(oracle.assess(planks, 4L, progression).isEmpty(), "a produced resource must not fall back to manual leaf gathering");
        assertEquals(AcquisitionKind.manualGather, oracle.assess(log, 1L, progression).orElseThrow().kind());

        PlanningResult result = planWithBoundary(
            graph,
            BaseBoundarySnapshot.empty,
            planks,
            4L,
            fac(ownedCraftingTable())
        );
        assertEquals(PlanningStatus.feasible, result.status());
        ProductionPlan plan = result.paretoPlans().get(0);
        CompiledRecipe planksRecipe = graph.producersOf(planks).get(0);
        assertTrue(hasCraftOf(plan, planksRecipe), "planks must be crafted from the log, not gathered");
        assertEquals(AcquisitionKind.manualGather, singleAcquireOf(plan, log).acquisitionKind());
        assertEquals(0, acquiresOf(plan, planks).size(), "planks must never be acquired as a leaf");
    }

    @Test
    void r2_reversePairIsClassifiedPackingUnpacking() {
        ComponentStackKey log = item("minecraft:oak_log");
        ComponentStackKey planks = item("minecraft:oak_planks");
        ComponentStackKey ingot = item("minecraft:iron_ingot");
        ComponentStackKey block = item("minecraft:iron_block");
        ComponentStackKey shard = item("test:raw_shard");
        ComponentStackKey shardBlock = item("test:shard_block");

        RawRecipeRecord logToPlanks = rec(
            "minecraft:crafting",
            "minecraft:oak_planks",
            List.of("minecraft:crafting_table"),
            List.of(slot(log, 1L)),
            List.of(out(planks, 4L))
        );
        RawRecipeRecord planksToLog = rec(
            "minecraft:crafting",
            "audit:oak_log_from_planks",
            List.of("minecraft:crafting_table"),
            List.of(slot(planks, 4L)),
            List.of(out(log, 1L))
        );
        JeiRecipeNormalizer.NormalizationResult planksPair = JeiRecipeNormalizer.normalize(
            List.of(logToPlanks, planksToLog),
            List.of(),
            List.of()
        );
        assertEquals(Set.of(CanonicalRecipeTag.packingUnpacking), canonicalTagsProducing(planksPair, planks));
        assertEquals(Set.of(CanonicalRecipeTag.packingUnpacking), canonicalTagsProducing(planksPair, log));

        RawRecipeRecord ingotsToBlock = rec(
            "minecraft:crafting",
            "minecraft:iron_block",
            List.of("minecraft:crafting_table"),
            List.of(slot(ingot, 9L)),
            List.of(out(block, 1L))
        );
        RawRecipeRecord blockToIngots = rec(
            "minecraft:crafting",
            "audit:iron_ingot_from_block",
            List.of("minecraft:crafting_table"),
            List.of(slot(block, 1L)),
            List.of(out(ingot, 9L))
        );
        JeiRecipeNormalizer.NormalizationResult ironPair = JeiRecipeNormalizer.normalize(
            List.of(ingotsToBlock, blockToIngots),
            List.of(),
            List.of()
        );
        assertEquals(Set.of(CanonicalRecipeTag.packingUnpacking), canonicalTagsProducing(ironPair, block));
        assertEquals(Set.of(CanonicalRecipeTag.packingUnpacking), canonicalTagsProducing(ironPair, ingot));
        assertFalse(canonicalTagsProducing(ironPair, block).contains(CanonicalRecipeTag.normalProduction));

        RawRecipeRecord shardsToBlock = rec(
            "bigmod:compressor",
            "audit:shard_block",
            List.of("bigmod:compressor"),
            List.of(slot(shard, 4L)),
            List.of(out(shardBlock, 1L))
        );
        RawRecipeRecord blockToShards = rec(
            "bigmod:decompressor",
            "audit:raw_shard",
            List.of("bigmod:decompressor"),
            List.of(slot(shardBlock, 1L)),
            List.of(out(shard, 4L))
        );
        JeiRecipeNormalizer.NormalizationResult syntheticPair = JeiRecipeNormalizer.normalize(
            List.of(shardsToBlock, blockToShards),
            List.of(),
            List.of()
        );
        assertEquals(
            Set.of(CanonicalRecipeTag.packingUnpacking),
            canonicalTagsProducing(syntheticPair, shardBlock),
            "packing detection must come from catalog topology, not from item wordlists"
        );
        assertEquals(Set.of(CanonicalRecipeTag.packingUnpacking), canonicalTagsProducing(syntheticPair, shard));
    }

    @Test
    void r3_baseContractAmortizesFixedDeliveryOnce() {
        ComponentStackKey cobble = item("test:quarry_cobble");
        BaseContract contract = new BaseContract(cobble, 2.0, 1.0, 0.5, 10.0, 3L, false, "audit:quarry");

        assertEquals(2.0, contract.baseUnitSeconds(), eps);
        assertEquals(12.5, contract.unitPriceForDemand(1L), eps);
        assertEquals(7.5, contract.unitPriceForDemand(2L), eps);
        assertEquals(17.5 / 3.0, contract.unitPriceForDemand(3L), eps);
        assertEquals(3.75, contract.unitPriceForDemand(8L), eps);
        for (long demand = 1L; demand <= 8L; demand++) {
            assertEquals(10.0 + demand * 2.5, contract.unitPriceForDemand(demand) * demand, eps, "demand=" + demand);
        }
        assertTrue(contract.unitPriceForDemand(1L) > contract.unitPriceForDemand(2L));
        assertTrue(contract.unitPriceForDemand(2L) > contract.unitPriceForDemand(3L));
        assertTrue(contract.unitPriceForDemand(3L) > contract.unitPriceForDemand(8L));

        BaseContract floorOnly = new BaseContract(item("test:floor"), 0.1, 1.5, 0.0, 0.0, 5L, false, "audit:floor");
        assertEquals(1.5, floorOnly.baseUnitSeconds(), eps);
        BaseContract free = new BaseContract(item("test:free"), 9.0, 1.0, 0.5, 10.0, 5L, true, "audit:free");
        assertEquals(0.0, free.baseUnitSeconds(), eps);
        assertEquals(0.0, free.unitPriceForDemand(4L), eps);

        CompiledRecipeGraph graph = compile(List.of());
        DefaultAcquisitionOracle oracle = new DefaultAcquisitionOracle(
            graph,
            boundaryWith(contract),
            DefaultHumanGuidanceView.empty()
        );
        AcquisitionOption option = oracle.assess(cobble, 5L, progression).orElseThrow();
        assertEquals(AcquisitionKind.baseSupply, option.kind());
        assertEquals(2.0, option.humanSecondsPerUnit(), eps, "fixed delivery must not be baked into the per-unit price");
        assertEquals(10.0, option.fixedTripHumanSeconds(), eps);
        assertEquals(0.5, option.scarcityPerUnit(), eps);
        assertEquals(3L, option.guaranteedQuantity());
        assertEquals(DefaultAcquisitionOracle.keyBase, option.translationKey());

        for (long quantity = 1L; quantity <= 3L; quantity++) {
            CostVector delivered = StepCosts.acquireCost(option, quantity, false);
            assertEquals(10.0 + quantity * 2.0, delivered.humanActiveSeconds(), eps, "quantity=" + quantity);
            assertEquals(quantity * 0.5, delivered.materialScarcity(), eps, "quantity=" + quantity);
            assertEquals(
                contract.unitPriceForDemand(quantity) * quantity,
                delivered.humanActiveSeconds() + delivered.materialScarcity(),
                eps,
                "quantity=" + quantity
            );
            CostVector unitEstimate = StepCosts.acquireUnitEstimate(option, quantity, false);
            assertEquals(
                contract.unitPriceForDemand(quantity),
                unitEstimate.humanActiveSeconds() + unitEstimate.materialScarcity(),
                eps,
                "quantity=" + quantity
            );
        }
    }

    @Test
    void r3_capacityLimitClampsAcquisitionEndToEnd() {
        ComponentStackKey cobble = item("test:quarry_cobble");
        BaseContract contract = new BaseContract(cobble, 2.0, 1.0, 0.5, 10.0, 3L, false, "audit:quarry");
        BaseBoundarySnapshot boundary = boundaryWith(contract);
        CompiledRecipeGraph graph = compile(List.of());

        PlanningResult withinCapacity = planWithBoundary(graph, boundary, cobble, 3L, FacilitySnapshot.empty);
        assertEquals(PlanningStatus.feasible, withinCapacity.status());
        ProductionPlan plan = withinCapacity.paretoPlans().get(0);
        assertTrue(plan.certificate().valid());
        AcquireStep acquire = singleAcquireOf(plan, cobble);
        assertEquals(3L, acquire.amount());
        assertEquals(AcquisitionKind.baseSupply, acquire.acquisitionKind());
        assertEquals(16.0, plan.totalCost().humanActiveSeconds(), eps);
        assertEquals(1.5, plan.totalCost().materialScarcity(), eps);
        assertEquals(0.0, plan.totalCost().tripActiveSeconds(), eps);
        assertEquals(
            3L * contract.unitPriceForDemand(3L),
            plan.totalCost().humanActiveSeconds() + plan.totalCost().materialScarcity(),
            eps
        );

        DefaultAcquisitionOracle oracle = new DefaultAcquisitionOracle(
            graph,
            boundary,
            DefaultHumanGuidanceView.empty()
        );
        AcquisitionOption cappedOption = oracle.assess(cobble, 5L, progression).orElseThrow();
        assertEquals(3L, cappedOption.guaranteedQuantity(), "demand beyond capacity must still expose the capped option");

        PlanningResult beyondCapacity = planWithBoundary(graph, boundary, cobble, 5L, FacilitySnapshot.empty);
        assertFalse(beyondCapacity.status().hasExecutablePlan());
        assertTrue(beyondCapacity.paretoPlans().isEmpty());
    }

    private static RawRecipeRecord taggedRecipe(
        String systemId,
        String sourceKind,
        String explicitId,
        String machineId,
        Set<Namespace> systemTags,
        ComponentStackKey input,
        ComponentStackKey output
    ) {
        return new RawRecipeRecord(
            ns(systemId),
            sourceKind,
            Optional.of(ResourceLocation.parse(explicitId)),
            machines(machineId),
            List.of(slot(input, 1L)),
            List.of(out(output, 1L)),
            Map.of(),
            1L,
            systemTags,
            Set.of(CanonicalRecipeTag.normalProduction),
            RecipeEvidenceTier.tierA,
            1.0,
            Optional.empty(),
            false
        );
    }

    private static BaseBoundarySnapshot boundaryWith(BaseContract... contracts) {
        Map<ComponentStackKey, BaseContract> byResource = new HashMap<>();
        for (BaseContract contract : contracts) {
            byResource.put(contract.resource(), contract);
        }
        return new BaseBoundarySnapshot(byResource, Set.of(), Set.of(), Set.of(), List.of());
    }

    private static PlanningResult planWithBoundary(
        CompiledRecipeGraph graph,
        BaseBoundarySnapshot boundary,
        ComponentStackKey target,
        long amount,
        FacilitySnapshot facilities
    ) {
        HumanGuidanceView guidance = DefaultHumanGuidanceView.empty();
        AcquisitionOracle oracle = new DefaultAcquisitionOracle(graph, boundary, guidance);
        PlanningContext context = new PlanningContext(
            InventorySnapshot.empty,
            facilities,
            oracle,
            guidance,
            ProgressionState.overworldDefault
        );
        PlanningRequest request = new PlanningRequest(
            List.of(new TargetDemand(target, amount)),
            context,
            PlanningProfile.defaultBalanced(),
            generousBudget()
        );
        return new DefaultPlanningService(graph).planOnce(request);
    }

    private static Set<CanonicalRecipeTag> canonicalTagsProducing(
        JeiRecipeNormalizer.NormalizationResult result,
        ComponentStackKey output
    ) {
        for (RawRecipeRecord record : result.normalizedRecords()) {
            for (RawOutputSlot slot : record.outputs()) {
                if (slot.resource().equals(output)) {
                    return record.canonicalTags();
                }
            }
        }
        throw new AssertionError("no normalized record produces " + output.displayId());
    }

    private static List<AcquireStep> acquiresOf(ProductionPlan plan, ComponentStackKey resource) {
        List<AcquireStep> out = new ArrayList<>();
        for (PlanStep step : plan.steps()) {
            if (step instanceof AcquireStep acquire && acquire.resource().equals(resource)) {
                out.add(acquire);
            }
        }
        return out;
    }

    private static AcquireStep singleAcquireOf(ProductionPlan plan, ComponentStackKey resource) {
        List<AcquireStep> steps = acquiresOf(plan, resource);
        assertEquals(1, steps.size(), "expected exactly one acquire step for " + resource.displayId());
        return steps.get(0);
    }

    private static boolean hasCraftOf(ProductionPlan plan, CompiledRecipe recipe) {
        for (PlanStep step : plan.steps()) {
            if (step instanceof CraftStep craft && craft.recipeKey().equals(recipe.key())) {
                return true;
            }
        }
        return false;
    }
}
