package dev.vfyjxf.cirrus.api.recipe;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.screening.RecipeUsePolicy;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeLookup;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeLookupCondition;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystemTags;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cirrus.api.recipe.value.TimeValue;
import dev.vfyjxf.cirrus.recipe.system.AnvilRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.BrewingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.CompostingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.CookingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.CraftingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.FuelRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.GrindstoneRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.SmithingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.StonecuttingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.VanillaBrewingRecipe;
import dev.vfyjxf.cirrus.recipe.system.WorldInteractionRecipeSystem;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.RichIterable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class VanillaSystemsAndLookupTest {

    @Test
    void registersAllNativeVanillaSystemsTagsAndMachines(MinecraftServer server) {
        RecipeApi api = RecipeApi.instance();

        assertTrue(api.hasSystem(CraftingRecipeSystem.systemId));
        assertTrue(api.hasSystem(CookingRecipeSystem.smeltingSystemId));
        assertTrue(api.hasSystem(CookingRecipeSystem.blastingSystemId));
        assertTrue(api.hasSystem(CookingRecipeSystem.smokingSystemId));
        assertTrue(api.hasSystem(CookingRecipeSystem.campfireCookingSystemId));
        assertTrue(api.hasSystem(StonecuttingRecipeSystem.systemId));
        assertTrue(api.hasSystem(SmithingRecipeSystem.systemId));
        assertTrue(api.hasSystem(BrewingRecipeSystem.systemId));
        assertTrue(api.hasSystem(CompostingRecipeSystem.systemId));
        assertTrue(api.hasSystem(FuelRecipeSystem.systemId));
        assertTrue(api.hasSystem(AnvilRecipeSystem.systemId));
        assertTrue(api.hasSystem(GrindstoneRecipeSystem.systemId));
        assertTrue(api.hasSystem(WorldInteractionRecipeSystem.systemId));

        assertTrue(api.getSystemTags(FuelRecipeSystem.systemId).contains(RecipeSystemTags.displayOnly));
        assertTrue(api.getSystemTags(FuelRecipeSystem.systemId).contains(RecipeSystemTags.vanilla));
        assertTrue(
            api.getSystemTags(WorldInteractionRecipeSystem.systemId).contains(RecipeSystemTags.worldInteraction)
        );
        assertTrue(api.getSystemTags(CompostingRecipeSystem.systemId).contains(RecipeSystemTags.composting));
        assertTrue(api.getSystemTags(AnvilRecipeSystem.systemId).contains(RecipeSystemTags.anvil));
        assertTrue(api.getSystemTags(GrindstoneRecipeSystem.systemId).contains(RecipeSystemTags.grindstone));

        assertTrue(
            api.getMachines(CookingRecipeSystem.campfireCookingSystemId, false)
                    .anySatisfy(m -> m.toItemStack().is(Items.SOUL_CAMPFIRE))
        );
        assertTrue(
            api.getMachines(CompostingRecipeSystem.systemId, false)
                    .anySatisfy(m -> m.toItemStack().is(Items.COMPOSTER))
        );
        assertTrue(
            api.getMachines(AnvilRecipeSystem.systemId, false)
                    .anySatisfy(m -> m.toItemStack().is(Items.ANVIL))
        );
        assertTrue(
            api.getMachines(GrindstoneRecipeSystem.systemId, false)
                    .anySatisfy(m -> m.toItemStack().is(Items.GRINDSTONE))
        );
    }

    @Test
    void nativeBrewingDiscoversFullVanillaPotionChainWithoutJei(MinecraftServer server) {
        RecipeApi api = RecipeApi.instance();
        BrewingRecipeSystem brewing = (BrewingRecipeSystem) api.getSystem(BrewingRecipeSystem.systemId);

        assertFalse(brewing.allRecipes().isEmpty());

        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        ItemStack swiftness = PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS);
        ItemStack splashSwiftness = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.SWIFTNESS);
        ItemStack lingeringSwiftness = PotionContents.createItemStack(Items.LINGERING_POTION, Potions.SWIFTNESS);

        RichIterable<VanillaBrewingRecipe> toAwkward = brewing.createLookup()
                .withOutput(ContentStack.fromStack(awkward), MatchStrategy.identityAndComponent)
                .get();
        assertFalse(toAwkward.isEmpty());

        RichIterable<VanillaBrewingRecipe> toSwiftness = brewing.createLookup()
                .withOutput(ContentStack.fromStack(swiftness), MatchStrategy.identityAndComponent)
                .get();
        assertFalse(toSwiftness.isEmpty());
        VanillaBrewingRecipe swiftnessRecipe = toSwiftness.getFirst();
        assertNotNull(swiftnessRecipe);
        assertTrue(swiftnessRecipe.reagent().test(new ItemStack(Items.SUGAR)));

        RoutingRecipeView view = brewing.routingView(ResolveContext.empty(), swiftnessRecipe);
        assertNotNull(view);
        boolean hasTime = false;
        for (InputContent input : view.inputs()) {
            if (input instanceof ValueContent<?> value && value.content().content() instanceof TimeValue) {
                assertEquals(400L, value.amount());
                hasTime = true;
            }
        }
        assertTrue(hasTime, "Brewing recipe should carry 400-tick TimeValue");

        assertTrue(
            brewing.createLookup()
                    .withOutput(ContentStack.fromStack(splashSwiftness), MatchStrategy.identityAndComponent)
                    .anyMatch(),
            "Splash swiftness potion should be discovered via container mix BFS"
        );
        assertTrue(
            brewing.createLookup()
                    .withOutput(ContentStack.fromStack(lingeringSwiftness), MatchStrategy.identityAndComponent)
                    .anyMatch(),
            "Lingering swiftness potion should be discovered via container mix BFS"
        );
    }

    @Test
    void craftingEmitsRemainingContainersAndResolvesSpecialRecipes(MinecraftServer server) {
        RecipeApi api = RecipeApi.instance();
        CraftingRecipeSystem crafting = (CraftingRecipeSystem) api.getSystem(CraftingRecipeSystem.systemId);

        RecipeHolder<CraftingRecipe> cake = crafting.findRecipe(ResourceLocation.withDefaultNamespace("cake"));
        assertNotNull(cake);
        RoutingRecipeView cakeView = crafting.routingView(ResolveContext.empty(), cake);
        assertNotNull(cakeView);

        boolean hasBuckets = false;
        for (OutputContent output : cakeView.outputs()) {
            if (output instanceof StackContent<?, ?> stack && stack.isItem()) {
                ItemStack itemStack = stack.<Item, ItemStack>cast().content().value();
                if (itemStack.is(Items.BUCKET) && stack.amount() == 3L) {
                    hasBuckets = true;
                }
            }
        }
        assertTrue(hasBuckets, "Cake recipe should return 3 empty buckets");

        RecipeHolder<CraftingRecipe> firework = crafting
                .findRecipe(ResourceLocation.withDefaultNamespace("firework_rocket"));
        assertNotNull(firework);
        assertTrue(api.resolve(ResolveContext.empty(), crafting, firework).resolved());
    }

    @Test
    void cookingEmitsCookingTimeAndSmithingHandlesTransformAndTrimWithoutReflection(MinecraftServer server) {
        RecipeApi api = RecipeApi.instance();

        @SuppressWarnings("unchecked")
        RecipeSystem<RecipeHolder<AbstractCookingRecipe>> smelting = (RecipeSystem<RecipeHolder<AbstractCookingRecipe>>) api
                .getSystem(CookingRecipeSystem.smeltingSystemId);
        RecipeHolder<AbstractCookingRecipe> ironIngot = smelting.createLookup()
                .withOutput(ContentStack.fromContent(Items.IRON_INGOT), MatchStrategy.identity)
                .first();
        assertNotNull(ironIngot);
        RecipeProjection smeltingProjection = api.resolve(ResolveContext.empty(), smelting, ironIngot).projection();
        assertNotNull(smeltingProjection);
        assertTrue(
            smeltingProjection.inputs().anySatisfy(
                in -> in.content() instanceof ValueContent<?> vc && vc.content().content() instanceof TimeValue
            )
        );

        SmithingRecipeSystem smithing = (SmithingRecipeSystem) api.getSystem(SmithingRecipeSystem.systemId);
        RecipeHolder<SmithingRecipe> transform = smithing.allRecipes()
                .detect(holder -> holder.value() instanceof SmithingTransformRecipe);
        RecipeHolder<SmithingRecipe> trim = smithing.allRecipes()
                .detect(holder -> holder.value() instanceof SmithingTrimRecipe);
        assertNotNull(transform);
        assertNotNull(trim);
        assertTrue(api.resolve(ResolveContext.empty(), smithing, transform).resolved());
        assertTrue(api.resolve(ResolveContext.empty(), smithing, trim).resolved());
        assertEquals(RecipeUsePolicy.targetOnly, smithing.routingHints(trim).usePolicy());
        assertEquals(RecipeUsePolicy.normal, smithing.routingHints(transform).usePolicy());
    }

    @Test
    void compostingFuelAnvilGrindstoneAndWorldInteractionResolveCleanly(MinecraftServer server) {
        RecipeApi api = RecipeApi.instance();

        CompostingRecipeSystem composting = (CompostingRecipeSystem) api.getSystem(CompostingRecipeSystem.systemId);
        assertTrue(
            composting.createLookup()
                    .withInput(ContentStack.fromContent(Items.WHEAT_SEEDS), MatchStrategy.identity)
                    .withOutput(ContentStack.fromContent(Items.BONE_MEAL), MatchStrategy.identity)
                    .anyMatch()
        );

        FuelRecipeSystem fueling = (FuelRecipeSystem) api.getSystem(FuelRecipeSystem.systemId);
        FuelRecipeSystem.VanillaFuelRecipe coalFuel = fueling.createLookup()
                .withInput(ContentStack.fromContent(Items.COAL), MatchStrategy.identity)
                .first();
        assertNotNull(coalFuel);
        assertEquals(1600, coalFuel.burnTicks());
        FuelRecipeSystem.VanillaFuelRecipe lavaFuel = fueling.createLookup()
                .withInput(ContentStack.fromContent(Items.LAVA_BUCKET), MatchStrategy.identity)
                .withOutput(ContentStack.fromContent(Items.BUCKET), MatchStrategy.identity)
                .first();
        assertNotNull(lavaFuel);
        assertEquals(20000, lavaFuel.burnTicks());

        AnvilRecipeSystem anvil = (AnvilRecipeSystem) api.getSystem(AnvilRecipeSystem.systemId);
        Holder<Enchantment> sharpness = server.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS);
        ItemStack sharp2 = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(sharpness, 2));
        assertTrue(
            anvil.createLookup()
                    .withOutput(ContentStack.fromStack(sharp2), MatchStrategy.identityAndComponent)
                    .anyMatch(),
            "Anvil should synthesize Sharpness I + Sharpness I -> Sharpness II"
        );
        AnvilRecipeSystem.VanillaAnvilRecipe repair = anvil.allRecipes()
                .detect(r -> r.kind() == AnvilRecipeSystem.Kind.materialRepair);
        assertNotNull(repair);
        assertEquals(RecipeUsePolicy.targetOnly, anvil.routingHints(repair).usePolicy());

        GrindstoneRecipeSystem grindstone = (GrindstoneRecipeSystem) api.getSystem(GrindstoneRecipeSystem.systemId);
        assertTrue(
            grindstone.createLookup()
                    .withInput(ContentStack.fromContent(Items.ENCHANTED_BOOK), MatchStrategy.identity)
                    .withOutput(ContentStack.fromContent(Items.BOOK), MatchStrategy.identity)
                    .anyMatch()
        );

        WorldInteractionRecipeSystem world = (WorldInteractionRecipeSystem) api
                .getSystem(WorldInteractionRecipeSystem.systemId);
        assertTrue(
            world.createLookup()
                    .withInput(ContentStack.fromContent(Items.OAK_LOG), MatchStrategy.identity)
                    .withOutput(ContentStack.fromContent(Items.STRIPPED_OAK_LOG), MatchStrategy.identity)
                    .anyMatch(),
            "Stripping oak log should be discovered"
        );
        assertTrue(
            world.createLookup()
                    .withInput(ContentStack.fromContent(Items.WHITE_CONCRETE_POWDER), MatchStrategy.identity)
                    .withOutput(ContentStack.fromContent(Items.WHITE_CONCRETE), MatchStrategy.identity)
                    .anyMatch(),
            "Concrete hardening should be discovered"
        );
        assertTrue(
            world.createLookup()
                    .withInput(ContentStack.fromContent(Items.DIRT), MatchStrategy.identity)
                    .withOutput(ContentStack.fromContent(Items.MUD), MatchStrategy.identity)
                    .anyMatch(),
            "Dirt to mud conversion should be discovered"
        );
        ItemStack waterBottle = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        assertTrue(
            world.createLookup()
                    .withInput(ContentStack.fromContent(Items.GLASS_BOTTLE), MatchStrategy.identity)
                    .withOutput(ContentStack.fromStack(waterBottle), MatchStrategy.identityAndComponent)
                    .anyMatch(),
            "Water bottle filling should be discovered"
        );
    }

    @Test
    void recipeLookupIsNonDestructiveIndexedAndSupportsConditionsAndId(MinecraftServer server) {
        RecipeApi api = RecipeApi.instance();
        CraftingRecipeSystem crafting = (CraftingRecipeSystem) api.getSystem(CraftingRecipeSystem.systemId);

        // Immutable collections must never be mutated by withInput / withOutput
        List<ContentStack<?, ?>> immutableInputs = List.of(
            ContentStack.fromContent(Items.STICK),
            ContentStack.fromContent(Items.IRON_INGOT)
        );
        RichIterable<RecipeHolder<CraftingRecipe>> ironTools = crafting.createLookup()
                .withInput(immutableInputs, MatchStrategy.identity)
                .get();
        assertFalse(ironTools.isEmpty());

        // Non-item stack (Fluid) must return empty rather than ignoring the filter
        assertFalse(
            crafting.createLookup()
                    .withInput(ContentStack.fromContent(Fluids.WATER, 1000), MatchStrategy.identity)
                    .anyMatch()
        );

        // withId + custom RecipeLookupCondition
        ResourceLocation chestId = ResourceLocation.withDefaultNamespace("chest");
        RecipeLookupCondition<RecipeHolder<CraftingRecipe>> hasAtLeastEightIngredients = holder -> holder.value()
                .getIngredients().size()
                >= 8;
        RecipeHolder<CraftingRecipe> found = crafting.createLookup()
                .withId(chestId)
                .withCondition(hasAtLeastEightIngredients)
                .first();
        assertNotNull(found);
        assertEquals(chestId, found.id());

        // Cache invalidation works cleanly
        RecipeLookup.invalidateCache();
        assertNotNull(crafting.createLookup().withId(chestId).first());
    }
}
