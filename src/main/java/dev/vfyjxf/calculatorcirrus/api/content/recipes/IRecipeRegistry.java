package dev.vfyjxf.calculatorcirrus.api.content.recipes;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface IRecipeRegistry {

    static IRecipeRegistry getInstance() {
        //TODO:
        return null;
    }

    <T> void registerRecipeHandler(Class<T> recipeClass, IRecipeHandler<T> handler);

    @Nullable <T> IRecipeHandler<T> getRecipeHandler(Class<T> recipeClass);

    void registerUniversalRecipeHandler(IUniversalRecipeHandler handler);

    List<IUniversalRecipeHandler> allUniversalHandlers();

    List<IRecipeHandler<?>> allHandlers();

}
