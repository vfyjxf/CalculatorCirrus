package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cloudlib.api.util.Namespace;

public abstract class BasicRecipeSystem<R> implements RecipeSystem<R> {

    private final Namespace systemId;

    protected BasicRecipeSystem(Namespace systemId) {
        this.systemId = systemId;
    }

    @Override
    public Namespace systemId() {
        return systemId;
    }

}
