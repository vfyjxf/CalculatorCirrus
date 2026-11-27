package dev.vfyjxf.cirrus.planning;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionEstimate;
import dev.vfyjxf.cirrus.api.planning.context.BaseBoundarySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.ContextAttributeView;
import dev.vfyjxf.cirrus.api.planning.context.ContextKey;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.ObjectivePreset;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.RawIngredientSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.api.planning.spi.AcquisitionEstimatorSpi;
import dev.vfyjxf.cirrus.api.planning.spi.BaseBoundaryProvider;
import dev.vfyjxf.cirrus.api.planning.spi.ContextContributor;
import dev.vfyjxf.cirrus.api.planning.spi.PlanningTelemetrySink;
import dev.vfyjxf.cirrus.api.planning.spi.RecipeMetadataEnricher;
import dev.vfyjxf.cirrus.api.planning.tag.CanonicalRecipeTag;
import dev.vfyjxf.cirrus.api.planning.tag.RecipeEvidenceTier;
import dev.vfyjxf.cirrus.api.planning.telemetry.ExecutionFailureCategory;
import dev.vfyjxf.cirrus.api.planning.telemetry.PlanningTelemetryEvent;
import dev.vfyjxf.cirrus.api.recipe.execution.PauseReason;
import dev.vfyjxf.cirrus.api.recipe.execution.util.PauseReasonKey;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystemTags;
import dev.vfyjxf.cirrus.planning.bridge.DefaultDomainProfileProvider;
import dev.vfyjxf.cirrus.planning.bridge.PlanningExtensionRegistry;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultAcquisitionOracle;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import dev.vfyjxf.cirrus.planning.core.learn.ExecutionFailureTracker;
import dev.vfyjxf.cirrus.planning.governance.UnifiedBatchGovernance;
import dev.vfyjxf.cirrus.planning.normalize.DualTagClassifier;
import dev.vfyjxf.cirrus.planning.normalize.JeiNormalizationPatch;
import dev.vfyjxf.cirrus.planning.normalize.JeiRecipeNormalizer;
import dev.vfyjxf.cirrus.planning.telemetry.ExecutionTelemetryBridge;
import dev.vfyjxf.cirrus.planning.telemetry.LocalAnalyticsAdvisor;
import dev.vfyjxf.cirrus.planning.telemetry.PlanningTelemetryBus;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.compile;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.item;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.out;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.rec;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Planning v3:
 * - Dual-layer RecipeSystemTags & 10 CanonicalRecipeTags
 * - 4-stage JeiRecipeNormalizer (J1-J4, mirror dedup, fidelity, patches)
 * - UnifiedBatchGovernance (all 5 batch filtering & orphan-protection tools)
 * - 7 SPI extensibility points via PlanningExtensionRegistry
 * - PlanningTelemetryBus, LocalAnalyticsAdvisor, ExecutionTelemetryBridge, DefaultDomainProfileProvider
 */
class PlanningV3GovernanceAndTelemetryTest {

    @Test
    void dualTagClassifierDetectsCanonicalRolesWithoutWordlists() {
        ResourceKey ingot = item("test:ingot");
        ResourceKey block = item("test:block");
        ResourceKey seed = item("test:seed");
        ResourceKey dust = item("test:dust");

        RawRecipeRecord pack = rec("minecraft:crafting", "pack", List.of(), List.of(slot(ingot, 9)), List.of(out(block, 1)));
        RawRecipeRecord loop = rec("mod:grow", "grow", List.of("mod:chamber"), List.of(slot(seed, 1)), List.of(out(seed, 2)));
        RawRecipeRecord gen = new RawRecipeRecord(
            "mod:void_miner", "mod_native", Optional.of("gen_1"), List.of("mod:miner"),
            List.of(new RawIngredientSlot(Optional.empty(), List.of(item("mod:lens")), 0L, SlotMatchMode.identity, 0, 1)),
            List.of(out(dust, 1)), Map.of(), 0L, Set.of(RecipeSystemTags.resourceGen.toString()), Set.of(), RecipeEvidenceTier.tierA, 1.0, Optional.empty(), false
        );

        Set<CanonicalRecipeTag> packTags = DualTagClassifier.tagSet(DualTagClassifier.classify(pack, false, false));
        Set<CanonicalRecipeTag> loopTags = DualTagClassifier.tagSet(DualTagClassifier.classify(loop, false, false));
        Set<CanonicalRecipeTag> genTags = DualTagClassifier.tagSet(DualTagClassifier.classify(gen, false, false));
        Set<CanonicalRecipeTag> mirrorTags = DualTagClassifier.tagSet(DualTagClassifier.classify(pack, true, false));
        Set<CanonicalRecipeTag> suspectTags = DualTagClassifier.tagSet(DualTagClassifier.classify(pack, false, true));

        assertTrue(packTags.contains(CanonicalRecipeTag.packingUnpacking));
        assertTrue(loopTags.contains(CanonicalRecipeTag.catalyticLoop));
        assertTrue(genTags.contains(CanonicalRecipeTag.resourceGeneration));
        assertTrue(mirrorTags.contains(CanonicalRecipeTag.junkOrDuplicate));
        assertTrue(suspectTags.contains(CanonicalRecipeTag.suspiciousJei));
    }

