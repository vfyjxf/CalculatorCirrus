package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cirrus.api.recipe.filter.RecipeFilter;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface RecipeFilterRegister {

    <R> void registerFilter(Namespace filterId, Namespace systemId, RecipeFilter<R> filter);

}
