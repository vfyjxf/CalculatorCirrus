package dev.vfyjxf.cirrus.api.content;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(EphemeralTestServerProvider.class)
class ComponentCanonicalizerTest {

    @Test
    void equalMapsFromIndependentConstructionShareCanonical(MinecraftServer server) {
        DataComponentMap first = sword("Shared").getComponents();
        DataComponentMap second = sword("Shared").getComponents();

        assertEquals(first, second, "fixture sanity: independent stacks must be equal");
        assertNotSame(first, second, "fixture sanity: independent stacks must not share the map instance");
        assertSame(ComponentCanonicalizer.canonical(first), ComponentCanonicalizer.canonical(second));
    }

    @Test
    void canonicalIsIdempotent(MinecraftServer server) {
        DataComponentMap map = sword("Idempotent").getComponents();
        assertSame(ComponentCanonicalizer.canonical(map), ComponentCanonicalizer.canonical(map));
    }

    @Test
    void unequalMapsGetDifferentCanonicals(MinecraftServer server) {
        DataComponentMap first = sword("A").getComponents();
        DataComponentMap second = sword("B").getComponents();
        assertNotEquals(first, second, "fixture sanity");
        assertNotEquals(ComponentCanonicalizer.canonical(first), ComponentCanonicalizer.canonical(second));
    }

    @Test
    void plainStacksShareThePrototypeCanonical(MinecraftServer server) {
        var itemStackType = CirrusTypes.Contents.itemStack;
        assertSame(
            itemStackType.canonicalComponents(new ItemStack(Items.STONE)),
            itemStackType.canonicalComponents(new ItemStack(Items.STONE))
        );
        // Vanilla interns item prototypes (Item.Properties.buildComponents uses a Guava Interner),
        // so simple items with value-equal prototypes may even share one canonical across items —
        // safe here because every consumer still checks content identity separately.
    }

    @Test
    void emptyMapCanonicalizesToItself(MinecraftServer server) {
        assertSame(DataComponentMap.EMPTY, ComponentCanonicalizer.canonical(DataComponentMap.EMPTY));
    }

    @Test
    void nullMapReturnsNull(MinecraftServer server) {
        assertNull(ComponentCanonicalizer.canonical(null));
    }

    @Test
    void potionAndContainerMapsCanonicalize(MinecraftServer server) {
        DataComponentMap firstPotion = potion().getComponents();
        DataComponentMap secondPotion = potion().getComponents();
        assertSame(ComponentCanonicalizer.canonical(firstPotion), ComponentCanonicalizer.canonical(secondPotion));

        DataComponentMap firstContainer = container().getComponents();
        DataComponentMap secondContainer = container().getComponents();
        assertSame(ComponentCanonicalizer.canonical(firstContainer), ComponentCanonicalizer.canonical(secondContainer));

        assertNotEquals(
            ComponentCanonicalizer.canonical(firstPotion),
            ComponentCanonicalizer.canonical(firstContainer)
        );
    }

    @Test
    void canonicalSurvivesPoolClear(MinecraftServer server) {
        DataComponentMap first = sword("PostClear").getComponents();
        DataComponentMap beforeClear = ComponentCanonicalizer.canonical(first);
        ComponentCanonicalizer.clear();
        // New instances canonicalized after the clear still agree with each other.
        DataComponentMap second = sword("PostClear").getComponents();
        DataComponentMap third = sword("PostClear").getComponents();
        assertSame(ComponentCanonicalizer.canonical(second), ComponentCanonicalizer.canonical(third));
        // Value equality holds across representative generations.
        assertEquals(beforeClear, ComponentCanonicalizer.canonical(second));
    }

    //region fixtures

    private static ItemStack sword(String name) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        stack.set(DataComponents.DAMAGE, 42);
        CompoundTag tag = new CompoundTag();
        tag.putString("origin", "test");
        tag.putLong("seed", 7L);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static ItemStack potion() {
        ItemStack stack = new ItemStack(Items.POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING));
        return stack;
    }

    private static ItemStack container() {
        ItemStack stack = new ItemStack(Items.BUNDLE);
        stack.set(
            DataComponents.CONTAINER,
            ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.EMERALD)))
        );
        return stack;
    }

    //endregion
}
