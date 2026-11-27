package dev.vfyjxf.cirrus.api.recipe;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cloudlib.api.util.Namespace;

record BuiltinRecipeContentType<C extends RecipeContent>(Namespace typeId) implements RecipeContentType<C> {}
