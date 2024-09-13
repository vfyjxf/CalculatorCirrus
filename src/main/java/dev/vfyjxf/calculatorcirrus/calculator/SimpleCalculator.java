package dev.vfyjxf.calculatorcirrus.calculator;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.CommonRecipe;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.IngredientType;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeDatabase;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeTransformer;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;
import dev.vfyjxf.calculatorcirrus.recipes.ingredients.TagIngredientStack;
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
    private @Range(from = 1, to = Long.MAX_VALUE) int amount = 1;
    private MutableList<IngredientStack> results;
    private MutableList<IngredientStack> leftovers;
    private final CostList costList = new CostList();

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
        if (target != null) {
            IngredientStack detect = recipe.getOutputs().detect(output -> output.contains(target));
            if (detect != null) {
                if (detect.isChanced()) {
                    amount = (int) Math.ceil(amount / (detect.getAmount() * detect.getChance()));
                } else {
                    amount = (int) Math.ceilDiv(amount, detect.getAmount());
                }
            }
        }
        if (recipe.getInputs().isEmpty()) {
            results.addAll(recipe.getOutputs().collect(i -> i.copy().multiply(amount)));
            return;
        }
        costList.addIngredient(recipe.getInputs().collect(i -> new ChancedStack(i.copy().reverse().multiply(amount))));
        MutableList<RecipeTriple> decomposable = findDecomposable();
        int round = 0;
        while (decomposable.notEmpty()) {
            decomposable.flatCollect(RecipeTriple::outputs).forEach(costList::addIngredient);
            costList.addIngredient(decomposable.flatCollect(RecipeTriple::inputs).collect(s -> s.multiply(-1)));
            costList.trim();
            decomposable = findDecomposable();
            if (round++ > 2000) { //TODO: make it configurable.
                throw new IllegalStateException("Too many rounds.");
            }
        }
        leftovers.addAll(costList.ingredients.select(i -> i.getAmount() > 0).
                                 collect(ChancedStack::getResult)
                                 .toList());
        results.addAll(costList.ingredients.select(i -> i.getAmount() < 0)
                               .collect(ChancedStack::getResult)
                               .collect(IngredientStack::reverse)
                               .toList());
    }


    private MutableList<RecipeTriple> findDecomposable() {
        MutableList<RecipeTriple> ret = Lists.mutable.empty();
        for (ChancedStack ingredient : costList.ingredients) {
            if (ingredient.getAmount() >= 0) continue;
            CommonRecipe<?> recipe = null;
            IngredientStack ingredientStack = ingredient.getStack();
            if (ingredientStack.isList()) {
                for (ContentStack<?> stack : ingredientStack.getStacks()) {
                    recipe = getRecipe(stack);
                    if (recipe != null) {
                        break;
                    }
                }
            } else if (ingredientStack instanceof TagIngredientStack tagIngredient) {
                recipe = getRecipe(tagIngredient.getInternalStack());
            } else {
                recipe = getRecipe(ingredientStack.getOnly());
            }
            if (recipe == null) {
                continue;
            }
            IngredientStack targetStack = recipe.data().getOutputs().detect(output -> output.matches(ingredientStack));
            if (targetStack == null) {
                throw new IllegalStateException("Recipe:" + recipe.id() + " doesn't contain the output: " + ingredient + " but it was in path database.");
            }
            long multiplier = Math.ceilDiv(-ingredient.getAmount(), targetStack.getAmount());
            ret.add(new RecipeTriple(ingredientStack, recipe, multiplier));
        }
        return ret;
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

    private record RecipeTriple(IngredientStack ingredient, CommonRecipe<?> recipe, long multiplier) {
        public MutableList<ChancedStack> inputs() {
            return recipe.data().getInputs().collect(i -> {
                if (i.isChanced()) {
                    double expected = i.getExactExpectation(true);
                    return new ChancedStack(i, expected * multiplier);
                }
                return new ChancedStack(i, i.getAmount() * multiplier);
            });
        }

        public MutableList<? extends ChancedStack> outputs() {
            return recipe.data().getOutputs().collect(i -> {
                if (i.isChanced()) {
                    double expected = i.getExactExpectation(false);
                    return new ChancedStack(i, expected * multiplier);
                }
                return new ChancedStack(i, i.getAmount() * multiplier);
            });
        }

        public MutableList<? extends IngredientStack> catalysts() {
            return recipe.data().getCatalysts().collect(i -> i.copy().multiply(multiplier));
        }
    }

    private static class ChancedStack {
        private final IngredientStack stack;
        private double amount;

        public ChancedStack(IngredientStack stack) {
            this(stack, stack.getAmount());
        }

        public ChancedStack(IngredientStack stack, double amount) {
            this.stack = stack;
            this.amount = amount;
        }

        public IngredientStack getStack() {
            return stack;
        }

        public IngredientStack getResult() {
            return stack.setAmount((long) Math.ceil(amount));
        }

        public long getAmount() {
            return ((long) amount);
        }

        public ChancedStack multiply(double multiplier) {
            amount *= multiplier;
            return this;
        }

        public ChancedStack setAmount(double amount) {
            this.amount = amount;
            return this;
        }
    }

    private static class CostList {

        private final MutableList<ChancedStack> ingredients = Lists.mutable.empty();

        public void addIngredient(ChancedStack ingredient) {
            addIngredient(ingredient, false);
        }

        public void addIngredient(ChancedStack ingredient, boolean consume) {
            ChancedStack stack = ingredients.detect(i -> i.stack.matches(ingredient.stack));
            IngredientStack ingredientStack = ingredient.stack;
            if (stack == null) {
                if (ingredientStack.isChanced()) {
                    long amount = (long) (
                            consume ? Math.ceil(ingredientStack.getAmount() * ingredientStack.getChance()) :
                                    Math.ceil(ingredientStack.getAmount() / ingredientStack.getChance())
                    );
                    ingredients.add(ingredient.setAmount(amount));
                } else {
                    ingredients.add(ingredient);
                }
            } else {
                stack.setAmount(stack.getAmount() + ingredient.getAmount());
            }
        }

        public void addIngredient(MutableList<ChancedStack> ingredients) {
            for (ChancedStack ingredient : ingredients) {
                addIngredient(ingredient);
            }
        }

        public void trim() {
            ingredients.removeIf((i -> i.getAmount() == 0));
        }
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