    @Test
    void jeiNormalizerDedupesMirrorsAppliesJ4PatchesAndComputesFidelity() {
        ResourceKey ore = item("test:ore");
        ResourceKey ingot = item("test:ingot");
        ResourceKey catalyst = item("test:press_mold");
        ResourceKey plate = item("test:plate");

        RawRecipeRecord nativeSmelt = new RawRecipeRecord(
            "minecraft:smelting", "vanilla", Optional.of("vanilla:smelt_ingot"), List.of("minecraft:furnace"),
            List.of(slot(ore, 1)), List.of(out(ingot, 1)), Map.of(),
            1L, Set.of(RecipeSystemTags.vanilla.toString()), Set.of(CanonicalRecipeTag.normalProduction),
            RecipeEvidenceTier.tierA, 1.0, Optional.empty(), false
        );
        RawRecipeRecord jeiMirror = new RawRecipeRecord(
            "jei:category/minecraft/furnace", "jei_fallback", Optional.empty(), List.of("minecraft:furnace"),
            List.of(slot(ore, 1)), List.of(out(ingot, 1)), Map.of(),
            2L, Set.of(RecipeSystemTags.jeiFallback.toString()), Set.of(CanonicalRecipeTag.normalProduction),
            RecipeEvidenceTier.tierC, 1.0, Optional.empty(), false
        );
        RawRecipeRecord jeiMoldRecipe = new RawRecipeRecord(
            "jei:category/mod/inscriber", "jei_fallback", Optional.of("mod:press_plate"), List.of(),
            List.of(slot(catalyst, 1), slot(ingot, 1)), List.of(out(plate, 1)), Map.of(),
            3L, Set.of(RecipeSystemTags.jeiFallback.toString()), Set.of(CanonicalRecipeTag.normalProduction),
            RecipeEvidenceTier.tierC, 1.0, Optional.empty(), false
        );

        JeiNormalizationPatch patch = new JeiNormalizationPatch(
            "patch:inscriber_mold",
            Set.of("jei:category/mod/inscriber"),
            "press_plate",
            Set.of(),
            List.of("mod:inscriber"),
            Set.of(0),
            Set.of(),
            Set.of(),
            RecipeEvidenceTier.tierB,
            0.92
        );

        var norm = JeiRecipeNormalizer.normalize(List.of(nativeSmelt, jeiMirror, jeiMoldRecipe), List.of(patch), List.of());
        assertEquals(1, norm.stats().mirrorDuplicatesMarked());
        assertEquals(1, norm.stats().patchesApplied());

        RawRecipeRecord outMirror = norm.normalizedRecords().get(1);
        assertTrue(outMirror.canonicalTags().contains(CanonicalRecipeTag.junkOrDuplicate));

        RawRecipeRecord outPatched = norm.normalizedRecords().get(2);
        assertTrue(outPatched.machineIds().contains("mod:inscriber"));
        assertTrue(outPatched.inputs().get(0).catalyst(), "J4 patch must promote slot 0 to catalyst");
        assertEquals(RecipeEvidenceTier.tierB, outPatched.evidenceTier());
        assertEquals(0.92, outPatched.structuralFidelity(), 1e-9);
        assertTrue(outPatched.semanticKey().isPresent());
    }

