package dev.vfyjxf.cirrus.internal.debug;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.content.complex.DurabilityContent;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotAddress;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotChannel;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.GridDiff;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionPipeline;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionResult;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionStage;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.SlotDiff;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.content.ContentApiImpl;
import dev.vfyjxf.cirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.cirrus.internal.InternalPlugin;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class DebugProjectionPluginTest {

    @Test
    void debugProjectionPluginRegistersAConfigurableDevelopmentProbeRecipe() {
        assumeTrue(DebugProjectionPlugin.debugEnabled());

        DebugProjectionPlugin plugin = new DebugProjectionPlugin();
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        InternalPlugin internal = new InternalPlugin();
        internal.registerProjectionPlugin(builder);
        internal.registerProjectionEffect(builder);
        plugin.registerProjectionPlugin(builder);
        plugin.registerProjectionContextOption(builder);
        plugin.registerRecipeSystem(builder);
        plugin.registerProjectionEffect(builder);
        plugin.registerMachineEntry(builder);

        RecipeApi previous = RecipeApiImpl.instance();
        ContentApiImpl previousContent = ContentApiImpl.instance();
        attachContentApi();
        RecipeApiImpl api = new RecipeApiImpl(builder);
        RecipeApiImpl.attach(api);
        try {
            assertNotNull(api.findProjectionPlugin(DebugProjectionPlugin.plugin.id()));
            assertTrue(
                api.projectionContextOptions().anySatisfy(option -> option.key().equals(DebugProjectionPlugin.scaleKey))
            );
            assertTrue(
                api.projectionContextOptions()
                        .anySatisfy(option -> option.key().equals(DebugProjectionPlugin.extraOutputKey))
            );
            assertTrue(
                api.projectionContextOptions()
                        .anySatisfy(option -> option.key().equals(DebugProjectionPlugin.channelKey))
            );

            @SuppressWarnings("rawtypes")
            RecipeSystem system = api.findSystem(DebugProjectionPlugin.systemId);
            assertNotNull(system);
            Object recipe = system.findRecipe("projection_probe");
            assertNotNull(recipe);
            @SuppressWarnings("unchecked")
            RecipeProjection projection = api.resolve(ResolveContext.empty(), system, recipe).projection();
            assertNotNull(projection);

            ExecutionContext context = new ExecutionContext().set(DebugProjectionPlugin.scaleKey, 2L)
                    .set(DebugProjectionPlugin.extraOutputKey, true).set(DebugProjectionPlugin.channelKey, "input");
            ProjectionPipeline pipeline = ProjectionPipeline.fromRegistry();
            ProjectionResult first = pipeline.trace(context, projection);
            ProjectionResult second = pipeline.trace(context, projection);
            ProjectionResult scaled = pipeline.trace(context.set(DebugProjectionPlugin.scaleKey, 3L), projection);
            ProjectionResult restored = pipeline.trace(context.set(DebugProjectionPlugin.scaleKey, 2L), projection);

            assertTrue(first.allChanges().size() >= 2);
            assertEquals(1, first.diff().slotDiffs(SlotChannel.output).stream().filter(d -> d.status() == SlotDiff.DiffStatus.added).count());
            assertEquals(4L, first.projectedRecipe().inputs().getFirst().amount());
            assertEquals(4L, second.projectedRecipe().inputs().getFirst().amount());
            assertEquals(6L, scaled.projectedRecipe().inputs().getFirst().amount());
            assertEquals(4L, restored.projectedRecipe().inputs().getFirst().amount());
            assertEquals(2L, projection.inputs().getFirst().content().amount());
            assertTrue(first.pluginSummary(DebugProjectionPlugin.plugin).inputChangeCount() >= 1);
            assertTrue(first.pluginSummary(DebugProjectionPlugin.plugin).outputChangeCount() >= 1);
        } finally {
            if (previous instanceof RecipeApiImpl impl) {
                RecipeApiImpl.attach(impl);
            }
            if (previousContent != null) {
                ContentApiImpl.attach(previousContent);
            }
        }
    }

    @Test
    void debugProjectionPluginPublishesBuiltInProbeAndCraftingGridRecipes(MinecraftServer server) {
        assumeTrue(DebugProjectionPlugin.debugEnabled());

        RecipeApi previous = RecipeApiImpl.instance();
        ContentApiImpl previousContent = ContentApiImpl.instance();
        attachContentApi();
        RecipeApiImpl api = attachDebugRecipeApi();
        try {
            @SuppressWarnings("unchecked")
            RecipeSystem<DebugProjectionPlugin.DebugProjectionRecipe> system = (RecipeSystem<DebugProjectionPlugin.DebugProjectionRecipe>) api
                    .findSystem(DebugProjectionPlugin.systemId);
            assertNotNull(system);
            assertEquals(11 + DebugProjectionPlugin.longChainDepth + 1, system.allRecipes().size());

            ProjectionResult unpack = trace(api, system, "fluid_container_unpack", new ExecutionContext());
            assertTrue(
                unpack.diff().slotDiffs(SlotChannel.input).stream()
                        .anyMatch(d -> d.status() == SlotDiff.DiffStatus.transformed
                                && d.touchedByEffects().contains(CirrusTypes.ProjectionEffects.fluidContainerUnpack.id()))
            );
            assertEquals(2, unpack.projectedRecipe().inputs().size());

            ProjectionResult durability = trace(api, system, "durability_cost", new ExecutionContext());
            long transformedTools = durability.diff().slotDiffs(SlotChannel.input).stream()
                    .filter(
                        d -> d.status() == SlotDiff.DiffStatus.transformed
                                && d.after() instanceof DurabilityContent
                                && d.touchedByEffects().contains(CirrusTypes.ProjectionEffects.durabilityCost.id())
                    )
                    .count();
            assertEquals(2L, transformedTools);
            assertEquals(2, durability.projectedRecipe().inputs().size());
            assertTrue(
                durability.projectedRecipe().inputs().anySatisfy(
                    in -> in instanceof DurabilityContent dc && dc.durabilityCost() == 1
                )
            );
            assertTrue(
                durability.projectedRecipe().inputs().anySatisfy(
                    in -> in instanceof DurabilityContent dc && dc.durabilityCost() == 4
                )
            );

            ProjectionResult guaranteed = trace(api, system, "guaranteed_output", new ExecutionContext());
            assertTrue(
                guaranteed.diff().slotDiffs(SlotChannel.output).stream()
                        .anyMatch(d -> d.status() == SlotDiff.DiffStatus.removed
                                && d.touchedByEffects().contains(CirrusTypes.ProjectionEffects.guaranteedOutput.id()))
            );
            assertEquals(1, guaranteed.projectedRecipe().outputs().size());

            ProjectionResult resolveEffect = trace(api, system, "resolve_policy_effect", new ExecutionContext());
            assertTrue(resolveEffect.runnable());
            assertEquals(5L, resolveEffect.projectedRecipe().inputs().getFirst().amount());
            assertTrue(resolveEffect.steps().anySatisfy(s -> DebugProjectionPlugin.resolveBoostInputId.equals(s.effectId())));

            ProjectionResult resolveGuard = trace(api, system, "resolve_policy_guard", new ExecutionContext());
            assertFalse(resolveGuard.runnable());
            assertEquals(1, resolveGuard.guardFindings().size());
            assertEquals(DebugProjectionPlugin.resolveGuardFailId, resolveGuard.guardFindings().getFirst().guardId());
            assertEquals("debug_guard_failed", resolveGuard.guardFindings().getFirst().reasonCode());
            assertTrue(resolveGuard.allChanges().isEmpty());
            assertEquals(2L, resolveGuard.projectedRecipe().inputs().getFirst().amount());

            ExecutionContext gridCtx = new ExecutionContext().set(DebugProjectionPlugin.scaleKey, 3L);
            ProjectionResult gridResult = trace(api, system, "crafting_grid_transform", gridCtx);
            GridDiff gridDiff = gridResult.diff().inputGridDiff();
            assertNotNull(gridDiff);
            assertEquals(SlotDiff.DiffStatus.transformed, gridDiff.cell(0, 0).status());
            assertTrue(gridDiff.cell(0, 0).after() instanceof DurabilityContent);
            assertEquals(SlotDiff.DiffStatus.transformed, gridDiff.cell(1, 1).status());
            assertEquals(SlotDiff.DiffStatus.adjusted, gridDiff.cell(2, 1).status());
            assertEquals("\u00d73", gridDiff.cell(2, 1).deltaBadge());
            assertEquals(SlotDiff.DiffStatus.unchanged, gridDiff.cell(1, 2).status());
        } finally {
            if (previous instanceof RecipeApiImpl impl) {
                RecipeApiImpl.attach(impl);
            }
            if (previousContent != null) {
                ContentApiImpl.attach(previousContent);
            }
        }
    }

    @Test
    void debugProjectionPluginPublishesRichMultiStageAndGuardShowcaseRecipes(MinecraftServer server) {
        assumeTrue(DebugProjectionPlugin.debugEnabled());

        RecipeApi previous = RecipeApiImpl.instance();
        ContentApiImpl previousContent = ContentApiImpl.instance();
        attachContentApi();
        RecipeApiImpl api = attachDebugRecipeApi();
        try {
            assertNotNull(api.findProjectionPlugin(DebugProjectionPlugin.overclockPlugin.id()));
            @SuppressWarnings("unchecked")
            RecipeSystem<DebugProjectionPlugin.DebugProjectionRecipe> system = (RecipeSystem<DebugProjectionPlugin.DebugProjectionRecipe>) api
                    .findSystem(DebugProjectionPlugin.systemId);
            assertNotNull(system);

            ProjectionResult full = trace(api, system, "full_pipeline_showcase", new ExecutionContext());
            assertTrue(full.runnable());
            assertTrue(full.pluginSummary(ProjectionPlugin.builtin).totalChangeCount() >= 4);
            assertTrue(full.pluginSummary(DebugProjectionPlugin.overclockPlugin).totalChangeCount() >= 4);
            assertTrue(full.pluginSummary(DebugProjectionPlugin.plugin).totalChangeCount() >= 1);
            assertTrue(full.projected().tags().has(DebugProjectionPlugin.overclockedTag));
            assertEquals(
                Integer.valueOf(4),
                full.projected().attributes().find(DebugProjectionPlugin.overclockMultiplierAttr)
            );
            assertTrue(
                full.stepsInStage(ProjectionStage.finalize).stream()
                        .anyMatch(s -> DebugProjectionPlugin.thermalByproductEffectId.equals(s.effectId()))
            );

            ProjectionResult blockedHv = trace(
                api,
                system,
                "full_pipeline_showcase",
                new ExecutionContext()
                        .set(DebugProjectionPlugin.overclockTierKey, 3L)
                        .set(DebugProjectionPlugin.highVoltageKey, false)
            );
            assertFalse(blockedHv.runnable());
            assertTrue(
                blockedHv.guardFindings().anySatisfy(
                    f -> !f.passed()
                            && f.blocking()
                            && DebugProjectionPlugin.hvSupplyGuardId.equals(f.guardId())
                            && "hv_power_required_for_tier_3_plus".equals(f.reasonCode())
                )
            );

            ProjectionResult validatedHv = trace(
                api,
                system,
                "full_pipeline_showcase",
                new ExecutionContext()
                        .set(DebugProjectionPlugin.overclockTierKey, 3L)
                        .set(DebugProjectionPlugin.highVoltageKey, true)
            );
            assertTrue(validatedHv.runnable());
            assertTrue(validatedHv.projected().tags().has(DebugProjectionPlugin.hvValidatedTag));

            ProjectionResult rearrange = trace(
                api,
                system,
                "grid_cell_rearrange",
                new ExecutionContext().set(DebugProjectionPlugin.scaleKey, 2L)
            );
            GridDiff rearrangeGrid = rearrange.diff().inputGridDiff();
            assertNotNull(rearrangeGrid);
            assertEquals(SlotDiff.DiffStatus.moved, rearrangeGrid.cell(2, 2).status());
            assertEquals(SlotAddress.inputGrid(0, 0, 3), rearrangeGrid.cell(2, 2).movedFrom());
            assertEquals(SlotDiff.DiffStatus.transformed, rearrangeGrid.cell(1, 1).status());
            assertEquals(SlotDiff.DiffStatus.transformed, rearrangeGrid.cell(0, 2).status());
            assertEquals(SlotDiff.DiffStatus.adjusted, rearrangeGrid.cell(1, 0).status());

            ProjectionResult tier1 = trace(
                api,
                system,
                "overclock_energy_line",
                new ExecutionContext().set(DebugProjectionPlugin.overclockTierKey, 1L)
            );
            assertTrue(tier1.runnable());
            assertTrue(
                tier1.guardFindings().anySatisfy(
                    f -> !f.passed()
                            && !f.blocking()
                            && DebugProjectionPlugin.thermalThresholdGuardId.equals(f.guardId())
                            && "overclock_tier_below_2".equals(f.reasonCode())
                )
            );
            InputContent tier1Energy = tier1.projectedRecipe().inputs().detect(DebugProjectionPluginTest::isEnergyInput);
            OutputContent tier1Time = tier1.projectedRecipe().outputs().detect(DebugProjectionPluginTest::isTimeOutput);
            assertNotNull(tier1Energy);
            assertNotNull(tier1Time);
            assertEquals(8192L, tier1Energy.amount());
            assertEquals(80L, tier1Time.amount());

            ProjectionResult tier2Batch = trace(
                api,
                system,
                "overclock_energy_line",
                new ExecutionContext()
                        .set(DebugProjectionPlugin.overclockTierKey, 2L)
                        .set(DebugProjectionPlugin.parallelBatchKey, 3L)
            );
            assertTrue(tier2Batch.runnable());
            InputContent tier2Energy = tier2Batch.projectedRecipe().inputs()
                    .detect(DebugProjectionPluginTest::isEnergyInput);
            OutputContent tier2Time = tier2Batch.projectedRecipe().outputs()
                    .detect(DebugProjectionPluginTest::isTimeOutput);
            assertNotNull(tier2Energy);
            assertNotNull(tier2Time);
            assertEquals(32_768L, tier2Energy.amount());
            assertEquals(40L, tier2Time.amount());
            assertEquals(12L, tier2Batch.projectedRecipe().inputs().getFirst().amount());
            assertEquals(6L, tier2Batch.projectedRecipe().outputs().getFirst().amount());

            ProjectionResult stress = trace(api, system, "multi_effect_stress", new ExecutionContext());
            assertTrue(stress.runnable());
            assertTrue(stress.allChanges().size() >= 10);
            long distinctEffects = stress.steps().select(step -> !step.changes().isEmpty())
                    .collect(step -> step.effectId())
                    .toSet()
                    .size();
            assertTrue(distinctEffects >= 5);
        } finally {
            if (previous instanceof RecipeApiImpl impl) {
                RecipeApiImpl.attach(impl);
            }
            if (previousContent != null) {
                ContentApiImpl.attach(previousContent);
            }
        }
    }

    @Test
    void debugProjectionPluginPublishesLongCalculatorStressChain(MinecraftServer server) {
        assumeTrue(DebugProjectionPlugin.debugEnabled());

        RecipeApi previous = RecipeApiImpl.instance();
        ContentApiImpl previousContent = ContentApiImpl.instance();
        attachContentApi();
        RecipeApiImpl api = attachDebugRecipeApi();
        try {
            @SuppressWarnings("unchecked")
            RecipeSystem<DebugProjectionPlugin.DebugProjectionRecipe> system = (RecipeSystem<DebugProjectionPlugin.DebugProjectionRecipe>) api
                    .findSystem(DebugProjectionPlugin.systemId);
            assertNotNull(system);
            DebugProjectionPlugin.DebugProjectionRecipe targetRecipe = system
                    .findRecipe(DebugProjectionPlugin.longChainTargetId);
            assertNotNull(targetRecipe);

            ProjectionResult targetResult = trace(api, system, DebugProjectionPlugin.longChainTargetId, new ExecutionContext());
            assertTrue(targetResult.runnable());
            assertTrue(
                targetResult.steps().anySatisfy(step -> DebugProjectionPlugin.resolveBoostInputId.equals(step.effectId()))
            );
            assertTrue(targetResult.projectedRecipe().outputs().anySatisfy(DebugProjectionPluginTest::isEnergyOutput));
            assertTrue(targetResult.projectedRecipe().outputs().anySatisfy(DebugProjectionPluginTest::isTimeOutput));

            assertNotNull(system.findRecipe("debug_chain_step_" + (DebugProjectionPlugin.longChainDepth - 1)));
            assertNull(system.findRecipe("debug_chain_step_" + DebugProjectionPlugin.longChainDepth));
        } finally {
            if (previous instanceof RecipeApiImpl impl) {
                RecipeApiImpl.attach(impl);
            }
            if (previousContent != null) {
                ContentApiImpl.attach(previousContent);
            }
        }
    }

    private static RecipeApiImpl attachDebugRecipeApi() {
        DebugProjectionPlugin plugin = new DebugProjectionPlugin();
        InternalPlugin internal = new InternalPlugin();
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        internal.registerProjectionPlugin(builder);
        internal.registerProjectionEffect(builder);
        plugin.registerProjectionPlugin(builder);
        plugin.registerProjectionContextOption(builder);
        plugin.registerRecipeSystem(builder);
        plugin.registerProjectionEffect(builder);
        plugin.registerMachineEntry(builder);
        RecipeApiImpl api = new RecipeApiImpl(builder);
        RecipeApiImpl.attach(api);
        return api;
    }

    private static ProjectionResult trace(
        RecipeApi api,
        RecipeSystem<DebugProjectionPlugin.DebugProjectionRecipe> system,
        String recipeId,
        ExecutionContext ctx
    ) {
        DebugProjectionPlugin.DebugProjectionRecipe recipe = system.findRecipe(recipeId);
        assertNotNull(recipe);
        RecipeProjection projection = api.resolve(ResolveContext.empty(), system, recipe).projection();
        assertNotNull(projection);
        return ProjectionPipeline.fromRegistry().trace(ctx, projection);
    }

    private static boolean isEnergyInput(InputContent input) {
        return input instanceof ValueContent<?> value
                && value.type().typeId().equals(Namespace.of("calculator_cirrus", "energy"));
    }

    private static boolean isEnergyOutput(OutputContent output) {
        return output instanceof ValueContent<?> value
                && value.type().typeId().equals(Namespace.of("calculator_cirrus", "energy"));
    }

    private static boolean isTimeOutput(OutputContent output) {
        return output instanceof ValueContent<?> value
                && value.type().typeId().equals(Namespace.of("calculator_cirrus", "time"));
    }

    private static void attachContentApi() {
        ContentRegistryBuilder builder = new ContentRegistryBuilder();
        InternalPlugin internal = new InternalPlugin();
        internal.registerContentType(builder);
        internal.registerContentVariant(builder);
        internal.registerContentResolver(builder);
        ContentApiImpl.attach(new ContentApiImpl(builder));
    }
}
