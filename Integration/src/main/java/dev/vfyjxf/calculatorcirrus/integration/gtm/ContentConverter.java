package dev.vfyjxf.cirrus.integration.gtm;

import com.gregtechceu.gtceu.api.recipe.content.Content;
import dev.vfyjxf.cirrus.api.recipe.content.IngredientStack;
import org.jetbrains.annotations.Nullable;

public interface ContentConverter {

    @Nullable
    IngredientStack convert(Content content);

}
