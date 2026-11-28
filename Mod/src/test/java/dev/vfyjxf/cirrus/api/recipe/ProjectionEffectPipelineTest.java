package dev.vfyjxf.cirrus.api.recipe;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.complex.FluidContainerContent;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContextKey;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectedRecipe;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionAttributes;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionKeys;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionTags;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotAddress;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotChannel;
import dev.vfyjxf.cirrus.api.recipe.projection.TagQuery;
import dev.vfyjxf.cirrus.api.recipe.projection.display.ProjectionDisplayModel;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ChangeReason;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.GridDiff;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionDiff;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionGuard;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionPipeline;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionResult;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionStage;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.SlotDiff;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.recipe.projection.BuiltinProjectionEffects;
import dev.vfyjxf.cirrus.recipe.system.BasicRecipeSystem;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
public class ProjectionEffectPipelineTest {

    private static final TestRecipeSystem testSystem = new TestRecipeSystem();
    private static final TestRecipe dummyRecipe = new TestRecipe("test_recipe");

    @Test
    void fluidContainerUnpackPreservesGridCellAndInjectsNamedCatalyst(MinecraftServer server) {
        RecipeProjection raw = RecipeProjection.builder(testSystem, dummyRecipe)
                .inputGrid(3, 3)
                .inputCell(
                    1,
                    1,
                    InputContent.fromItemStack(new ItemStack(Items.WATER_BUCKET)),
                    ProjectionAttributes.empty(),
                    ProjectionTags.of(ProjectionKeys.Tags.fluidContainer)
                )
                .inputCell(0, 0, InputContent.fromItemStack(new ItemStack(Items.STICK)))
                .output(OutputContent.fromItemStack(new ItemStack(Items.STONE)))
                .build();

        ProjectionPipeline pipeline = ProjectionPipeline.of(BuiltinProjectionEffects.fluidContainerUnpack);
        ProjectionResult result = pipeline.trace(new ExecutionContext(), raw);
        ProjectedRecipe projected = result.projectedRecipe();

        assertTrue(result.runnable());
        assertEquals(3, projected.inputs().size());
        assertTrue(projected.inputs().anySatisfy(FluidContainerContent.class::isInstance));

        GridDiff inputGrid = result.diff().inputGridDiff();
        assertNotNull(inputGrid);
        assertTrue(inputGrid.hasChanges());
        assertEquals(SlotDiff.DiffStatus.unchanged, inputGrid.cell(0, 0).status());

        GridDiff.CellDiff unpackedCell = inputGrid.cell(1, 1);
        assertEquals(SlotDiff.DiffStatus.transformed, unpackedCell.status());
        assertEquals(SlotAddress.inputGrid(1, 1, 3), unpackedCell.address());
        assertNotNull(unpackedCell.before());
        assertNotNull(unpackedCell.after());
        assertEquals(1L, unpackedCell.before().amount());
        assertTrue(unpackedCell.after().amount() > 0L);
        assertEquals("\u21c4", unpackedCell.deltaBadge());
        assertTrue(unpackedCell.touchedByEffects().contains(CirrusTypes.ProjectionEffects.fluidContainerUnpack.id()));

        List<SlotDiff> loose = result.diff().looseSlotDiffs(SlotChannel.input);
        assertEquals(1, loose.size());
        SlotDiff containerDiff = loose.getFirst();
        assertEquals(SlotDiff.DiffStatus.added, containerDiff.status());
        assertNull(containerDiff.before());
        assertTrue(containerDiff.after() instanceof FluidContainerContent);
        FluidContainerContent containerReq = (FluidContainerContent) Objects.requireNonNull(containerDiff.after());
        assertFalse(containerReq.isEnumerable());
        assertTrue(containerReq.satisfy(ContentStack.fromStack(new ItemStack(Items.WATER_BUCKET))));
    }

