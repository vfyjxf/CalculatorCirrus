package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.widget.calculator.calc.DebugRecipeProvider;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionTargetRecipeState;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorTargetRecipeState;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.DatabasePage;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class OmniCalculatorViewContractTest {

    @BeforeEach
    void clearCalculatorTarget() {
        CalculatorTargetRecipeState.clear();
        ExecutionTargetRecipeState.clear();
    }

    @Test
    void calculateSwitchesFromEmptyStateToTreeResultView() {
        OmniCalculatorView view = new OmniCalculatorView();

        assertFalse(view.isCalculated());
        assertFalse(view.debugShowingCalculatingState());
        assertEquals(OmniCalculatorView.ProcessView.TREE, view.currentProcessView());

        view.onCalculate();
        view.debugCompletePendingCalculation();

        assertFalse(view.debugShowingCalculatingState());
        assertTrue(view.isCalculated());
        assertEquals(OmniCalculatorView.ProcessView.TREE, view.currentProcessView());
    }

    @Test
    void restartingCalculationReplacesPendingTaskAndFinishesNormally() {
        FakeRecipe first = new FakeRecipe(
            "first",
            java.util.List.of(InputContent.fromItemStack(new ItemStack(Items.OAK_LOG), 1)),
            java.util.List.of(OutputContent.fromItemStack(new ItemStack(Items.OAK_PLANKS), 4, 1f))
        );
        FakeRecipe second = new FakeRecipe(
            "second",
            java.util.List.of(InputContent.fromItemStack(new ItemStack(Items.OAK_PLANKS), 2)),
            java.util.List.of(OutputContent.fromItemStack(new ItemStack(Items.STICK), 4, 1f))
        );
        FakeRecipeSystem system = new FakeRecipeSystem(Lists.immutable.of(first, second));
        RecipeApiImpl previous = attachRecipeApi(system);
        try {
            CalculatorTargetRecipeState.set(RecipeEntry.of(system, first), 1);
            OmniCalculatorView view = new OmniCalculatorView();
            assertTrue(view.debugCalculationPending());
            assertTrue(view.debugShowingCalculatingState());

            CalculatorTargetRecipeState.set(RecipeEntry.of(system, second), 1);
            view.tick();

            assertTrue(view.debugCalculationPending());
            assertTrue(view.debugShowingCalculatingState());
            view.debugCompletePendingCalculation();

            assertFalse(view.debugCalculationPending());
            assertFalse(view.debugShowingCalculatingState());
            assertTrue(view.isCalculated());
            assertEquals(OmniCalculatorView.ProcessView.TREE, view.currentProcessView());
        } finally {
            restoreRecipeApi(previous);
        }
    }

    @Test
    void repeatedCalculatePreservesSelectedResultTab() {
        OmniCalculatorView view = new OmniCalculatorView();

        view.onCalculate();
        view.debugCompletePendingCalculation();
        view.switchProcessView(OmniCalculatorView.ProcessView.MATRIX);
        view.onCalculate();
        view.debugCompletePendingCalculation();

        assertTrue(view.isCalculated());
        assertEquals(OmniCalculatorView.ProcessView.MATRIX, view.currentProcessView());
    }

    @Test
    void settingsButtonTogglesCalculatorSettingsPanelAndCalculateReturnsToResults() {
        OmniCalculatorView view = new OmniCalculatorView();

        view.toggleSettingsPanel();

        assertTrue(view.debugSettingsPanelOpen());

        view.onCalculate();
        view.debugCompletePendingCalculation();

        assertFalse(view.debugSettingsPanelOpen());
        assertTrue(view.isCalculated());
        assertEquals(OmniCalculatorView.ProcessView.TREE, view.currentProcessView());
    }

    @Test
    void treeResultStartsWithMaterialPanelHidden() {
        TreeViewWidget treeView = new TreeViewWidget();

        treeView.setGraph(DebugRecipeProvider.createDebugProcessGraph());

        assertFalse(treeView.debugIsMaterialPanelVisible());
    }

    @Test
    void treeResultShowsHeaderActionsNextToViewTabs() {
        OmniCalculatorView view = new OmniCalculatorView();

        view.onCalculate();
        view.debugCompletePendingCalculation();

        assertEquals(4, view.debugTreeHeaderActionCount());
        assertTrue(view.debugTreeHeaderActionsVisible());

        view.switchProcessView(OmniCalculatorView.ProcessView.MATRIX);

        assertFalse(view.debugTreeHeaderActionsVisible());
    }

    @Test
    void executionPathActionIsUnavailableWithoutCalculatedRoute() {
        OmniCalculatorView view = new OmniCalculatorView();

        assertFalse(view.debugCurrentPathCanSetExecutionTarget());
    }

    @Test
    void switchingProcessTabsRepeatedlyDoesNotReuseDestroyedWidgets() {
        OmniCalculatorView view = new OmniCalculatorView();
        view.onCalculate();
        view.debugCompletePendingCalculation();

        assertDoesNotThrow(() -> {
            view.switchProcessView(OmniCalculatorView.ProcessView.MATRIX);
            view.switchProcessView(OmniCalculatorView.ProcessView.MILESTONES);
            view.switchProcessView(OmniCalculatorView.ProcessView.TREE);
            view.switchProcessView(OmniCalculatorView.ProcessView.MATRIX);
            view.switchProcessView(OmniCalculatorView.ProcessView.TREE);
        });
        assertEquals(OmniCalculatorView.ProcessView.TREE, view.currentProcessView());
    }

    @Test
    void treeResultBuildsNonEmptyMaterialEntries() {
        TreeViewWidget treeView = new TreeViewWidget();

        treeView.setGraph(DebugRecipeProvider.createDebugProcessGraph());

        assertTrue(treeView.debugMaterialEntryCount() > 0);
    }

    @Test
    void treeResultCarriesRequestedCraftCountAndDefersAutoFitUntilSized() {
        TreeViewWidget treeView = new TreeViewWidget();

        treeView.setCraftCount(16);
        treeView.setGraph(DebugRecipeProvider.createDebugProcessGraph());

        assertEquals(16, treeView.debugCraftCount());
        assertTrue(treeView.debugCanvasPendingAutoFit());
    }

    @Test
    void controlBarUsesCompactFixedMetrics() {
        ControlBarWidget.ControlBarMetrics metrics = ControlBarWidget.debugMetrics();

        assertEquals(30, metrics.height());
        assertEquals(18, metrics.targetSlotSize());
        assertEquals(56, metrics.amountInputWidth());
        assertEquals(16, metrics.settingsButtonSize());
        assertEquals(72, metrics.calculateButtonWidth());
        assertFalse(ControlBarWidget.debugUsesLegacyStripTexture());
        assertTrue(ControlBarWidget.debugUsesStandardButtonTexture());
    }

    @Test
    void controlBarTopControlsExposeTooltipsAndAmountCalculatorPopup() {
        ControlBarWidget controlBar = new ControlBarWidget(new OmniCalculatorView());

        assertTrue(controlBar.debugAmountUsesCalculatorPopup());
        assertTrue(DatabasePage.debugTooltipLines(controlBar.debugTargetTooltip()).size() >= 2);
        assertTrue(DatabasePage.debugTooltipLines(controlBar.debugAmountTooltip()).size() >= 5);
        assertTrue(DatabasePage.debugTooltipLines(controlBar.debugAmountTooltip()).stream()
            .anyMatch(line -> line.contains("Middle") || line.contains("middle") || line.contains("中键")));
        assertTrue(DatabasePage.debugTooltipLines(controlBar.debugSettingsTooltip()).size() >= 2);
        assertTrue(DatabasePage.debugTooltipLines(controlBar.debugCalculateTooltip()).size() >= 2);

        controlBar.setTargetItem(new ItemStack(Items.SMOKER));

        assertTrue(DatabasePage.debugTooltipLines(controlBar.debugTargetTooltip()).stream()
            .anyMatch(line -> !line.isBlank()));
    }

    @Test
    void viewSwitcherActionsStayCompactWhenTreeViewIsActive() {
        ViewSwitcherWidget.ViewSwitcherMetrics metrics = ViewSwitcherWidget.debugMetrics();

        assertEquals(18, metrics.height());
        assertEquals(16, metrics.actionButtonSize());
    }

    private record FakeRecipe(
        String id,
        Collection<InputContent> inputs,
        Collection<OutputContent> outputs
    ) {
    }

    private static final class FakeRecipeSystem implements RecipeSystem<FakeRecipe> {
        private final RichIterable<FakeRecipe> recipes;
        private final Map<String, FakeRecipe> byId = new ConcurrentHashMap<>();

        private FakeRecipeSystem(RichIterable<FakeRecipe> recipes) {
            this.recipes = recipes;
            recipes.forEach(recipe -> byId.put(recipe.id(), recipe));
        }

        @Override
        public Namespace systemId() {
            return Namespace.of("test", "omnicalc");
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
        public boolean sameRecipe(FakeRecipe left, FakeRecipe right) {
            return Objects.equals(left.id(), right.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, FakeRecipe recipe) {
            return ResolveResult.resolved(RecipeProjection.create(RecipeEntry.of(this, recipe), recipe.inputs(), recipe.outputs()));
        }

        @Override
        public RichIterable<FakeRecipe> allRecipes() {
            return recipes;
        }
    }

    private static RecipeApiImpl attachRecipeApi(RecipeSystem<?> system) {
        RecipeApi previousApi = RecipeApiImpl.instance();
        RecipeApiImpl previous = previousApi instanceof RecipeApiImpl impl ? impl : null;
        dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder builder = new dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder();
        builder.registerSystem(system);
        RecipeApiImpl.attach(new RecipeApiImpl(builder));
        return previous;
    }

    private static void restoreRecipeApi(@Nullable RecipeApiImpl previous) {
        if (previous != null) {
            RecipeApiImpl.attach(previous);
        }
    }

}
