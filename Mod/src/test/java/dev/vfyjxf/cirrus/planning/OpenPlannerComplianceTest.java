package dev.vfyjxf.cirrus.planning;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOption;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.OwnedMachineGroup;
import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.CostWeights;
import dev.vfyjxf.cirrus.api.planning.cost.ObjectivePreset;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.RawIngredientSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawOutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeCatalogSnapshot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeGraphCompiler;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmissionPolicy;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningService;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class OpenPlannerComplianceTest {

    protected abstract RecipeGraphCompiler compiler();

    protected abstract SystemAdmissionPolicy admissionPolicy();

    protected abstract PlanningService createService(CompiledRecipeGraph graph);

    protected abstract HumanGuidanceView emptyGuidance();

    protected abstract AcquisitionOracle defaultOracle(CompiledRecipeGraph graph);

    @Test
    void tc08_stoneBricksMixedBatchSavesThreeStone() {
        ResourceKey stone = ResourceKey.item("minecraft:stone");
        ResourceKey bricks = ResourceKey.item("minecraft:stone_bricks");
        RawRecipeCatalogSnapshot snap = new RawRecipeCatalogSnapshot(
            1L,
            List.of(
                new RawRecipeRecord(
                    "minecraft:crafting",
                    "vanilla",
                    Optional.of("minecraft:stone_bricks"),
                    List.of("minecraft:crafting_table"),
                    List.of(new RawIngredientSlot(Optional.empty(), List.of(stone), 4, SlotMatchMode.identity)),
                    List.of(new RawOutputSlot(bricks, 4)),
                    Map.of()
                ),
                new RawRecipeRecord(
                    "minecraft:stonecutting",
                    "vanilla",
                    Optional.of("minecraft:stone_bricks_cut"),
                    List.of("minecraft:stonecutter"),
                    List.of(new RawIngredientSlot(Optional.empty(), List.of(stone), 1, SlotMatchMode.identity)),
                    List.of(new RawOutputSlot(bricks, 1)),
                    Map.of()
                )
            )
        );
        CompiledRecipeGraph g = compiler().compile(snap, admissionPolicy());
        FacilitySnapshot fac = new FacilitySnapshot(
            List.of(
                new OwnedMachineGroup(
                    "minecraft:crafting_table",
                    new MachineCapabilityId("cirrus:capability/crafting"),
                    1,
                    1.0,
                    false
                ),
                new OwnedMachineGroup(
                    "minecraft:stonecutter",
                    new MachineCapabilityId("cirrus:capability/stonecutting"),
                    1,
                    1.0,
                    false
                )
            ),
            List.of(),
            List.of(),
            Set.of()
        );
        AcquisitionOracle stoneOracle = (k, amt, prog) -> k.equals(stone)
                ? Optional.of(new AcquisitionOption(AcquisitionKind.manualGather, 2.0, 0.0, 0.0, 5.0, "stone"))
                : Optional.empty();
        PlanningProfile minRaw = new PlanningProfile(
            "min_raw",
            ObjectivePreset.frugalMaterials,
            new CostWeights(0.4, 0.05, 0.8, 4.0, 0.1),
            0.25,
            3
        );
        PlanningRequest req = new PlanningRequest(
            List.of(new TargetDemand(bricks, 5)),
            new PlanningContext(
                InventorySnapshot.empty,
                fac,
                stoneOracle,
                emptyGuidance(),
                ProgressionState.overworldDefault
            ),
            minRaw,
            PlanningBudget.defaultInteractive()
        );

        PlanningResult res = createService(g).planOnce(req);
        assertEquals(PlanningStatus.feasible, res.status());
        ProductionPlan best = res.paretoPlans().get(0);
        assertTrue(best.certificate().valid(), () -> "Violations: " + best.certificate().violations());
        assertEquals(5L, best.preparation().toAcquireFromWorld().amountOf(stone));
    }

    @Test
    void tc15_selfAmplifyingNetheriteTemplateLoop() {
        ResourceKey tmpl = ResourceKey.item("minecraft:netherite_upgrade_smithing_template");
        ResourceKey diamond = ResourceKey.item("minecraft:diamond");
        ResourceKey netherrack = ResourceKey.item("minecraft:netherrack");
        RawRecipeCatalogSnapshot snap = new RawRecipeCatalogSnapshot(
            1L,
            List.of(
                new RawRecipeRecord(
                    "minecraft:crafting",
                    "vanilla",
                    Optional.of("dup_template"),
                    List.of("minecraft:crafting_table"),
                    List.of(
                        new RawIngredientSlot(Optional.empty(), List.of(tmpl), 1, SlotMatchMode.identity),
                        new RawIngredientSlot(Optional.empty(), List.of(diamond), 7, SlotMatchMode.identity),
                        new RawIngredientSlot(Optional.empty(), List.of(netherrack), 1, SlotMatchMode.identity)
                    ),
                    List.of(new RawOutputSlot(tmpl, 2)),
                    Map.of()
                )
            )
        );
        CompiledRecipeGraph g = compiler().compile(snap, admissionPolicy());
        assertFalse(g.cycleMacrosProducing(tmpl).isEmpty());

        InventorySnapshot inv = new InventorySnapshot(new StockLedger(Map.of(tmpl, 1L)), StockLedger.empty);
        FacilitySnapshot fac = new FacilitySnapshot(
            List.of(
                new OwnedMachineGroup(
                    "minecraft:crafting_table",
                    new MachineCapabilityId("cirrus:capability/crafting"),
                    1,
                    1.0,
                    false
                )
            ),
            List.of(),
            List.of(),
            Set.of()
        );
        PlanningRequest req = new PlanningRequest(
            List.of(new TargetDemand(tmpl, 5)),
            new PlanningContext(inv, fac, defaultOracle(g), emptyGuidance(), ProgressionState.overworldDefault),
            PlanningProfile.defaultBalanced(),
            PlanningBudget.defaultInteractive()
        );
        PlanningResult res = createService(g).planOnce(req);
        assertEquals(PlanningStatus.feasible, res.status());
        ProductionPlan best = res.paretoPlans().get(0);
        assertTrue(best.certificate().valid());
        CycleLoopStep loop = best.steps().stream().filter(CycleLoopStep.class::isInstance)
                .map(CycleLoopStep.class::cast).findFirst().orElseThrow();
        assertEquals(4L, loop.iterations());
    }

    @Test
    void tc16_twoStepRegenerativeCatalystClosedLoop() {
        ResourceKey fresh = ResourceKey.item("mod:fresh_cat");
        ResourceKey spent = ResourceKey.item("mod:spent_cat");
        ResourceKey ore = ResourceKey.item("mod:raw_ore");
        ResourceKey reagent = ResourceKey.item("mod:reagent");
        ResourceKey prod = ResourceKey.item("mod:refined_product");

        RawRecipeCatalogSnapshot snap = new RawRecipeCatalogSnapshot(
            1L,
            List.of(
                new RawRecipeRecord(
                    "mod:chem",
                    "custom",
                    Optional.of("react"),
                    List.of("mod:reactor"),
                    List.of(
                        new RawIngredientSlot(Optional.empty(), List.of(fresh), 1, SlotMatchMode.identity),
                        new RawIngredientSlot(Optional.empty(), List.of(ore), 1, SlotMatchMode.identity)
                    ),
                    List.of(new RawOutputSlot(spent, 1), new RawOutputSlot(prod, 1)),
                    Map.of()
                ),
                new RawRecipeRecord(
                    "mod:chem",
                    "custom",
                    Optional.of("regen"),
                    List.of("mod:reactor"),
                    List.of(
                        new RawIngredientSlot(Optional.empty(), List.of(spent), 1, SlotMatchMode.identity),
                        new RawIngredientSlot(Optional.empty(), List.of(reagent), 1, SlotMatchMode.identity)
                    ),
                    List.of(new RawOutputSlot(fresh, 1)),
                    Map.of()
                )
            )
        );
        CompiledRecipeGraph g = compiler().compile(snap, admissionPolicy());
        assertFalse(g.cycleMacrosProducing(prod).isEmpty());

        InventorySnapshot inv = new InventorySnapshot(new StockLedger(Map.of(fresh, 1L)), StockLedger.empty);
        FacilitySnapshot fac = new FacilitySnapshot(
            List.of(
                new OwnedMachineGroup(
                    "mod:reactor",
                    new MachineCapabilityId("cirrus:capability/system/mod/chem"),
                    1,
                    1.0,
                    false
                )
            ),
            List.of(),
            List.of(),
            Set.of()
        );
        PlanningRequest req = new PlanningRequest(
            List.of(new TargetDemand(prod, 10)),
            new PlanningContext(inv, fac, defaultOracle(g), emptyGuidance(), ProgressionState.overworldDefault),
            PlanningProfile.defaultBalanced(),
            PlanningBudget.defaultInteractive()
        );
        PlanningResult res = createService(g).planOnce(req);
        assertEquals(PlanningStatus.feasible, res.status());
        ProductionPlan best = res.paretoPlans().get(0);
        assertTrue(best.certificate().valid());
        CycleLoopStep loop = best.steps().stream().filter(CycleLoopStep.class::isInstance)
                .map(CycleLoopStep.class::cast).findFirst().orElseThrow();
        assertEquals(10L, loop.iterations());
        assertEquals(1L, loop.workingSeedAmounts().get(fresh));
    }
}
