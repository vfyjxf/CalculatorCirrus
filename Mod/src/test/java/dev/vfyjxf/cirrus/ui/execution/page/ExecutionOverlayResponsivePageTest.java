package dev.vfyjxf.cirrus.ui.execution.page;

import dev.vfyjxf.cirrus.api.recipe.execution.OutputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.InputBinding;
import dev.vfyjxf.cirrus.ui.process.MaterialCategories;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionTelemetrySnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriver;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayLaneSegment;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayNode;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayNodeKind;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayRecipePreview;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayTraceSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayTargetProgress;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowStatus;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.recipe.content.RecipeContentRendererTestBootstrap;
import dev.vfyjxf.cirrus.ui.execution.state.ExecutionOverlayViewState;
import dev.vfyjxf.cirrus.ui.execution.tooltip.ExecutionRecipeLayoutTooltipComponent;
import dev.vfyjxf.cirrus.ui.execution.widget.JeiRecipePreviewWidget;
import dev.vfyjxf.cirrus.ui.process.ProcessNode;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipEntry;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionOverlayResponsivePageTest {

    @BeforeAll
    static void initializeRenderers() {
        RecipeContentRendererTestBootstrap.ensureInitialized();
    }

    @Test
    void ingredientGridCellWidthNeverExceedsAvailableWidth() {
        assertEquals(60, IngredientsPage.resolveGridCellWidth(60));
        assertEquals(66, IngredientsPage.resolveGridCellWidth(148));
        assertEquals(66, IngredientsPage.resolveGridCellWidth(180));
        assertEquals(2, IngredientsPage.resolveGridColumns(100));
        assertEquals(2, IngredientsPage.resolveGridColumns(108));
        assertEquals(2, IngredientsPage.resolveGridColumns(180));
    }

    @Test
    void ingredientListStacksMetricsWhenSpaceIsTight() {
        assertTrue(IngredientsPage.useStackedListMetrics(92, 38));
        assertTrue(IngredientsPage.useStackedListMetrics(120, 76));
        assertFalse(IngredientsPage.useStackedListMetrics(140, 48));
    }

    @Test
    void ingredientToolbarUsesIconSizedControls() {
        assertEquals(20, IngredientsPage.resolveModeButtonWidth());
        assertEquals(20, IngredientsPage.resolveSortButtonWidth());
        assertEquals(20, IngredientsPage.resolveSortDirectionButtonWidth());
        assertEquals(20, IngredientsPage.resolveToolbarHeight(96, 140));
        assertEquals(20, IngredientsPage.resolveToolbarHeight(148, 180));
        assertEquals(5, IngredientsPage.resolveScrollbarWidth(96, 140));
        assertEquals(6, IngredientsPage.resolveScrollbarWidth(148, 180));
    }

    @Test
    void timelineUsesDenseMetricsOnSmallPanels() {
        assertEquals(4, TimelinePage.resolvePagePadding(90, 120));
        assertEquals(22, TimelinePage.resolveOverviewHeight(90, 120));
        assertEquals(26, TimelinePage.resolveNodeRowHeight(90, 120));
        assertEquals(6, TimelinePage.resolveLaneIndent(90, 120));
        assertEquals(3, TimelinePage.resolveTimelineScrollbarWidth(90, 120));
        assertEquals(2, TimelinePage.resolveTimelineBodyInset(90, 120));
    }

    @Test
    void timelineUsesCompactButNotDenseMetricsOnMidPanels() {
        assertEquals(4, TimelinePage.resolvePagePadding(110, 140));
        assertEquals(26, TimelinePage.resolveOverviewHeight(110, 140));
        assertEquals(28, TimelinePage.resolveNodeRowHeight(110, 140));
        assertEquals(8, TimelinePage.resolveLaneIndent(110, 140));
        assertEquals(4, TimelinePage.resolveTimelineScrollbarWidth(110, 140));
        assertEquals(3, TimelinePage.resolveTimelineBodyInset(110, 140));
    }

    @Test
    void timelineKeepsDefaultMetricsOnNormalPanels() {
        assertEquals(6, TimelinePage.resolvePagePadding(160, 220));
        assertEquals(30, TimelinePage.resolveOverviewHeight(160, 220));
        assertEquals(30, TimelinePage.resolveNodeRowHeight(160, 220));
        assertEquals(10, TimelinePage.resolveLaneIndent(160, 220));
        assertEquals(4, TimelinePage.resolveTimelineScrollbarWidth(160, 220));
        assertEquals(3, TimelinePage.resolveTimelineBodyInset(160, 220));
    }

    @Test
    void recipePageUsesThreeDensityBands() {
        assertEquals(RecipePage.LayoutDensity.dense, RecipePage.resolveLayoutDensity(96, 128));
        assertEquals(RecipePage.LayoutDensity.compact, RecipePage.resolveLayoutDensity(132, 176));
        assertEquals(RecipePage.LayoutDensity.normal, RecipePage.resolveLayoutDensity(160, 220));
        assertEquals(22, RecipePage.controlColumnWidth(RecipePage.LayoutDensity.normal));
        assertEquals(19, RecipePage.controlColumnWidth(RecipePage.LayoutDensity.compact));
        assertEquals(16, RecipePage.controlColumnWidth(RecipePage.LayoutDensity.dense));
        assertEquals(2, RecipePage.topRowContentTopInset());
        assertEquals(4, RecipePage.controlButtonGap(RecipePage.LayoutDensity.normal));
        assertEquals(3, RecipePage.controlButtonGap(RecipePage.LayoutDensity.compact));
        assertEquals(2, RecipePage.controlButtonGap(RecipePage.LayoutDensity.dense));
        assertEquals(70, RecipePage.materialPanelHeight(RecipePage.LayoutDensity.normal));
        assertEquals(58, RecipePage.materialPanelHeight(RecipePage.LayoutDensity.compact));
        assertEquals(46, RecipePage.materialPanelHeight(RecipePage.LayoutDensity.dense));
        assertEquals(22, RecipeMaterialsPanelWidget.resolveToolbarHeight(RecipePage.LayoutDensity.normal));
        assertEquals(22, RecipeMaterialsPanelWidget.resolveButtonSize(RecipePage.LayoutDensity.dense));
        assertEquals(0, RecipeMaterialsPanelWidget.resolveGridGap(RecipePage.LayoutDensity.normal));
        assertEquals(36, RecipeMaterialsPanelWidget.resolveCellWidth(RecipePage.LayoutDensity.normal));
        assertEquals(32, RecipeMaterialsPanelWidget.resolveCellWidth(RecipePage.LayoutDensity.dense));
        assertEquals(22, RecipeMaterialsPanelWidget.resolveCellHeight(RecipePage.LayoutDensity.normal));
        assertEquals(22, RecipeMaterialsPanelWidget.resolveCellHeight(RecipePage.LayoutDensity.dense));
        assertEquals(8, RecipeCurrentRecipeCardWidget.traceRightInset(RecipePage.LayoutDensity.normal));
        assertEquals(6, RecipeCurrentRecipeCardWidget.traceRightInset(RecipePage.LayoutDensity.compact));
        assertEquals(5, RecipeCurrentRecipeCardWidget.traceRightInset(RecipePage.LayoutDensity.dense));
        assertEquals(20, RecipeCurrentRecipeCardWidget.traceToggleWidth());
        assertEquals(13, RecipeCurrentRecipeCardWidget.traceToggleHeight());
    }

    @Test
    void recipePageConstructionDoesNotRequireMountedContext() {
        assertDoesNotThrow(() -> new RecipePage(new EmptyExecutionOverlayViewState()));
    }

    @Test
    void recipeStatusKeepsTargetPaneAndTextAreaResponsive() {
        assertEquals(62, RecipeStatusCardWidget.targetPaneWidth(RecipePage.LayoutDensity.normal, 180));
        assertEquals(42, RecipeStatusCardWidget.targetPaneWidth(RecipePage.LayoutDensity.dense, 96));
        assertEquals(36, RecipeStatusCardWidget.targetPaneWidth(RecipePage.LayoutDensity.dense, 72));
        assertTrue(RecipeStatusCardWidget.statusTextAvailableWidth(RecipePage.LayoutDensity.normal, 180)
            > RecipeStatusCardWidget.statusTextAvailableWidth(RecipePage.LayoutDensity.dense, 96));
        assertTrue(RecipeStatusCardWidget.useAlignedOutputs(RecipePage.LayoutDensity.compact, 180, 1));
        assertFalse(RecipeStatusCardWidget.useAlignedOutputs(RecipePage.LayoutDensity.normal, 180, 3));
    }

    @Test
    void recipeStatusUsesCompactFixedControls() {
        assertEquals(15, RecipeStatusCardWidget.bottomStripHeight(RecipePage.LayoutDensity.normal));
        assertEquals(14, RecipeStatusCardWidget.bottomStripHeight(RecipePage.LayoutDensity.compact));
        assertEquals(13, RecipeStatusCardWidget.bottomStripHeight(RecipePage.LayoutDensity.dense));
        assertEquals(38, RecipeStatusCardWidget.quantityInputWidth(RecipePage.LayoutDensity.normal));
        assertEquals(34, RecipeStatusCardWidget.quantityInputWidth(RecipePage.LayoutDensity.compact));
        assertEquals(30, RecipeStatusCardWidget.quantityInputWidth(RecipePage.LayoutDensity.dense));
        assertEquals(0, RecipeStatusCardWidget.infoIconCellWidth(RecipePage.LayoutDensity.normal));
        assertEquals(0, RecipeStatusCardWidget.infoIconCellWidth(RecipePage.LayoutDensity.compact));
        assertEquals(0, RecipeStatusCardWidget.infoIconCellWidth(RecipePage.LayoutDensity.dense));
        assertEquals(0, RecipeStatusCardWidget.infoIconBlockWidth(RecipePage.LayoutDensity.normal));
        assertEquals(3, RecipeStatusCardWidget.metricTextOffset());
    }

    @Test
    void currentRecipeViewportShapesAdaptToContentCount() {
        assertEquals(new RecipeCurrentRecipeCardWidget.ViewportShape(3, 1),
            RecipeCurrentRecipeCardWidget.inputShape(RecipePage.LayoutDensity.normal, 3));
        assertEquals(new RecipeCurrentRecipeCardWidget.ViewportShape(3, 2),
            RecipeCurrentRecipeCardWidget.inputShape(RecipePage.LayoutDensity.compact, 7));
        assertEquals(new RecipeCurrentRecipeCardWidget.ViewportShape(2, 2),
            RecipeCurrentRecipeCardWidget.inputShape(RecipePage.LayoutDensity.dense, 7));
        assertEquals(new RecipeCurrentRecipeCardWidget.ViewportShape(1, 1),
            RecipeCurrentRecipeCardWidget.outputShape(RecipePage.LayoutDensity.normal, 1));
        assertEquals(new RecipeCurrentRecipeCardWidget.ViewportShape(2, 1),
            RecipeCurrentRecipeCardWidget.outputShape(RecipePage.LayoutDensity.normal, 2));
        assertEquals(new RecipeCurrentRecipeCardWidget.ViewportShape(1, 2),
            RecipeCurrentRecipeCardWidget.outputShape(RecipePage.LayoutDensity.dense, 4));
    }

    @Test
    void jeiPreviewScalesDownToTheAvailableRecipeCardArea() {
        assertEquals(1.0f, JeiRecipePreviewWidget.contentScale(160, 90, 120, 70));
        assertEquals(0.5f, JeiRecipePreviewWidget.contentScale(60, 80, 120, 70));
        assertEquals(0.0f, JeiRecipePreviewWidget.contentScale(0, 80, 120, 70));
    }

    @Test
    void timelineNodeTooltipUsesExecutionRecipePreviewComponentFallback() {
        RecipeEntry<TestRecipe> recipe = RecipeEntry.of(testRecipeSystem, new TestRecipe("timeline_preview"));
        var preview = new ExecutionOverlayRecipePreview(
            recipe,
            2,
            Lists.immutable.of(new InputBinding(input(8L), 16L)),
            Lists.immutable.of(new OutputBinding(resource(2L), 2L))
        );
        var node = new ExecutionOverlayNode(
            "node",
            Component.literal("Craft furnace"),
            null,
            OutputContent.fromValue(longValueType, 2L),
            preview,
            ExecutionFlowStatus.running,
            ExecutionOverlayNodeKind.RECIPE,
            1,
            true,
            true,
            Lists.immutable.of(new ExecutionOverlayLaneSegment(
                "execute:0",
                "execute",
                Component.translatable("cirrus.ui.execution.step.execute"),
                Component.translatable("cirrus.ui.execution.step.execute.desc", 2),
                preview,
                ExecutionFlowStatus.running,
                true
            )),
            Lists.immutable.empty()
        );

        var tooltip = TimelinePage.debugNodeTooltip(node);

        assertTrue(tooltip.flatEntries().stream().anyMatch(entry ->
            entry instanceof TooltipEntry.ComponentEntry component
                && component.component() instanceof ExecutionRecipeLayoutTooltipComponent
        ));
    }

    @Test
    void leftAlignedFallbackRecipePreviewUsesTextInset() {
        var input = new ProcessNode.MaterialSlot("input", 64, MaterialCategories.ITEM);
        var output = new ProcessNode.MaterialSlot("output", 1, MaterialCategories.ITEM);

        var component = ExecutionRecipeLayoutTooltipComponent.leftAligned(List.of(input), List.of(output));

        assertEquals(4, component.leftPadding());
        assertEquals(3, component.topPadding());
        assertEquals(3, component.rightPadding());
        assertEquals(3, component.bottomPadding());
    }

    @Test
    void timelineCompressesLongLaneProgressWithoutLosingTheCurrentStep() {
        var segments = Lists.immutable.ofAll(java.util.stream.IntStream.range(0, 18)
            .mapToObj(index -> new ExecutionOverlayLaneSegment(
                "step:" + index,
                "cycle-" + (index % 4),
                Component.literal("Step " + index),
                Component.literal("Step " + index + " detail"),
                null,
                index < 9 ? ExecutionFlowStatus.completed : index == 9 ? ExecutionFlowStatus.running : ExecutionFlowStatus.pending,
                index == 9
            ))
            .toList());

        var groups = TimelinePage.compressedLaneGroups(segments, 40);

        assertTrue(groups.size() <= 8);
        assertEquals(segments.size(), groups.stream().mapToInt(TimelinePage.LaneSegmentGroup::count).sum());
        assertTrue(groups.stream().anyMatch(TimelinePage.LaneSegmentGroup::current));
    }

    @Test
    void timelineCompressesRepeatedCyclesEvenWhenTheStripIsWide() {
        var segments = Lists.immutable.ofAll(java.util.stream.IntStream.range(0, 12)
            .mapToObj(index -> new ExecutionOverlayLaneSegment(
                "repeat:" + index,
                "phase-" + (index % 3),
                Component.literal("Phase " + (index % 3)),
                Component.literal("Phase detail " + index),
                null,
                index < 6 ? ExecutionFlowStatus.completed : index == 6 ? ExecutionFlowStatus.running : ExecutionFlowStatus.pending,
                index == 6
            ))
            .toList());

        var groups = TimelinePage.compressedLaneGroups(segments, 240);

        assertEquals(1, groups.size());
        assertEquals(segments.size(), groups.get(0).count());
        assertTrue(groups.get(0).current());
    }

    private static final TestRecipeSystem testRecipeSystem = new TestRecipeSystem();

    private static final class EmptyExecutionOverlayViewState implements ExecutionOverlayViewState {
        @Override
        public ExecutionDriver driver() {
            return null;
        }

        @Override
        public ExecutionDriverSnapshot snapshot() {
            return null;
        }

        @Override
        public ExecutionTelemetrySnapshot telemetry() {
            return null;
        }

        @Override
        public ExecutionOverlayTraceSnapshot trace() {
            return null;
        }

        @Override
        public OverlayTargetProgress targetProgress() {
            return null;
        }

        @Override
        public boolean calculating() {
            return false;
        }

        @Override
        public RecipeActiveContext activeRecipeContext() {
            return null;
        }

        @Override
        public String targetLabel() {
            return null;
        }

        @Override
        public int targetTimes() {
            return 1;
        }

        @Override
        public void setTargetTimes(int times) {
        }

        @Override
        public void flushTargetTimes() {
        }

        @Override
        public RecipeContent targetContent() {
            return RecipeContent.empty();
        }

        @Override
        public boolean traceEnabled() {
            return false;
        }

        @Override
        public void setTraceEnabled(boolean enabled) {
        }
    }

    private record TestRecipe(String id) {}

    private static final class TestRecipeSystem implements RecipeSystem<TestRecipe> {
        @Override
        public Namespace systemId() {
            return Namespace.of("test", "timeline_page");
        }

        @Override
        public Class<?> basicType() {
            return TestRecipe.class;
        }

        @Override
        public ResourceLocation getId(TestRecipe recipe) {
            return ResourceLocation.fromNamespaceAndPath("test", recipe.id());
        }

        @Override
        public TestRecipe findRecipe(String uniqueId) {
            return new TestRecipe(uniqueId);
        }

        @Override
        public TestRecipe findRecipe(ResourceLocation registryName) {
            return new TestRecipe(registryName.getPath());
        }

        @Override
        public int hash(TestRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(TestRecipe r1, TestRecipe r2) {
            return r1.id().equals(r2.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, TestRecipe recipe) {
            return ResolveResult.resolved(RecipeProjection.create(
                this,
                recipe,
                java.util.List.of(input(8L)),
                java.util.List.of(OutputContent.fromValue(longValueType, 1L))
            ));
        }

        @Override
        public RichIterable<TestRecipe> allRecipes() {
            return Lists.immutable.empty();
        }
    }

    private static ContentResource resource(long amount) {
        return ContentValue.fromValue(longValueType, amount);
    }

    private static InputContent input(long amount) {
        return InputContent.fromValue(longValueType, amount);
    }

    private static final ValueType<Long> longValueType = new ValueType<>() {
        @Override
        public Namespace typeId() {
            return Namespace.of("test", "timeline_amount");
        }

        @Override
        public long getValue(Long content) {
            return content;
        }

        @Override
        public boolean matches(Long fist, Long second) {
            return fist.equals(second);
        }

        @Override
        public Long fromValue(long value) {
            return value;
        }

        @Override
        public Long copy(Long content) {
            return content;
        }
    };
}
