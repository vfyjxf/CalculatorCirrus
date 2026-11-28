package dev.vfyjxf.cirrus.planning;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
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
import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.spi.AcquisitionEstimatorSpi;
import dev.vfyjxf.cirrus.api.planning.spi.BaseBoundaryProvider;
import dev.vfyjxf.cirrus.api.planning.spi.ContextContributor;
import dev.vfyjxf.cirrus.api.planning.spi.DecisionDetectorExtension;
import dev.vfyjxf.cirrus.api.planning.spi.PlanningTelemetrySink;
import dev.vfyjxf.cirrus.api.planning.spi.RecipeMetadataEnricher;
import dev.vfyjxf.cirrus.api.planning.spi.ScoringContributor;
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
import dev.vfyjxf.cirrus.planning.normalize.DualTagClassifier;
import dev.vfyjxf.cirrus.planning.normalize.JeiNormalizationPatch;
import dev.vfyjxf.cirrus.planning.normalize.JeiRecipeNormalizer;
import dev.vfyjxf.cirrus.planning.telemetry.ExecutionTelemetryBridge;
import dev.vfyjxf.cirrus.planning.telemetry.LocalAnalyticsAdvisor;
import dev.vfyjxf.cirrus.planning.telemetry.PlanningTelemetryBus;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.compile;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.item;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.machine;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.ns;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.out;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.rec;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the dual-layer canonical tagging pipeline, the 4-stage JEI normalizer,
 * the telemetry bus/advisor/execution bridge, and all seven planning SPI extension points.
 */
class PlanningTelemetryAndExtensionTest {

    @Test
    void dualTagClassifierDetectsCanonicalRolesWithoutWordlists() {
        ComponentStackKey ingot = item("test:ingot");
        ComponentStackKey block = item("test:block");
        ComponentStackKey seed = item("test:seed");
        ComponentStackKey dust = item("test:dust");

        RawRecipeRecord pack = rec("minecraft:crafting", "pack", List.of(), List.of(slot(ingot, 9)), List.of(out(block, 1)));
        RawRecipeRecord planks = rec("minecraft:crafting", "planks_from_log", List.of(), List.of(slot(item("test:log"), 1)), List.of(out(item("test:plank"), 4)));
        RawRecipeRecord loop = rec("mod:grow", "grow", List.of("mod:chamber"), List.of(slot(seed, 1)), List.of(out(seed, 2)));
        RawRecipeRecord gen = new RawRecipeRecord(
            ns("mod:void_miner"), "mod_native", Optional.of(ResourceLocation.parse("gen_1")),
            List.of(machine("mod:miner")),
            List.of(new RawIngredientSlot(Optional.empty(), List.of(item("mod:lens")), 0L, SlotMatchMode.identity, 0, 1)),
            List.of(out(dust, 1)), Map.of(), 0L,
            Set.of(RecipeSystemTags.resourceGen), Set.of(),
            RecipeEvidenceTier.tierA, 1.0, Optional.empty(), false
        );

        Set<CanonicalRecipeTag> packTags = DualTagClassifier.tagSet(DualTagClassifier.classify(pack, false, false, true));
        Set<CanonicalRecipeTag> isolatedPackTags = DualTagClassifier.tagSet(DualTagClassifier.classify(pack, false, false, false));
        Set<CanonicalRecipeTag> planksTags = DualTagClassifier.tagSet(DualTagClassifier.classify(planks, false, false, false));
        Set<CanonicalRecipeTag> loopTags = DualTagClassifier.tagSet(DualTagClassifier.classify(loop, false, false));
        Set<CanonicalRecipeTag> genTags = DualTagClassifier.tagSet(DualTagClassifier.classify(gen, false, false));
        Set<CanonicalRecipeTag> mirrorTags = DualTagClassifier.tagSet(DualTagClassifier.classify(pack, true, false, true));
        Set<CanonicalRecipeTag> suspectTags = DualTagClassifier.tagSet(DualTagClassifier.classify(pack, false, true, true));

        assertTrue(packTags.contains(CanonicalRecipeTag.packingUnpacking));
        assertFalse(isolatedPackTags.contains(CanonicalRecipeTag.packingUnpacking), "9:1 scaling alone must not be packing without a reverse recipe");
        assertFalse(planksTags.contains(CanonicalRecipeTag.packingUnpacking), "1 log -> 4 planks must not be misclassified as unpacking");
        assertTrue(loopTags.contains(CanonicalRecipeTag.catalyticLoop));
        assertTrue(genTags.contains(CanonicalRecipeTag.resourceGeneration));
        assertTrue(mirrorTags.contains(CanonicalRecipeTag.junkOrDuplicate));
        assertTrue(suspectTags.contains(CanonicalRecipeTag.suspiciousJei));
    }

