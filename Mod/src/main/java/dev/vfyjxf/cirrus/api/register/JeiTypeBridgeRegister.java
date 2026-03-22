package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cirrus.api.recipe.integration.JeiValueTypeBridge;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import mezz.jei.api.ingredients.IIngredientType;

public interface JeiTypeBridgeRegister {

    void registerStackTypeBridge(Namespace typeId, IIngredientType<?> ingredientType);

    <S, T> void registerValueTypeBridge(IIngredientType<S> ingredientType, JeiValueTypeBridge<S, T> bridge);

}
