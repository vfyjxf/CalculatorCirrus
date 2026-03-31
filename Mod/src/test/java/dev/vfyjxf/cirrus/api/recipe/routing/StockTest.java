package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(EphemeralTestServerProvider.class)
class StockTest {

    @Test
    void consumeInputUsesSharedStockAcrossOverlappingSemanticInputs(MinecraftServer server) {
        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 64)));

        long exactConsumed = stock.consumeInput(InputContent.fromItemStack(new ItemStack(Items.OAK_LOG), 64), 64);
        long tagConsumed = stock.consumeInput(InputContent.fromItemTag(ItemTags.LOGS, 64), 64);

        assertEquals(64L, exactConsumed);
        assertEquals(0L, tagConsumed);
    }

    @Test
    void consumeInputConsumesTagDemandAcrossMultipleConcreteStacks(MinecraftServer server) {
        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 32)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.BIRCH_LOG, 32)));

        long consumed = stock.consumeInput(InputContent.fromItemTag(ItemTags.LOGS, 64), 64);

        assertEquals(64L, consumed);
        assertEquals(0L, stock.countMatching(ContentStack.fromStack(new ItemStack(Items.OAK_LOG))));
        assertEquals(0L, stock.countMatching(ContentStack.fromStack(new ItemStack(Items.BIRCH_LOG))));
    }
}
