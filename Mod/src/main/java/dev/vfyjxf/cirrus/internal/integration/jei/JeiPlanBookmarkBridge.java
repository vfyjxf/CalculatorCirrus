package dev.vfyjxf.cirrus.internal.integration.jei;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningService;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.content.type.FluidStackType;
import dev.vfyjxf.cirrus.planning.bridge.CirrusContentBridge;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cirrus.planning.bridge.PlanningContextFactory;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionQueueStore;
import dev.vfyjxf.cirrus.ui.execution.overlay.ExecutionOverlayOpenState;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorScreen;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cirrus.ui.workbench.flow.StepNodeCardRenderer;
import dev.vfyjxf.cirrus.ui.workbench.layout.PreparationLedgerModel;
import dev.vfyjxf.cirrus.ui.workbench.value.ValueTypeVisualRegistry;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IBookmarkManager;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Pure public-API bridge between Cirrus production flows and JEI's {@link IBookmarkManager}.
 */
public final class JeiPlanBookmarkBridge {

    private JeiPlanBookmarkBridge() {}

    public static boolean isPlanBookmarked(@Nullable ProductionPlan plan) {
        if (plan == null) {
            return false;
        }
        String id = CirrusPlanBookmarkStore.planKeyOf(plan);
        CirrusPlanBookmarkStore.PlanBookmarkEntry entry = CirrusPlanBookmarkStore.find(id);
        if (entry == null) {
            return false;
        }
        IJeiRuntime runtime = CirrusJeiPlugin.jeiRuntimeOrNull();
        if (runtime != null) {
            IBookmarkManager bookmarks = runtime.getGuiRuntime().getBookmarkManager();
            if (!bookmarks.contains(PlanFlowRecipeCategory.recipeType, PlanFlowRecipe.of(entry))) {
                CirrusPlanBookmarkStore.remove(id);
                return false;
            }
        }
        return true;
    }

    /**
     * Toggles a production plan flow bookmark in JEI via {@link IBookmarkManager}. Returns {@code true} if now bookmarked.
     */
    public static boolean togglePlanBookmark(
        ProductionPlan plan,
        @Nullable PreparationLedgerModel ledger,
        Set<String> checkedStepIds
    ) {
        String key = CirrusPlanBookmarkStore.planKeyOf(plan);
        if (isPlanBookmarked(plan)) {
            removePlanBookmark(key);
            return false;
        }
        bookmarkPlan(plan, ledger, checkedStepIds);
        return true;
    }

    public static void bookmarkPlan(
        ProductionPlan plan,
        @Nullable PreparationLedgerModel ledger,
        Set<String> checkedStepIds
    ) {
        String title = resolveDisplayTitle(plan);
        CirrusPlanBookmarkStore.PlanBookmarkEntry entry = CirrusPlanBookmarkStore
                .fromPlan(plan, ledger, checkedStepIds, title);
        CirrusPlanBookmarkStore.put(entry);
        IJeiRuntime runtime = CirrusJeiPlugin.jeiRuntimeOrNull();
        if (runtime != null) {
            IBookmarkManager bookmarks = runtime.getGuiRuntime().getBookmarkManager();
            PlanFlowRecipe recipe = PlanFlowRecipe.of(entry);
            if (!bookmarks.contains(PlanFlowRecipeCategory.recipeType, recipe)) {
                bookmarks.add(PlanFlowRecipeCategory.recipeType, recipe);
            }
        }
    }

    public static void removePlanBookmark(String id) {
        CirrusPlanBookmarkStore.PlanBookmarkEntry existing = CirrusPlanBookmarkStore.find(id);
        CirrusPlanBookmarkStore.remove(id);
        if (existing == null) {
            return;
        }
        IJeiRuntime runtime = CirrusJeiPlugin.jeiRuntimeOrNull();
        if (runtime != null) {
            runtime.getGuiRuntime().getBookmarkManager()
                    .remove(PlanFlowRecipeCategory.recipeType, PlanFlowRecipe.of(existing));
        }
    }

    public static void showRecipesFor(@Nullable ResourceKey resource) {
        showRoleFor(resource, RecipeIngredientRole.OUTPUT);
    }

    public static void showUsesFor(@Nullable ResourceKey resource) {
        showRoleFor(resource, RecipeIngredientRole.INPUT);
    }

