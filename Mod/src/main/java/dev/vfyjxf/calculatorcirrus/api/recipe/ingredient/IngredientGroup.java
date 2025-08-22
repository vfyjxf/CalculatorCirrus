package dev.vfyjxf.calculatorcirrus.api.recipe.ingredient;

import com.mojang.serialization.Codec;
import dev.vfyjxf.calculatorcirrus.api.CirrusRegistries;
import dev.vfyjxf.calculatorcirrus.util.CirrusLocations;
import dev.vfyjxf.cloudlib.api.event.RegisterListener;
import dev.vfyjxf.cloudlib.api.registry.util.SimpleRegister;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.resources.ResourceLocation;

/**
 * The group of ingredients in the recipe.
 *
 * @param id
 */
public record IngredientGroup(ResourceLocation id) {

    //region registry
    private static final String builtinNamespace = "builtin";

    @RegisterListener
    private static final SimpleRegister<IngredientGroup> register = SimpleRegister.create(
            CirrusRegistries.Keys.ingredientGroup,
            builtinNamespace
    );
    //endregion

    //region codec
    public static final Codec<IngredientGroup> codec = CirrusRegistries.ingredientGroup.byNameCodec();
    //endregion

    //region factory
    public static IngredientGroup of(ResourceLocation id) {
        return new IngredientGroup(id);
    }
    //endregion

    //region builtin groups

    /**
     * The ingredient is used as input such energy and item.
     */
    public static final IngredientGroup input = register(CirrusLocations.of(builtinNamespace, "input"));

    /**
     * The ingredient is used as output.
     */
    public static final IngredientGroup output = register(CirrusLocations.of(builtinNamespace, "output"));

    /**
     * The ingredient is used as a catalyst, something like wrench in gregtech.
     */
    public static final IngredientGroup catalyst = register(CirrusLocations.of(builtinNamespace, "catalyst"));

    public static final IngredientGroup unknown = register(CirrusLocations.of(builtinNamespace, "unknown"));

    //endregion

    //region jei integration

    public static RecipeIngredientRole toJei(IngredientGroup type) {
        if (type == input) return RecipeIngredientRole.INPUT;
        if (type == output) return RecipeIngredientRole.OUTPUT;
        return RecipeIngredientRole.RENDER_ONLY;
    }

    public static IngredientGroup fromJei(RecipeIngredientRole role) {
        return switch (role) {
            case INPUT -> input;
            case OUTPUT -> output;
            default -> unknown;
        };
    }

    //endregion

    //region internal
    private static IngredientGroup register(ResourceLocation location) {
        return register.register(location, new IngredientGroup(location));
    }
    //endregion

}
