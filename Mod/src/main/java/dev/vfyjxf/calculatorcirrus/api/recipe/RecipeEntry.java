package dev.vfyjxf.calculatorcirrus.api.recipe;

import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeSystem;

public interface RecipeEntry<R> {

    RecipeSystem<R> system();

    R recipe();

}
