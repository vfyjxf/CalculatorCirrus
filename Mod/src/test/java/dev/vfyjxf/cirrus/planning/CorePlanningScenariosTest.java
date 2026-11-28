package dev.vfyjxf.cirrus.planning;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOption;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
import dev.vfyjxf.cirrus.api.planning.context.AutomatedLine;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.CostWeights;
import dev.vfyjxf.cirrus.api.planning.cost.ObjectivePreset;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeAdvisory;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.SystemRule;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.planning.core.graph.DefaultSystemAdmissionPolicy;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import dev.vfyjxf.cirrus.planning.core.service.DefaultPlanningService;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.capability;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.compile;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.fac;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.generousBudget;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.item;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.machine;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.machines;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.out;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.owned;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.ownedCraftingTable;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.ownedFurnace;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.ownedStonecutter;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.plan;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.rec;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.request;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.slot;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.tagSlot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorePlanningScenariosTest {

    @Test
    void tc04_reversiblePackingConvertFromStockNoRoundTripAndDirectBlockCraft() {
        ComponentStackKey ingot = item("minecraft:iron_ingot");
        ComponentStackKey block = item("minecraft:iron_block");
        ComponentStackKey rawIron = item("minecraft:raw_iron");
        CompiledRecipeGraph g = compile(
            List.of(
                rec(
                    "minecraft:crafting",
                    "pack",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(ingot, 9)),
                    List.of(out(block, 1))
                ),
                rec(
                    "minecraft:crafting",
                    "unpack",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(block, 1)),
                    List.of(out(ingot, 9))
                ),
                rec(
                    "minecraft:smelting",
                    "smelt",
                    List.of("minecraft:furnace"),
                    List.of(slot(rawIron, 1)),
                    List.of(out(ingot, 1))
                )
            )
        );
        FacilitySnapshot f = fac(ownedCraftingTable(), ownedFurnace());

        InventorySnapshot stocked = new InventorySnapshot(new StockLedger(Map.of(block, 16L)), StockLedger.empty);
        PlanningResult fromStock = plan(g, ingot, 20L, stocked, f, List.of());
        assertEquals(PlanningStatus.feasible, fromStock.status());
        ProductionPlan bestStock = fromStock.paretoPlans().get(0);
        assertTrue(bestStock.certificate().valid());
        ConvertStep conv = bestStock.steps().stream().filter(ConvertStep.class::isInstance).map(ConvertStep.class::cast)
                .findFirst().orElseThrow();
        assertEquals(3L, conv.batches());
        assertTrue(bestStock.preparation().toAcquireFromWorld().isEmpty());

        PlanningResult fromEmpty = plan(g, ingot, 9L, InventorySnapshot.empty, f, List.of());
        ProductionPlan bestEmpty = fromEmpty.paretoPlans().get(0);
        assertTrue(bestEmpty.certificate().valid());
        assertFalse(bestEmpty.steps().isEmpty(), "a plan from an empty inventory still has steps");
        assertTrue(bestEmpty.steps().stream().noneMatch(ConvertStep.class::isInstance));

        PlanningResult directBlock = plan(g, block, 2L, InventorySnapshot.empty, f, List.of());
        ProductionPlan bestBlock = directBlock.paretoPlans().get(0);
        assertTrue(bestBlock.certificate().valid());
        assertEquals(18L, bestBlock.preparation().toAcquireFromWorld().amountOf(rawIron));
    }

    @Test
    void tc05_openWorldAcquireAndRecursiveFurnaceProvision() {
        ComponentStackKey cobble = item("minecraft:cobblestone");
        ComponentStackKey furnace = item("minecraft:furnace");
        ComponentStackKey rawIron = item("minecraft:raw_iron");
        ComponentStackKey ingot = item("minecraft:iron_ingot");
        CompiledRecipeGraph g = compile(
            List.of(
                rec(
                    "minecraft:crafting",
                    "minecraft:furnace",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(cobble, 8)),
                    List.of(out(furnace, 1))
                ),
                rec(
                    "minecraft:smelting",
                    "minecraft:iron_ingot",
                    List.of("minecraft:furnace"),
                    List.of(slot(rawIron, 1)),
                    List.of(out(ingot, 1))
                )
            )
        );

        PlanningResult res = plan(g, ingot, 8L, InventorySnapshot.empty, fac(ownedCraftingTable()), List.of());
        assertEquals(PlanningStatus.feasible, res.status());
        ProductionPlan best = res.paretoPlans().get(0);
        assertTrue(best.certificate().valid());
        ProvisionMachineStep prov = best.steps().stream().filter(ProvisionMachineStep.class::isInstance)
                .map(ProvisionMachineStep.class::cast).findFirst().orElseThrow();
        assertEquals(item("minecraft:furnace"), prov.machine().contentKey().orElseThrow());
        assertTrue(prov.craftedByPredecessorSteps());
        assertEquals(8L, best.preparation().toAcquireFromWorld().amountOf(cobble));
        assertEquals(8L, best.preparation().toAcquireFromWorld().amountOf(rawIron));
    }

    @Test
    void tc06_automatedLinePreferredWithLowHumanAttention() {
        ComponentStackKey ingot = item("minecraft:iron_ingot");
        ComponentStackKey rawIron = item("minecraft:raw_iron");
        CompiledRecipeGraph g = compile(
            List.of(
                rec(
                    "minecraft:smelting",
                    "smelt",
                    List.of("minecraft:furnace"),
                    List.of(slot(rawIron, 1)),
                    List.of(out(ingot, 1))
                )
            )
        );
        AutomatedLine ironFarm = new AutomatedLine("line:iron_farm", ingot, 120.0, 0.4, 256L, true);
        FacilitySnapshot f = new FacilitySnapshot(List.of(ownedFurnace()), List.of(ironFarm), List.of(), Set.of());

        PlanningResult res = plan(g, ingot, 64L, InventorySnapshot.empty, f, List.of());
        ProductionPlan best = res.paretoPlans().get(0);
        assertTrue(best.certificate().valid());
        LinePullStep pull = best.steps().stream().filter(LinePullStep.class::isInstance).map(LinePullStep.class::cast)
                .findFirst().orElseThrow();
        assertEquals("line:iron_farm", pull.lineId());
        assertEquals(64L, pull.amount());
        assertTrue(best.totalCost().humanActiveSeconds() <= 1.0);
    }

    @Test
    void tc07_machineCapabilityEquivalenceRunsSmeltingWithZeroCapital() {
        ComponentStackKey rawIron = item("minecraft:raw_iron");
        ComponentStackKey ingot = item("minecraft:iron_ingot");
        CompiledRecipeGraph g = compile(
            List.of(
                rec(
                    "minecraft:smelting",
                    "smelt",
                    List.of("minecraft:furnace"),
                    List.of(slot(rawIron, 1)),
                    List.of(out(ingot, 1))
                )
            )
        );
        FacilitySnapshot f = fac(owned("ironfurnaces:diamond_furnace", capability("minecraft:smelting"), 1));

        PlanningResult res = plan(g, ingot, 4L, InventorySnapshot.empty, f, List.of());
        ProductionPlan best = res.paretoPlans().get(0);
        assertTrue(best.certificate().valid());
        assertEquals(0.0, best.totalCost().machineSetupCapital(), 1e-9);
        assertTrue(best.preparation().machinesToProvision().isEmpty());
    }

    @Test
    void tc08_a12_intermediateProductMixedBatchSavesStone() {
        ComponentStackKey stone = item("minecraft:stone");
        ComponentStackKey bricks = item("minecraft:stone_bricks");
        ComponentStackKey wall = item("mod:monument");
        CompiledRecipeGraph g = compile(
            List.of(
                rec(
                    "minecraft:crafting",
                    "bricks_4",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(stone, 4)),
                    List.of(out(bricks, 4))
                ),
                rec(
                    "minecraft:stonecutting",
                    "bricks_1",
                    List.of("minecraft:stonecutter"),
                    List.of(slot(stone, 1)),
                    List.of(out(bricks, 1))
                ),
                rec(
                    "minecraft:crafting",
                    "monument",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(bricks, 5)),
                    List.of(out(wall, 1))
                )
            )
        );
        FacilitySnapshot f = fac(ownedCraftingTable(), ownedStonecutter());
        AcquisitionOracle stoneOracle = (k, amt, prog) -> k.equals(stone)
                ? Optional.of(new AcquisitionOption(AcquisitionKind.manualGather, 2.0, 0.0, 0.0, 6.0, "stone"))
                : Optional.empty();
        PlanningProfile minRaw = new PlanningProfile(
            "min_raw",
            ObjectivePreset.frugalMaterials,
            new CostWeights(0.4, 0.05, 0.8, 4.0, 0.1),
            0.25,
            3
        );
        PlanningRequest req = new PlanningRequest(
            List.of(new TargetDemand(wall, 1)),
            new PlanningContext(
                InventorySnapshot.empty,
                f,
                stoneOracle,
                DefaultHumanGuidanceView.empty(),
                ProgressionState.overworldDefault
            ),
            minRaw,
            generousBudget()
        );
        PlanningResult res = new DefaultPlanningService(g).planOnce(req);
        assertEquals(PlanningStatus.feasible, res.status());
        ProductionPlan best = res.paretoPlans().get(0);
        assertTrue(best.certificate().valid());
        assertEquals(5L, best.preparation().toAcquireFromWorld().amountOf(stone));
    }

    @Test
    void tc09_setCoverPicksSingleSharedMachineOverTwoCheaperLocalOnes() {
        ComponentStackKey ore = item("minecraft:cobblestone");
        ComponentStackKey a = item("mod:a"), b = item("mod:b"), c = item("mod:c"), d = item("mod:d");
        CompiledRecipeGraph g = compile(
            List.of(
                rec("mod:s1", "ra", List.of("mod:m1", "mod:m3"), List.of(slot(ore, 1)), List.of(out(a, 1))),
                rec("mod:s2", "rb", List.of("mod:m1", "mod:m3"), List.of(slot(ore, 1)), List.of(out(b, 1))),
                rec("mod:s3", "rc", List.of("mod:m2", "mod:m3"), List.of(slot(ore, 1)), List.of(out(c, 1))),
                rec("mod:s4", "rd", List.of("mod:m2", "mod:m3"), List.of(slot(ore, 1)), List.of(out(d, 1)))
            )
        );
        PlanningRequest req = request(
            g,
            List.of(new TargetDemand(a, 1), new TargetDemand(b, 1), new TargetDemand(c, 1), new TargetDemand(d, 1)),
            InventorySnapshot.empty,
            FacilitySnapshot.empty,
            DefaultHumanGuidanceView.empty(),
            PlanningProfile.defaultBalanced(),
            generousBudget()
        );
        PlanningResult res = new DefaultPlanningService(g).planOnce(req);
        ProductionPlan best = res.paretoPlans().get(0);
        assertTrue(best.certificate().valid());
        assertEquals(Set.of(machine("mod:m3")), best.preparation().machinesToProvision().keySet());
    }

    @Test
    void tc10_selfCyclicMachineGracefullyDetachedWithoutStackOverflow() {
        ComponentStackKey cobble = item("minecraft:cobblestone");
        ComponentStackKey mach = item("mod:self_mach");
        ComponentStackKey part = item("mod:part");
        CompiledRecipeGraph g = compile(
            List.of(
                rec("mod:sys", "make_mach", List.of("mod:self_mach"), List.of(slot(cobble, 4)), List.of(out(mach, 1))),
                rec("mod:sys", "make_part", List.of("mod:self_mach"), List.of(slot(cobble, 1)), List.of(out(part, 1)))
            )
        );
        PlanningResult res = plan(g, part, 2L, InventorySnapshot.empty, FacilitySnapshot.empty, List.of());
        ProductionPlan best = res.paretoPlans().get(0);
        assertTrue(best.certificate().valid());
        ProvisionMachineStep prov = best.steps().stream().filter(ProvisionMachineStep.class::isInstance)
                .map(ProvisionMachineStep.class::cast).findFirst().orElseThrow();
        assertFalse(prov.craftedByPredecessorSteps());
    }

    @Test
    void a11_highFanoutTagRelaxesUntilReachableMemberFound() {
        ComponentStackKey cobble = item("minecraft:cobblestone");
        ComponentStackKey goal = item("mod:tag_goal");
        List<ComponentStackKey> members = new ArrayList<>();
        List<RawRecipeRecord> recs = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            members.add(item("mod:cand_" + String.format("%02d", i)));
        }
        // Only member #30 is producible; other 39 are unproducible intermediates via banned/dead paths.
        ComponentStackKey validCand = members.get(30);
        recs.add(
            rec(
                "minecraft:crafting",
                "make_valid",
                List.of("minecraft:crafting_table"),
                List.of(slot(cobble, 1)),
                List.of(out(validCand, 1))
            )
        );
        recs.add(
            rec(
                "minecraft:crafting",
                "make_goal",
                List.of("minecraft:crafting_table"),
                List.of(tagSlot("mod:huge_tag", members, 1)),
                List.of(out(goal, 1))
            )
        );

        CompiledRecipeGraph g = compile(recs);
        AcquisitionOracle cobbleOnly = (k, amt, prog) -> k.equals(cobble)
                ? Optional.of(new AcquisitionOption(AcquisitionKind.manualGather, 0.2, 0.0, 2.0, 0.2, "cobble"))
                : Optional.empty();
        PlanningRequest req = new PlanningRequest(
            List.of(new TargetDemand(goal, 1)),
            new PlanningContext(
                InventorySnapshot.empty,
                fac(ownedCraftingTable()),
                cobbleOnly,
                DefaultHumanGuidanceView.empty(),
                ProgressionState.overworldDefault
            ),
            PlanningProfile.defaultBalanced(),
            generousBudget()
        );
        PlanningResult res = new DefaultPlanningService(g).planOnce(req);
        assertEquals(PlanningStatus.feasible, res.status());
        assertTrue(res.paretoPlans().get(0).certificate().valid());
    }

    @Test
    void optInSystemsBlockedByDefaultAndUnlockableViaFacilityOrRule() {
        ComponentStackKey cobble = item("minecraft:cobblestone");
        ComponentStackKey gtOut = item("mod:gt_plate");
        String gtSys = "jei:category/gregtech/bender";
        CompiledRecipeGraph g = compile(
            List.of(rec(gtSys, "gt:bend", List.of("gregtech:bender"), List.of(slot(cobble, 1)), List.of(out(gtOut, 1)))),
            DefaultSystemAdmissionPolicy.defaults()
                    .withSystemOverride(Namespace.parse(gtSys), SystemAdmission.optInOnly)
        );

        PlanningResult blocked = plan(g, gtOut, 1L, InventorySnapshot.empty, FacilitySnapshot.empty, List.of());
        assertEquals(PlanningStatus.provenInfeasible, blocked.status());

        FacilitySnapshot unlockedFac = new FacilitySnapshot(
            List.of(), List.of(), List.of(), Set.of(Namespace.parse(gtSys))
        );
        PlanningResult unlockedByFac = plan(g, gtOut, 1L, InventorySnapshot.empty, unlockedFac, List.of());
        assertEquals(PlanningStatus.feasible, unlockedByFac.status());
        assertTrue(unlockedByFac.paretoPlans().get(0).certificate().valid());

        SystemRule unlockRule = new SystemRule(
            "optin_gt", GuidanceScope.session, RuleStrength.hardPin, Namespace.parse(gtSys), true
        );
        PlanningResult unlockedByRule = plan(
            g,
            gtOut,
            1L,
            InventorySnapshot.empty,
            FacilitySnapshot.empty,
            List.of(unlockRule)
        );
        assertEquals(PlanningStatus.feasible, unlockedByRule.status());
        assertTrue(unlockedByRule.paretoPlans().get(0).certificate().valid());
    }

    @Test
    void lastResortAndDiscouragedRoutesStrictlyRankedBehindStandardRoute() {
        ComponentStackKey cobble = item("minecraft:cobblestone");
        ComponentStackKey ingot = item("minecraft:iron_ingot");
        ComponentStackKey sword = item("minecraft:iron_sword");
        DefaultSystemAdmissionPolicy policy = DefaultSystemAdmissionPolicy.defaults()
                .withExternalVerdicts(Map.of(), Map.of(2L, RecipeAdvisory.lastResort));
        RawRecipeRecord normal = new RawRecipeRecord(
            Namespace.parse("minecraft:smelting"),
            "vanilla",
            Optional.of(ResourceLocation.parse("normal_smelt")),
            machines("minecraft:furnace"),
            List.of(slot(item("minecraft:raw_iron"), 1)),
            List.of(out(ingot, 1)),
            Map.of(),
            1L
        );
        RawRecipeRecord salvage = new RawRecipeRecord(
            Namespace.parse("mod:salvaging"),
            "custom",
            Optional.of(ResourceLocation.parse("salvage_iron")),
            machines(),
            List.of(slot(cobble, 1)),
            List.of(out(ingot, 1)),
            Map.of(),
            2L
        );
        RawRecipeRecord swordRecycle = rec(
            "minecraft:smelting",
            "recycle_sword",
            List.of("minecraft:furnace"),
            List.of(slot(sword, 1)),
            List.of(out(ingot, 1))
        );

        CompiledRecipeGraph g = compile(List.of(normal, salvage, swordRecycle), policy);
        PlanningResult res = plan(g, ingot, 2L, InventorySnapshot.empty, fac(ownedFurnace()), List.of());
        ProductionPlan best = res.paretoPlans().get(0);
        assertTrue(best.certificate().valid());
    }
}
