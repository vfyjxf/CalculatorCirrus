package dev.vfyjxf.cirrus.ui.widget.calculator;

import dev.vfyjxf.cirrus.api.planning.graph.RawOutputSlot;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectedRecipe;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectionEngine;
import dev.vfyjxf.cirrus.api.recipe.projection.context.CalculatorProjectionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContexts;
import dev.vfyjxf.cirrus.planning.bridge.CirrusCatalogAdapter;
import dev.vfyjxf.cirrus.planning.bridge.CirrusContentBridge;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public final class CalculatorTargetRecipeState {

    public record Selection(@Nullable RecipeEntry<?> recipe, int times, long version, List<TargetDemand> demands) {
        public Selection {
            if (times <= 0) {
                throw new IllegalArgumentException("times must be positive");
            }
            demands = List.copyOf(demands);
        }

        public Selection(RecipeEntry<?> recipe, int times, long version) {
            this(recipe, times, version, initialDemands(recipe, times));
        }
    }

    private static final AtomicLong versionCounter = new AtomicLong(0L);
    private static volatile @Nullable Selection selection;
    private static volatile boolean pending;

    private CalculatorTargetRecipeState() {}

    public static Selection set(RecipeEntry<?> recipe, int times) {
        return set(recipe, times, List.of());
    }

    public static Selection set(RecipeEntry<?> recipe, int times, List<TargetDemand> fallbackDemands) {
        int resolvedTimes = Math.max(1, times);
        List<TargetDemand> resolvedDemands = initialDemands(recipe, resolvedTimes);
        if (resolvedDemands.isEmpty() && fallbackDemands != null && !fallbackDemands.isEmpty()) {
            resolvedDemands = List.copyOf(fallbackDemands);
        }
        Selection next = new Selection(recipe, resolvedTimes, versionCounter.incrementAndGet(), resolvedDemands);
        selection = next;
        pending = true;
        return next;
    }

    public static Selection setDemands(List<TargetDemand> demands, @Nullable RecipeEntry<?> primaryRecipe) {
        Selection next = new Selection(primaryRecipe, 1, versionCounter.incrementAndGet(), List.copyOf(demands));
        selection = next;
        pending = true;
        return next;
    }

    public static @Nullable Selection current() {
        return selection;
    }

    public static @Nullable Selection consumePending() {
        if (!pending) {
            return null;
        }
        pending = false;
        return selection;
    }

    public static void markConsumed() {
        pending = false;
    }

    public static boolean hasPending() {
        return pending && selection != null;
    }

    public static List<TargetDemand> currentDemands() {
        Selection current = selection;
        return current == null ? List.of() : current.demands();
    }

    public static long currentVersion() {
        return versionCounter.get();
    }

    public static void clear() {
        selection = null;
        pending = false;
        versionCounter.incrementAndGet();
    }

    public static List<TargetDemand> initialDemands(@Nullable RecipeEntry<?> recipe, int times) {
        if (recipe == null) {
            return List.of();
        }
        int resolvedTimes = Math.max(1, times);
        Optional<CirrusCatalogAdapter.RecipeContentSource> contentSource = CirrusCatalogAdapter.contentSource(recipe);
        if (contentSource.isPresent()) {
            List<TargetDemand> fromSource = demandsFromOutputs(contentSource.get().outputs(), resolvedTimes);
            if (!fromSource.isEmpty()) {
                return fromSource;
            }
        }
        try {
            ProjectedRecipe projected = projectedRecipe(recipe);
            if (projected != null) {
                List<TargetDemand> fromProjected = demandsFromOutputs(projected.outputs(), resolvedTimes);
                if (!fromProjected.isEmpty()) {
                    return fromProjected;
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            return List.of();
        }
        return List.of();
    }

    private static List<TargetDemand> demandsFromOutputs(Iterable<OutputContent> outputs, int times) {
        for (OutputContent output : outputs) {
            RawOutputSlot slot = CirrusContentBridge.mapOutput(output).slot();
            if (slot == null) {
                continue;
            }
            long amount = saturatedMultiply(slot.amount(), times);
            if (amount > 0L) {
                return List.of(new TargetDemand(slot.resource(), amount));
            }
        }
        for (OutputContent output : outputs) {
            TargetDemand fallback = fallbackDemandFromOutput(output, times);
            if (fallback != null) {
                return List.of(fallback);
            }
        }
        return List.of();
    }

    private static @Nullable TargetDemand fallbackDemandFromOutput(@Nullable OutputContent output, int times) {
        if (output == null || output.isEmpty()) {
            return null;
        }
        if (output instanceof StackContent<?, ?> stackContent) {
            Optional<ResourceKey> key = CirrusContentBridge.toConcreteResourceKey(stackContent.content());
            long amount = saturatedMultiply(Math.max(1L, stackContent.amount()), times);
            if (key.isPresent() && amount > 0L) {
                return new TargetDemand(key.get(), amount);
            }
        }
        return null;
    }

    private static long saturatedMultiply(long amount, int times) {
        if (amount <= 0L || times <= 0) {
            return 0L;
        }
        return amount > Long.MAX_VALUE / times ? Long.MAX_VALUE : amount * times;
    }

    public static RecipeContent previewContent(@Nullable RecipeEntry<?> recipe) {
        ProjectedRecipe projected = projectedRecipe(recipe);
        if (projected == null) {
            return RecipeContent.empty();
        }
        for (OutputContent output : projected.outputs()) {
            if (output != null && output.notEmpty()) {
                return output.copyWithAmount(Math.max(1L, output.amount()));
            }
        }
        return RecipeContent.empty();
    }

    public static @Nullable ProjectedRecipe projectedRecipe(@Nullable RecipeEntry<?> recipe) {
        if (recipe == null) {
            return null;
        }
        RecipeProjection projection = recipe.resolve();
        if (projection == null) {
            return null;
        }
        Selection selection = current();
        int times = selection != null && recipe.equals(selection.recipe()) ? selection.times() : 1;
        long version = selection != null && recipe.equals(selection.recipe()) ? selection.version() : 0L;
        return new ProjectionEngine().project(
            ProjectionContexts
                    .calculator(ProjectionContexts.global(), new CalculatorProjectionContext(recipe, times, version)),
            projection
        );
    }

    public static ItemStack previewStack(@Nullable RecipeEntry<?> recipe) {
        RecipeContent preview = previewContent(recipe);
        if (!(preview instanceof StackContent<?, ?> stackContent)) {
            return ItemStack.EMPTY;
        }
        var contentStack = stackContent.content();
        if (contentStack.isEmpty() || !contentStack.isItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack previewStack = contentStack.<Item, ItemStack>cast().value().copy();
        previewStack.setCount(1);
        return previewStack;
    }
}