    @Test
    void jeiNormalizerDedupesMirrorsAppliesJ4PatchesAndComputesFidelity() {
        ComponentStackKey ore = item("test:ore");
        ComponentStackKey ingot = item("test:ingot");
        ComponentStackKey catalyst = item("test:press_mold");
        ComponentStackKey plate = item("test:plate");

        RawRecipeRecord nativeSmelt = new RawRecipeRecord(
            ns("minecraft:smelting"), "vanilla", Optional.of(ResourceLocation.parse("vanilla:smelt_ingot")),
            List.of(machine("minecraft:furnace")),
            List.of(slot(ore, 1)), List.of(out(ingot, 1)), Map.of(),
            1L, Set.of(RecipeSystemTags.vanilla), Set.of(CanonicalRecipeTag.normalProduction),
            RecipeEvidenceTier.tierA, 1.0, Optional.empty(), false
        );
        RawRecipeRecord jeiMirror = new RawRecipeRecord(
            ns("jei:category/minecraft/furnace"), "jei_fallback", Optional.empty(),
            List.of(machine("minecraft:furnace")),
            List.of(slot(ore, 1)), List.of(out(ingot, 1)), Map.of(),
            2L, Set.of(RecipeSystemTags.jeiFallback), Set.of(CanonicalRecipeTag.normalProduction),
            RecipeEvidenceTier.tierC, 1.0, Optional.empty(), false
        );
        RawRecipeRecord jeiMoldRecipe = new RawRecipeRecord(
            ns("jei:category/mod/inscriber"), "jei_fallback", Optional.of(ResourceLocation.parse("mod:press_plate")),
            List.of(),
            List.of(slot(catalyst, 1), slot(ingot, 1)), List.of(out(plate, 1)), Map.of(),
            3L, Set.of(RecipeSystemTags.jeiFallback), Set.of(CanonicalRecipeTag.normalProduction),
            RecipeEvidenceTier.tierC, 1.0, Optional.empty(), false
        );

        JeiNormalizationPatch patch = new JeiNormalizationPatch(
            "patch:inscriber_mold",
            Set.of(ns("jei:category/mod/inscriber")),
            "press_plate",
            Set.of(),
            List.of(machine("mod:inscriber")),
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
        assertTrue(outPatched.machines().contains(machine("mod:inscriber")));
        assertTrue(outPatched.inputs().get(0).catalyst(), "J4 patch must promote slot 0 to catalyst");
        assertEquals(RecipeEvidenceTier.tierB, outPatched.evidenceTier());
        assertEquals(0.92, outPatched.structuralFidelity(), 1e-9);
        assertTrue(outPatched.semanticKey().isPresent());
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

            ComponentStackKey gear = item("test:gear");
            for (int i = 0; i < 4; i++) {
                bus.publish(new PlanningTelemetryEvent.PlanInteractionEvent(
                    "pi-" + i, 1000L + i, "w1", "p1", gear, 8L, ObjectivePreset.autoSmart,
                    "plan_a", List.of("plan_b"),
                    Map.of("sys:mod:press", 1.0), List.of(Map.of("sys:mod:cast", 1.0)),
                    1.0, List.of(1.0)
                ));
            }
            bus.publish(new PlanningTelemetryEvent.CraftingExecutionEvent(
                "ce-1", 2000L, "w1", "p1", "sem:press", ns("mod:press"), "c1", "v1", 4L, 2.5, true, ExecutionFailureCategory.other
            ));
            bus.publish(new PlanningTelemetryEvent.CraftingExecutionEvent(
                "ce-2", 2001L, "w1", "p1", "sem:press", ns("mod:press"), "c1", "v1", 4L, 4.5, true, ExecutionFailureCategory.other
            ));
            bus.publish(new PlanningTelemetryEvent.CraftingExecutionEvent(
                "ce-cancel", 2002L, "w1", "p1", "sem:press", ns("mod:press"), "c1", "v1", 0L, 0.0, false, ExecutionFailureCategory.userCancel
            ));

            assertEquals(7, seen.size());
            assertEquals(List.of(gear), advisor.suggestFrequentBaseCandidates(3L, 5));
            Double ema = advisor.calibratedSecondsPerBatchOrNull(ns("mod:press"));
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
        ComponentStackKey ore = item("test:ore");
        ComponentStackKey gear = item("test:gear");
        CompiledRecipeGraph graph = compile(
            List.of(rec("minecraft:crafting", "test:gear", List.of("minecraft:crafting_table"), List.of(slot(ore, 1)), List.of(out(gear, 1))))
        );
        PlanningContext baseCtx = new PlanningContext(
            InventorySnapshot.empty,
            FacilitySnapshot.empty,
            new DefaultAcquisitionOracle(graph),
            DefaultHumanGuidanceView.empty(),
            ProgressionState.overworldDefault
        );

        PlanningExtensionRegistry reg = new PlanningExtensionRegistry();
        ContextKey<String> customKey = new ContextKey<>("test:tier", String.class);
        ContextContributor contributor = new ContextContributor() {
            @Override
            public String contributorId() {
                return "test:ctx";
            }

            @Override
            public void contribute(ContextAttributeView.Builder target, PlanningContext baseContext) {
                target.put(customKey, "lv");
            }
        };
        reg.registerContextContributor(contributor);
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
            public Optional<AcquisitionEstimate> estimate(ComponentStackKey resource, long quantity, PlanningContext context) {
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
                return RecipeMetadataEnricher.EnrichmentDelta.none;
            }
        });
        ScoringContributor scoring = new ScoringContributor() {
            @Override
            public String contributorId() {
                return "test:scoring";
            }

            @Override
            public double recipeSoftBiasSeconds(CompiledRecipe recipe, PlanningContext context) {
                return 1.5;
            }

            @Override
            public double resourceSoftBiasSeconds(ComponentStackKey resource, PlanningContext context) {
                return 2.0;
            }
        };
        reg.registerScoringContributor(scoring);
        reg.registerDecisionDetector(new DecisionDetectorExtension() {
            @Override
            public String detectorId() {
                return "test:detector";
            }

            @Override
            public List<DecisionPrompt> detect(PlanningRequest request, List<ProductionPlan> plans, CompiledRecipeGraph graph) {
                return List.of();
            }
        });
        reg.registerTelemetrySink(new PlanningTelemetrySink() {
            @Override
            public String sinkId() {
                return "test:sink";
            }

            @Override
            public void onEvent(PlanningTelemetryEvent event) {}
        });

