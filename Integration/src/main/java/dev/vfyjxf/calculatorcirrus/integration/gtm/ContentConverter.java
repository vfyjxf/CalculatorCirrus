package dev.vfyjxf.calculatorcirrus.integration.gtm;

import com.gregtechceu.gtceu.api.recipe.content.Content;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface ContentConverter {

    @Nullable
    IngredientStack convert(Content content);

}
