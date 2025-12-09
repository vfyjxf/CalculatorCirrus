package dev.vfyjxf.cirrus.api.recipe;

import dev.vfyjxf.cirrus.api.recipe.content.constraint.RecipeConstraint;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.view.RecipeView;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record RecipeEntry<R>(
    RecipeSystem<R> system,
    R recipe
) {

    public static <R> RecipeEntry<R> of(RecipeSystem<R> system, R recipe) {
        Checks.checkNotNull(system, "system");
        Checks.checkNotNull(recipe, "recipe");
        return new RecipeEntry<>(system, recipe);
    }

    public Namespace systemId() {
        return system.systemId();
    }

    public @Nullable ResourceLocation id() {
        return system.getId(recipe);
    }

    public RecipeConstraint<R> constraint() {
        return system.getRecipeConstraint(recipe);
    }

    public RecipeView resolve() {
        return system.resolveRecipe(recipe);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RecipeEntry<?> that = (RecipeEntry<?>) o;
        return system == that.system && system.sameRecipe(recipe, (R) that.recipe);
    }

    @Override
    public int hashCode() {
        int result = system.systemId().hashCode();
        result = 31 * result + system.hash(recipe);
        return result;
    }

    @Override
    public String toString() {
        return "RecipeEntry{" +
               "system=" + system +
               ", recipe=" + system.getId(recipe) +
               '}';
    }
}
