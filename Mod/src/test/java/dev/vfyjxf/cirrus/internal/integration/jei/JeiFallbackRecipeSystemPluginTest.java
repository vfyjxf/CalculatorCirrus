package dev.vfyjxf.cirrus.internal.integration.jei;

import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cirrus.recipe.system.CraftingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.jei.JeiCategoryRecipeSystem;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.IRecipeCategoriesLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiFallbackRecipeSystemPluginTest {

    @Test
    void registersFallbackSystemsThroughNormalRecipeRegistry() {
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        new JeiFallbackRecipeSystemPlugin().registerRecipeSystem(builder);

        RecipeApiImpl api = new RecipeApiImpl(builder);

        assertNotNull(api.findSystem(JeiCategoryRecipeSystem.systemId(RecipeTypes.CRAFTING)));
        assertTrue(api.allSystems().anySatisfy(system -> system instanceof JeiCategoryRecipeSystem<?>));
    }

    @Test
    void nativeAndFallbackJeiTypesRemainIndependentlyAddressable() {
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        CraftingRecipeSystem nativeSystem = new CraftingRecipeSystem();
        new JeiFallbackRecipeSystemPlugin().registerRecipeSystem(builder);
        builder.registerSystem(nativeSystem);
        builder.registerRecipeTypeForSystem(nativeSystem.systemId(), RecipeTypes.CRAFTING);
        new JeiFallbackRecipeSystemPlugin().registerJeiRecipeTypeBridge(builder);

        RecipeApiImpl api = new RecipeApiImpl(builder);
        RecipeApiImpl.attach(api);

        assertTrue(api.getJeiRecipeTypeForSystem(nativeSystem.systemId()).contains(RecipeTypes.CRAFTING));
        assertTrue(api.getJeiRecipeTypeForSystem(JeiCategoryRecipeSystem.systemId(RecipeTypes.SMELTING)).contains(RecipeTypes.SMELTING));
        assertTrue(api.getJeiRecipeTypeForSystem(JeiCategoryRecipeSystem.systemId(RecipeTypes.CRAFTING)).contains(RecipeTypes.CRAFTING));
        assertNotNull(api.findSystem(JeiCategoryRecipeSystem.systemId(RecipeTypes.CRAFTING)));
    }

    @Test
    void excludesInformationRecipeTypeFromFallbackSystemsAndBridge() {
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        new JeiFallbackRecipeSystemPlugin().registerRecipeSystem(builder);
        new JeiFallbackRecipeSystemPlugin().registerJeiRecipeTypeBridge(builder);

        RecipeApiImpl api = new RecipeApiImpl(builder);

        assertNull(api.findSystem(JeiCategoryRecipeSystem.systemId(RecipeTypes.INFORMATION)));
        assertTrue(api.getJeiRecipeTypeForSystem(JeiCategoryRecipeSystem.systemId(RecipeTypes.INFORMATION)).isEmpty());
    }

    @Test
    void excludesJeiTagInformationRecipeTypes() {
        RecipeType<Object> itemTags = RecipeType.create("minecraft", "item_tag_information", Object.class);
        RecipeType<Object> fluidTags = RecipeType.create("minecraft", "fluid_tag_information", Object.class);
        RecipeType<Object> genericTagInfo = RecipeType.create("minecraft", "tag_information", Object.class);
        RecipeType<Object> normalTagRecipe = RecipeType.create("example", "tag_pressing", Object.class);

        assertTrue(JeiRecipeCategoryFilter.excluded(itemTags));
        assertTrue(JeiRecipeCategoryFilter.excluded(fluidTags));
        assertTrue(JeiRecipeCategoryFilter.excluded(genericTagInfo));
        assertTrue(JeiRecipeCategoryFilter.excluded(normalTagRecipe));
    }

    @Test
    void runtimeCallbackDoesNotRequireRecipeApiInstance() throws Exception {
        var field = RecipeApiImpl.class.getDeclaredField("instance");
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, null);

        try {
            assertNull(RecipeApiImpl.instance());
            new CirrusJeiPlugin().onRuntimeAvailable(runtimeWithNoCategories());
        } finally {
            field.set(null, previous);
        }
    }

    private static IJeiRuntime runtimeWithNoCategories() {
        IRecipeCategoriesLookup categories = proxy(IRecipeCategoriesLookup.class, method -> switch (method.getName()) {
            case "get" -> Stream.empty();
            default -> null;
        });
        IRecipeManager manager = proxy(IRecipeManager.class, method -> switch (method.getName()) {
            case "createRecipeCategoryLookup" -> categories;
            default -> null;
        });
        IFocusFactory focusFactory = proxy(IFocusFactory.class, method -> null);
        IJeiHelpers helpers = proxy(IJeiHelpers.class, method -> switch (method.getName()) {
            case "getFocusFactory" -> focusFactory;
            default -> null;
        });
        IIngredientManager ingredientManager = proxy(IIngredientManager.class, method -> null);
        return proxy(IJeiRuntime.class, method -> switch (method.getName()) {
            case "getRecipeManager" -> manager;
            case "getIngredientManager" -> ingredientManager;
            case "getJeiHelpers" -> helpers;
            default -> null;
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, ProxyAnswer answer) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if ("toString".equals(method.getName())) {
                return type.getSimpleName() + " proxy";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == args[0];
            }
            return answer.invoke(method);
        });
    }

    private interface ProxyAnswer {
        Object invoke(java.lang.reflect.Method method);
    }
}
