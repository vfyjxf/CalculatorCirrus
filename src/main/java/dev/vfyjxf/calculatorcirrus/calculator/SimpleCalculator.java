package dev.vfyjxf.calculatorcirrus.calculator;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.CommonRecipe;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.IngredientType;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeDatabase;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeTransformer;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;
import dev.vfyjxf.calculatorcirrus.recipes.ingredients.TagIngredientStack;
import org.eclipse.collections.api.collection.MutableCollection;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

/**
 * A simple ingredient calculator.
 * <p>
 * Only support single path calculation.
 * <p>
 * Do not support loop calculation.
 */
public class SimpleCalculator implements RecipeCalculator {

    private final MutableMap<ContentStack<?>, CommonRecipe<?>> paths = Maps.mutable.empty();
    private final MutableList<RecipeTransformer> transformers = Lists.mutable.empty();
    private @Nullable RecipeDatabase dataBase;
    private CommonRecipe<?> recipe;
    private @Range(from = 1, to = Long.MAX_VALUE) long amount = 1;
    private MutableList<IngredientStack> results;
    private MutableList<IngredientStack> leftovers;

    @Override
    public MutableCollection<? extends IngredientStack> targets() {
        return recipe.data().get(IngredientType.OUTPUT).asUnmodifiable();
    }

    @Override
    public CommonRecipe<?> getRecipe() {
        return recipe;
    }

    @Override
    public void setAmount(@Range(from = 1, to = Long.MAX_VALUE) long amount) {
        this.amount = amount;
    }

    @Override
    public @Range(from = 1, to = Long.MAX_VALUE) long getAmount() {
        return amount;
    }

    @Override
    public <T> void setRecipe(RecipeSystem<T> recipeSystem, T recipe) {
        this.recipe = CommonRecipe.create(recipeSystem, recipe);
    }

    @Override
    public void addPath(ContentStack<?> target, CommonRecipe<?> recipe) {
        target = target.normalize();
        if (paths.containsKey(target)) {
            throw new IllegalArgumentException("The target already has a recipe path.");
        }
        if (recipe.data().getOutputs().noneSatisfyWith(IngredientStack::contains, target)) {
            throw new IllegalArgumentException("The target isn't an output of the recipe.");
        }
    }

    @Override
    public void addTransform(RecipeTransformer transformer) {
        if (transformers.contains(transformer)) {
            throw new IllegalArgumentException("The transformer already exists.");
        }
        transformers.add(transformer);
    }

    @Override
    public void setDataBase(@Nullable RecipeDatabase dataBase) {
        this.dataBase = dataBase;
    }

    @Override
    public void calculate() {
        results = Lists.mutable.empty();
        leftovers = Lists.mutable.empty();
        if (recipe.getInputs().isEmpty()) {
            results.addAll(recipe.getOutputs().collect(i -> i.multiply(amount)));
            return;
        }

        MutableList<IngredientStack> ingredients = Lists.mutable.empty();
        ingredients.addAll(recipe.getInputs().collect(i -> i.multiply(amount)));
        MutableList<RecipeTriple> decomposable = findDecomposable(ingredients);
        int round = 0;
        while (decomposable.notEmpty()) {

            //remove decomposable ingredients
            ingredients.removeAll(decomposable.collect(RecipeTriple::ingredient));
            results.addAll(ingredients.collect(i -> i.multiply(amount)));
            ingredients = decomposable.flatCollect(d -> d.recipe().data().getInputs().collect(i -> i.multiply(d.amount)));
            if (ingredients.isEmpty()) break;
            decomposable = findDecomposable(ingredients);
            if (round++ > 2000) { //TODO: make it configurable.
                break;
//                throw new IllegalStateException("Too many rounds.");
            }
        }
        results.addAll(ingredients);
    }

    private MutableList<RecipeTriple> findDecomposable(MutableList<IngredientStack> ingredients) {
        MutableList<RecipeTriple> ret = Lists.mutable.empty();
        for (IngredientStack ingredient : ingredients) {
            CommonRecipe<?> recipe = null;
            if (ingredient.isList()) {
                for (ContentStack<?> stack : ingredient.getStacks()) {
                    recipe = getRecipe(stack.normalize());
                    if (recipe != null) {
                        break;
                    }
                }
            } else if (ingredient instanceof TagIngredientStack tagIngredient) {
                recipe = getRecipe(tagIngredient.getInternalStack());
            } else {
                recipe = getRecipe(ingredient.getOnly().normalize());
            }
            if (recipe == null) {
                continue;
            }
            long times = Math.ceilDiv(ingredient.getAmount(), recipe.getOutputs().getAny().getAmount());
            if (times <= 0) {
                continue;
            }
            ret.add(new RecipeTriple(ingredient, recipe, times));
        }
        return ret;
    }

    private @Nullable CommonRecipe<?> getRecipe(ContentStack<?> contentStack) {
        CommonRecipe<?> recipe = paths.get(contentStack);
        if (recipe != null) {
            return recipe;
        }
        return getRecipeFromDatabase(contentStack);
    }

    private @Nullable CommonRecipe<?> getRecipeFromDatabase(ContentStack<?> contentStack) {
        if (dataBase == null) {
            return null;
        }
        return dataBase.get(contentStack);
    }

    private record RecipeTriple(IngredientStack ingredient, CommonRecipe<?> recipe, long amount) {
        public MutableList<? extends IngredientStack> inputs() {
            return recipe.data().getInputs().collect(i -> i.multiply(amount));
        }

        public MutableList<? extends IngredientStack> outputs() {
            return recipe.data().getOutputs().collect(i -> i.multiply(amount));
        }

        public MutableList<? extends IngredientStack> catalysts() {
            return recipe.data().getCatalysts().collect(i -> i.multiply(amount));
        }
    }


    @Override
    public MutableList<IngredientStack> getResults() {
        return results;
    }


}
