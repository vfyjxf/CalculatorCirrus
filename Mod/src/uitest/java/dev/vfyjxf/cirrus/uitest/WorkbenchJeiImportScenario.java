package dev.vfyjxf.cirrus.uitest;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.internal.integration.jei.CirrusJeiPlugin;
import dev.vfyjxf.cirrus.internal.integration.jei.JeiTargetRecipeBridge;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorScreen;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorTargetRecipeState;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.SpatialDagCanvasWidget;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchPhase;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cloudlib.uitest.api.TestContext;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

@ClientScenario(
    name = "cirrus.workbench.jei_import",
    group = "workbench",
    tags = {"workbench", "jei"},
    requiresWorld = true,
    hideGui = true,
    timeoutMs = 30_000,
    scenarioTimeoutMs = 180_000
)
public final class WorkbenchJeiImportScenario {

    private @Nullable WorkbenchShellWidget shell;
    private @Nullable SpatialDagCanvasWidget dag;

    public void define(ScenarioBuilder build) {
        build.guiScale(1).log("workbench.jei_import: JEI campfire import with empty and stocked inventory")
                .action("ensure runtime graph compilation is running", context -> {
                    CirrusPlanningRuntime runtime = CirrusPlanningRuntime.sharedOrCreate();
                    if (runtime.currentGraph().isEmpty() && !runtime.isCompiling()) {
                        runtime.reloadFromRuntimeAsync();
                    }
                })
                .waitUntil(
                    "planner graph and JEI runtime are ready",
                    () -> CirrusPlanningRuntime.sharedOrCreate().currentGraph().isPresent()
                            && CirrusJeiPlugin.recipeManagerOrNull() != null
                            && CirrusJeiPlugin.focusFactoryOrNull() != null
                )
                .group(
                    "import campfire from JEI with empty inventory",
                    () -> build.action("clear player inventory and import campfire via JEI bridge", context -> {
                        clearPlayerInventory();
                        importCraftingRecipeFromJei(Items.CAMPFIRE);
                    }).awaitScreen(CalculatorScreen.class).action("capture shell and dag", this::capture)
                            .waitUntil(
                                "campfire plan finishes with non-empty DAG",
                                () -> hasNonEmptyPlanAndDag(ComponentStackKey.ofItem(Items.CAMPFIRE))
                            ).frames(2).check(
                                "campfire plan has crafting steps and DAG nodes",
                                () -> hasNonEmptyPlanAndDag(ComponentStackKey.ofItem(Items.CAMPFIRE))
                            )
                )
                .group(
                    "import campfire from JEI while already holding campfire in inventory",
                    () -> build.action("put campfire in inventory and re-import campfire from JEI", context -> {
                        LocalPlayer player = Minecraft.getInstance().player;
                        if (player != null) {
                            player.getInventory().setItem(0, new ItemStack(Items.CAMPFIRE, 1));
                        }
                        importCraftingRecipeFromJei(Items.CAMPFIRE);
                    }).awaitScreen(CalculatorScreen.class).action("capture shell and dag", this::capture)
                            .waitUntil(
                                "stocked campfire import still produces crafting steps and DAG nodes",
                                () -> hasNonEmptyPlanAndDag(ComponentStackKey.ofItem(Items.CAMPFIRE))
                            ).frames(2).check(
                                "stocked campfire import does not collapse to 0 steps",
                                () -> hasNonEmptyPlanAndDag(ComponentStackKey.ofItem(Items.CAMPFIRE))
                            )
                )
                .group(
                    "direct open after JEI import starts clean without stale recipe",
                    () -> build.action("close and reopen CalculatorScreen directly", context -> {
                        Minecraft.getInstance().setScreen(null);
                        Minecraft.getInstance().setScreen(new CalculatorScreen());
                    }).awaitScreen(CalculatorScreen.class).action("capture shell and dag", this::capture).frames(2)
                            .check(
                                "direct open has empty targets",
                                () -> shell != null && shell.activeSnapshot().targets().isEmpty()
                            )
                )
                .group(
                    "second JEI import of chest produces non-empty DAG",
                    () -> build.action("import chest from JEI", context -> importCraftingRecipeFromJei(Items.CHEST))
                            .awaitScreen(CalculatorScreen.class).action("capture shell and dag", this::capture)
                            .waitUntil(
                                "chest plan finishes with non-empty DAG",
                                () -> hasNonEmptyPlanAndDag(ComponentStackKey.ofItem(Items.CHEST))
                            ).frames(2).check(
                                "chest plan has crafting steps and DAG nodes",
                                () -> hasNonEmptyPlanAndDag(ComponentStackKey.ofItem(Items.CHEST))
                            )
                ).action("cleanup", context -> {
                    clearPlayerInventory();
                    CalculatorTargetRecipeState.clear();
                    Minecraft.getInstance().setScreen(null);
                });
    }

    private void capture(TestContext context) {
        shell = WorkbenchTestKit.shell(context);
        dag = WorkbenchTestKit.first(SpatialDagCanvasWidget.class);
    }

    private boolean hasNonEmptyPlanAndDag(ComponentStackKey expectedTarget) {
        WorkbenchShellWidget currentShell = shell;
        SpatialDagCanvasWidget currentDag = dag;
        if (currentShell == null || currentDag == null) {
            return false;
        }
        WorkbenchUiSnapshot snap = currentShell.activeSnapshot();
        if (snap.phase() != WorkbenchPhase.readyPareto && snap.phase() != WorkbenchPhase.readyBudgetHit) {
            return false;
        }
        List<TargetDemand> targets = snap.targets();
        if (targets.isEmpty() || !expectedTarget.equals(targets.get(0).resource())) {
            return false;
        }
        var plan = snap.selectedPlan().orElse(null);
        return plan != null && !plan.steps().isEmpty() && !currentDag.dagLayout().nodes().isEmpty();
    }

    private static void importCraftingRecipeFromJei(Item targetItem) {
        IRecipeManager manager = CirrusJeiPlugin.recipeManagerOrNull();
        IFocusFactory focusFactory = CirrusJeiPlugin.focusFactoryOrNull();
        if (manager == null || focusFactory == null) {
            throw new IllegalStateException("JEI runtime is not ready");
        }
        IRecipeCategory<RecipeHolder<CraftingRecipe>> category = manager.getRecipeCategory(RecipeTypes.CRAFTING);
        ResourceLocation targetId = BuiltInRegistries.ITEM.getKey(targetItem);
        RecipeHolder<CraftingRecipe> holder = manager.createRecipeLookup(RecipeTypes.CRAFTING).get()
                .filter(h -> targetId.equals(h.id())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing crafting recipe for " + targetId));
        Optional<IRecipeLayoutDrawable<RecipeHolder<CraftingRecipe>>> layoutOpt = manager
                .createRecipeLayoutDrawable(category, holder, focusFactory.getEmptyFocusGroup());
        IRecipeLayoutDrawable<RecipeHolder<CraftingRecipe>> layout = layoutOpt
                .orElseThrow(() -> new IllegalStateException("JEI failed to build layout for " + targetId));
        RecipeEntry<?> entry = JeiTargetRecipeBridge.resolveCalculator(layout);
        if (entry == null) {
            throw new IllegalStateException("JeiTargetRecipeBridge returned null for " + targetId);
        }
        CalculatorTargetRecipeState.set(entry, 1);
        Minecraft.getInstance().setScreen(new CalculatorScreen());
    }

    private static void clearPlayerInventory() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.getInventory().clearContent();
        }
    }
}
