package dev.vfyjxf.calculatorcirrus.api.recipe.view.facet;

import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.RecipeContent;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BiPredicate;

public record RecipeFacetType(
    ResourceLocation id,
    BiPredicate<RecipeEntry<?>, RecipeContent> selector
) {

    public boolean select(RecipeEntry<?> recipe, RecipeContent content) {
        return selector.test(recipe, content);
    }
}
