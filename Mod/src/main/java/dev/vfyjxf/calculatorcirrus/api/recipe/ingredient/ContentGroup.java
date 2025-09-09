//package dev.vfyjxf.calculatorcirrus.api.recipe.ingredient;
//
//import com.mojang.serialization.Codec;
//import dev.vfyjxf.calculatorcirrus.CirrusConstants;
//import dev.vfyjxf.calculatorcirrus.api.CirrusRegistries;
//import dev.vfyjxf.calculatorcirrus.util.CirrusLocations;
//import dev.vfyjxf.cloudlib.api.registry.util.SimpleRegister;
//import mezz.jei.api.recipe.RecipeIngredientRole;
//import net.minecraft.resources.ResourceLocation;
//import net.neoforged.bus.api.SubscribeEvent;
//import net.neoforged.fml.common.EventBusSubscriber;
//import net.neoforged.neoforge.registries.RegisterEvent;
//
///**
// * The group of ingredients in the recipe.
// *
// * @param id
// */
//@EventBusSubscriber(modid = CirrusConstants.MOD_ID)
//public record ContentGroup(ResourceLocation id) {
//
//    //region registry
//    private static final String builtinNamespace = "builtin";
//
//    private static final SimpleRegister<ContentGroup> register = SimpleRegister.create(
//            CirrusRegistries.Keys.contentGroup,
//            builtinNamespace
//    );
//    //endregion
//
//    //region codec
//    public static final Codec<ContentGroup> codec = CirrusRegistries.contentGroup.byNameCodec();
//    //endregion
//
//    //region factory
//    public static ContentGroup of(ResourceLocation id) {
//        return new ContentGroup(id);
//    }
//    //endregion
//
//    //region builtin groups
//
//    /**
//     * The recipe content is used as input such energy and item.
//     */
//    public static final ContentGroup input = register(CirrusLocations.of(builtinNamespace, "input"));
//
//    /**
//     * The  recipe content is used as output.
//     */
//    public static final ContentGroup output = register(CirrusLocations.of(builtinNamespace, "output"));
//
//    public static final ContentGroup unknown = register(CirrusLocations.of(builtinNamespace, "unknown"));
//
//    //endregion
//
//    //region jei integration
//
//    public static RecipeIngredientRole toJei(ContentGroup type) {
//        if (type == input) return RecipeIngredientRole.INPUT;
//        if (type == output) return RecipeIngredientRole.OUTPUT;
//        return RecipeIngredientRole.RENDER_ONLY;
//    }
//
//    public static ContentGroup fromJei(RecipeIngredientRole role) {
//        return switch (role) {
//            case INPUT -> input;
//            case OUTPUT -> output;
//            default -> unknown;
//        };
//    }
//
//    //endregion
//
//    //region internal
//    private static ContentGroup register(ResourceLocation location) {
//        return register.register(location, new ContentGroup(location));
//    }
//
//    @SubscribeEvent
//    private static void onRegister(RegisterEvent event) {
//        register.onRegisterEvent(event);
//    }
//    //endregion
//
//}
