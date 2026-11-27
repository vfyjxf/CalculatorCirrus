package dev.vfyjxf.cirrus.recipe.content;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(EphemeralTestServerProvider.class)
class RecipeContentRenderSupportTest {

    @Test
    void visualStackDoesNotInjectRecipeAmountIntoRenderedStack(MinecraftServer server) {
        InputContent content = InputContent.fromItemStack(Items.COBBLESTONE.getDefaultInstance(), 9);
        ContentStack<Item, ItemStack> stack = ContentStack.fromStack(Items.COBBLESTONE.getDefaultInstance());

        ContentStack<Item, ItemStack> visual = RecipeContentRenderSupport.visualStack(stack).cast();
        ContentStack<Item, ItemStack> tooltip = RecipeContentRenderSupport.stackForTooltipDisplay(content, stack)
                .cast();

        assertEquals(1L, visual.amount());
        assertEquals(9L, tooltip.amount());
        assertEquals(1, visual.value().getCount());
        assertEquals(1, tooltip.value().getCount());
    }
}
