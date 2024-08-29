package dev.vfyjxf.calculatorcirrus.integration.jei.recipes;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.MatchContext;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeData;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeLookup;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.function.Predicate;

//TODO: Implement this class
public class JeiRecipeSystem implements RecipeSystem<Object> {
    private static final ResourceLocation SYSTEM_ID = RecipeSystem.createId("jei", "jei");

    @Override
    public RecipeLookup<Object> createLookup() {
        return new JeiRecipeLookup();
    }

    @Override
    public @Unmodifiable RichIterable<Object> allRecipes() {
        return null;
    }

    @Override
    public ResourceLocation systemId() {
        return SYSTEM_ID;
    }

    @Override
    public Class<?> basicRecipeType() {
        return Object.class;
    }

    @Override
    public boolean containsRecipe(Object recipe) {
        throw new UnsupportedOperationException("Not implemented");
    }



    @Override
    public @Nullable ResourceLocation getRegistryName(Object recipe) {
        return null;
    }

    @Override
    public @Nullable Object getRecipe(String uniqueId) {
        return null;
    }

    @Override
    public @Nullable Object getRecipe(ResourceLocation registryName) {
        return null;
    }

    @Override
    public RecipeData parseRecipe(Object recipe) {
        return null;
    }

    @Override
    public MutableList<ContentStack<ItemStack>> getMachines() {
        return null;
    }


    private class JeiRecipeLookup implements RecipeLookup<Object> {

        @Override
        public RecipeLookup<Object> filter(Predicate<Object> predicate) {
            return null;
        }

        @Override
        public RecipeLookup<Object> withInput(ContentStack<?> input, MatchContext context) {
            return null;
        }

        @Override
        public RecipeLookup<Object> withInput(Collection<? extends ContentStack<?>> inputs, MatchContext context) {
            return null;
        }

        @Override
        public RecipeLookup<Object> withOutput(ContentStack<?> output, MatchContext context) {
            return null;
        }

        @Override
        public RecipeLookup<Object> withOutput(Collection<? extends ContentStack<?>> outputs, MatchContext context) {
            return null;
        }

        @Override
        public RichIterable<Object> get() {
            return null;
        }
    }
}