    private static void showRoleFor(@Nullable ResourceKey resource, RecipeIngredientRole role) {
        if (resource == null) {
            return;
        }
        IJeiRuntime runtime = CirrusJeiPlugin.jeiRuntimeOrNull();
        if (runtime == null) {
            return;
        }
        ContentStack<?, ?> content = resolveDisplayContent(resource);
        Object ingredient = (content != null && !content.isEmpty()) ? content.value() : null;
        if (ingredient == null) {
            return;
        }
        IIngredientManager manager = runtime.getIngredientManager();
        IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
        manager.createTypedIngredient(ingredient).ifPresent(typed -> {
            runtime.getGuiRuntime().getRecipesGui().show(focusFactory.createFocus(role, typed));
        });
    }

    static void openBookmarkInWorkbench(CirrusPlanBookmarkStore.PlanBookmarkEntry entry) {
        openBookmarkInWorkbench(entry, 1);
    }

    static void openBookmarkInWorkbench(CirrusPlanBookmarkStore.PlanBookmarkEntry entry, int scaleMultiplier) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        int mult = Math.max(1, scaleMultiplier);
        long scaledAmount = safeScale(Math.max(1L, entry.primaryAmount()), mult);
        CalculatorScreen screen = new CalculatorScreen();
        mc.setScreen(screen);
        TargetDemand demand = new TargetDemand(entry.primaryResource(), scaledAmount);
        screen.shell().tabManager().openOrFocusTab(List.of(demand), null);
    }

    static void sendBookmarkToExecution(CirrusPlanBookmarkStore.PlanBookmarkEntry entry) {
        sendBookmarkToExecution(entry, 1);
    }

    static void sendBookmarkToExecution(CirrusPlanBookmarkStore.PlanBookmarkEntry entry, int scaleMultiplier) {
        int mult = Math.max(1, scaleMultiplier);
        CirrusPlanningRuntime runtime = CirrusPlanningRuntime.sharedOrCreate();
        if (runtime.currentSnapshot().isEmpty()) {
            runtime.reloadFromRuntimeAsync().thenAccept(snapOpt -> {
                if (snapOpt.isEmpty()) {
                    return;
                }
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    mc.execute(() -> enqueueResolvedBookmark(entry, runtime, mult));
                }
            });
            return;
        }
        enqueueResolvedBookmark(entry, runtime, mult);
    }

    private static void enqueueResolvedBookmark(
        CirrusPlanBookmarkStore.PlanBookmarkEntry entry,
        CirrusPlanningRuntime runtime,
        int scaleMultiplier
    ) {
        ProductionPlan plan = scaleMultiplier == 1 ? entry.livePlan() : null;
        if (plan == null) {
            plan = recomputePlanForBookmark(entry, runtime, scaleMultiplier);
        }
        if (plan == null) {
            return;
        }
        CompiledRecipeGraph graph = runtime.currentGraph().orElse(null);
        RecipeEntry<?> resolved = null;
        int resolvedTimes = 1;
        int craftStepCount = 0;
        for (int i = plan.steps().size() - 1; i >= 0; i--) {
            PlanStep step = plan.steps().get(i);
            CompiledRecipeKey key = null;
            int batches = 1;
            if (step instanceof CraftStep craft) {
                key = craft.recipeKey();
                batches = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, craft.batches()));
                craftStepCount++;
            } else if (step instanceof ConvertStep convert) {
                key = convert.conversionRecipeKey();
                batches = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, convert.batches()));
                craftStepCount++;
            }
            if (key == null) {
                continue;
            }
            RecipeEntry<?> candidate = runtime.resolveRecipeEntry(key).orElse(null);
            if (candidate == null) {
                continue;
            }
            if (resolved == null) {
                resolved = candidate;
                resolvedTimes = batches;
            }
            if (graph != null) {
                boolean matches = graph.recipeByKey(key)
                        .map(r -> r.outputs().stream().anyMatch(o -> entry.primaryResource().equals(o.resource())))
                        .orElse(false);
                if (matches) {
                    resolved = candidate;
                    resolvedTimes = batches;
                    break;
                }
            }
        }
        if (resolved == null) {
            return;
        }
        int totalCraftSteps = Math.max(1, Math.max(craftStepCount, entry.craftSteps()));
        ExecutionQueueStore.enqueue(resolved, Math.max(1, resolvedTimes), plan, totalCraftSteps, true);
        ExecutionOverlayOpenState.setOpen(true);
    }

    private static @Nullable ProductionPlan recomputePlanForBookmark(
        CirrusPlanBookmarkStore.PlanBookmarkEntry entry,
        CirrusPlanningRuntime runtime,
        int scaleMultiplier
    ) {
        PlanningService service = runtime.currentService().orElse(null);
        PlanningContextFactory factory = runtime.contextFactory().orElse(null);
        if (service == null || factory == null) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        long scaledAmount = safeScale(Math.max(1L, entry.primaryAmount()), Math.max(1, scaleMultiplier));
        List<TargetDemand> targets = List.of(new TargetDemand(entry.primaryResource(), scaledAmount));
        PlanningContext rawCtx = factory.createContext(mc == null ? null : mc.player, null, runtime.guidanceStore());
        InventorySnapshot filteredInv = PlanningContextFactory.excludeTargetStock(rawCtx.inventory(), targets);
        PlanningContext ctx = new PlanningContext(
            filteredInv,
            rawCtx.facilities(),
            rawCtx.acquisitionOracle(),
            rawCtx.guidance(),
            rawCtx.progression()
        );
        PlanningRequest request = new PlanningRequest(
            targets,
            ctx,
            PlanningProfile.defaultBalanced(),
            PlanningBudget.defaultInteractive()
        );
        PlanningResult result = service.planOnce(request);
        if (result.paretoPlans().isEmpty()) {
            return null;
        }
        return result.paretoPlans().get(0);
    }

    private static String resolveDisplayTitle(ProductionPlan plan) {
        CirrusPlanningRuntime runtime = CirrusPlanningRuntime.sharedIfCreated();
        CompiledRecipeGraph graph = runtime == null ? null : runtime.currentGraph().orElse(null);
        for (int i = plan.steps().size() - 1; i >= 0; i--) {
            StepNodeCardRenderer.StepVisuals visuals = StepNodeCardRenderer
                    .resolve(plan.steps().get(i), graph, null, false);
            if (visuals.primaryOutput() != null) {
                return CoPilotPaint.resourceName(visuals.primaryOutput()).getString();
            }
        }
        return plan.planId();
    }

    static @Nullable ContentStack<?, ?> resolveDisplayContent(@Nullable ResourceKey resource) {
        if (resource == null) {
            return null;
        }
        try {
            ContentStack<?, ?> content = CirrusContentBridge.toBestEffortContentStack(resource, 1L).orElse(null);
            if (content != null && !content.isEmpty()) {
                return content;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Headless environments have no bound client content bridge.
        }
        return null;
    }

    static ItemStack resolveDisplayStack(ResourceKey resource) {
        ContentStack<?, ?> content = resolveDisplayContent(resource);
        if (content != null && content.value() instanceof ItemStack itemStack) {
            ItemStack copy = itemStack.copy();
            copy.setCount(1);
            return copy;
        }
        return new ItemStack(Items.CRAFTING_TABLE);
    }

    static PlanFlowViewModel.ResourceUnitSpec resolveUnitSpec(@Nullable ResourceKey resource) {
        if (resource == null) {
            return PlanFlowViewModel.ResourceUnitSpec.plain;
        }
        boolean fluidKey = isFluidContentType(resource.contentTypeId());
        ContentStack<?, ?> content = resolveDisplayContent(resource);
        if (content != null && !content.isEmpty()) {
            if (content.value() instanceof ItemStack stack) {
                return new PlanFlowViewModel.ResourceUnitSpec(
                    PlanFlowViewModel.QuantityUnitKind.itemStack,
                    Math.max(1L, stack.getMaxStackSize())
                );
            }
            if (fluidKey || content.type() instanceof FluidStackType) {
                return PlanFlowViewModel.ResourceUnitSpec.fluid;
            }
            long unitQty = content.type().quantity();
            if (unitQty > 1L) {
                return new PlanFlowViewModel.ResourceUnitSpec(PlanFlowViewModel.QuantityUnitKind.itemStack, unitQty);
            }
            return PlanFlowViewModel.ResourceUnitSpec.plain;
        }
        if (fluidKey) {
            return PlanFlowViewModel.ResourceUnitSpec.fluid;
        }
        if (ValueTypeVisualRegistry.isValueResourceKey(resource)) {
            return PlanFlowViewModel.ResourceUnitSpec.plain;
        }
        return PlanFlowViewModel.ResourceUnitSpec.defaultItem;
    }

    private static boolean isFluidContentType(@Nullable String contentTypeId) {
        if (contentTypeId == null) {
            return false;
        }
        return contentTypeId.contains("fluid");
    }

    private static long safeScale(long base, int mult) {
        if (mult <= 1) {
            return base;
        }
        try {
            return Math.multiplyExact(base, mult);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