    @Test
    void unifiedBatchGovernanceAllFiveToolsAndOrphanGuard() {
        ResourceKey a = item("test:a");
        ResourceKey sharedOut = item("test:shared_out");
        ResourceKey uniqueOut = item("test:unique_out");

        RawRecipeRecord goodProd = new RawRecipeRecord(
            "minecraft:crafting", "vanilla", Optional.of("good:shared"), List.of("minecraft:crafting_table"),
            List.of(slot(a, 2)), List.of(out(sharedOut, 1)), Map.of(),
            1L, Set.of(RecipeSystemTags.vanilla.toString()), Set.of(CanonicalRecipeTag.normalProduction),
            RecipeEvidenceTier.tierA, 1.0, Optional.empty(), false
        );
        RawRecipeRecord badShared = new RawRecipeRecord(
            "jei:category/junk/altar", "jei_fallback", Optional.of("junk:shared"), List.of(),
            List.of(slot(a, 1)), List.of(out(sharedOut, 1)), Map.of(),
            2L, Set.of(RecipeSystemTags.jeiFallback.toString()), Set.of(CanonicalRecipeTag.suspiciousJei),
            RecipeEvidenceTier.tierC, 0.30, Optional.empty(), true
        );
        RawRecipeRecord badUnique = new RawRecipeRecord(
            "jei:category/junk/altar", "jei_fallback", Optional.of("junk:unique"), List.of(),
            List.of(slot(a, 1)), List.of(out(uniqueOut, 1)), Map.of(),
            3L, Set.of(RecipeSystemTags.jeiFallback.toString()), Set.of(CanonicalRecipeTag.suspiciousJei),
            RecipeEvidenceTier.tierC, 0.30, Optional.empty(), true
        );
        CompiledRecipeGraph g = compile(List.of(goodProd, badShared, badUnique));

        // Tool 1: System Triage Board
        var board = UnifiedBatchGovernance.buildSystemTriageBoard(g);
        assertEquals(2, board.size());
        assertEquals("jei:category/junk/altar", board.get(0).systemId());
        assertEquals(1, board.get(0).uniqueOutputCountIfDisabled());

        // Tool 2: No-Regex Composable Faceted Filter
        var filter = new UnifiedBatchGovernance.FacetedFilterSpec(
            Set.of(), Set.of(), Set.of(), Set.of(CanonicalRecipeTag.suspiciousJei),
            null, null, null, null, null, 0.50, null, "junk"
        );
        List<CompiledRecipe> matched = UnifiedBatchGovernance.filterRecipes(g, filter);
        assertEquals(2, matched.size());

        // Tool 3: 4-Tier Find Similar
        var buckets = UnifiedBatchGovernance.findSimilarBuckets(g, matched.get(0));
        assertEquals(4, buckets.size());
        assertEquals(2, buckets.get(3).matchCount());

        // Tool 4: Orphan Protection Guard
        var orphanReport = UnifiedBatchGovernance.previewOrphanImpact(g, matched);
        assertEquals(Set.of(uniqueOut), orphanReport.orphanedResourcesIfUnprotected());
        assertEquals(1, orphanReport.exemptedUniqueProducerCount());
        assertEquals(1, orphanReport.safeRecipesWithExemption().size());

        List<GuidanceRule> safeBanRules = UnifiedBatchGovernance.compileBulkBanRules(
            g, matched, true, GuidanceScope.modpackDefault, "bulk_ban"
        );
        assertEquals(1, safeBanRules.size());

        // Tool 5: Pack Curation Bundle
        var bundle = new UnifiedBatchGovernance.PackCurationBundle("3.0", "pack:test", safeBanRules, List.of());
        assertEquals(1, bundle.rules().size());
    }