    @Test
    void durabilityCostRemovesBreakingToolAtGridCellAndRetainsHealthyTool(MinecraftServer server) {
        ItemStack breaking = new ItemStack(Items.IRON_PICKAXE);
        breaking.setDamageValue(Math.max(0, breaking.getMaxDamage() - 1));
        ItemStack healthy = new ItemStack(Items.IRON_PICKAXE);

        RecipeProjection raw = RecipeProjection.builder(testSystem, dummyRecipe)
                .inputGrid(3, 3)
                .inputCell(
                    0,
                    0,
                    InputContent.fromItemStack(breaking),
                    ProjectionAttributes.of(ProjectionKeys.durabilityCost, 1),
                    ProjectionTags.of(ProjectionKeys.Tags.durabilityTool)
                )
                .inputCell(
                    2,
                    0,
                    InputContent.fromItemStack(healthy),
                    ProjectionAttributes.of(ProjectionKeys.durabilityCost, 1),
                    ProjectionTags.of(ProjectionKeys.Tags.durabilityTool)
                )
                .output(OutputContent.fromItemStack(new ItemStack(Items.IRON_NUGGET)))
                .build();

        ProjectionResult result = ProjectionPipeline.of(BuiltinProjectionEffects.durabilityCost)
                .trace(new ExecutionContext(), raw);

        assertEquals(1, result.projectedRecipe().inputs().size());
        GridDiff grid = result.diff().inputGridDiff();
        assertNotNull(grid);
        assertEquals(SlotDiff.DiffStatus.removed, grid.cell(0, 0).status());
        assertEquals("\u00d7", grid.cell(0, 0).deltaBadge());
        assertNull(grid.cell(0, 0).after());
        assertNotNull(grid.cell(0, 0).before());
        assertEquals(SlotDiff.DiffStatus.unchanged, grid.cell(2, 0).status());
    }

    @Test
    void guaranteedOutputTagFiltersProbabilisticOutputs(MinecraftServer server) {
        RecipeProjection raw = RecipeProjection.builder(testSystem, dummyRecipe)
                .input(InputContent.fromItemStack(new ItemStack(Items.STICK)))
                .output(OutputContent.fromItemStack(new ItemStack(Items.DIAMOND), 1, 0.25F))
                .output(OutputContent.fromItemStack(new ItemStack(Items.COBBLESTONE)))
                .tag(ProjectionKeys.Tags.guaranteedOutput)
                .build();

        ProjectionResult result = ProjectionPipeline.of(BuiltinProjectionEffects.guaranteedOutput)
                .trace(new ExecutionContext(), raw);

        assertEquals(1, result.projectedRecipe().outputs().size());
        SlotDiff diamondDiff = result.diff().slotDiff(SlotAddress.output(0));
        assertNotNull(diamondDiff);
        assertEquals(SlotDiff.DiffStatus.removed, diamondDiff.status());
        assertTrue(diamondDiff.touchedByEffects().contains(CirrusTypes.ProjectionEffects.guaranteedOutput.id()));
        SlotDiff cobbleDiff = result.diff().slotDiff(SlotAddress.output(1));
        assertNotNull(cobbleDiff);
        assertEquals(SlotDiff.DiffStatus.unchanged, cobbleDiff.status());
    }

    @Test
    void craftingGridTracksCellMoveScaleRemoveAndDisplayModel(MinecraftServer server) {
        RecipeProjection raw = RecipeProjection.builder(testSystem, dummyRecipe)
                .inputGrid(3, 3)
                .inputCell(0, 0, InputContent.fromItemStack(new ItemStack(Items.IRON_INGOT, 1)))
                .inputCell(1, 0, InputContent.fromItemStack(new ItemStack(Items.GOLD_INGOT, 2)))
                .inputCell(0, 2, InputContent.fromItemStack(new ItemStack(Items.COAL, 1)))
                .output(OutputContent.fromItemStack(new ItemStack(Items.COMPASS, 1)))
                .build();

        Namespace gridManipId = Namespace.of(CirrusConstants.modId, "test/grid_manip");
        ProjectionEffect gridManip = ProjectionEffect.builder(gridManipId)
                .stage(ProjectionStage.transform)
                .apply(tx -> {
                    tx.moveGridCell(SlotChannel.input, 0, 0, 2, 2);
                    tx.setSlotAmount(SlotAddress.inputGrid(1, 0, 3), 6L);
                    tx.clearGridCell(SlotChannel.input, 0, 2);
                    tx.setGridCell(
                        SlotChannel.input,
                        1,
                        1,
                        InputContent.fromItemStack(new ItemStack(Items.REDSTONE, 4)),
                        ChangeReason.added
                    );
                })
                .build();

        ProjectionResult result = ProjectionPipeline.of(gridManip).trace(new ExecutionContext(), raw);
        GridDiff grid = result.diff().inputGridDiff();
        assertNotNull(grid);
        assertEquals(SlotDiff.DiffStatus.removed, grid.cell(0, 0).status());
        assertEquals(SlotDiff.DiffStatus.moved, grid.cell(2, 2).status());
        assertEquals(SlotAddress.inputGrid(0, 0, 3), grid.cell(2, 2).movedFrom());
        assertEquals(SlotDiff.DiffStatus.adjusted, grid.cell(1, 0).status());
        assertEquals("\u00d73", grid.cell(1, 0).deltaBadge());
        assertEquals(SlotDiff.DiffStatus.removed, grid.cell(0, 2).status());
        assertEquals(SlotDiff.DiffStatus.added, grid.cell(1, 1).status());
        assertEquals("+4", grid.cell(1, 1).deltaBadge());

        ProjectionDisplayModel display = result.toDisplayModel();
        assertTrue(display.hasAnyDiffs());
        assertNotNull(display.inputSide().grid());
        assertEquals(SlotDiff.DiffStatus.moved, display.inputSide().grid().cell(2, 2).status());
        assertEquals(SlotDiff.DiffStatus.adjusted, display.inputSide().grid().cell(1, 0).status());
        assertFalse(display.changeRows(SlotChannel.input).isEmpty());
    }

