package dev.vfyjxf.cirrus.api.recipe;

import dev.vfyjxf.cirrus.api.recipe.content.constraint.RecipeConstraint;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

public record RecipeEntry<R>(RecipeSystem<R> system, R recipe) {

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

    public ResolveResult resolveResult() {
        return resolveResult(ResolveContext.empty());
    }

    public ResolveResult resolveResult(ResolveContext context) {
        return RecipeApi.instance().resolve(context, system, recipe);
    }

    public @Nullable RecipeProjection resolve() {
        return resolveResult().projection();
    }

    public boolean supportsCalculator() {
        return resolve() != null;
    }

    public boolean supportsRecipeDatabase() {
        return supportsCalculator() && id() != null;
    }

    public boolean supportsExecution() {
        return supportsCalculator() && !RecipeApi.instance().getMachines(systemId(), true).isEmpty();
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
        return "RecipeEntry{" + "system=" + system + ", recipe=" + system.getId(recipe) + '}';
    }
}
