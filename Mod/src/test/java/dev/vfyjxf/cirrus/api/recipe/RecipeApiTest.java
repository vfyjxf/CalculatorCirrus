package dev.vfyjxf.cirrus.api.recipe;

import dev.vfyjxf.cirrus.api.recipe.system.VanillaRecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.recipe.system.CraftingRecipeSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
public class RecipeApiTest {

    @SuppressWarnings("unchecked")
    @Test
    void testCraftingRecipeSystemParse(MinecraftServer server) {
        RecipeApi recipeApi = RecipeApi.instance();
        var craftingSystem = (VanillaRecipeSystem<CraftingRecipe>) recipeApi.getSystem(CraftingRecipeSystem.systemId);

        int total = 0;
        int resolved = 0;
        for (var recipe : craftingSystem.allRecipes()) {
            total++;
            if (recipeApi.resolve(ResolveContext.empty(), craftingSystem, recipe).resolved()) {
                resolved++;
            }
        }

        assertTrue(total > 0, "crafting recipe catalog should not be empty");
        assertTrue(resolved > 0, "crafting system should resolve at least one recipe");
    }

    @Test
    void craftingSystemRegistersCraftingTableMachine(MinecraftServer server) {
        RecipeApi recipeApi = RecipeApi.instance();
        boolean hasCraftingTable = recipeApi.getMachines(CraftingRecipeSystem.systemId, false)
                .anySatisfy(machine -> machine.toItemStack().is(Items.CRAFTING_TABLE));

        assertTrue(hasCraftingTable, "crafting recipes should expose the crafting table as their machine icon");
    }
}
