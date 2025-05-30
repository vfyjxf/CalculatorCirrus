package dev.vfyjxf.calculatorcirrus.calculator.simple;

import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.recipe.CommonRecipe;
import dev.vfyjxf.calculatorcirrus.api.recipe.IngredientType;
import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeDatabase;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeTransformer;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredients.IngredientStack;
import dev.vfyjxf.calculatorcirrus.calculator.RecipeCalculator;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.eclipse.collections.impl.factory.Multimaps;
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
    private @Nullable ContentStack<?> target;
    private @Range(from = 1, to = Integer.MAX_VALUE) int amount = 1;
    private MutableList<IngredientStack> results;
    private MutableList<IngredientStack> leftovers;

    @Override
    public MutableList<? extends IngredientStack> targets() {
        return recipe.data().get(IngredientType.OUTPUT).asUnmodifiable();
    }

    @Override
    public void setTarget(@Nullable ContentStack<?> target) {
        this.target = target;
    }

    @Override
    public @Nullable ContentStack<?> getTarget() {
        return target;
    }

    @Override
    public CommonRecipe<?> getRecipe() {
        return recipe;
    }

    @Override
    public void setAmount(@Range(from = 1, to = Integer.MAX_VALUE) int amount) {
        this.amount = amount;
    }

    @Override
    public @Range(from = 1, to = Integer.MAX_VALUE) int getAmount() {
        return amount;
    }

    @Override
    public <T> void setRecipe(RecipeSystem<T> recipeSystem, T recipe) {
        this.recipe = CommonRecipe.create(recipeSystem, recipe);
        if (target != null && this.recipe.data().getOutputs().noneSatisfyWith(IngredientStack::contains, target)) {
            throw new IllegalArgumentException("The target isn't an output of the recipe.");
        }
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
        paths.put(target, recipe);
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
        int timesToCraft = amount;
        if (target != null) {
            IngredientStack detect = recipe.getOutputs().detect(output -> output.contains(target));
            if (detect.isChanced()) {
                timesToCraft = (int) Math.ceil(amount / (detect.getChance() * detect.getAmount()));
            } else {
                timesToCraft = (int) Math.ceilDiv(amount, detect.getAmount());
            }
        }
        MutableList<StackToCraft> stacksToCraft = findStacks();
        while (!stacksToCraft.isEmpty()) {
        }
    }

    private record StackToCraft(IngredientStack stack, CommonRecipe<?> recipe, double amount) {}

    private MutableList<StackToCraft> findStacks() {
        return null;
    }

    private @Nullable CommonRecipe<?> getRecipe(ContentStack<?> contentStack) {
        ContentStack<?> target = contentStack.normalize();
        CommonRecipe<?> recipe = paths.get(target);
        if (recipe != null) {
            return recipe;
        }
        if (dataBase != null) {
            return dataBase.get(target);
        }
        return null;
    }

    @Override
    public MutableList<IngredientStack> getResults() {
        return results;
    }

    @Override
    public MutableListMultimap<IngredientType, ? extends IngredientStack> getCalculations() {
        MutableListMultimap<IngredientType, IngredientStack> calculations = Multimaps.mutable.list.empty();
        calculations.putAll(IngredientType.CALCULATED, results);
        calculations.putAll(IngredientType.LEFTOVER, leftovers);
        return calculations;
    }
}
