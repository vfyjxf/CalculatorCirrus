package dev.vfyjxf.cirrus.internal.integration.jei;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.recipe.system.BrewingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.CookingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.SmithingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.StonecuttingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.VanillaBrewingRecipe;
import dev.vfyjxf.cirrus.recipe.system.jei.JeiCategoryRecipeSystem;
import dev.vfyjxf.cirrus.testutil.Reflect;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.RichIterable;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class JeiLookupAndBridgeTest {

    @Test
    void bridgeResolvesAllVanillaRecipeHoldersAndNativeBrewing(MinecraftServer server) {
        RecipeApi api = RecipeApi.instance();

        Object smeltingHolder = api.getSystem(CookingRecipeSystem.smeltingSystemId).allRecipes().getFirst();
        Object stonecuttingHolder = api.getSystem(StonecuttingRecipeSystem.systemId).allRecipes().getFirst();
        Object smithingHolder = api.getSystem(SmithingRecipeSystem.systemId).allRecipes().getFirst();

        RecipeEntry<?> smeltingEntry = JeiTargetRecipeBridge.resolve(RecipeTypes.SMELTING, smeltingHolder);
        RecipeEntry<?> stonecuttingEntry = JeiTargetRecipeBridge.resolve(RecipeTypes.STONECUTTING, stonecuttingHolder);
        RecipeEntry<?> smithingEntry = JeiTargetRecipeBridge.resolve(RecipeTypes.SMITHING, smithingHolder);

        assertNotNull(smeltingEntry);
        assertEquals(CookingRecipeSystem.smeltingSystemId, smeltingEntry.systemId());
        assertNotNull(stonecuttingEntry);
        assertEquals(StonecuttingRecipeSystem.systemId, stonecuttingEntry.systemId());
        assertNotNull(smithingEntry);
        assertEquals(SmithingRecipeSystem.systemId, smithingEntry.systemId());

        ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        IJeiBrewingRecipe jeiBrewing = proxy(IJeiBrewingRecipe.class, (method, args) -> switch (method.getName()) {
            case "getPotionInputs" -> List.of(water);
            case "getIngredients" -> List.of(new ItemStack(Items.NETHER_WART));
            case "getPotionOutput" -> awkward;
            case "getUid" -> ResourceLocation.withDefaultNamespace("jei_water_to_awkward");
            default -> null;
        });

        RecipeEntry<?> brewingEntry = JeiTargetRecipeBridge.resolve(RecipeTypes.BREWING, jeiBrewing);
        assertNotNull(brewingEntry);
        assertEquals(BrewingRecipeSystem.systemId, brewingEntry.systemId());
        assertTrue(brewingEntry.recipe() instanceof VanillaBrewingRecipe);
    }

    @Test
    void jeiCategoryLookupSupportsFluidFocusMultiInputAndIdLookup(MinecraftServer server) throws Exception {
        Object previousManager = Reflect.staticField(CirrusJeiPlugin.class, "recipeManager");
        Object previousFocusFactory = Reflect.staticField(CirrusJeiPlugin.class, "focusFactory");

        RecipeType<MockFluidRecipe> type = RecipeType
                .create("cirrus_test", "fluid_mixing", MockFluidRecipe.class);
        MockFluidRecipe waterIron = new MockFluidRecipe(
            ResourceLocation.fromNamespaceAndPath("cirrus_test", "water_iron"),
            true,
            true
        );
        MockFluidRecipe waterOnly = new MockFluidRecipe(
            ResourceLocation.fromNamespaceAndPath("cirrus_test", "water_only"),
            true,
            false
        );
        MockFluidRecipe ironOnly = new MockFluidRecipe(
            ResourceLocation.fromNamespaceAndPath("cirrus_test", "iron_only"),
            false,
            true
        );
        List<MockFluidRecipe> all = List.of(waterIron, waterOnly, ironOnly);
        Map<MockFluidRecipe, ResourceLocation> ids = Map.of(
            waterIron, waterIron.id(),
            waterOnly, waterOnly.id(),
            ironOnly, ironOnly.id()
        );

        IRecipeCategory<MockFluidRecipe> category = proxy(
            IRecipeCategory.class, (method, args) -> switch (method
                    .getName()) {
                case "getRecipeType" -> type;
                case "getRegistryName" -> args != null && args.length > 0 ? ids.get(args[0]) : null;
                case "isHandled" -> true;
                default -> null;
            }
        );

        IFocusFactory focusFactory = proxy(IFocusFactory.class, (method, args) -> {
            if ("createFocus".equals(method.getName()) && args != null && args.length == 3) {
                RecipeIngredientRole role = (RecipeIngredientRole) args[0];
                IIngredientType<?> ingredientType = (IIngredientType<?>) args[1];
                Object value = args[2];
                return createMockFocus(role, ingredientType, value);
            }
            return null;
        });

        IRecipeManager manager = proxy(IRecipeManager.class, (method, args) -> switch (method.getName()) {
            case "getRecipeCategory" -> category;
            case "createRecipeLookup" -> createMockRecipeLookup(all);
            default -> null;
        });

        try {
            Reflect.setStatic(CirrusJeiPlugin.class, "recipeManager", manager);
            Reflect.setStatic(CirrusJeiPlugin.class, "focusFactory", focusFactory);

            JeiCategoryRecipeSystem<MockFluidRecipe> system = JeiCategoryRecipeSystem.create(type);

            // O(1) id lookup
            assertEquals(waterIron, system.findRecipe(waterIron.id()));
            assertEquals(waterOnly, system.createLookup().withId(waterOnly.id()).first());

            // Multi-input AND intersection across FluidStack + ItemStack focuses
            RichIterable<MockFluidRecipe> matched = system.createLookup()
                    .withInput(ContentStack.fromContent(Fluids.WATER, 1000), MatchStrategy.identity)
                    .withInput(ContentStack.fromContent(Items.IRON_INGOT), MatchStrategy.identity)
                    .get();
            assertEquals(1, matched.size());
            assertEquals(waterIron, matched.getFirst());

            // Single FluidStack focus
            RichIterable<MockFluidRecipe> waterRecipes = system.createLookup()
                    .withInput(ContentStack.fromContent(Fluids.WATER, 1000), MatchStrategy.identity)
                    .get();
            assertEquals(2, waterRecipes.size());
            assertTrue(waterRecipes.contains(waterIron));
            assertTrue(waterRecipes.contains(waterOnly));
        } finally {
            Reflect.setStatic(CirrusJeiPlugin.class, "recipeManager", previousManager);
            Reflect.setStatic(CirrusJeiPlugin.class, "focusFactory", previousFocusFactory);
        }
    }

    private record MockFluidRecipe(ResourceLocation id, boolean usesWater, boolean usesIron) {}

    @SuppressWarnings("unchecked")
    private static IRecipeLookup<MockFluidRecipe> createMockRecipeLookup(List<MockFluidRecipe> all) {
        List<IFocus<?>> focuses = new ArrayList<>();
        return proxy(IRecipeLookup.class, (method, args) -> switch (method.getName()) {
            case "limitFocus" -> {
                if (args != null && args.length == 1 && args[0] instanceof Collection<?> col) {
                    for (Object item : col) {
                        focuses.add((IFocus<?>) item);
                    }
                }
                yield null;
            }
            case "get" -> {
                if (focuses.isEmpty()) {
                    yield all.stream();
                }
                yield all.stream().filter(recipe -> focuses.stream().anyMatch(focus -> matchesFocus(recipe, focus)));
            }
            default -> null;
        });
    }

    private static boolean matchesFocus(MockFluidRecipe recipe, IFocus<?> focus) {
        Object raw = focus.getTypedValue().getIngredient();
        if (raw instanceof FluidStack fluid) {
            return recipe.usesWater() && fluid.getFluid() == Fluids.WATER;
        }
        if (raw instanceof ItemStack stack) {
            return recipe.usesIron() && stack.is(Items.IRON_INGOT);
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IFocus<?> createMockFocus(
        RecipeIngredientRole role,
        IIngredientType<?> ingredientType,
        Object value
    ) {
        ITypedIngredient typed = proxy(ITypedIngredient.class, (method, args) -> switch (method.getName()) {
            case "getType" -> ingredientType;
            case "getIngredient" -> value;
            default -> null;
        });
        return proxy(IFocus.class, (method, args) -> switch (method.getName()) {
            case "getRole" -> role;
            case "getTypedValue" -> typed;
            case "checkedCast" -> args != null && args.length == 1 && args[0] == NeoForgeTypes.FLUID_STACK
                    ? Optional.of(typed)
                    : Optional.empty();
            default -> null;
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, ProxyHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if ("toString".equals(method.getName())) {
                return type.getSimpleName() + "Proxy";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == args[0];
            }
            Object result = handler.invoke(method, args);
            if (result == null && method.getReturnType().isInstance(proxy)) {
                return proxy;
            }
            return result;
        });
    }

    @FunctionalInterface
    private interface ProxyHandler {
        @Nullable
        Object invoke(Method method, Object @Nullable [] args);
    }
}
