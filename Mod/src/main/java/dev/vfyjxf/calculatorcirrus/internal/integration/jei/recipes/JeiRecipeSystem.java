package dev.vfyjxf.calculatorcirrus.internal.integration.jei.recipes;

import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeData;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeLookup;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.ordered.OrderedIterable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.function.Predicate;

//TODO: Implement this class
public class JeiRecipeSystem implements RecipeSystem<Object> {
    private static final ResourceLocation SYSTEM_ID = RecipeSystem.systemId("jei", "jei");

    @Override
    public RecipeLookup<Object> createLookup() {
        return new JeiRecipeLookup();
    }

    @Override
    public @Unmodifiable OrderedIterable<Object> allRecipes() {
        throw new UnsupportedOperationException("Not Implemented");
    }

    @Override
    public ResourceLocation systemId() {
        return SYSTEM_ID;
    }

    @Override
    public Class<?> basicType() {
        return Object.class;
    }

    @Override
    public boolean contains(Object recipe) {
        throw new UnsupportedOperationException("Not implemented");
    }


    @Override
    public @Nullable ResourceLocation getRecipeId(Object recipe) {
        throw new UnsupportedOperationException("Not Implemented");
    }

    @Override
    public @Nullable Object getRecipe(String uniqueId) {
        throw new UnsupportedOperationException("Not Implemented");
    }

    @Override
    public @Nullable Object getRecipe(ResourceLocation registryName) {
        throw new UnsupportedOperationException("Not Implemented");
    }

    @Override
    public RecipeData parseRecipe(Object recipe) {
        throw new UnsupportedOperationException("Not Implemented");
    }

    @Override
    public MutableList<ContentStack<ItemStack>> getMachines() {
        throw new UnsupportedOperationException("Not Implemented");
    }


    private class JeiRecipeLookup implements RecipeLookup<Object> {

        @Override
        public RecipeLookup<Object> filter(Predicate<Object> predicate) {
             throw new UnsupportedOperationException("Not Implemented");
        }

        @Override
        public RecipeLookup<Object> withInput(ContentStack<?> input, MatchStrategy strategy) {
            throw new UnsupportedOperationException("Not Implemented");
        }

        @Override
        public RecipeLookup<Object> withInput(Collection<? extends ContentStack<?>> inputs, MatchStrategy strategy) {
            throw new UnsupportedOperationException("Not Implemented");
        }

        @Override
        public RecipeLookup<Object> withOutput(ContentStack<?> output, MatchStrategy strategy) {
            throw new UnsupportedOperationException("Not Implemented");
        }

        @Override
        public RecipeLookup<Object> withOutput(Collection<? extends ContentStack<?>> outputs, MatchStrategy strategy) {
            throw new UnsupportedOperationException("Not Implemented");
        }

        @Override
        public RichIterable<Object> get() {
            throw new UnsupportedOperationException("Not Implemented");
        }
    }
}
