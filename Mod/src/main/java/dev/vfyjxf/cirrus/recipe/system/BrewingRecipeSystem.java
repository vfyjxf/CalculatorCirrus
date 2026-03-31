package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveError;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.internal.integration.jei.CirrusJeiPlugin;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.impl.collection.mutable.CollectionAdapter;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

public final class BrewingRecipeSystem extends BasicRecipeSystem<IJeiBrewingRecipe> {

    public static final Namespace systemId = Namespace.ofMc("brewing");

    public BrewingRecipeSystem() {
        super(systemId);
    }

    @Override
    public Class<?> basicType() {
        return IJeiBrewingRecipe.class;
    }

    @Override
    public @Nullable ResourceLocation getId(IJeiBrewingRecipe recipe) {
        return recipe.getUid();
    }

    @Override
    public @Nullable IJeiBrewingRecipe findRecipe(String uniqueId) {
        return findRecipe(ResourceLocation.parse(uniqueId));
    }

    @Override
    public @Nullable IJeiBrewingRecipe findRecipe(ResourceLocation registryName) {
        var manager = CirrusJeiPlugin.recipeManagerOrNull();
        if (manager == null) {
            return null;
        }
        return manager.createRecipeLookup(RecipeTypes.BREWING)
            .get()
            .filter(recipe -> registryName.equals(recipe.getUid()))
            .findFirst()
            .orElse(null);
    }

    @Override
    public int hash(IJeiBrewingRecipe recipe) {
        ResourceLocation id = recipe.getUid();
        return id == null ? System.identityHashCode(recipe) : id.hashCode();
    }

    @Override
    public boolean sameRecipe(IJeiBrewingRecipe first, IJeiBrewingRecipe second) {
        ResourceLocation firstId = first.getUid();
        ResourceLocation secondId = second.getUid();
        if (firstId != null || secondId != null) {
            return Objects.equals(firstId, secondId);
        }
        return first == second || first.equals(second);
    }

    @Override
    public ResolveResult resolveFallback(ResolveContext context, IJeiBrewingRecipe recipe) {
        InputContent potion = stacksInput(recipe.getPotionInputs());
        InputContent ingredient = stacksInput(recipe.getIngredients());
        OutputContent output = OutputContent.fromItemStack(recipe.getPotionOutput());
        if (potion.isEmpty() || ingredient.isEmpty() || output.isEmpty()) {
            return ResolveResult.failed(ResolveError.invalidRecipe);
        }
        return ResolveResult.resolved(RecipeProjection.create(
            this,
            recipe,
            List.of(potion, ingredient),
            List.of(output)
        ));
    }

    private static InputContent stacksInput(List<ItemStack> stacks) {
        List<ItemStack> visible = stacks.stream()
            .filter(stack -> stack != null && !stack.isEmpty())
            .map(ItemStack::copy)
            .toList();
        if (visible.isEmpty()) {
            return InputContent.empty;
        }
        if (visible.size() == 1) {
            return InputContent.fromItemStack(visible.getFirst(), Math.max(1, visible.getFirst().getCount()));
        }
        return InputContent.fromTypedStackList(CirrusTypes.Contents.ITEM_STACK, visible, 1);
    }

    @Override
    public @Unmodifiable RichIterable<IJeiBrewingRecipe> allRecipes() {
        var manager = CirrusJeiPlugin.recipeManagerOrNull();
        if (manager == null) {
            return CollectionAdapter.adapt(List.<IJeiBrewingRecipe>of()).asUnmodifiable();
        }
        return CollectionAdapter.adapt(manager.createRecipeLookup(RecipeTypes.BREWING).get().toList())
            .asUnmodifiable();
    }

    @Override
    public String toString() {
        return "BrewingRecipeSystem";
    }
}
