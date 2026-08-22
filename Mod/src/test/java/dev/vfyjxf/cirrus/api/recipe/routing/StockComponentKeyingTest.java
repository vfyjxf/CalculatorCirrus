package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.routing.state.ResourceLedger;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for component-aware amount keying: same-item stacks with different components
 * must keep independent amounts in {@link Stock} and in the {@link ResourceLedger} byproduct pool.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class StockComponentKeyingTest {

    @Test
    void sameItemDifferentComponentsKeepSeparateAmounts(net.minecraft.server.MinecraftServer server) {
        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(renamedSword("Fire", 5)));
        stock.add(ContentStack.fromStack(renamedSword("Frost", 7)));

        assertEquals(5L, stock.countMatching(ContentStack.fromStack(renamedSword("Fire"))));
        assertEquals(7L, stock.countMatching(ContentStack.fromStack(renamedSword("Frost"))));
        assertEquals(2, stock.getStacks().size());
    }

    @Test
    void countMatchingNormalizedIsExactPerVariant(net.minecraft.server.MinecraftServer server) {
        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(renamedSword("Fire", 5)));
        stock.add(ContentStack.fromStack(renamedSword("Frost", 7)));

        assertEquals(5L, stock.countMatchingNormalized(ContentStack.fromStack(renamedSword("Fire")).normalize()));
        assertEquals(7L, stock.countMatchingNormalized(ContentStack.fromStack(renamedSword("Frost")).normalize()));
        assertEquals(0L, stock.countMatchingNormalized(ContentStack.fromStack(renamedSword("Storm")).normalize()));
    }

    @Test
    void consumingOneVariantDoesNotDrainTheOther(net.minecraft.server.MinecraftServer server) {
        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(renamedSword("Fire", 5)));
        stock.add(ContentStack.fromStack(renamedSword("Frost", 7)));

        long consumed = stock.consume(ContentStack.fromStack(renamedSword("Fire")), 3);

        assertEquals(3L, consumed);
        assertEquals(2L, stock.countMatching(ContentStack.fromStack(renamedSword("Fire"))));
        assertEquals(7L, stock.countMatching(ContentStack.fromStack(renamedSword("Frost"))));
    }

    @Test
    void transactionRollbackRestoresBothVariants(net.minecraft.server.MinecraftServer server) {
        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(renamedSword("Fire", 5)));
        stock.add(ContentStack.fromStack(renamedSword("Frost", 7)));

        Stock.Mark mark = stock.mark();
        stock.consume(ContentStack.fromStack(renamedSword("Fire")), 4);
        stock.consume(ContentStack.fromStack(renamedSword("Frost")), 6);
        stock.rollback(mark);

        assertEquals(5L, stock.countMatching(ContentStack.fromStack(renamedSword("Fire"))));
        assertEquals(7L, stock.countMatching(ContentStack.fromStack(renamedSword("Frost"))));
    }

    @Test
    void byproductPoolKeepsVariantsSeparate(net.minecraft.server.MinecraftServer server) {
        ResourceLedger ledger = new ResourceLedger(Stock.empty());
        ContentStack<?, ?> fire = ContentStack.fromStack(renamedSword("Fire", 1)).normalize();
        ContentStack<?, ?> frost = ContentStack.fromStack(renamedSword("Frost", 1)).normalize();
        ledger.addByproduct(fire, 5);
        ledger.addByproduct(frost, 7);

        assertEquals(5L, ledger.peekByproductAvailableNormalized(ContentStack.fromStack(renamedSword("Fire")).normalize()));
        assertEquals(7L, ledger.peekByproductAvailableNormalized(ContentStack.fromStack(renamedSword("Frost")).normalize()));
        assertEquals(0L, ledger.peekByproductAvailableNormalized(ContentStack.fromStack(renamedSword("Storm")).normalize()));
    }

    @Test
    void byproductTransactionRollbackRestoresBothVariants(net.minecraft.server.MinecraftServer server) {
        ResourceLedger ledger = new ResourceLedger(Stock.empty());
        ContentStack<?, ?> fire = ContentStack.fromStack(renamedSword("Fire", 1)).normalize();
        ContentStack<?, ?> frost = ContentStack.fromStack(renamedSword("Frost", 1)).normalize();
        ledger.addByproduct(fire, 5);
        ledger.addByproduct(frost, 7);

        ResourceLedger.Mark mark = ledger.mark();
        ledger.addByproduct(fire, 3);
        ledger.rollback(mark);

        assertEquals(5L, ledger.peekByproductAvailableNormalized(fire));
        assertEquals(7L, ledger.peekByproductAvailableNormalized(frost));
    }

    private static ItemStack renamedSword(String name) {
        return renamedSword(name, 1);
    }

    private static ItemStack renamedSword(String name, int count) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }
}
