package dev.vfyjxf.cirrus.internal.integration.jei;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.planning.adapter.OfflineContentTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CirrusPlanBookmarkStoreTest {

    @BeforeEach
    void resetStore() {
        CirrusPlanBookmarkStore.resetForTests();
    }

    private static ProductionPlan samplePlan() {
        ComponentStackKey ore = OfflineContentTypes.item("minecraft:iron_ore");
        ComponentStackKey ingot = OfflineContentTypes.item("minecraft:iron_ingot");
        AcquireStep step1 = new AcquireStep("a001", List.of(), ore, 8L, AcquisitionKind.manualGather, CostVector.zero);
        AcquireStep step2 = new AcquireStep(
            "a002",
            List.of("a001"),
            ingot,
            8L,
            AcquisitionKind.manualGather,
            CostVector.zero
        );
        PreparationSummary prep = new PreparationSummary(
            StockLedger.empty,
            new StockLedger(Map.of(ore, 8L)),
            Map.of(),
            List.of(),
            List.of()
        );
        return new ProductionPlan(
            "plan-test-1",
            "balanced",
            List.of(step1, step2),
            prep,
            List.of(),
            new CostVector(5.0, 2.0, 0.0, 0.0, 1.0),
            new VerificationCertificate(true, true, true, true, true, true, List.of())
        );
    }

    @Test
    void planBookmarkRoundTripsThroughJsonWithMaterialsAndChecklistProgress() {
        ProductionPlan plan = samplePlan();
        TargetDemand target = new TargetDemand(OfflineContentTypes.item("minecraft:iron_ingot"), 8L);
        CirrusPlanBookmarkStore.PlanBookmarkEntry entry = CirrusPlanBookmarkStore
                .fromPlan(plan, List.of(target), null, Set.of("a001"), "Iron Ingot");
        CirrusPlanBookmarkStore.put(entry);

        assertTrue(CirrusPlanBookmarkStore.contains(entry.id()));
        assertEquals(Set.of("a001"), CirrusPlanBookmarkStore.checkedSteps(entry.id()));

        String json = CirrusPlanBookmarkStore.serializeToJson();
        CirrusPlanBookmarkStore.resetForTests();
        assertFalse(CirrusPlanBookmarkStore.contains(entry.id()));

        CirrusPlanBookmarkStore.loadFromJson(json);
        CirrusPlanBookmarkStore.PlanBookmarkEntry restored = CirrusPlanBookmarkStore.find(entry.id());
        assertNotNull(restored);
        assertEquals("Iron Ingot", restored.title());
        assertEquals(2, restored.totalSteps());
        assertEquals(2, restored.steps().size());
        assertEquals("a001", restored.steps().get(0).stepId());
        assertEquals(List.of("a001"), restored.steps().get(1).dependsOn());
        assertEquals(1, restored.rawMaterials().size());
        assertEquals(Set.of("a001"), restored.checkedStepIds());
        assertEquals(Set.of("a001"), CirrusPlanBookmarkStore.checkedSteps(entry.id()));
        assertTrue(PlanFlowRecipe.of(restored).registryName().getPath().startsWith("plan_flow/"));
    }

    @Test
    void checklistProgressUpdatesPersistIntoExistingBookmark() {
        ProductionPlan plan = samplePlan();
        CirrusPlanBookmarkStore.PlanBookmarkEntry entry = CirrusPlanBookmarkStore
                .fromPlan(plan, List.of(), null, Set.of(), "Progress Flow");
        CirrusPlanBookmarkStore.put(entry);

        CirrusPlanBookmarkStore.saveCheckedSteps(entry.id(), Set.of("a001"));
        CirrusPlanBookmarkStore.PlanBookmarkEntry updated = CirrusPlanBookmarkStore.find(entry.id());
        assertNotNull(updated);
        assertEquals(Set.of("a001"), updated.checkedStepIds());

        assertTrue(CirrusPlanBookmarkStore.remove(entry.id()));
        assertFalse(CirrusPlanBookmarkStore.contains(entry.id()));
    }

    @Test
    void planFlowRecipeCodecRoundTripsFullFlowWithoutExternalStore() {
        ProductionPlan plan = samplePlan();
        TargetDemand target = new TargetDemand(OfflineContentTypes.item("minecraft:iron_ingot"), 8L);
        CirrusPlanBookmarkStore.PlanBookmarkEntry entry = CirrusPlanBookmarkStore
                .fromPlan(plan, List.of(target), null, Set.of("a001"), "Iron Ingot");
        PlanFlowRecipe original = PlanFlowRecipe.of(entry);

        JsonElement encoded = PlanFlowRecipe.codec.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        CirrusPlanBookmarkStore.resetForTests();

        PlanFlowRecipe decoded = PlanFlowRecipe.codec.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(original.id(), decoded.id());
        assertEquals("Iron Ingot", decoded.latestEntry().title());
        assertEquals(8L, decoded.latestEntry().primaryAmount());
        assertEquals(1, decoded.latestEntry().rawMaterials().size());
        assertEquals(2, decoded.latestEntry().steps().size());
        assertEquals(List.of("a001"), decoded.latestEntry().steps().get(1).dependsOn());
        assertEquals(Set.of("a001"), decoded.latestEntry().checkedStepIds());
    }
}
