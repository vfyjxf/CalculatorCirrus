package dev.vfyjxf.cirrus.api.recipe.database;

import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class RecipeDatabaseFailureCapture {
    private static final ThreadLocal<Context> current = new ThreadLocal<>();

    private RecipeDatabaseFailureCapture() {
    }

    public static <T> T capture(
        String database,
        Consumer<RecipeDatabaseFailureRegistry.Failure> failures,
        Supplier<T> action
    ) {
        return capture(database, null, "", failures, action);
    }

    public static <T> T capture(
        String database,
        DatabaseRecipeRef ref,
        String recipeClass,
        Consumer<RecipeDatabaseFailureRegistry.Failure> failures,
        Supplier<T> action
    ) {
        Context previous = current.get();
        current.set(new Context(database, ref, recipeClass, failures));
        try {
            return action.get();
        } finally {
            if (previous == null) {
                current.remove();
            } else {
                current.set(previous);
            }
        }
    }

    public static <R> void record(RecipeSystem<R> system, R recipe, Throwable throwable) {
        Context context = current.get();
        if (context == null || context.failures() == null || system == null || recipe == null || throwable == null) {
            return;
        }
        ResourceLocation id;
        try {
            id = system.getId(recipe);
        } catch (RuntimeException | LinkageError ignored) {
            record(throwable);
            return;
        }
        if (id == null) {
            record(throwable);
            return;
        }
        context.failures().accept(RecipeDatabaseFailureRegistry.Failure.from(
            new DatabaseRecipeRef(system.systemId(), id),
            context.database(),
            recipeClassName(recipe),
            throwable
        ));
    }

    public static void record(Throwable throwable) {
        Context context = current.get();
        if (context == null || context.failures() == null || context.ref() == null || throwable == null) {
            return;
        }
        context.failures().accept(RecipeDatabaseFailureRegistry.Failure.from(
            context.ref(),
            context.database(),
            context.recipeClass(),
            throwable
        ));
    }

    private record Context(
        String database,
        DatabaseRecipeRef ref,
        String recipeClass,
        Consumer<RecipeDatabaseFailureRegistry.Failure> failures
    ) {
        private Context {
            database = database == null ? "" : database;
            recipeClass = recipeClass == null ? "" : recipeClass;
        }
    }

    private static String recipeClassName(Object recipe) {
        if (recipe instanceof RecipeHolder<?> holder) {
            return "RecipeHolder(" + holder.value().getClass().getName() + ")";
        }
        return recipe == null ? "" : recipe.getClass().getName();
    }
}
