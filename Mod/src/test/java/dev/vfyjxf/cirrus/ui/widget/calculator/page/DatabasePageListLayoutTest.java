package dev.vfyjxf.cirrus.ui.widget.calculator.page;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.database.CustomRecipeDatabase;
import dev.vfyjxf.cirrus.api.recipe.database.DatabaseDefinition;
import dev.vfyjxf.cirrus.api.recipe.database.DatabaseKind;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseIndex;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseWorkspace;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContextKey;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.context.LongProjectionContextOption;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContextScope;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionPipeline;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionResult;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.content.ContentApiImpl;
import dev.vfyjxf.cirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.cirrus.internal.InternalPlugin;
import dev.vfyjxf.cirrus.internal.debug.DebugProjectionPlugin;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cirrus.recipe.content.RecipeContentRendererApi;
import dev.vfyjxf.cirrus.recipe.content.RecipeContentRendererRegistryBuilder;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionTargetRecipeState;
import dev.vfyjxf.cirrus.ui.execution.widget.ExecutionRecipeLayoutPreviewWidget;
import dev.vfyjxf.cirrus.ui.execution.widget.JeiRecipePreviewWidget;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorTargetRecipeState;
import dev.vfyjxf.cirrus.ui.widget.calculator.RecipeDatabaseKeyMappings;
import dev.vfyjxf.cirrus.ui.widget.calculator.calc.RecipeContentSlotsWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusButtonWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusCardWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusFittedTextWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusRowWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSectionWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusWrappedTextWidget;
import dev.vfyjxf.cirrus.ui.widget.toggle.sw.Slide;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipEntry;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexWrap;
import dev.vfyjxf.taffy.style.TaffyDimension;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DatabasePageListLayoutTest {
    private static final TestValueType input = new TestValueType("input");
    private static final TestValueType outA = new TestValueType("output_a");
    private static final TestValueType outB = new TestValueType("output_b");
    private static final TestValueType outC = new TestValueType("output_c");
    private static final TestValueType outD = new TestValueType("output_d");
    private static final ProjectionPlugin irrelevantProjectionPlugin = ProjectionPlugin.of(
        Namespace.of("test", "projection/irrelevant"),
        "cirrus.ui.calculator.database.plugin.builtin",
        "",
        true
    );
    private static final ExecutionContextKey<Long> irrelevantProjectionOptionKey = ExecutionContextKey
            .create(ResourceLocation.fromNamespaceAndPath("test", "projection/irrelevant/level"), Long.class, 1L);

    @BeforeAll
    static void attachRecipeContentRenderers() {
        RecipeContentRendererRegistryBuilder builder = new RecipeContentRendererRegistryBuilder();
        new InternalPlugin().registerRecipeContentRenderer(builder);
        RecipeContentRendererApi.attach(new RecipeContentRendererApi(builder));
        if (RecipeApiImpl.instance() == null) {
            RecipeApiImpl.attach(new RecipeApiImpl(new RecipeApiRegistryBuilder()));
        }
    }

    @AfterEach
    void clearTargetRecipeStates() {
        CalculatorTargetRecipeState.clear();
        ExecutionTargetRecipeState.clear();
    }

    @Test
    void listRowsPlaceOutputPreviewBeforeRecipeText() {
        DatabasePage page = new DatabasePage(indexWithMultiOutputRecipe());
        page.debugSetViewMode("list");
        page.debugSetQuery("multi_output_recipe_with_a_very_long_id");

        WidgetGroup<?> results = (WidgetGroup<?>) page.children().get(1);
        assertEquals(1, results.children().size());
        assertTrue(results.children().getFirst() instanceof DatabasePage.VirtualizedResultSectionsWidget);
        DatabasePage.VirtualizedResultSectionsWidget list = (DatabasePage.VirtualizedResultSectionsWidget) results
                .children().getFirst();

        assertEquals(1, list.debugCardCount());
    }

    @Test
    void listPreviewWidthIsCappedToKeepRecipeIdReadable() {
        assertEquals(32, DatabasePage.listRowHeight());
        assertEquals(2, DatabasePage.listRowGap());
        assertEquals(3, DatabasePage.listRowHorizontalPadding());
        assertEquals(3, DatabasePage.listOutputSlotLimit());
        assertEquals(56, DatabasePage.listOutputStripWidth(3));
        assertEquals(56, DatabasePage.listOutputStripWidth(5));
        assertEquals(DatabasePage.resultCardActionButtonSize() * 2 + 2 + 4, DatabasePage.resultCardActionColumnWidth());
    }

    @Test
    void recipeContentSlotsWidgetSupportsMultipleOutputsAndOverflowTooltip() {
        List<OutputContent> outputs = List.of(
            OutputContent.fromValue(outA, 1L),
            OutputContent.fromValue(outB, 2L),
            OutputContent.fromValue(outC, 3L),
            OutputContent.fromValue(outD, 4L)
        );

        assertEquals(3, RecipeContentSlotsWidget.visibleSlotCount(outputs.size(), 3));
        assertEquals(56, RecipeContentSlotsWidget.stripWidth(outputs.size(), 3, 1));
        assertEquals(0, RecipeContentSlotsWidget.slotIndexAt(1, 1, outputs.size(), 3, 1));
        assertEquals(1, RecipeContentSlotsWidget.slotIndexAt(19, 1, outputs.size(), 3, 1));
        assertEquals(-1, RecipeContentSlotsWidget.slotIndexAt(18, 1, outputs.size(), 3, 1));

        Tooltip first = RecipeContentSlotsWidget.tooltipAt(outputs, 1, 1, 3, 1);
        assertNotNull(first);
        assertFalse(first.flatEntries().isEmpty());

        Tooltip overflow = RecipeContentSlotsWidget.tooltipAt(outputs, 55, 17, 3, 1);
        assertNotNull(overflow);
        assertFalse(overflow.flatEntries().isEmpty());
    }

    @Test
    void systemSortGroupsVisibleResultsByRecipeSystem() {
        DatabasePage page = new DatabasePage(indexWithTwoSystems());
        page.debugSetSortMode("system");

        WidgetGroup<?> results = (WidgetGroup<?>) page.children().get(1);

        assertEquals(16, DatabasePage.sectionHeaderHeight());
        assertEquals(6, DatabasePage.sectionHeaderPaddingLeft());
        assertEquals(4, DatabasePage.sectionHeaderPaddingRight());
        assertEquals(3, DatabasePage.resultSectionBodyPadding());
        assertEquals(24, DatabasePage.resultCategoryHeaderHeight());
        assertEquals(3, DatabasePage.resultCategoryGroupPadding());
        assertEquals(6, DatabasePage.resultCategoryHeaderPaddingLeft());
        assertEquals(5, DatabasePage.resultCategoryHeaderPaddingRight());
        assertEquals(30, DatabasePage.resultCategoryCountWidth());
        assertEquals(14, DatabasePage.resultCategoryCountHeight());
        assertEquals(3, DatabasePage.resultCategoryCountPadding());
        assertEquals(1, results.children().size());
        assertTrue(results.children().getFirst() instanceof DatabasePage.VirtualizedResultSectionsWidget);
        DatabasePage.VirtualizedResultSectionsWidget list = (DatabasePage.VirtualizedResultSectionsWidget) results
                .children().getFirst();
        assertEquals(2, list.debugCategoryCount());
        assertEquals(2, list.debugCardCount());
    }

    @Test
    void resultCategoriesCollapseWithoutResettingScroll() {
        DatabasePage page = new DatabasePage(indexWithTwoSystems());
        page.debugSetSortMode("system");

        int expandedHeight = page.debugResultSectionsContentHeight();
        page.scrollState().jumpTo(0, page.debugResultCategoryY(0));

        page.debugToggleResultCategory(0);

        assertEquals(2, page.debugResultCategoryCount());
        assertEquals(2, page.debugResultCardCount());
        assertEquals(1, page.debugCollapsedResultCategoryCount());
        assertTrue(page.debugResultCategoryCollapsed(0));
        assertTrue(page.debugResultSectionsContentHeight() < expandedHeight);
        assertEquals(page.debugResultCategoryY(0), page.scrollState().targetScrollY());

        page.debugToggleResultCategory(0);

        assertEquals(0, page.debugCollapsedResultCategoryCount());
        assertFalse(page.debugResultCategoryCollapsed(0));
        assertEquals(expandedHeight, page.debugResultSectionsContentHeight());
        assertEquals(page.debugResultCategoryY(0), page.scrollState().targetScrollY());
    }

    @Test
    void systemSortUsesSystemTagsAsCategoriesWhenPresent() {
        DatabasePage page = new DatabasePage(indexWithTwoSystemsInOneCategory());
        page.debugSetSortMode("system");

        WidgetGroup<?> results = (WidgetGroup<?>) page.children().get(1);

        assertEquals(1, results.children().size());
        assertTrue(results.children().getFirst() instanceof DatabasePage.VirtualizedResultSectionsWidget);
        DatabasePage.VirtualizedResultSectionsWidget list = (DatabasePage.VirtualizedResultSectionsWidget) results
                .children().getFirst();
        assertEquals(1, list.debugCategoryCount());
        assertEquals(2, list.debugCardCount());
    }

    @Test
    void sortTooltipDebugStateChangesWithCurrentSortMode() {
        DatabasePage page = new DatabasePage(indexWithMultiOutputRecipe());

        page.debugSetSortMode("database");
        List<String> databaseTooltip = page.debugSortTooltipLines();
        assertEquals("database->system", page.debugSortTooltipModeState());

        page.debugSetSortMode("system");
        List<String> systemTooltip = page.debugSortTooltipLines();
        assertEquals("system->relevance", page.debugSortTooltipModeState());

        assertEquals(databaseTooltip.size(), systemTooltip.size());
        assertTrue(systemTooltip.size() >= 4);
        assertTrue(
            page.debugSortTooltip().flatEntries().stream().anyMatch(TooltipEntry.DynamicEntry.class::isInstance)
        );
    }

    @Test
    void databaseSortDirectionCanReverseSystemGroupingOrder() {
        DatabasePage page = new DatabasePage(indexWithTwoSystems());
        page.debugSetSortMode("system");
        page.debugSetSortDirection("descending");

        assertEquals("test:beta", page.debugFirstResultSystemGroup());
        assertEquals("descending", page.debugSortDirection());
    }

    @Test
    void viewTooltipTracksCurrentViewModeAfterToggle() {
        DatabasePage page = new DatabasePage(indexWithMultiOutputRecipe());
        var tooltip = page.debugViewTooltip();

        List<String> gridTooltip = DatabasePage.debugTooltipLines(tooltip);
        assertEquals("grid->list", page.debugViewTooltipModeState());

        page.debugSetViewMode("list");
        List<String> listTooltip = DatabasePage.debugTooltipLines(tooltip);

        assertEquals("list->grid", page.debugViewTooltipModeState());
        assertEquals(gridTooltip.size(), listTooltip.size());
        assertNotEquals(gridTooltip, listTooltip);
        assertTrue(tooltip.flatEntries().stream().anyMatch(TooltipEntry.DynamicEntry.class::isInstance));
    }

    @Test
    void databaseRecipeCardsCanSetCalculatorAndExecutionTargets() {
        RecipeDatabaseIndex index = indexWithMultiOutputRecipe();
        RecipeEntry<?> recipe = index.records().getFirst().recipe();
        DatabasePage page = new DatabasePage(index);

        page.debugActivateFirstResultCard("setCalculatorTarget");

        assertNotNull(CalculatorTargetRecipeState.current());
        assertEquals(recipe, CalculatorTargetRecipeState.current().recipe());
        assertEquals(null, ExecutionTargetRecipeState.current());

        page.debugActivateFirstResultCard("setExecutionTarget");

        assertNotNull(ExecutionTargetRecipeState.current());
        assertEquals(recipe, ExecutionTargetRecipeState.current().recipe());
    }

    @Test
    void resultSectionsDoNotReserveLegacyResultsHeader() {
        DatabasePage page = new DatabasePage(indexWithMultiOutputRecipe());
        WidgetGroup<?> results = (WidgetGroup<?>) page.children().get(1);
        DatabasePage.VirtualizedResultSectionsWidget list = (DatabasePage.VirtualizedResultSectionsWidget) results
                .children().getFirst();

        assertEquals(0, list.debugFirstCategoryY());
    }

    @Test
    void databaseRecipeTooltipDocumentsConfigurableShortcutActions() {
        DatabasePage page = new DatabasePage(indexWithMultiOutputRecipe());

        List<String> cardLines = DatabasePage.debugTooltipLines(page.debugFirstResultTooltip());
        List<String> calculatorLines = DatabasePage
                .debugTooltipLines(page.debugFirstResultActionTooltip("setCalculatorTarget"));
        List<String> executionLines = DatabasePage
                .debugTooltipLines(page.debugFirstResultActionTooltip("setExecutionTarget"));

        assertFalse(hasLine(cardLines, "Shortcuts", "快捷键", "calculator.database.shortcut.header")); // NOPMD - expected zh_cn lang text
        assertTrue(
            hasLine(cardLines, "View projection details", "查看投射详情", "calculator.database.shortcut.default_click") // NOPMD - expected zh_cn lang text
        );
        assertFalse(hasLine(cardLines, "Set execution target", "设置执行目标", "calculator.database.action.set_execution")); // NOPMD - expected zh_cn lang text
        assertTrue(
            hasLine(
                calculatorLines,
                "Set calculator target",
                "设为计算器目标", // NOPMD - expected zh_cn lang text
                "calculator.database.shortcut.set_calculator_target"
            )
        );
        assertTrue(
            hasLine(calculatorLines, "Open in calculator", "在计算器里打开", "calculator.database.shortcut.open_calculator") // NOPMD - expected zh_cn lang text
        );
        assertTrue(hasLine(calculatorLines, "Left Button", "按键 1")); // NOPMD - expected zh_cn lang text
        assertTrue(
            hasLine(executionLines, "Set execution target", "设置执行器目标", "calculator.database.action.set_execution") // NOPMD - expected zh_cn lang text
        );
    }

    @Test
    void openRecipeInCalculatorShortcutDefaultsToShiftLeftClick() {
        assertEquals(KeyModifier.SHIFT, RecipeDatabaseKeyMappings.openRecipeInCalculator.getDefaultKeyModifier());
        assertEquals(
            InputConstants.Type.MOUSE,
            RecipeDatabaseKeyMappings.openRecipeInCalculator.getDefaultKey().getType()
        );
        assertEquals(
            GLFW.GLFW_MOUSE_BUTTON_LEFT,
            RecipeDatabaseKeyMappings.openRecipeInCalculator.getDefaultKey().getValue()
        );
        String keyName = RecipeDatabaseKeyMappings.keyName(RecipeDatabaseKeyMappings.openRecipeInCalculator)
                .getString();
    }

    @Test
    void databaseRecipeCardDefaultClickOpensProjectionDetail() {
        DatabasePage page = new DatabasePage(indexWithMultiOutputRecipe());

        page.debugOpenFirstResultCardDefault();

        assertTrue(page.debugRecipeDetailOpen());
        assertEquals("test:multi_output_recipe_with_a_very_long_id", page.debugSelectedRecipeDetail());
        assertEquals(null, CalculatorTargetRecipeState.current());
        assertEquals(null, ExecutionTargetRecipeState.current());
    }

    @Test
    void projectionRecipeViewKeepsGridSchemaAndLooseOutputsGeneric() {
        FakeRecipeSystem system = new FakeRecipeSystem(Namespace.of("test", "projection_view"), List.of());
        FakeRecipe recipe = new FakeRecipe("projection_view", List.of(), List.of());
        RecipeProjection projection = RecipeProjection.builder(RecipeEntry.of(system, recipe))
                .inputGrid(3, 3)
                .inputCellIndex(0, InputContent.fromValue(input, 1L))
                .inputCellIndex(4, InputContent.fromValue(input, 2L))
                .output(OutputContent.fromValue(outA, 1L))
                .output(OutputContent.fromValue(outB, 2L))
                .build();

        ProjectionRecipeViewWidget view = ProjectionRecipeViewWidget.create(projection);

        assertNotNull(view);
        assertTrue(view.usesGrid());
        assertEquals(2, view.inputCount());
        assertEquals(2, view.outputCount());
        assertFalse(view.hasDiffOverlays());
    }

    @Test
    void projectionRecipeViewRendersCraftingGridCellDiffOverlays() {
        assumeTrue(DebugProjectionPlugin.debugEnabled());

        RecipeApi previous = RecipeApiImpl.instance();
        ContentApiImpl previousContent = ContentApiImpl.instance();
        attachContentApiForDebugProjection();
        RecipeApiImpl api = attachDebugProjectionRecipeApi();
        try {
            @SuppressWarnings("unchecked")
            RecipeSystem<DebugProjectionPlugin.DebugProjectionRecipe> system = (RecipeSystem<DebugProjectionPlugin.DebugProjectionRecipe>) api
                    .findSystem(DebugProjectionPlugin.systemId);
            assertNotNull(system);
            DebugProjectionPlugin.DebugProjectionRecipe gridRecipe = system.findRecipe("crafting_grid_transform");
            assertNotNull(gridRecipe);
            RecipeProjection raw = api.resolve(ResolveContext.empty(), system, gridRecipe).projection();
            assertNotNull(raw);

            ExecutionContext ctx = new ExecutionContext()
                    .set(DebugProjectionPlugin.scaleKey, 3L)
                    .set(DebugProjectionPlugin.channelKey, "input");
            ProjectionResult result = ProjectionPipeline.fromRegistry().trace(ctx, raw);
            ProjectionRecipeViewWidget view = ProjectionRecipeViewWidget.create(result);

            assertNotNull(view);
            assertTrue(view.usesGrid());
            assertTrue(view.hasDiffOverlays());
            assertTrue(view.changedSlotCount() >= 4);

            ProjectionPipelineWidget widePipeline = new ProjectionPipelineWidget(
                raw,
                result,
                List.of(ProjectionPlugin.builtin, DebugProjectionPlugin.plugin),
                ProjectionPlugin.builtin,
                p -> true,
                ignored -> {},
                540
            );
            ProjectionPipelineWidget narrowPipeline = new ProjectionPipelineWidget(
                raw,
                result,
                List.of(ProjectionPlugin.builtin, DebugProjectionPlugin.plugin),
                ProjectionPlugin.builtin,
                p -> true,
                ignored -> {},
                240
            );
            assertEquals(4, widePipeline.stageCount());
            assertTrue(widePipeline.effectCardCount() >= 4);
            assertEquals(widePipeline.effectCardCount(), narrowPipeline.effectCardCount());
            assertTrue(widePipeline.visibleItemCount() >= 4);
            assertEquals(widePipeline.visibleItemCount(), narrowPipeline.visibleItemCount());
            assertTrue(narrowPipeline.measuredHeight() > widePipeline.measuredHeight());

            DebugProjectionPlugin.DebugProjectionRecipe showcaseRecipe = system.findRecipe("full_pipeline_showcase");
            assertNotNull(showcaseRecipe);
            RecipeProjection showcaseRaw = api.resolve(ResolveContext.empty(), system, showcaseRecipe).projection();
            assertNotNull(showcaseRaw);
            ProjectionResult showcaseResult = ProjectionPipeline.fromRegistry().trace(new ExecutionContext(), showcaseRaw);
            ProjectionPipelineWidget showcasePipeline = new ProjectionPipelineWidget(
                showcaseRaw,
                showcaseResult,
                List.of(ProjectionPlugin.builtin, DebugProjectionPlugin.plugin, DebugProjectionPlugin.overclockPlugin),
                DebugProjectionPlugin.overclockPlugin,
                p -> true,
                ignored -> {},
                480
            );
            assertEquals(4, showcasePipeline.stageCount());
            assertTrue(showcasePipeline.effectCardCount() >= 5);
            assertTrue(showcasePipeline.visibleItemCount() >= 8);
            assertEquals(
                2,
                showcasePipeline.entryCountByTemplate(
                    dev.vfyjxf.cirrus.ui.workbench.projection.ProjectionDiffTemplate.Kind.valueMeter
                )
            );
            assertEquals(
                1,
                showcasePipeline.entryCountByTemplate(
                    dev.vfyjxf.cirrus.ui.workbench.projection.ProjectionDiffTemplate.Kind.durability
                )
            );
            assertTrue(
                showcasePipeline.entryCountByTemplate(
                    dev.vfyjxf.cirrus.ui.workbench.projection.ProjectionDiffTemplate.Kind.fluid
                ) >= 2
            );
            assertTrue(
                showcasePipeline.entryCountByTemplate(
                    dev.vfyjxf.cirrus.ui.workbench.projection.ProjectionDiffTemplate.Kind.stackSlot
                ) >= 2
            );

            // Verify sidebar system tooltip shows cycling active machine and Shift expands full machine list
            dev.vfyjxf.cirrus.api.recipe.database.DatabaseCategoryTree.CategoryNode debugNode = new dev.vfyjxf.cirrus.api.recipe.database.DatabaseCategoryTree.CategoryNode(
                "sys:" + DebugProjectionPlugin.systemId,
                "Debug Projection",
                10,
                dev.vfyjxf.cirrus.api.recipe.database.DatabaseCategoryTree.NodeKind.system,
                false,
                "calculator_cirrus",
                List.of()
            );
            List<dev.vfyjxf.cirrus.api.content.value.ContentStack<net.minecraft.world.item.Item, net.minecraft.world.item.ItemStack>> machines = List.of(
                dev.vfyjxf.cirrus.api.content.value.ContentStack.fromStack(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CRAFTING_TABLE)),
                dev.vfyjxf.cirrus.api.content.value.ContentStack.fromStack(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FURNACE)),
                dev.vfyjxf.cirrus.api.content.value.ContentStack.fromStack(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREWING_STAND))
            );
            var singleTip = dev.vfyjxf.cirrus.ui.workbench.database.DatabaseExplorerWorkspace.buildSystemNodeTooltip(
                debugNode,
                "Debug / Debug Projection",
                List.of(machines.getFirst()),
                0,
                false,
                20
            );
            assertNotNull(singleTip.iconStack());
            assertFalse(singleTip.hasInteractiveGrid());
            var singleLists = singleTip.blocks().stream()
                    .filter(dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip.Block.ItemList.class::isInstance)
                    .map(dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip.Block.ItemList.class::cast)
                    .toList();
            assertEquals(1, singleLists.size());
            assertEquals(1, singleLists.getFirst().entries().size());

            var picked = new java.util.concurrent.atomic.AtomicReference<dev.vfyjxf.cirrus.api.content.value.ContentStack<net.minecraft.world.item.Item, net.minecraft.world.item.ItemStack>>();
            var multiTip = dev.vfyjxf.cirrus.ui.workbench.database.DatabaseExplorerWorkspace.buildSystemNodeTooltip(
                debugNode,
                "Debug / Debug Projection",
                machines,
                1,
                false,
                20,
                picked::set
            );
            assertNotNull(multiTip.iconStack());
            assertTrue(multiTip.hasInteractiveGrid());
            assertTrue(
                multiTip.blocks().stream()
                        .anyMatch(dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip.Block.FooterHint.class::isInstance)
            );
            var grid = multiTip.interactiveGrid();
            assertNotNull(grid);
            assertEquals(3, grid.items().size());
            assertEquals(1, grid.activeIndex());
            assertEquals(10, grid.maxColumns());
            assertEquals(4, grid.maxRows());

            var interaction = grid.interaction();
            assertNotNull(interaction);
            var slotTip = interaction.tooltipForItem(1, machines.get(1));
            assertNotNull(slotTip);
            assertNotNull(slotTip.iconStack());
            assertEquals(
                net.minecraft.network.chat.Component.literal("minecraft:furnace"),
                slotTip.subtitle()
            );
            interaction.onItemClick(2, machines.get(2));
            assertEquals(machines.get(2), picked.get());

            // Verify JEI-style GridGeometry hit-testing and scrolling on an overflowing 45-slot grid (10x4 visible, 5 total rows)
            var overflowGeom = new dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltipRenderer.GridGeometry(
                new dev.vfyjxf.cloudlib.api.math.Rect(10, 20, 196, 80),
                new dev.vfyjxf.cloudlib.api.math.Rect(14, 24, 180, 72),
                new dev.vfyjxf.cloudlib.api.math.Rect(196, 24, 6, 72),
                10,
                4,
                5,
                1,
                18,
                45
            );
            assertEquals(0, overflowGeom.itemIndexAt(15, 25, 0));
            assertEquals(10, overflowGeom.itemIndexAt(15, 25, 1));
            assertEquals(44, overflowGeom.itemIndexAt(14 + 4 * 18 + 2, 24 + 3 * 18 + 2, 1));
            assertEquals(-1, overflowGeom.itemIndexAt(14 + 5 * 18 + 2, 24 + 3 * 18 + 2, 1));
            assertTrue(overflowGeom.isOverScrollbar(198, 40));
            assertEquals(0, overflowGeom.scrollRowForPointerY(25));
            assertEquals(1, overflowGeom.scrollRowForPointerY(94));

            var measured = dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltipRenderer.measure(multiTip, null);
            assertTrue(measured.width() >= dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltipRenderer.minWidth);
            assertTrue(measured.height() > 60);
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
    void databaseRecipeRowsOpenDetailPageWithPreviewAndEffectSections() {
        DatabasePage page = new DatabasePage(indexWithMultiOutputRecipe());
        page.debugSetViewMode("list");
        page.debugSetQuery("multi_output_recipe_with_a_very_long_id");

        page.debugOpenFirstResultDetail();

        assertTrue(page.debugRecipeDetailOpen());
        assertEquals("test:multi_output_recipe_with_a_very_long_id", page.debugSelectedRecipeDetail());
        assertEquals(0, page.debugVisibleResultCount());
        assertEquals(0, page.debugSystemRecipeSelectionCount());
        assertEquals(0, page.debugRecipeDetailPluginCount());
        assertEquals(3, page.debugRecipeDetailSectionCount());
        assertFalse(page.debugRecipeDetailUsesJeiPreview());

        WidgetGroup<?> results = (WidgetGroup<?>) page.children().get(1);
        assertEquals(1, results.children().size());
        assertTrue(results.children().getFirst() instanceof WidgetGroup<?>);
        WidgetGroup<?> shell = (WidgetGroup<?>) results.children().getFirst();
        assertEquals(3, shell.children().size());
        assertTrue(shell.children().getFirst() instanceof CirrusCardWidget);
        assertTrue(shell.children().get(1) instanceof CirrusCardWidget);
        assertTrue(shell.children().get(2) instanceof CirrusCardWidget);
        CirrusCardWidget header = (CirrusCardWidget) shell.children().getFirst();
        assertTrue(header.body().children().getFirst() instanceof WidgetGroup<?>);
        WidgetGroup<?> headerRow = (WidgetGroup<?>) header.body().children().getFirst();
        assertTrue(headerRow.children().getFirst() instanceof CirrusButtonWidget);
        assertEquals(18, DatabasePage.detailIconButtonSize());
        assertEquals(36, DatabasePage.detailEffectRowHeight());
        assertEquals(236, DatabasePage.detailProjectionControlPanelWidth());
        assertEquals(180, DatabasePage.detailProjectionControlPanelHeight());
        CirrusCardWidget preview = (CirrusCardWidget) shell.children().get(1);
        assertEquals(1, preview.body().children().size());
        assertTrue(preview.body().children().getFirst() instanceof WidgetGroup<?>);
        WidgetGroup<?> groupedPreview = (WidgetGroup<?>) preview.body().children().getFirst();
        assertEquals(2, groupedPreview.children().size());
        assertTrue(groupedPreview.children().getFirst() instanceof CirrusFittedTextWidget);
        CirrusFittedTextWidget previewCaption = (CirrusFittedTextWidget) groupedPreview.children().getFirst();
        assertTrue(groupedPreview.children().get(1) instanceof ExecutionRecipeLayoutPreviewWidget);
        ExecutionRecipeLayoutPreviewWidget originalPreview = (ExecutionRecipeLayoutPreviewWidget) groupedPreview
                .children().get(1);
        assertEquals(1, originalPreview.inputSlotCount());
        assertEquals(4, originalPreview.outputSlotCount());
        assertTrue(originalPreview.framed());
        assertFalse(preview.extension().visible());

        page.debugLeaveRecipeDetail();
        assertFalse(page.debugRecipeDetailOpen());
        assertEquals(1, page.debugVisibleResultCount());
    }

    @Test
    void databasePageCanOpenRecipeDetailFromExternalRecipeEntry() {
        RecipeDatabaseIndex index = indexWithMultiOutputRecipe();
        RecipeEntry<?> entry = index.records().getFirst().recipe();
        DatabasePage page = new DatabasePage(index);

        assertTrue(page.openRecipe(entry));

        assertTrue(page.debugRecipeDetailOpen());
        assertEquals("test:multi_output_recipe_with_a_very_long_id", page.debugSelectedRecipeDetail());
    }

    @Test
    void calculatorProjectionDetailUsesCalculatorContextAndExternalBackAction() {
        RecipeDatabaseIndex index = indexWithMultiOutputRecipe();
        RecipeEntry<?> entry = index.records().getFirst().recipe();
        int[] backCalls = {0};
        DatabasePage page = DatabasePage.calculatorProjection(index, entry, 12, 34L, () -> backCalls[0]++);

        assertFalse(page.debugBrowserToolbarVisible());
        assertFalse(page.debugSearchBoxVisible());
        assertFalse(page.debugDatabaseChoiceVisible());
        assertFalse(page.debugCreateDatabaseButtonVisible());
        assertFalse(page.debugSourcesButtonVisible());
        assertFalse(page.debugDeleteDatabaseVisible());
        assertFalse(page.debugViewButtonVisible());
        assertFalse(page.debugSortButtonVisible());
        assertFalse(page.debugSortDirectionButtonVisible());
        page.debugOpenSourcesPage();
        assertFalse(page.debugSourcesPageOpen());

        assertTrue(page.openRecipe(entry));
        assertEquals(ProjectionContextScope.calculator, page.debugProjectionContextScope());
        assertEquals("builtin", page.debugProjectionContextDatabaseId());
        assertTrue(page.debugProjectionContextHasCalculatorRecipe());

        page.debugLeaveRecipeDetail();

        assertEquals(1, backCalls[0]);
        assertTrue(page.debugRecipeDetailOpen());
    }

    @Test
    void calculatorProjectionFromRecipeBuildsOnlySelectedRecipeIndex() {
        RecipeApi previous = RecipeApiImpl.instance();
        Namespace source = Namespace.of("test", "calculator_projection");
        FakeRecipe target = new FakeRecipe(
            "target",
            List.of(InputContent.fromValue(input, 1L)),
            List.of(OutputContent.fromValue(outA, 1L))
        );
        FakeRecipe unrelated = new FakeRecipe(
            "unrelated",
            List.of(InputContent.fromValue(input, 1L)),
            List.of(OutputContent.fromValue(outB, 1L))
        );
        FakeRecipeSystem system = new FakeRecipeSystem(source, List.of(target, unrelated));
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        builder.registerSystem(system);
        RecipeApiImpl.attach(new RecipeApiImpl(builder));
        try {
            RecipeEntry<?> entry = RecipeEntry.of(system, target);
            DatabasePage page = DatabasePage.calculatorProjection(entry, 2, 7L, () -> {});

            assertFalse(page.debugDatabaseScopedIndex());
            assertEquals(1, page.debugIndexRecordCount());
            assertEquals(0, page.debugSystemCatalogRecordCount());
            assertEquals(0, system.debugAllRecipesCalls());
            assertTrue(page.openRecipe(entry));
            assertEquals(ProjectionContextScope.calculator, page.debugProjectionContextScope());
            assertEquals("builtin", page.debugProjectionContextDatabaseId());
            assertFalse(page.debugActiveDatabaseEditable());
        } finally {
            if (previous instanceof RecipeApiImpl impl) {
                RecipeApiImpl.attach(impl);
            }
        }
    }

    @Test
    void projectionDebugEffectsAndControlsShareIntegratedProjectionSection() {
        assumeTrue(DebugProjectionPlugin.debugEnabled());

        RecipeApi previous = RecipeApiImpl.instance();
        ContentApiImpl previousContent = ContentApiImpl.instance();
        attachContentApiForDebugProjection();
        RecipeApiImpl api = attachDebugProjectionRecipeApi();
        try {
            RecipeSystem<?> system = api.findSystem(DebugProjectionPlugin.systemId);
            assertNotNull(system);
            CustomRecipeDatabase database = new CustomRecipeDatabase(
                "debug",
                "Debug",
                List.of(),
                List.of(DebugProjectionPlugin.systemId),
                List.of()
            );
            DatabasePage page = new DatabasePage(
                new RecipeDatabaseWorkspace(List.of(database), "debug"),
                List.of(system),
                Map.of()
            );

            page.debugSetProjectionContextOption("calculator_cirrus:debug/projection_scale", 2L);
            page.debugSetProjectionContextOption("calculator_cirrus:debug/projection_extra_output", true);
            page.debugSetQuery("projection_probe");
            page.debugOpenFirstResultDetail();

            assertTrue(page.debugRecipeDetailEffectCount() >= 2);
            assertEquals(1, page.debugRecipeDetailPluginCount());
            WidgetGroup<?> results = (WidgetGroup<?>) page.children().get(1);
            WidgetGroup<?> shell = (WidgetGroup<?>) results.children().getFirst();
            assertEquals(3, shell.children().size());
            assertTrue(shell.children().get(2) instanceof CirrusCardWidget);

            CirrusCardWidget debug = (CirrusCardWidget) shell.children().get(2);
            assertEquals(1, debug.body().children().size());
            WidgetGroup<?> workspace = (WidgetGroup<?>) debug.body().children().getFirst();
            assertEquals(2, workspace.children().size());
            assertEquals(FlexWrap.NO_WRAP, workspace.style().layoutStyle().flexWrap);
            assertEquals(AlignItems.STRETCH, workspace.style().layoutStyle().alignItems);
            assertEquals(TaffyDimension.percent(1f), workspace.style().layoutStyle().size.width);

            WidgetGroup<?> overview = (WidgetGroup<?>) workspace.children().getFirst();
            assertEquals(1.0f, overview.style().layoutStyle().flexGrow, 0.001f);
            assertEquals(1.0f, overview.style().layoutStyle().flexShrink, 0.001f);
            assertEquals(TaffyDimension.length(0), overview.style().layoutStyle().minSize.width);
            assertTrue(overview.children().size() >= 7);
            assertTrue(page.debugProjectionEffectsMerged());
            assertTrue(overview.children().stream().anyMatch(ProjectionPipelineWidget.class::isInstance));
            assertTrue(overview.children().stream().anyMatch(ProjectionRecipeViewWidget.class::isInstance));
            assertTrue(overview.children().get(5) instanceof WidgetGroup<?>);
            assertTrue(overview.children().get(6) instanceof WidgetGroup<?>);
            WidgetGroup<?> inputEffectGroup = (WidgetGroup<?>) overview.children().get(5);
            WidgetGroup<?> outputEffectGroup = (WidgetGroup<?>) overview.children().get(6);
            assertTrue(inputEffectGroup.children().size() >= 2);
            assertTrue(outputEffectGroup.children().size() >= 2);

            WidgetGroup<?> controls = (WidgetGroup<?>) workspace.children().get(1);
            assertEquals(
                TaffyDimension.length(DatabasePage.detailProjectionControlPanelWidth()),
                controls.style().layoutStyle().size.width
            );
            assertEquals(
                TaffyDimension.length(DatabasePage.detailProjectionControlPanelWidth()),
                controls.style().layoutStyle().minSize.width
            );
            assertEquals(
                TaffyDimension.length(DatabasePage.detailProjectionControlPanelWidth()),
                controls.style().layoutStyle().maxSize.width
            );
            assertEquals(0.0f, controls.style().layoutStyle().flexGrow, 0.001f);
            assertEquals(0.0f, controls.style().layoutStyle().flexShrink, 0.001f);
            assertTrue(controls.children().size() >= 3);
            assertEquals(4, DatabasePage.detailProjectionRowPadding());

            // Multi-plugin recipe defaults to All changes and supports switching to single plugin & back
            page.debugLeaveRecipeDetail();
            page.debugSetQuery("full_pipeline_showcase");
            page.debugOpenFirstResultDetail();
            assertTrue(page.debugProjectionDiffShowAll());
            assertTrue(page.debugRecipeDetailPluginCount() >= 3);
            WidgetGroup<?> showcaseResults = (WidgetGroup<?>) page.children().get(1);
            WidgetGroup<?> showcaseShell = (WidgetGroup<?>) showcaseResults.children().getFirst();
            CirrusCardWidget showcaseDebug = (CirrusCardWidget) showcaseShell.children().get(2);
            WidgetGroup<?> showcaseWorkspace = (WidgetGroup<?>) showcaseDebug.body().children().getFirst();
            WidgetGroup<?> showcaseOverview = (WidgetGroup<?>) showcaseWorkspace.children().getFirst();
            // caption, pipeline, scopeHeader, preview, mergeRow, summaryStrip, inputsGroup, outputsGroup, metaGroup
            assertEquals(9, showcaseOverview.children().size());
            WidgetGroup<?> allInputsGroup = (WidgetGroup<?>) showcaseOverview.children().get(6);
            WidgetGroup<?> allOutputsGroup = (WidgetGroup<?>) showcaseOverview.children().get(7);
            WidgetGroup<?> allMetaGroup = (WidgetGroup<?>) showcaseOverview.children().get(8);
            assertTrue(allInputsGroup.children().size() - 1 >= 4);
            assertTrue(allOutputsGroup.children().size() - 1 >= 3);
            assertTrue(allMetaGroup.children().size() - 1 >= 2);

            page.debugSelectProjectionPlugin(DebugProjectionPlugin.overclockPlugin.id().toString());
            assertFalse(page.debugProjectionDiffShowAll());
            WidgetGroup<?> ocResults = (WidgetGroup<?>) page.children().get(1);
            WidgetGroup<?> ocShell = (WidgetGroup<?>) ocResults.children().getFirst();
            CirrusCardWidget ocDebug = (CirrusCardWidget) ocShell.children().get(2);
            WidgetGroup<?> ocWorkspace = (WidgetGroup<?>) ocDebug.body().children().getFirst();
            WidgetGroup<?> ocOverview = (WidgetGroup<?>) ocWorkspace.children().getFirst();
            WidgetGroup<?> ocInputsGroup = (WidgetGroup<?>) ocOverview.children().get(6);
            assertEquals(1, ocInputsGroup.children().size() - 1);

            page.debugSetProjectionDiffShowAll(true);
            assertTrue(page.debugProjectionDiffShowAll());
            assertEquals(8, DatabasePage.detailProjectionPanelGap());
            assertEquals(3, DatabasePage.detailProjectionControlMarginLeft());
            WidgetGroup<?> parameters = (WidgetGroup<?>) controls.children().get(2);
            // children: 0 = caption, 1 = selected plugin banner, 2 = scale (Long), 3 = extra_output (Boolean)
            assertTrue(parameters.children().size() >= 4);
            WidgetGroup<?> booleanRow = (WidgetGroup<?>) parameters.children().get(3);
            assertTrue(booleanRow.children().getFirst() instanceof CirrusWrappedTextWidget);
            assertTrue(booleanRow.children().get(1) instanceof Slide);

            page.debugSetProjectionEffectsMerged(false);
            assertFalse(page.debugProjectionEffectsMerged());
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
    void databaseRecipeDetailPreviewFiltersImplicitEnergyValues() {
        DatabasePage page = new DatabasePage(indexWithEnergySpecRecipe());
        page.debugSetQuery("energy_spec_recipe");

        page.debugOpenFirstResultDetail();

        WidgetGroup<?> results = (WidgetGroup<?>) page.children().get(1);
        WidgetGroup<?> shell = (WidgetGroup<?>) results.children().getFirst();
        CirrusCardWidget preview = (CirrusCardWidget) shell.children().get(1);
        assertTrue(preview.body().children().getFirst() instanceof WidgetGroup<?>);
        WidgetGroup<?> groupedPreview = (WidgetGroup<?>) preview.body().children().getFirst();
        assertTrue(groupedPreview.children().getFirst() instanceof CirrusFittedTextWidget);
        CirrusFittedTextWidget previewCaption = (CirrusFittedTextWidget) groupedPreview.children().getFirst();
        assertTrue(groupedPreview.children().get(1) instanceof ExecutionRecipeLayoutPreviewWidget);
        ExecutionRecipeLayoutPreviewWidget originalPreview = (ExecutionRecipeLayoutPreviewWidget) groupedPreview
                .children().get(1);

        assertEquals(2, originalPreview.outputSlotCount());
        assertEquals(3, originalPreview.valueCount());
    }

    @Test
    void projectionDetailRefreshKeepsScrollPosition() {
        DatabasePage page = new DatabasePage(indexWithMultiOutputRecipe());
        page.debugSetQuery("multi_output_recipe_with_a_very_long_id");
        page.debugOpenFirstResultDetail();
        page.scrollState().contentSize(100, 200);
        page.scrollState().jumpTo(0, 42);

        page.debugRefreshProjectionPreservingScroll();

        assertEquals(42f, page.scrollState().targetScrollY());
    }

    @Test
    void projectionDetailNavigationRestoresPreviousPageScrollPositions() {
        DatabasePage page = new DatabasePage(indexWithMultiOutputRecipe());
        page.debugSetQuery("multi_output_recipe_with_a_very_long_id");
        page.scrollState().contentSize(100, 200);
        page.scrollState().jumpTo(0, 37);

        page.debugOpenFirstResultDetail();

        assertEquals(0f, page.scrollState().targetScrollY());
        page.scrollState().contentSize(100, 200);
        page.scrollState().jumpTo(0, 42);

        page.debugLeaveRecipeDetail();

        assertEquals(37f, page.scrollState().targetScrollY());

        page.debugOpenFirstResultDetail();

        assertEquals(42f, page.scrollState().targetScrollY());
    }

    @Test
    void sourceSubPagesRestoreIndependentScrollPositions() {
        Namespace source = Namespace.of("test", "scroll_source");
        FakeRecipeSystem system = new FakeRecipeSystem(
            source,
            List.of(
                new FakeRecipe(
                    "scroll_recipe",
                    List.of(InputContent.fromValue(input, 1L)),
                    List.of(OutputContent.fromValue(outA, 1L))
                )
            )
        );
        DatabasePage page = databasePageWithScratch(system);
        page.scrollState().contentSize(100, 200);
        page.scrollState().jumpTo(0, 23);

        page.debugOpenSourcesPage();

        assertEquals(0f, page.scrollState().targetScrollY());
        page.scrollState().contentSize(100, 200);
        page.scrollState().jumpTo(0, 31);

        page.debugOpenSystemRecipeSource(source.toString());

        assertEquals(0f, page.scrollState().targetScrollY());
        page.scrollState().contentSize(100, 200);
        page.scrollState().jumpTo(0, 44);

        page.debugLeaveSystemRecipeSource();

        assertEquals(31f, page.scrollState().targetScrollY());

        page.debugLeaveSourcesPage();

        assertEquals(23f, page.scrollState().targetScrollY());

        page.debugOpenSourcesPage();

        assertEquals(31f, page.scrollState().targetScrollY());

        page.debugOpenSystemRecipeSource(source.toString());

        assertEquals(44f, page.scrollState().targetScrollY());
    }

    @Test
    void jeiRecipePreviewWidgetIsAbsentWhenJeiRuntimeCannotRenderRecipe() {
        assertEquals(null, JeiRecipePreviewWidget.create(null));
        assertEquals(
            null,
            JeiRecipePreviewWidget.create(
                RecipeEntry.of(
                    new FakeRecipeSystem(Namespace.of("test", "no_jei"), List.of()),
                    new FakeRecipe("no_jei", List.of(), List.of())
                )
            )
        );
    }

    @Test
    void databasePageDefaultsToEmptyUntilSystemsAreAdded() {
        Namespace source = Namespace.of("test", "manual");
        FakeRecipeSystem system = new FakeRecipeSystem(
            source,
            List.of(
                new FakeRecipe(
                    "manual_recipe",
                    List.of(InputContent.fromValue(input, 1L)),
                    List.of(OutputContent.fromValue(outA, 1L))
                )
            )
        );
        DatabasePage page = databasePageWithScratch(system);

        assertEquals(0, page.debugActiveDatabaseSystemSourceCount());
        assertEquals(0, page.debugVisibleResultCount());

        page.debugToggleSystemSource(source.toString());

        assertEquals(1, page.debugActiveDatabaseSystemSourceCount());
        assertEquals(1, page.debugEnabledDatabaseSystemSourceCount());
        assertEquals(1, page.debugVisibleResultCount());

        page.debugSetSystemSourceEnabled(source.toString(), false);

        assertEquals(1, page.debugActiveDatabaseSystemSourceCount());
        assertEquals(0, page.debugEnabledDatabaseSystemSourceCount());
        assertEquals(0, page.debugVisibleResultCount());
    }

    @Test
    void togglingSystemSourcesPreservesScrollPosition() {
        Namespace source = Namespace.of("jei", "scroll_preserve");
        FakeRecipeSystem system = new FakeRecipeSystem(
            source,
            List.of(
                new FakeRecipe(
                    "scroll_recipe",
                    List.of(InputContent.fromValue(input, 1L)),
                    List.of(OutputContent.fromValue(outA, 1L))
                )
            )
        );
        DatabasePage page = databasePageWithScratch(system);
        page.scrollState().contentSize(100, 240);
        page.scrollState().jumpTo(0, 58);

        page.debugToggleSystemSource(source.toString());

        assertEquals(58f, page.scrollState().targetScrollY());

        page.scrollState().jumpTo(0, 71);
        page.debugSetSystemSourceEnabled(source.toString(), false);

        assertEquals(71f, page.scrollState().targetScrollY());
    }

    @Test
    void canToggleAllSourcesInSameRootAtOnce() {
        Namespace jeiAlpha = Namespace.of("jei", "alpha");
        Namespace jeiBeta = Namespace.of("jei", "beta");
        Namespace other = Namespace.of("other", "gamma");
        FakeRecipeSystem alphaSystem = new FakeRecipeSystem(
            jeiAlpha,
            List.of(
                new FakeRecipe(
                    "alpha_recipe",
                    List.of(InputContent.fromValue(input, 1L)),
                    List.of(OutputContent.fromValue(outA, 1L))
                )
            )
        );
        FakeRecipeSystem betaSystem = new FakeRecipeSystem(
            jeiBeta,
            List.of(
                new FakeRecipe(
                    "beta_recipe",
                    List.of(InputContent.fromValue(input, 1L)),
                    List.of(OutputContent.fromValue(outB, 1L))
                )
            )
        );
        FakeRecipeSystem otherSystem = new FakeRecipeSystem(
            other,
            List.of(
                new FakeRecipe(
                    "other_recipe",
                    List.of(InputContent.fromValue(input, 1L)),
                    List.of(OutputContent.fromValue(outC, 1L))
                )
            )
        );
        DatabasePage page = databasePageWithScratch(List.of(alphaSystem, betaSystem, otherSystem));

        page.debugToggleSystemSourceGroup("jei");

        assertEquals(2, page.debugActiveDatabaseSystemSourceCount());
        assertEquals(2, page.debugEnabledDatabaseSystemSourceCount());
        assertTrue(page.debugVisibleResultCount() > 0);

        page.debugToggleSystemSourceGroup("jei");

        assertEquals(2, page.debugActiveDatabaseSystemSourceCount());
        assertEquals(0, page.debugEnabledDatabaseSystemSourceCount());
    }

    @Test
    void builtinDatabaseHidesMutatingControlsAndShowsSystemCatalog() {
        Namespace source = Namespace.of("test", "builtin_readonly");
        FakeRecipeSystem system = new FakeRecipeSystem(
            source,
            List.of(
                new FakeRecipe(
                    "readonly_recipe",
                    List.of(InputContent.fromValue(input, 1L)),
                    List.of(OutputContent.fromValue(outA, 1L))
                )
            )
        );
        DatabasePage page = new DatabasePage(RecipeDatabaseWorkspace.defaults(), List.of(system), Map.of());

        assertTrue(page.debugActiveDatabaseBuiltin());
        assertFalse(page.debugActiveDatabaseEditable());
        assertFalse(page.debugDeleteDatabaseEnabled());
        assertFalse(page.debugSourcesButtonEnabled());
        assertFalse(page.debugDeleteDatabaseVisible());
        assertFalse(page.debugSourcesButtonVisible());
        assertEquals(1, page.debugVisibleResultCount());

        page.debugOpenSourcesPage();
        page.debugToggleSystemSource(source.toString());
        page.debugSetSystemSourceEnabled(source.toString(), true);
        page.debugOpenSystemRecipeSource(source.toString());
        page.debugSetSystemRecipeSelected(source.toString(), "test:readonly_recipe", true);

        assertFalse(page.debugSourcesPageOpen());
        assertEquals(null, page.debugSelectedSystemRecipeSource());
        assertEquals(0, page.debugActiveDatabaseSystemSourceCount());
        assertEquals(0, page.debugActiveDatabaseRecipeCount());
        assertEquals(1, page.debugVisibleResultCount());
    }

    @Test
    void sourcesPageUsesCompactGridEntries() {
        Namespace source = Namespace.of("test", "compact_source");
        FakeRecipeSystem system = new FakeRecipeSystem(
            source,
            List.of(
                new FakeRecipe(
                    "compact_recipe",
                    List.of(InputContent.fromValue(input, 1L)),
                    List.of(OutputContent.fromValue(outA, 1L))
                )
            )
        );
        DatabasePage page = databasePageWithScratch(system);

        page.debugOpenSourcesPage();

        assertTrue(page.debugSourcesPageOpen());
        WidgetGroup<?> results = (WidgetGroup<?>) page.children().get(1);
        CirrusSectionWidget sources = (CirrusSectionWidget) results.children().getFirst();
        assertEquals(2, sources.body().children().size());
        assertTrue(sources.body().children().get(1) instanceof DatabasePage.VirtualizedSourceGroupsWidget);
        DatabasePage.VirtualizedSourceGroupsWidget groups = (DatabasePage.VirtualizedSourceGroupsWidget) sources.body()
                .children().get(1);

        assertEquals(4, DatabasePage.sourceSectionBodyPadding());
        assertEquals(20, DatabasePage.systemSourceGroupHeaderHeight());
        assertEquals(3, DatabasePage.systemSourceGroupPadding());
        assertEquals(1, groups.debugGroupCount());
        assertEquals(1, groups.debugSourceCount());
        assertEquals(1, groups.children().size());
        WidgetGroup<?> groupWidget = (WidgetGroup<?>) groups.children().getFirst();
        assertEquals(2, groupWidget.children().size());
        assertEquals(1, ((WidgetGroup<?>) groupWidget.children().get(1)).children().size());
        assertEquals(34, DatabasePage.systemSourceControlHeight());
        assertEquals(2, DatabasePage.systemSourceControlGap());
        assertEquals(20, DatabasePage.sourcesButtonWidth());
    }

    @Test
    void sourceGroupHeaderBulkButtonTogglesOnlyThroughButtonAction() {
        Namespace first = Namespace.of("jei", "category/first");
        Namespace second = Namespace.of("jei", "category/second");
        DatabasePage page = databasePageWithScratch(
            List.of(
                new FakeRecipeSystem(
                    first,
                    List.of(
                        new FakeRecipe(
                            "first_recipe",
                            List.of(InputContent.fromValue(input, 1L)),
                            List.of(OutputContent.fromValue(outA, 1L))
                        )
                    )
                ),
                new FakeRecipeSystem(
                    second,
                    List.of(
                        new FakeRecipe(
                            "second_recipe",
                            List.of(InputContent.fromValue(input, 1L)),
                            List.of(OutputContent.fromValue(outA, 1L))
                        )
                    )
                )
            )
        );

        page.debugOpenSourcesPage();

        WidgetGroup<?> results = (WidgetGroup<?>) page.children().get(1);
        CirrusSectionWidget sources = (CirrusSectionWidget) results.children().getFirst();
        DatabasePage.VirtualizedSourceGroupsWidget groups = (DatabasePage.VirtualizedSourceGroupsWidget) sources.body()
                .children().get(1);
        WidgetGroup<?> groupWidget = (WidgetGroup<?>) groups.children().getFirst();
        CirrusRowWidget header = (CirrusRowWidget) groupWidget.children().getFirst();

        assertEquals(4, header.children().size());
        assertEquals(0, page.debugActiveDatabaseSystemSourceCount());
        assertEquals(0, page.debugEnabledDatabaseSystemSourceCount());

        page.debugToggleSystemSourceGroup("jei");
        assertEquals(2, page.debugActiveDatabaseSystemSourceCount());
        assertEquals(2, page.debugEnabledDatabaseSystemSourceCount());

        page.debugToggleSystemSourceGroup("jei");
        assertEquals(2, page.debugActiveDatabaseSystemSourceCount());
        assertEquals(0, page.debugEnabledDatabaseSystemSourceCount());
    }

    @Test
    void sourceCardsExpandToNamespaceTextAndClampAtMaxWidth() {
        Namespace readable = Namespace.of("jei", "category/minecraft/compostable");
        Namespace oversized = Namespace.of("jei", "category/minecraft/very_long_recipe_category_that_clamps");
        FakeRecipeSystem readableSystem = new FakeRecipeSystem(
            readable,
            List.of(
                new FakeRecipe(
                    "readable_recipe",
                    List.of(InputContent.fromValue(input, 1L)),
                    List.of(OutputContent.fromValue(outA, 1L))
                )
            )
        );
        FakeRecipeSystem oversizedSystem = new FakeRecipeSystem(
            oversized,
            List.of(
                new FakeRecipe(
                    "oversized_recipe",
                    List.of(InputContent.fromValue(input, 1L)),
                    List.of(OutputContent.fromValue(outA, 1L))
                )
            )
        );
        DatabasePage page = databasePageWithScratch(List.of(readableSystem, oversizedSystem));

        page.debugOpenSourcesPage();

        WidgetGroup<?> results = (WidgetGroup<?>) page.children().get(1);
        CirrusSectionWidget sources = (CirrusSectionWidget) results.children().getFirst();
        assertTrue(sources.body().children().get(1) instanceof DatabasePage.VirtualizedSourceGroupsWidget);
        DatabasePage.VirtualizedSourceGroupsWidget groups = (DatabasePage.VirtualizedSourceGroupsWidget) sources.body()
                .children().get(1);

        assertEquals(1, groups.debugGroupCount());
        assertEquals(2, groups.debugSourceCount());
        assertEquals(1, groups.children().size());
        WidgetGroup<?> groupWidget = (WidgetGroup<?>) groups.children().getFirst();
        assertEquals(2, groupWidget.children().size());
        assertEquals(2, ((WidgetGroup<?>) groupWidget.children().get(1)).children().size());
    }

    @Test
    void sourceGroupsRenderAllEntriesInNormalWidgetTree() {
        ArrayList<FakeRecipeSystem> systems = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            Namespace source = Namespace.of("jei", "category/wide_source_" + index);
            systems.add(
                new FakeRecipeSystem(
                    source,
                    List.of(
                        new FakeRecipe(
                            "wide_recipe_" + index,
                            List.of(InputContent.fromValue(input, 1L)),
                            List.of(OutputContent.fromValue(outA, 1L))
                        )
                    )
                )
            );
        }
        DatabasePage page = databasePageWithScratch(systems);

        page.debugOpenSourcesPage();

        WidgetGroup<?> results = (WidgetGroup<?>) page.children().get(1);
        CirrusSectionWidget sources = (CirrusSectionWidget) results.children().getFirst();
        DatabasePage.VirtualizedSourceGroupsWidget groups = (DatabasePage.VirtualizedSourceGroupsWidget) sources.body()
                .children().get(1);

        assertEquals(1, groups.children().size());
        WidgetGroup<?> groupWidget = (WidgetGroup<?>) groups.children().getFirst();
        assertEquals(2, groupWidget.children().size());
        WidgetGroup<?> rows = (WidgetGroup<?>) groupWidget.children().get(1);
        assertEquals(10, rows.children().size());
        assertEquals(10, groups.debugSourceCount());
    }

    @Test
    void databasePageCanMultiSelectIndividualSystemRecipesWithoutEnablingWholeSource() {
        Namespace source = Namespace.of("test", "manual_partial");
        FakeRecipeSystem system = new FakeRecipeSystem(
            source,
            List.of(
                new FakeRecipe(
                    "partial_alpha",
                    List.of(InputContent.fromValue(input, 1L)),
                    List.of(OutputContent.fromValue(outA, 1L))
                ),
                new FakeRecipe(
                    "partial_beta",
                    List.of(InputContent.fromValue(input, 2L)),
                    List.of(OutputContent.fromValue(outB, 1L))
                )
            )
        );
        DatabasePage page = databasePageWithScratch(system);

        assertEquals(0, page.debugVisibleResultCount());
        assertEquals(0, page.debugSystemRecipeSelectionCount());
        assertEquals(null, page.debugSelectedSystemRecipeSource());

        page.debugOpenSystemRecipeSource(source.toString());

        assertEquals(2, page.debugSystemRecipeSelectionCount());

        page.debugSetSystemRecipeSelected(source.toString(), "test:partial_alpha", true);
        page.debugSetSystemRecipeSelected(source.toString(), "test:partial_beta", true);

        assertEquals(0, page.debugActiveDatabaseSystemSourceCount());
        assertEquals(2, page.debugActiveDatabaseRecipeCount());

        page.debugLeaveSystemRecipeSource();
        page.debugLeaveSourcesPage();
        page.debugSetQuery("partial_alpha");
        assertEquals(1, page.debugVisibleResultCount());

        page.debugOpenSystemRecipeSource(source.toString());
        page.debugSetSystemRecipeSelected(source.toString(), "test:partial_alpha", false);
        page.debugLeaveSystemRecipeSource();
        page.debugLeaveSourcesPage();
        page.debugSetQuery("partial_alpha");

        assertEquals(1, page.debugActiveDatabaseRecipeCount());
        assertEquals(0, page.debugVisibleResultCount());
    }

    @Test
    void systemRecipeSourcePageShowsAllMatchesWithSelectableRecipeRows() {
        Namespace source = Namespace.of("test", "manual_many");
        ArrayList<FakeRecipe> recipes = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            recipes.add(
                new FakeRecipe(
                    "manual_recipe_" + i,
                    List.of(InputContent.fromValue(input, i + 1L)),
                    List.of(OutputContent.fromValue(outA, 1L), OutputContent.fromValue(outB, 1L))
                )
            );
        }
        FakeRecipeSystem system = new FakeRecipeSystem(source, recipes);
        DatabasePage page = databasePageWithScratch(system);

        page.debugOpenSystemRecipeSource(source.toString());

        assertEquals(20, page.debugSystemRecipeSelectionCount());
        assertEquals(28, DatabasePage.systemRecipeSelectionRowHeight());
        assertEquals(2, DatabasePage.systemRecipeSelectionRowGap());
        assertEquals(3, DatabasePage.systemRecipeSelectionRowPadding());
        assertEquals(4, DatabasePage.systemRecipeSelectionActionGap());
        assertEquals(20, DatabasePage.systemRecipeActionButtonWidth());
        assertEquals(598, DatabasePage.systemRecipeSelectionListHeight(20));

        WidgetGroup<?> results = (WidgetGroup<?>) page.children().get(1);
        CirrusSectionWidget section = (CirrusSectionWidget) results.children().getFirst();
        assertEquals(2, section.body().children().size());
        assertTrue(section.body().children().get(1) instanceof DatabasePage.SystemRecipeSelectionListWidget);
        DatabasePage.SystemRecipeSelectionListWidget list = (DatabasePage.SystemRecipeSelectionListWidget) section
                .body().children().get(1);

        assertEquals(20, list.selectionCount());
    }

    @Test
    void systemRecipeSourcePageBulkSelectsCurrentFilteredMatches() {
        Namespace source = Namespace.of("test", "manual_bulk");
        FakeRecipeSystem system = new FakeRecipeSystem(
            source,
            List.of(
                new FakeRecipe(
                    "bulk_alpha",
                    List.of(InputContent.fromValue(input, 1L)),
                    List.of(OutputContent.fromValue(outA, 1L))
                ),
                new FakeRecipe(
                    "bulk_beta",
                    List.of(InputContent.fromValue(input, 2L)),
                    List.of(OutputContent.fromValue(outB, 1L))
                )
            )
        );
        DatabasePage page = databasePageWithScratch(system);

        page.debugOpenSystemRecipeSource(source.toString());
        page.debugSetQuery("bulk_alpha");
        page.debugSetVisibleSystemRecipesSelected(source.toString(), true);

        assertEquals(1, page.debugActiveDatabaseRecipeCount());

        page.debugSetVisibleSystemRecipesSelected(source.toString(), false);

        assertEquals(0, page.debugActiveDatabaseRecipeCount());
    }

    @Test
    void databasePageDeletesCustomDatabasesOnlyAfterConfirmation() {
        Namespace source = Namespace.of("test", "delete_active");
        RecipeDatabaseWorkspace workspace = new RecipeDatabaseWorkspace(
            List.of(
                CustomRecipeDatabase.builtinDatabase(),
                new CustomRecipeDatabase("scratch", "Scratch", List.of(), List.of(source), List.of())
            ),
            "scratch"
        );
        DatabasePage page = new DatabasePage(workspace, List.of(), Map.of());

        assertEquals("scratch", page.debugActiveDatabaseId());
        assertEquals(2, page.debugCustomDatabaseCount());
        assertTrue(page.debugDeleteDatabaseEnabled());

        page.debugRequestDeleteActiveDatabase();

        assertEquals("scratch", page.debugActiveDatabaseId());
        assertTrue(page.debugDeleteDatabaseConfirmVisible());

        page.debugConfirmDeleteActiveDatabase();

        assertEquals(CustomRecipeDatabase.builtinId, page.debugActiveDatabaseId());
        assertEquals(1, page.debugCustomDatabaseCount());
        assertTrue(page.debugActiveDatabaseBuiltin());

        page.debugRequestDeleteActiveDatabase();
        assertTrue(page.debugActiveDatabaseBuiltin());
        assertFalse(page.debugDeleteDatabaseEnabled());
        assertFalse(page.debugDeleteDatabaseVisible());
        assertFalse(page.debugDeleteDatabaseConfirmVisible());
    }

    @Test
    void databasePageSearchesOnlyTheActiveDatabase() {
        Namespace alpha = Namespace.of("test", "alpha-active");
        Namespace beta = Namespace.of("test", "beta-active");
        FakeRecipeSystem alphaSystem = new FakeRecipeSystem(
            alpha,
            List.of(
                new FakeRecipe(
                    "alpha_recipe",
                    List.of(InputContent.fromValue(input, 1L)),
                    List.of(OutputContent.fromValue(outA, 1L))
                )
            )
        );
        FakeRecipeSystem betaSystem = new FakeRecipeSystem(
            beta,
            List.of(
                new FakeRecipe(
                    "beta_recipe",
                    List.of(InputContent.fromValue(input, 1L)),
                    List.of(OutputContent.fromValue(outB, 1L))
                )
            )
        );
        RecipeDatabaseWorkspace workspace = new RecipeDatabaseWorkspace(
            List.of(
                new CustomRecipeDatabase("alpha", "Alpha", List.of(), List.of(alpha), List.of()),
                new CustomRecipeDatabase("beta", "Beta", List.of(), List.of(beta), List.of())
            ),
            "alpha"
        );
        DatabasePage page = new DatabasePage(workspace, List.of(alphaSystem, betaSystem), Map.of());

        page.debugSetQuery("alpha_recipe");
        assertEquals(1, page.debugVisibleResultCount());
        page.debugSetQuery("beta_recipe");
        assertEquals(0, page.debugVisibleResultCount());

        page.debugSetActiveDatabase("beta");

        page.debugSetQuery("alpha_recipe");
        assertEquals(0, page.debugVisibleResultCount());
        page.debugSetQuery("beta_recipe");
        assertEquals(1, page.debugVisibleResultCount());
    }

    private static DatabasePage databasePageWithScratch(RecipeSystem<?> system) {
        return databasePageWithScratch(List.of(system));
    }

    private static DatabasePage databasePageWithScratch(List<? extends RecipeSystem<?>> systems) {
        RecipeDatabaseWorkspace workspace = new RecipeDatabaseWorkspace(
            List.of(
                CustomRecipeDatabase.builtinDatabase(),
                new CustomRecipeDatabase("scratch", "Scratch", List.of(), List.of(), List.of())
            ),
            "scratch"
        );
        return new DatabasePage(workspace, List.copyOf(systems), Map.of());
    }

    private static RecipeDatabaseIndex indexWithMultiOutputRecipe() {
        Namespace source = Namespace.of("test", "database");
        FakeRecipe recipe = new FakeRecipe(
            "multi_output_recipe_with_a_very_long_id",
            List.of(InputContent.fromValue(input, 1L)),
            List.of(
                OutputContent.fromValue(outA, 1L),
                OutputContent.fromValue(outB, 1L),
                OutputContent.fromValue(outC, 1L),
                OutputContent.fromValue(outD, 1L)
            )
        );
        FakeRecipeSystem system = new FakeRecipeSystem(source, List.of(recipe));
        return RecipeDatabaseIndex.build(
            List.of(new DatabaseDefinition("main", "Main", DatabaseKind.system, true, 0, List.of(source))),
            Lists.immutable.of(system),
            Map.of()
        );
    }

    private static RecipeApiImpl attachDebugProjectionRecipeApi() {
        InternalPlugin internal = new InternalPlugin();
        DebugProjectionPlugin plugin = new DebugProjectionPlugin();
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        internal.registerProjectionPlugin(builder);
        internal.registerProjectionEffect(builder);
        plugin.registerProjectionPlugin(builder);
        plugin.registerProjectionContextOption(builder);
        plugin.registerRecipeSystem(builder);
        plugin.registerProjectionEffect(builder);
        plugin.registerMachineEntry(builder);
        builder.registerProjectionPlugin(irrelevantProjectionPlugin);
        builder.registerProjectionContextOption(
            new LongProjectionContextOption(
                Namespace.of("test", "projection/irrelevant_level"),
                irrelevantProjectionPlugin,
                irrelevantProjectionOptionKey,
                "cirrus.ui.calculator.database.debug_plugin.scale",
                "",
                1L,
                8L,
                1L
            )
        );
        RecipeApiImpl api = new RecipeApiImpl(builder);
        RecipeApiImpl.attach(api);
        return api;
    }

    private static void attachContentApiForDebugProjection() {
        ContentRegistryBuilder builder = new ContentRegistryBuilder();
        InternalPlugin internal = new InternalPlugin();
        internal.registerContentType(builder);
        internal.registerContentVariant(builder);
        internal.registerContentResolver(builder);
        ContentApiImpl.attach(new ContentApiImpl(builder));
    }

    private static RecipeDatabaseIndex indexWithEnergySpecRecipe() {
        Namespace source = Namespace.of("test", "energy");
        FakeRecipe recipe = new FakeRecipe(
            "energy_spec_recipe",
            List.of(InputContent.fromValue(input, 1L)),
            List.of(OutputContent.fromValue(outA, 1L), OutputContent.fromEnergy(16_000_000L))
        );
        FakeRecipeSystem system = new FakeRecipeSystem(source, List.of(recipe));
        return RecipeDatabaseIndex.build(
            List.of(new DatabaseDefinition("main", "Main", DatabaseKind.system, true, 0, List.of(source))),
            Lists.immutable.of(system),
            Map.of()
        );
    }

    private static RecipeDatabaseIndex indexWithTwoSystems() {
        Namespace alpha = Namespace.of("test", "alpha");
        Namespace beta = Namespace.of("test", "beta");
        FakeRecipeSystem alphaSystem = new FakeRecipeSystem(
            alpha,
            List.of(
                new FakeRecipe(
                    "alpha_recipe",
                    List.of(InputContent.fromValue(input, 1L)),
                    List.of(OutputContent.fromValue(outA, 1L))
                )
            )
        );
        FakeRecipeSystem betaSystem = new FakeRecipeSystem(
            beta,
            List.of(
                new FakeRecipe(
                    "beta_recipe",
                    List.of(InputContent.fromValue(input, 1L)),
                    List.of(OutputContent.fromValue(outB, 1L))
                )
            )
        );
        return RecipeDatabaseIndex.build(
            List.of(new DatabaseDefinition("main", "Main", DatabaseKind.system, true, 0, List.of(alpha, beta))),
            Lists.immutable.of(betaSystem, alphaSystem),
            Map.of()
        );
    }

    private static RecipeDatabaseIndex indexWithTwoSystemsInOneCategory() {
        Namespace alpha = Namespace.of("test", "alpha");
        Namespace beta = Namespace.of("test", "beta");
        Namespace category = Namespace.of("test", "category/crafting");
        FakeRecipeSystem alphaSystem = new FakeRecipeSystem(
            alpha,
            List.of(
                new FakeRecipe(
                    "alpha_recipe",
                    List.of(InputContent.fromValue(input, 1L)),
                    List.of(OutputContent.fromValue(outA, 1L))
                )
            )
        );
        FakeRecipeSystem betaSystem = new FakeRecipeSystem(
            beta,
            List.of(
                new FakeRecipe(
                    "beta_recipe",
                    List.of(InputContent.fromValue(input, 1L)),
                    List.of(OutputContent.fromValue(outB, 1L))
                )
            )
        );
        return RecipeDatabaseIndex.build(
            List.of(new DatabaseDefinition("main", "Main", DatabaseKind.system, true, 0, List.of(alpha, beta))),
            Lists.immutable.of(betaSystem, alphaSystem),
            Map.of(alpha, List.of(category), beta, List.of(category))
        );
    }

    private static boolean hasLine(List<String> lines, String... needles) {
        for (String line : lines) {
            for (String needle : needles) {
                if (line.contains(needle)) {
                    return true;
                }
            }
        }
        return false;
    }

    private record FakeRecipe(String id, Collection<InputContent> inputs, Collection<OutputContent> outputs) {}

    private static final class FakeRecipeSystem implements RecipeSystem<FakeRecipe> {
        private final Namespace systemId;
        private final RichIterable<FakeRecipe> recipes;
        private final Map<String, FakeRecipe> byId = new ConcurrentHashMap<>();
        private int allRecipesCalls;

        private FakeRecipeSystem(Namespace systemId, Collection<FakeRecipe> recipes) {
            this.systemId = systemId;
            this.recipes = Lists.immutable.ofAll(recipes);
            recipes.forEach(recipe -> byId.put(recipe.id(), recipe));
        }

        @Override
        public Namespace systemId() {
            return systemId;
        }

        @Override
        public Class<?> basicType() {
            return FakeRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(FakeRecipe recipe) {
            return ResourceLocation.fromNamespaceAndPath("test", recipe.id());
        }

        @Override
        public @Nullable FakeRecipe findRecipe(String uniqueId) {
            return byId.get(uniqueId);
        }

        @Override
        public @Nullable FakeRecipe findRecipe(ResourceLocation registryName) {
            return byId.get(registryName.getPath());
        }

        @Override
        public int hash(FakeRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(FakeRecipe r1, FakeRecipe r2) {
            return Objects.equals(r1.id(), r2.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, FakeRecipe recipe) {
            return ResolveResult
                    .resolved(RecipeProjection.create(RecipeEntry.of(this, recipe), recipe.inputs(), recipe.outputs()));
        }

        @Override
        public RichIterable<FakeRecipe> allRecipes() {
            allRecipesCalls++;
            return recipes;
        }

        private int debugAllRecipesCalls() {
            return allRecipesCalls;
        }
    }

    private static final class TestValueType implements ValueType<Long> {
        private final Namespace id;

        private TestValueType(String path) {
            this.id = Namespace.of("test", path);
        }

        @Override
        public Namespace typeId() {
            return id;
        }

        @Override
        public long getValue(Long content) {
            return content;
        }

        @Override
        public boolean matches(Long first, Long second) {
            return Objects.equals(first, second);
        }

        @Override
        public Long fromValue(long value) {
            return value;
        }

        @Override
        public Long copy(Long content) {
            return content;
        }
    }
}
