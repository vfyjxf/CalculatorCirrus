package dev.vfyjxf.calculatorcirrus.api.content.recipes;

import mezz.jei.api.recipe.RecipeIngredientRole;

public interface IngredientType {

    /**
     * The ingredient is used as input such energy and item.
     */
    IngredientType INPUT = new IngredientType() {
        @Override
        public String toString() {
            return "input";
        }
    };

    /**
     * The ingredient is used as output.
     */
    IngredientType OUTPUT = new IngredientType() {
        @Override
        public String toString() {
            return "output";
        }
    };

    /**
     * The ingredient is used as catalyst,something like wrench in gregtech.
     */
    IngredientType CATALYST = new IngredientType() {
        @Override
        public String toString() {
            return "catalyst";
        }
    };

    IngredientType UNKNOWN = new IngredientType() {
        @Override
        public String toString() {
            return "unknown";
        }
    };

    static RecipeIngredientRole toJei(IngredientType type) {
        if (type == INPUT) return RecipeIngredientRole.INPUT;
        if (type == OUTPUT) return RecipeIngredientRole.OUTPUT;
        return RecipeIngredientRole.RENDER_ONLY;
    }

    static IngredientType fromJei(RecipeIngredientRole role) {
        return switch (role) {
            case INPUT -> INPUT;
            case OUTPUT -> OUTPUT;
            default -> UNKNOWN;
        };
    }

}
