package dev.vfyjxf.cirrus.ui.widget.calculator;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectedRecipe;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectionEngine;
import dev.vfyjxf.cirrus.api.recipe.projection.context.CalculatorProjectionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContexts;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

public final class CalculatorTargetRecipeState {

    public record Selection(RecipeEntry<?> recipe, int times, long version) {
        public Selection {
            if (times <= 0) {
                throw new IllegalArgumentException("times must be positive");
            }
        }
    }

    private static final AtomicLong versionCounter = new AtomicLong(0L);
    private static volatile @Nullable Selection selection;

    private CalculatorTargetRecipeState() {
    }

    public static Selection set(RecipeEntry<?> recipe, int times) {
        Selection next = new Selection(recipe, Math.max(1, times), versionCounter.incrementAndGet());
        selection = next;
        return next;
    }

    public static @Nullable Selection current() {
        return selection;
    }

    public static long currentVersion() {
        return versionCounter.get();
    }

    public static void clear() {
        selection = null;
        versionCounter.incrementAndGet();
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
            ProjectionContexts.calculator(
                ProjectionContexts.global(),
                new CalculatorProjectionContext(recipe, times, version)
            ),
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
