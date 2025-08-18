package dev.vfyjxf.calculatorcirrus.api.recipe.ingredient;

import com.mojang.serialization.Codec;
import dev.vfyjxf.calculatorcirrus.api.CirrusRegistries;
import dev.vfyjxf.calculatorcirrus.util.CirrusLocations;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.resources.ResourceLocation;

/**
 * The group of ingredient in the recipe.
 *
 * @param id
 */
public record IngredientGroup(ResourceLocation id) {

    static Codec<IngredientGroup> codec = CirrusRegistries.ingredientGroup.byNameCodec();

    private static final String builtinNamespace = "builtin";

    static IngredientGroup of(ResourceLocation id) {
        return new IngredientGroup(id);
    }

    /**
     * The ingredient is used as input such energy and item.
     */
    public static final IngredientGroup input = of(CirrusLocations.of(builtinNamespace, "input"));

    /**
     * The ingredient is used as output.
     */
    public static final IngredientGroup output = of(CirrusLocations.of(builtinNamespace, "output"));

    /**
     * The ingredient is used as catalyst, something like wrench in gregtech.
     */
    public static final IngredientGroup catalyst = of(CirrusLocations.of(builtinNamespace, "catalyst"));

    public static final IngredientGroup unknown = of(CirrusLocations.of(builtinNamespace, "unknown"));

    static RecipeIngredientRole toJei(IngredientGroup type) {
        if (type == input) return RecipeIngredientRole.INPUT;
        if (type == output) return RecipeIngredientRole.OUTPUT;
        return RecipeIngredientRole.RENDER_ONLY;
    }

    static IngredientGroup fromJei(RecipeIngredientRole role) {
        return switch (role) {
            case INPUT -> input;
            case OUTPUT -> output;
            default -> unknown;
        };
    }

}
