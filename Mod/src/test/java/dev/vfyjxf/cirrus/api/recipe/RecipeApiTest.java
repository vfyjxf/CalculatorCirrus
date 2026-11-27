package dev.vfyjxf.cirrus.api.recipe;

import dev.vfyjxf.cirrus.recipe.system.CraftingRecipeSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
public class RecipeApiTest {

    @Test
    void craftingSystemRegistersCraftingTableMachine(MinecraftServer server) {
        RecipeApi recipeApi = RecipeApi.instance();
        boolean hasCraftingTable = recipeApi.getMachines(CraftingRecipeSystem.systemId, false)
                .anySatisfy(machine -> machine.toItemStack().is(Items.CRAFTING_TABLE));

        assertTrue(hasCraftingTable, "crafting recipes should expose the crafting table as their machine icon");
    }
}
