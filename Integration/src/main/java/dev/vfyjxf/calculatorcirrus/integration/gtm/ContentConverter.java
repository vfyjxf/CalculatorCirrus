package dev.vfyjxf.calculatorcirrus.integration.gtm;

import com.gregtechceu.gtceu.api.recipe.content.Content;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredients.IngredientStack;
import org.jetbrains.annotations.Nullable;

public interface ContentConverter {

    @Nullable
    IngredientStack convert(Content content);

}