    @Test
    void fastPathZeroTraceComputesOnDemandDiffLazilyAndPreservesImmutability(MinecraftServer server) {
        RecipeProjection raw = RecipeProjection.builder(testSystem, dummyRecipe)
                .inputGrid(2, 2)
                .inputCell(0, 0, InputContent.fromItemStack(new ItemStack(Items.COPPER_INGOT, 2)))
                .output(OutputContent.fromItemStack(new ItemStack(Items.COPPER_BLOCK, 1)))
                .build();

        ProjectionEffect doubleInputs = ProjectionEffect.builder(Namespace.of(CirrusConstants.modId, "test/double"))
                .stage(ProjectionStage.transform)
                .apply(tx -> {
                    for (var slot : tx.inputSlots()) {
                        tx.setSlotAmount(slot.address(), slot.content().amount() * 2L);
                    }
                })
                .build();

        ProjectionPipeline pipeline = ProjectionPipeline.of(doubleInputs);
        ProjectionResult fast = pipeline.evaluate(new ExecutionContext(), raw);

        // Fast path records zero per-step traces
        assertTrue(fast.steps().isEmpty());
        assertEquals(4L, fast.projectedRecipe().inputs().getFirst().amount());
        // Source projection must remain completely untouched
        assertEquals(2L, raw.inputs().getFirst().content().amount());

        // On-demand diff computes exact grid cell delta lazily without step trace
        ProjectionDiff lazyDiff = fast.diff();
        assertTrue(lazyDiff.hasChanges());
        GridDiff grid = lazyDiff.inputGridDiff();
        assertNotNull(grid);
        assertEquals(SlotDiff.DiffStatus.adjusted, grid.cell(0, 0).status());
        assertEquals("\u00d72", grid.cell(0, 0).deltaBadge());
        assertEquals(2L, grid.cell(0, 0).toSlotDiff().amountDelta());
    }