        assertEquals(1, reg.contextContributors().size());
        assertEquals(1, reg.baseProviders().size());
        assertEquals(1, reg.acquisitionEstimators().size());
        assertEquals(1, reg.recipeEnrichers().size());
        assertEquals(1, reg.scoringContributors().size());
        assertEquals(1, reg.decisionDetectors().size());
        assertEquals(1, reg.telemetrySinks().size());

        ContextAttributeView.Builder attributes = ContextAttributeView.builder();
        contributor.contribute(attributes, baseCtx);
        assertEquals(Optional.of("lv"), attributes.build().get(customKey));

        CompiledRecipe recipe = graph.producersOf(gear).get(0);
        assertEquals(1.5, scoring.recipeSoftBiasSeconds(recipe, baseCtx), 1e-12);
        assertEquals(2.0, scoring.resourceSoftBiasSeconds(ore, baseCtx), 1e-12);

        BaseBoundarySnapshot defaultMc = DefaultDomainProfileProvider.instance.provide(graph, baseCtx);
        assertTrue(defaultMc.isProtectedRare(ComponentStackKey.ofItem(Items.DIAMOND)));
        assertTrue(defaultMc.isCertifiedNatural(ComponentStackKey.ofItem(Items.COBBLESTONE)));
        assertFalse(defaultMc.pricePartialOrders().isEmpty());
        assertEquals(3, defaultMc.pricePartialOrders().size());
    }
}
