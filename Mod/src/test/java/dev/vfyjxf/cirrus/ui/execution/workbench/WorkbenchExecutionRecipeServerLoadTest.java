package dev.vfyjxf.cirrus.ui.execution.workbench;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(EphemeralTestServerProvider.class)
class WorkbenchExecutionRecipeServerLoadTest {

    @Test
    void serverLoadsWorkbenchDemoRecipeFromDatapack(MinecraftServer server) {
        assertCraftingRecipePresent(server, ResourceLocation.fromNamespaceAndPath("calculator_cirrus", "workbench_oak_sticks_demo"));
        assertCraftingRecipePresent(server, ResourceLocation.fromNamespaceAndPath("calculator_cirrus", "workbench_oak_planks_demo"));
    }

    private static void assertCraftingRecipePresent(MinecraftServer server, ResourceLocation recipeId) {
        var holder = server.getRecipeManager().byKey(recipeId).orElse(null);
        assertNotNull(holder, "missing recipe in server recipe manager: " + recipeId);
        assertEquals(RecipeType.CRAFTING, holder.value().getType(), "recipe must be crafting type: " + recipeId);
    }
}