    @Test
    void gasTagHierarchyForbidTagsAndDeterministicStagePriorityOrdering(MinecraftServer server) {
        Namespace parentTag = Namespace.of(CirrusConstants.modId, "test/fluid");
        Namespace childTag = Namespace.of(CirrusConstants.modId, "test/fluid/bucket");
        Namespace lockedTag = Namespace.of(CirrusConstants.modId, "test/locked");

        List<String> order = new ArrayList<>();
        ProjectionEffect lateFinalize = ProjectionEffect.builder(Namespace.of(CirrusConstants.modId, "test/fin"))
                .stage(ProjectionStage.finalize)
                .priority(0)
                .requireTags(parentTag)
                .apply(tx -> order.add("finalize"))
                .build();
        ProjectionEffect txHigh = ProjectionEffect.builder(Namespace.of(CirrusConstants.modId, "test/tx_high"))
                .stage(ProjectionStage.transform)
                .priority(10)
                .requireTags(parentTag)
                .apply(tx -> order.add("tx_10"))
                .build();
        ProjectionEffect txLow = ProjectionEffect.builder(Namespace.of(CirrusConstants.modId, "test/tx_low"))
                .stage(ProjectionStage.transform)
                .priority(-5)
                .requireTags(parentTag)
                .apply(tx -> order.add("tx_-5"))
                .build();
        ProjectionEffect prep = ProjectionEffect.builder(Namespace.of(CirrusConstants.modId, "test/prep"))
                .stage(ProjectionStage.prepare)
                .requireTags(parentTag)
                .forbidTags(lockedTag)
                .apply(tx -> order.add("prepare"))
                .build();

        RecipeProjection unlocked = RecipeProjection.builder(testSystem, dummyRecipe)
                .input(InputContent.fromItemStack(new ItemStack(Items.STICK)))
                .output(OutputContent.fromItemStack(new ItemStack(Items.TORCH)))
                .tag(childTag)
                .build();

        ProjectionPipeline pipeline = ProjectionPipeline.of(lateFinalize, txHigh, txLow, prep);
        pipeline.evaluate(new ExecutionContext(), unlocked);
        assertEquals(List.of("prepare", "tx_-5", "tx_10", "finalize"), order);

        // Now add forbidden tag -> prep must be skipped while others still match parentTag
        order.clear();
        RecipeProjection locked = unlocked.withTag(lockedTag);
        pipeline.evaluate(new ExecutionContext(), locked);
        assertEquals(List.of("tx_-5", "tx_10", "finalize"), order);
    }

    @Test
    void blockingAndNonBlockingGuardsControlExecutionAndRunnability(MinecraftServer server) {
        Namespace nonBlockingGuardId = Namespace.of(CirrusConstants.modId, "test/soft_guard");
        Namespace blockingGuardId = Namespace.of(CirrusConstants.modId, "test/hard_guard");

        ProjectionEffect softGuarded = ProjectionEffect.builder(Namespace.of(CirrusConstants.modId, "test/soft_fx"))
                .guard(
                    ProjectionGuard.nonBlocking(
                        nonBlockingGuardId,
                        (ctx, view) -> ProjectionGuard.Result.fail("optional_skip")
                    )
                )
                .apply(tx -> tx.setSlotAmount(SlotAddress.input(0), 99L))
                .build();

        ProjectionEffect hardGuarded = ProjectionEffect.builder(Namespace.of(CirrusConstants.modId, "test/hard_fx"))
                .guard(
                    ProjectionGuard.of(
                        blockingGuardId,
                        (ctx, view) -> ProjectionGuard.Result.fail("missing_machine_tier")
                    )
                )
                .apply(tx -> tx.setSlotAmount(SlotAddress.input(0), 50L))
                .build();

        RecipeProjection raw = RecipeProjection.builder(testSystem, dummyRecipe)
                .input(InputContent.fromItemStack(new ItemStack(Items.STICK, 2)))
                .output(OutputContent.fromItemStack(new ItemStack(Items.TORCH, 4)))
                .build();

        ProjectionResult softRes = ProjectionPipeline.of(softGuarded).trace(new ExecutionContext(), raw);
        assertTrue(softRes.runnable());
        assertEquals(2L, softRes.projectedRecipe().inputs().getFirst().amount());
        assertEquals(1, softRes.guardFindings().size());
        assertFalse(softRes.guardFindings().getFirst().blocking());
        assertEquals("optional_skip", softRes.guardFindings().getFirst().reasonCode());

        ProjectionResult hardRes = ProjectionPipeline.of(hardGuarded).trace(new ExecutionContext(), raw);
        assertFalse(hardRes.runnable());
        assertEquals(2L, hardRes.projectedRecipe().inputs().getFirst().amount());
        assertEquals(1, hardRes.guardFindings().size());
        assertTrue(hardRes.guardFindings().getFirst().blocking());
        assertEquals("missing_machine_tier", hardRes.guardFindings().getFirst().reasonCode());
    }

