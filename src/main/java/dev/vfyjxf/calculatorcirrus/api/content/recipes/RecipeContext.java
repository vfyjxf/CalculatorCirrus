package dev.vfyjxf.calculatorcirrus.api.content.recipes;

/**
 * The contexts affecting RecipeTransformer
 */
public interface RecipeContext {

    <T> T getContext(Class<T> type);

    <T> RecipeContext setContext(Class<T> type, T context);

}
