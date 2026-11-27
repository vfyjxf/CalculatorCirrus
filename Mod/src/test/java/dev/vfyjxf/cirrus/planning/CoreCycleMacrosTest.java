package dev.vfyjxf.cirrus.planning;

import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.compile;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.fac;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.item;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.out;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.owned;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.ownedCraftingTable;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.plan;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.rec;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreCycleMacrosTest {

    @Test
    void threeStepRegenerativeCatalystLoopMinesAndVerifies() {
        ResourceKey a = item("mod:cat_a"), b = item("mod:cat_b"), c = item("mod:cat_c");
        ResourceKey r1 = item("mod:r1"), r2 = item("mod:r2"), r3 = item("mod:r3"), prod = item("mod:prod3");
        CompiledRecipeGraph g = compile(
            List.of(
                rec(
                    "mod:chem",
                    "s1",
                    List.of("mod:chem_vat"),
                    List.of(slot(a, 1), slot(r1, 1)),
                    List.of(out(b, 1), out(prod, 1))
                ),
                rec("mod:chem", "s2", List.of("mod:chem_vat"), List.of(slot(b, 1), slot(r2, 1)), List.of(out(c, 1))),
                rec("mod:chem", "s3", List.of("mod:chem_vat"), List.of(slot(c, 1), slot(r3, 1)), List.of(out(a, 1)))
            )
        );
        assertFalse(g.cycleMacrosProducing(prod).isEmpty());
        InventorySnapshot inv = new InventorySnapshot(new StockLedger(Map.of(a, 1L)), StockLedger.empty);
        FacilitySnapshot f = fac(
            owned("mod:chem_vat", new MachineCapabilityId("cirrus:capability/system/mod/chem"), 1)
        );

        PlanningResult res = plan(g, prod, 7L, inv, f, List.of());
        assertEquals(PlanningStatus.feasible, res.status());
        ProductionPlan best = res.paretoPlans().get(0);
        assertTrue(best.certificate().valid());
        CycleLoopStep loop = best.steps().stream().filter(CycleLoopStep.class::isInstance)
                .map(CycleLoopStep.class::cast).findFirst().orElseThrow();
        assertEquals(7L, loop.iterations());
        assertEquals(3, loop.singleIterationTemplate().size());
    }

    @Test
    void fourStepRegenerativeCatalystLoopMinesAndVerifies() {
        ResourceKey a = item("mod:c4_a"), b = item("mod:c4_b"), c = item("mod:c4_c"), d = item("mod:c4_d");
        ResourceKey fuel = item("minecraft:coal"), prod = item("mod:prod4");
        CompiledRecipeGraph g = compile(
            List.of(
                rec(
                    "minecraft:crafting",
                    "c1",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(a, 1), slot(fuel, 1)),
                    List.of(out(b, 1), out(prod, 2))
                ),
                rec(
                    "minecraft:crafting",
                    "c2",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(b, 1)),
                    List.of(out(c, 1))
                ),
                rec(
                    "minecraft:crafting",
                    "c3",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(c, 1)),
                    List.of(out(d, 1))
                ),
                rec(
                    "minecraft:crafting",
                    "c4",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(d, 1)),
                    List.of(out(a, 1))
                )
            )
        );
        assertFalse(g.cycleMacrosProducing(prod).isEmpty());
        InventorySnapshot inv = new InventorySnapshot(new StockLedger(Map.of(a, 1L)), StockLedger.empty);

        PlanningResult res = plan(g, prod, 10L, inv, fac(ownedCraftingTable()), List.of());
        assertEquals(PlanningStatus.feasible, res.status());
        ProductionPlan best = res.paretoPlans().get(0);
        assertTrue(best.certificate().valid());
        CycleLoopStep loop = best.steps().stream().filter(CycleLoopStep.class::isInstance)
                .map(CycleLoopStep.class::cast).findFirst().orElseThrow();
        assertEquals(5L, loop.iterations());
        assertEquals(4, loop.singleIterationTemplate().size());
    }

    @Test
    void selfAmplifyingLoopSourcesSeedWhenSeedIsCraftableNotStocked() {
        ResourceKey cobble = item("minecraft:cobblestone");
        ResourceKey gem = item("mod:rare_gem");
        ResourceKey dust = item("minecraft:redstone");
        CompiledRecipeGraph g = compile(
            List.of(
                rec(
                    "minecraft:crafting",
                    "seed_gem",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(cobble, 64)),
                    List.of(out(gem, 1))
                ),
                rec(
                    "minecraft:crafting",
                    "dup_gem",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(gem, 1), slot(dust, 1)),
                    List.of(out(gem, 2))
                )
            )
        );
        PlanningResult res = plan(g, gem, 8L, InventorySnapshot.empty, fac(ownedCraftingTable()), List.of());
        assertEquals(PlanningStatus.feasible, res.status());
        ProductionPlan best = res.paretoPlans().get(0);
        assertTrue(best.certificate().valid());
        assertEquals(64L, best.preparation().toAcquireFromWorld().amountOf(cobble));
        assertEquals(7L, best.preparation().toAcquireFromWorld().amountOf(dust));
    }
}
