package dev.vfyjxf.cirrus.api.recipe;

import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchemas;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.VanillaRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.BrewingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.CookingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.CraftingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.SmithingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.StonecuttingRecipeSystem;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class VanillaRecipeSystemRegistrationTest {

    @Test
    void registersMissingVanillaSystemsAndMachines(MinecraftServer server) {
        RecipeApi api = RecipeApi.instance();

        assertTrue(api.hasSystem(CraftingRecipeSystem.systemId));
        assertTrue(api.hasSystem(CookingRecipeSystem.smeltingSystemId));
        assertTrue(api.hasSystem(CookingRecipeSystem.blastingSystemId));
        assertTrue(api.hasSystem(CookingRecipeSystem.smokingSystemId));
        assertTrue(api.hasSystem(CookingRecipeSystem.campfireCookingSystemId));
        assertTrue(api.hasSystem(StonecuttingRecipeSystem.systemId));
        assertTrue(api.hasSystem(SmithingRecipeSystem.systemId));
        assertTrue(api.hasSystem(BrewingRecipeSystem.systemId));

        assertTrue(
            api.getMachines(CookingRecipeSystem.smeltingSystemId, false)
                    .anySatisfy(machine -> machine.toItemStack().is(Items.FURNACE))
        );
        assertTrue(
            api.getMachines(CookingRecipeSystem.blastingSystemId, false)
                    .anySatisfy(machine -> machine.toItemStack().is(Items.BLAST_FURNACE))
        );
        assertTrue(
            api.getMachines(CookingRecipeSystem.smokingSystemId, false)
                    .anySatisfy(machine -> machine.toItemStack().is(Items.SMOKER))
        );
        assertTrue(
            api.getMachines(CookingRecipeSystem.campfireCookingSystemId, false)
                    .anySatisfy(machine -> machine.toItemStack().is(Items.CAMPFIRE))
        );
        assertTrue(
            api.getMachines(StonecuttingRecipeSystem.systemId, false)
                    .anySatisfy(machine -> machine.toItemStack().is(Items.STONECUTTER))
        );
        assertTrue(
            api.getMachines(SmithingRecipeSystem.systemId, false)
                    .anySatisfy(machine -> machine.toItemStack().is(Items.SMITHING_TABLE))
        );
        assertTrue(
            api.getMachines(BrewingRecipeSystem.systemId, false)
                    .anySatisfy(machine -> machine.toItemStack().is(Items.BREWING_STAND))
        );
    }

    @Test
    void resolvesCookingStonecuttingAndSmithing(MinecraftServer server) {
        RecipeApi api = RecipeApi.instance();

        assertResolved(api, CookingRecipeSystem.smeltingSystemId, AbstractCookingRecipe.class);
        assertResolved(api, StonecuttingRecipeSystem.systemId, StonecutterRecipe.class);
        assertResolved(api, SmithingRecipeSystem.systemId, SmithingRecipe.class);
    }

    @Test
    void shapedCraftingProjectionCarriesGridSchema(MinecraftServer server) {
        RecipeApi api = RecipeApi.instance();
        @SuppressWarnings("unchecked")
        VanillaRecipeSystem<CraftingRecipe> crafting = (VanillaRecipeSystem<CraftingRecipe>) api
                .getSystem(CraftingRecipeSystem.systemId);
        RecipeHolder<CraftingRecipe> shaped = crafting.allRecipes()
                .detect(holder -> holder.value() instanceof ShapedRecipe);

        assertNotNull(shaped);
        RecipeProjection projection = api.resolve(ResolveContext.empty(), crafting, shaped).projection();

        assertNotNull(projection);
        assertNotNull(projection.schema().find(ProjectionSchemas.gridSize));
        assertTrue(projection.inputs().anySatisfy(input -> input.schema().has(ProjectionSchemas.gridIndex)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void assertResolved(RecipeApi api, Namespace systemId, Class<?> recipeClass) {
        VanillaRecipeSystem system = (VanillaRecipeSystem) api.getSystem(systemId);
        Object recipe = system.allRecipes().getFirst();
        assertTrue(
            api.resolve(ResolveContext.empty(), system, recipe).resolved(),
            systemId + " should resolve its first recipe"
        );
    }
}
