package dev.vfyjxf.cirrus.api.recipe.filter;

import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;

public interface RecipeFilter<R> {

    boolean satisfy(RecipeSystem<R> system, R recipe);

}