    @Test
    void pipelineCompositionPluginTogglingAndWatchedKeyIntrospection(MinecraftServer server) {
        ProjectionPlugin customPlugin = ProjectionPlugin.of(
            Namespace.of(CirrusConstants.modId, "test/custom_plugin"),
            "test.custom",
            "",
            true
        );
        ExecutionContextKey<Long> multKey = ExecutionContextKey.create(
            ResourceLocation.fromNamespaceAndPath(CirrusConstants.modId, "test/mult"),
            Long.class,
            1L
        );
        Namespace tag = Namespace.of(CirrusConstants.modId, "test/scalable");
        Namespace scaleId = Namespace.of(CirrusConstants.modId, "test/scale_fx");
        Namespace bonusId = Namespace.of(CirrusConstants.modId, "test/bonus_fx");

        ProjectionEffect scaleFx = ProjectionEffect.builder(scaleId)
                .plugin(customPlugin)
                .watches(multKey)
                .forEachSlot(SlotChannel.input, TagQuery.requireAll(tag), (tx, slot) -> {
                    long m = tx.context().get(multKey);
                    if (m > 1L) {
                        tx.setSlotAmount(slot.address(), slot.content().amount() * m);
                    }
                })
                .build();
        ProjectionEffect bonusFx = ProjectionEffect.builder(bonusId)
                .plugin(ProjectionPlugin.builtin)
                .apply(tx -> tx.addOutput(OutputContent.fromItemStack(new ItemStack(Items.GOLD_NUGGET, 1))))
                .build();

        ProjectionPipeline composed = ProjectionPipeline.of(scaleFx).andThen(ProjectionPipeline.of(bonusFx));
        ProjectionPipeline withoutBonus = composed.without(bonusId);
        ProjectionPipeline onlyCustom = composed.forPlugin(customPlugin);

        RecipeProjection raw = RecipeProjection.builder(testSystem, dummyRecipe)
                .input(
                    InputContent.fromItemStack(new ItemStack(Items.IRON_INGOT, 3)),
                    ProjectionAttributes.empty(),
                    ProjectionTags.of(tag)
                )
                .output(OutputContent.fromItemStack(new ItemStack(Items.IRON_BLOCK, 1)))
                .build();

        ExecutionContext ctx = new ExecutionContext().set(multKey, 4L);
        ProjectionResult full = composed.trace(ctx, raw);
        assertEquals(12L, full.projectedRecipe().inputs().getFirst().amount());
        assertEquals(2, full.projectedRecipe().outputs().size());

        ProjectionResult noBonus = withoutBonus.trace(ctx, raw);
        assertEquals(1, noBonus.projectedRecipe().outputs().size());
        assertEquals(12L, onlyCustom.project(ctx, raw).inputs().getFirst().amount());

        // Disable plugin -> scaleFx skipped, yet applicableEffects(..., ignorePluginEnabled=true) still reports it & multKey!
        ExecutionContext disabled = ctx.set(customPlugin.enabledKey(), false);
        ProjectionResult disabledRes = composed.trace(disabled, raw);
        assertEquals(3L, disabledRes.projectedRecipe().inputs().getFirst().amount());
        List<ProjectionEffect> applicableIgnored = composed.applicableEffects(disabled, raw, true);
        assertTrue(applicableIgnored.stream().anyMatch(e -> e.id().equals(scaleId) && e.watchedContextKeys().contains(multKey)));
        List<ProjectionEffect> applicableActive = composed.applicableEffects(disabled, raw, false);
        assertEquals(List.of(bonusId), applicableActive.stream().map(ProjectionEffect::id).toList());
    }

    private record TestRecipe(String id) {}

    private static final class TestRecipeSystem extends BasicRecipeSystem<TestRecipe> {
        private TestRecipeSystem() {
            super(Namespace.of(CirrusConstants.modId, "test_projection_system"));
        }

        @Override
        public Class<?> basicType() {
            return TestRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(TestRecipe recipe) {
            return ResourceLocation.fromNamespaceAndPath(CirrusConstants.modId, recipe.id());
        }

        @Override
        public @Nullable TestRecipe findRecipe(String uniqueId) {
            return dummyRecipe.id().equals(uniqueId) ? dummyRecipe : null;
        }

        @Override
        public @Nullable TestRecipe findRecipe(ResourceLocation registryName) {
            return Objects.equals(getId(dummyRecipe), registryName) ? dummyRecipe : null;
        }

        @Override
        public int hash(TestRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(TestRecipe first, TestRecipe second) {
            return first.id().equals(second.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, TestRecipe recipe) {
            return ResolveResult.unsupported();
        }

        @Override
        public @Unmodifiable RichIterable<TestRecipe> allRecipes() {
            return Lists.immutable.of(dummyRecipe);
        }
    }
}