    @Test
    void telemetryBusLocalAnalyticsAndExecutionBridgeWorkEndToEnd() {
        LocalAnalyticsAdvisor advisor = new LocalAnalyticsAdvisor();
        List<PlanningTelemetryEvent> seen = new ArrayList<>();
        try (PlanningTelemetryBus bus = new PlanningTelemetryBus(true)) {
            bus.addSink(advisor);
            bus.addSink(new PlanningTelemetrySink() {
                @Override
                public String sinkId() {
                    return "test:capture";
                }

                @Override
                public void onEvent(PlanningTelemetryEvent event) {
                    seen.add(event);
                }
            });

            ResourceKey gear = item("test:gear");
            for (int i = 0; i < 4; i++) {
                bus.publish(new PlanningTelemetryEvent.PlanInteractionEvent(
                    "pi-" + i, 1000L + i, "w1", "p1", gear, 8L, ObjectivePreset.autoSmart,
                    "plan_a", List.of("plan_b"),
                    Map.of("sys:mod:press", 1.0), List.of(Map.of("sys:mod:cast", 1.0)),
                    1.0, List.of(1.0)
                ));
            }
            bus.publish(new PlanningTelemetryEvent.CraftingExecutionEvent(
                "ce-1", 2000L, "w1", "p1", "sem:press", "mod:press", "c1", "v1", 4L, 2.5, true, ExecutionFailureCategory.other
            ));
            bus.publish(new PlanningTelemetryEvent.CraftingExecutionEvent(
                "ce-2", 2001L, "w1", "p1", "sem:press", "mod:press", "c1", "v1", 4L, 4.5, true, ExecutionFailureCategory.other
            ));
            bus.publish(new PlanningTelemetryEvent.CraftingExecutionEvent(
                "ce-cancel", 2002L, "w1", "p1", "sem:press", "mod:press", "c1", "v1", 0L, 0.0, false, ExecutionFailureCategory.userCancel
            ));

            assertEquals(7, seen.size());
            assertEquals(List.of(gear), advisor.suggestFrequentBaseCandidates(3L, 5));
            Double ema = advisor.calibratedSecondsPerBatchOrNull("mod:press");
            assertNotNull(ema);
            assertEquals(2.5 * 0.85 + 4.5 * 0.15, ema, 1e-9);
            assertEquals(
                0.0,
                advisor.failureTracker().currentFailureBurden(
                    new ExecutionFailureTracker.Key("sem:press", "c1", "v1"),
                    advisor.currentLogicTick()
                ),
                1e-12
            );
        }

        assertEquals(
            ExecutionFailureCategory.userCancel,
            ExecutionTelemetryBridge.classifyPauseReason(new PauseReason(new PauseReasonKey(Namespace.of("cirrus", "user_cancel")), null))
        );
        assertEquals(
            ExecutionFailureCategory.outOfStock,
            ExecutionTelemetryBridge.classifyPauseReason(new PauseReason(new PauseReasonKey(Namespace.of("cirrus", "stock_shortage")), null))
        );
        assertEquals(
            ExecutionFailureCategory.wrongBinding,
            ExecutionTelemetryBridge.classifyPauseReason(new PauseReason(new PauseReasonKey(Namespace.of("cirrus", "slot_mismatch")), null))
        );
        assertEquals(
            ExecutionFailureCategory.hiddenRequirement,
            ExecutionTelemetryBridge.classifyPauseReason(new PauseReason(new PauseReasonKey(Namespace.of("cirrus", "constraint_failed")), null))
        );
    }

    @Test
    void spiRegistryAndDomainProfileProviderAreComposableAndDeterministic() {
        CompiledRecipeGraph emptyGraph = compile(List.of());
        PlanningContext baseCtx = new PlanningContext(
            InventorySnapshot.empty,
            FacilitySnapshot.empty,
            new DefaultAcquisitionOracle(emptyGraph),
            DefaultHumanGuidanceView.empty(),
            ProgressionState.overworldDefault
        );

        PlanningExtensionRegistry reg = new PlanningExtensionRegistry();
        ContextKey<String> customKey = new ContextKey<>("test:tier", String.class);
        reg.registerContextContributor(new ContextContributor() {
            @Override
            public String contributorId() {
                return "test:ctx";
            }

            @Override
            public void contribute(ContextAttributeView.Builder target, PlanningContext baseContext) {
                target.put(customKey, "lv");
            }
        });
        reg.registerBaseBoundaryProvider(new BaseBoundaryProvider() {
            @Override
            public String providerId() {
                return "test:base";
            }

            @Override
            public BaseBoundarySnapshot provide(CompiledRecipeGraph graph, PlanningContext context) {
                return DefaultDomainProfileProvider.instance.provide(graph, context);
            }
        });
        reg.registerAcquisitionEstimator(new AcquisitionEstimatorSpi() {
            @Override
            public String estimatorId() {
                return "test:jer_stub";
            }

            @Override
            public int priority() {
                return 100;
            }

            @Override
            public Optional<AcquisitionEstimate> estimate(ResourceKey resource, long quantity, PlanningContext context) {
                return Optional.empty();
            }
        });
        reg.registerRecipeMetadataEnricher(new RecipeMetadataEnricher() {
            @Override
            public String enricherId() {
                return "test:enricher";
            }

            @Override
            public EnrichmentDelta enrich(RawRecipeRecord record) {
                return EnrichmentDelta.none;
            }
        });

        assertEquals(1, reg.contextContributors().size());
        assertEquals(1, reg.baseProviders().size());
        assertEquals(1, reg.acquisitionEstimators().size());
        assertEquals(1, reg.recipeEnrichers().size());

        BaseBoundarySnapshot defaultMc = DefaultDomainProfileProvider.instance.provide(emptyGraph, baseCtx);
        assertTrue(defaultMc.isProtectedRare(item("minecraft:diamond")));
        assertTrue(defaultMc.isCertifiedNatural(item("minecraft:cobblestone")));
        assertFalse(defaultMc.pricePartialOrders().isEmpty());
    }
}
