package dev.vfyjxf.cirrus.internal.integration.jei;

import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystemTags;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cirrus.recipe.system.CraftingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.jei.JeiCategoryRecipeSystem;
import dev.vfyjxf.cirrus.testutil.Reflect;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeCategoriesLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiFallbackRecipeSystemPluginTest {

    @Test
    void registersFallbackSystemsThroughNormalRecipeRegistry() {
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        JeiFallbackRecipeSystemPlugin plugin = new JeiFallbackRecipeSystemPlugin();
        plugin.registerRecipeSystem(builder);
        plugin.registerRecipeSystemTag(builder);

        RecipeApiImpl api = new RecipeApiImpl(builder);

        assertNotNull(api.findSystem(JeiCategoryRecipeSystem.systemId(RecipeTypes.CRAFTING)));
        assertTrue(
            api.getSystemTags(JeiCategoryRecipeSystem.systemId(RecipeTypes.CRAFTING))
                    .contains(RecipeSystemTags.jeiFallback)
        );
        assertTrue(
            api.getSystemTags(JeiCategoryRecipeSystem.systemId(RecipeTypes.CRAFTING))
                    .contains(RecipeSystemTags.mirrorDuplicate)
        );
        assertTrue(
            api.getSystemTags(JeiCategoryRecipeSystem.systemId(RecipeTypes.FUELING))
                    .contains(RecipeSystemTags.displayOnly)
        );
    }

    @Test
    void nativeAndFallbackJeiTypesRemainIndependentlyAddressable() throws Exception {
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        CraftingRecipeSystem nativeSystem = new CraftingRecipeSystem();
        new JeiFallbackRecipeSystemPlugin().registerRecipeSystem(builder);
        builder.registerSystem(nativeSystem);
        builder.registerRecipeTypeForSystem(nativeSystem.systemId(), RecipeTypes.CRAFTING);
        new JeiFallbackRecipeSystemPlugin().registerJeiRecipeTypeBridge(builder);

        RecipeApiImpl api = new RecipeApiImpl(builder);
        Object previous = Reflect.staticField(RecipeApiImpl.class, "instance");
        Object previousCanonical = Reflect.staticField(RecipeApiImpl.class, "canonicalInstance");
        RecipeApiImpl.attach(api);
        try {
            assertTrue(api.getJeiRecipeTypeForSystem(nativeSystem.systemId()).contains(RecipeTypes.CRAFTING));
            assertTrue(
                api.getJeiRecipeTypeForSystem(JeiCategoryRecipeSystem.systemId(RecipeTypes.SMELTING))
                        .contains(RecipeTypes.SMELTING)
            );
            assertTrue(
                api.getJeiRecipeTypeForSystem(JeiCategoryRecipeSystem.systemId(RecipeTypes.CRAFTING))
                        .contains(RecipeTypes.CRAFTING)
            );
            assertNotNull(api.findSystem(JeiCategoryRecipeSystem.systemId(RecipeTypes.CRAFTING)));
        } finally {
            Reflect.setStatic(RecipeApiImpl.class, "instance", previous);
            Reflect.setStatic(RecipeApiImpl.class, "canonicalInstance", previousCanonical);
        }
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
        // JEI 19.28+ registers per-registry tag info as "<ns>:tag_recipes/<registry>"
        RecipeType<Object> itemTags = RecipeType.create("minecraft", "tag_recipes/item", Object.class);
        RecipeType<Object> fluidTags = RecipeType.create("minecraft", "tag_recipes/fluid", Object.class);
        // older JEI versions used "<ns>:*_tag_information" paths
        RecipeType<Object> legacyItemTags = RecipeType.create("minecraft", "item_tag_information", Object.class);
        RecipeType<Object> genericTagInfo = RecipeType.create("minecraft", "tag_information", Object.class);
        // a modded category that merely mentions "tag" must stay included
        RecipeType<Object> normalTagRecipe = RecipeType.create("example", "tag_pressing", Object.class);

        assertTrue(JeiRecipeCategoryFilter.excluded(itemTags));
        assertTrue(JeiRecipeCategoryFilter.excluded(fluidTags));
        assertTrue(JeiRecipeCategoryFilter.excluded(legacyItemTags));
        assertTrue(JeiRecipeCategoryFilter.excluded(genericTagInfo));
        assertFalse(JeiRecipeCategoryFilter.excluded(normalTagRecipe));
    }

    @Test
    void runtimeCallbackDoesNotRequireRecipeApiInstance() throws Exception {
        Object previous = Reflect.staticField(RecipeApiImpl.class, "instance");
        Reflect.setStatic(RecipeApiImpl.class, "instance", null);

        try {
            assertNull(RecipeApiImpl.instance());
            new CirrusJeiPlugin().onRuntimeAvailable(runtimeWithNoCategories());
        } finally {
            Reflect.setStatic(RecipeApiImpl.class, "instance", previous);
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
        @Nullable
        Object invoke(Method method);
    }
}
