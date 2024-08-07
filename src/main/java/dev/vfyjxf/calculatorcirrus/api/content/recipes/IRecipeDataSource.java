package dev.vfyjxf.calculatorcirrus.api.content.recipes;

import dev.vfyjxf.calculatorcirrus.api.data.lang.LangEntry;
import net.minecraft.resources.ResourceLocation;

/**
 * A source from recipe data.
 */
public interface IRecipeDataSource {

    LangEntry name();

    ResourceLocation uid();

    /**
     *
     * @param recipe
     * @return
     */
    boolean isSupport(Object recipe);

}
