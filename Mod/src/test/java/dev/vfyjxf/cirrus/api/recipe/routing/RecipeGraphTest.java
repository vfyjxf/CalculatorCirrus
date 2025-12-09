package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.system.VanillaRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.CraftingRecipeSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class RecipeGraphTest {

    @SuppressWarnings("unchecked")
    @Test
    void buildGraph(MinecraftServer server) {
        RecipeApi recipeApi = RecipeApi.instance();
        var craftingSystem = (VanillaRecipeSystem<CraftingRecipe>) recipeApi.getSystem(CraftingRecipeSystem.systemId);
        var recipes = craftingSystem.createLookup()
                                    .withInput(ContentStack.fromStack(new ItemStack(Items.OAK_LOG)))
                                    .get();
        RecipeGraph recipeGraph = RecipeGraph.buildGraph(
            recipes
                .select(recipe -> !(recipe.value() instanceof CustomRecipe))
                .collect(recipe -> RecipeEntry.of(craftingSystem, recipe))
        );
        System.out.println(recipeGraph);

    }
}