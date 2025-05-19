package dev.vfyjxf.calculatorcirrus.calculator.tree;

import dev.vfyjxf.calculatorcirrus.api.recipes.CommonRecipe;
import dev.vfyjxf.calculatorcirrus.api.recipes.RecipeDatabase;
import dev.vfyjxf.calculatorcirrus.api.recipes.ingredients.IngredientStack;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

@ApiStatus.Internal
public class IngredientTree {

    private CommonRecipe<?> targetRecipe;
    private RecipeDatabase database;
    private Node rootNode;
    private MutableList<IngredientStack> inventory = Lists.mutable.empty();

    public void setTargetRecipe(CommonRecipe<?> targetRecipe) {
        this.targetRecipe = targetRecipe;
    }

    public void setDatabase(RecipeDatabase database) {
        this.database = database;
    }

    public static class Node {

        private final IngredientStack target;
        /**
         * The recipe to craft {@link Node#target}.
         */
        private final CommonRecipe<?> recipe;

        private MutableList<Node> children = Lists.mutable.empty();

        public Node(IngredientStack target, CommonRecipe<?> recipe) {
            this.target = target;
            this.recipe = recipe;
        }

        public String displayAmount() {
            return Objects.toString(target.getAmount());
        }
    }
}
