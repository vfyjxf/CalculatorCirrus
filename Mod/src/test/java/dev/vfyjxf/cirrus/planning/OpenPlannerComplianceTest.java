package dev.vfyjxf.cirrus.planning;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOption;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.CostWeights;
import dev.vfyjxf.cirrus.api.planning.cost.ObjectivePreset;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeCatalogSnapshot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeGraphCompiler;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmissionPolicy;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
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

import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.capability;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.fac;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.item;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.out;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.owned;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.rec;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.slot;
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
        ComponentStackKey stone = item("minecraft:stone");
        ComponentStackKey bricks = item("minecraft:stone_bricks");
        RawRecipeCatalogSnapshot snap = new RawRecipeCatalogSnapshot(
            1L,
            List.of(
                rec(
                    "minecraft:crafting",
                    "minecraft:stone_bricks",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(stone, 4)),
                    List.of(out(bricks, 4))
                ),
                rec(
                    "minecraft:stonecutting",
                    "minecraft:stone_bricks_cut",
                    List.of("minecraft:stonecutter"),
                    List.of(slot(stone, 1)),
                    List.of(out(bricks, 1))
                )
            )
        );
        CompiledRecipeGraph g = compiler().compile(snap, admissionPolicy());
        FacilitySnapshot fac = fac(
            owned("minecraft:crafting_table", capability("minecraft:crafting"), 1),
            owned("minecraft:stonecutter", capability("minecraft:stonecutting"), 1)
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
        ComponentStackKey tmpl = item("minecraft:netherite_upgrade_smithing_template");
        ComponentStackKey diamond = item("minecraft:diamond");
        ComponentStackKey netherrack = item("minecraft:netherrack");
        RawRecipeCatalogSnapshot snap = new RawRecipeCatalogSnapshot(
            1L,
            List.of(
                rec(
                    "minecraft:crafting",
                    "dup_template",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(tmpl, 1), slot(diamond, 7), slot(netherrack, 1)),
                    List.of(out(tmpl, 2))
                )
            )
        );
        CompiledRecipeGraph g = compiler().compile(snap, admissionPolicy());
        assertFalse(g.cycleMacrosProducing(tmpl).isEmpty());

        InventorySnapshot inv = new InventorySnapshot(new StockLedger(Map.of(tmpl, 1L)), StockLedger.empty);
        FacilitySnapshot fac = fac(owned("minecraft:crafting_table", capability("minecraft:crafting"), 1));
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
        ComponentStackKey fresh = item("mod:fresh_cat");
        ComponentStackKey spent = item("mod:spent_cat");
        ComponentStackKey ore = item("mod:raw_ore");
        ComponentStackKey reagent = item("mod:reagent");
        ComponentStackKey prod = item("mod:refined_product");

        RawRecipeCatalogSnapshot snap = new RawRecipeCatalogSnapshot(
            1L,
            List.of(
                rec(
                    "mod:chem",
                    "react",
                    List.of("mod:reactor"),
                    List.of(slot(fresh, 1), slot(ore, 1)),
                    List.of(out(spent, 1), out(prod, 1))
                ),
                rec(
                    "mod:chem",
                    "regen",
                    List.of("mod:reactor"),
                    List.of(slot(spent, 1), slot(reagent, 1)),
                    List.of(out(fresh, 1))
                )
            )
        );
        CompiledRecipeGraph g = compiler().compile(snap, admissionPolicy());
        assertFalse(g.cycleMacrosProducing(prod).isEmpty());

        InventorySnapshot inv = new InventorySnapshot(new StockLedger(Map.of(fresh, 1L)), StockLedger.empty);
        FacilitySnapshot fac = fac(owned("mod:reactor", capability("mod:chem"), 1));
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
