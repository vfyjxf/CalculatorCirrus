package dev.vfyjxf.cirrus.api.content;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
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
import java.util.Objects;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Differential tests for the canonical fast path in {@code ContentStack.matches}: for every pair
 * shape, the production strategy (fast-path eligible) must agree with an equivalent strategy built
 * from a fresh matcher lambda (never fast-path eligible), and with vanilla
 * {@link ItemStack#isSameItemSameComponents} as ground truth.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class MatchStrategyCanonicalFastPathTest {

    /** Semantically identical to {@link MatchStrategy#identityAndComponent} but fast-path ineligible. */
    private static final MatchStrategy SLOW_REFERENCE =
            new MatchStrategy(true, false, true, (a, b) -> Objects.equals(a, b));

    @Test
    void fastPathAgreesWithSlowPath(net.minecraft.server.MinecraftServer server) {
        for (ItemStack left : fixtures()) {
            for (ItemStack right : fixtures()) {
                ContentStack<Item, ItemStack> leftStack = ContentStack.fromStack(left);
                ContentStack<Item, ItemStack> rightStack = ContentStack.fromStack(right);

                boolean expected = ItemStack.isSameItemSameComponents(left, right);
                boolean fast = leftStack.matches(rightStack, MatchStrategy.identityAndComponent);
                boolean slow = leftStack.matches(rightStack, SLOW_REFERENCE);

                assertEquals(expected, fast, describe(left) + " vs " + describe(right) + " (fast path)");
                assertEquals(expected, slow, describe(left) + " vs " + describe(right) + " (slow reference)");
            }
        }
    }

    @Test
    void randomizedDifferentialAgreesWithVanilla(net.minecraft.server.MinecraftServer server) {
        SplittableRandom random = new SplittableRandom(0xC1CA5);
        for (int i = 0; i < 500; i++) {
            ItemStack left = randomStack(random, i);
            // Half the pairs are copy chains (same-origin), half independent constructions.
            ItemStack right = random.nextBoolean() ? left.copy() : randomStack(random, i);

            boolean expected = ItemStack.isSameItemSameComponents(left, right);
            boolean actual = ContentStack.fromStack(left).matches(
                    ContentStack.fromStack(right),
                    MatchStrategy.identityAndComponent
            );
            assertEquals(expected, actual, "random case " + i + ": " + describe(left) + " vs " + describe(right));
        }
    }

    @Test
    void amountStrategiesStillRespectAmount(net.minecraft.server.MinecraftServer server) {
        ContentStack<Item, ItemStack> left = ContentStack.fromStack(new ItemStack(Items.DIAMOND, 3));
        ContentStack<Item, ItemStack> right = ContentStack.fromStack(new ItemStack(Items.DIAMOND, 5));

        assertTrue(left.matches(right, MatchStrategy.identityAndComponent));
        assertFalse(left.matches(right, MatchStrategy.identityAndAmount));
        assertFalse(left.matches(right, MatchStrategy.all));
    }

    @Test
    void customComponentMatcherIsNotFastPathed(net.minecraft.server.MinecraftServer server) {
        // A non-exact matcher keeps its own semantics: component-blind "nothing" must match
        // different components when combined with identity.
        MatchStrategy componentBlind = new MatchStrategy(true, false, true, MatchStrategy.nothing);
        ContentStack<Item, ItemStack> fireSword = ContentStack.fromStack(sword("Fire"));
        ContentStack<Item, ItemStack> frostSword = ContentStack.fromStack(sword("Frost"));

        assertFalse(fireSword.matches(frostSword, MatchStrategy.identityAndComponent));
        assertTrue(fireSword.matches(frostSword, componentBlind));
        assertFalse(componentBlind.usesExactComponents());
        assertTrue(MatchStrategy.identityAndComponent.usesExactComponents());
        assertTrue(MatchStrategy.all.usesExactComponents());
        assertFalse(MatchStrategy.identity.usesExactComponents());
    }

    @Test
    void potionVariantsDifferThroughComponents(net.minecraft.server.MinecraftServer server) {
        ContentStack<Item, ItemStack> healing = ContentStack.fromStack(potion(Potions.HEALING));
        ContentStack<Item, ItemStack> regeneration = ContentStack.fromStack(potion(Potions.REGENERATION));

        assertFalse(healing.matches(regeneration, MatchStrategy.identityAndComponent));
        assertTrue(healing.matches(ContentStack.fromStack(potion(Potions.HEALING)), MatchStrategy.identityAndComponent));
    }

    //region fixtures

    private static List<ItemStack> fixtures() {
        return List.of(
                new ItemStack(Items.STONE),
                new ItemStack(Items.DIAMOND_SWORD),
                sword("Fire"),
                sword("Frost"),
                swordWithDamage("Fire", 7),
                potion(Potions.HEALING),
                potion(Potions.REGENERATION),
                container("Diamond"),
                container("Emerald")
        );
    }

    private static ItemStack sword(String name) {
        return swordWithDamage(name, 42);
    }

    private static ItemStack swordWithDamage(String name, int damage) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        stack.set(DataComponents.DAMAGE, damage);
        CompoundTag tag = new CompoundTag();
        tag.putString("origin", name);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static ItemStack potion(net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion) {
        ItemStack stack = new ItemStack(Items.POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
        return stack;
    }

    private static ItemStack container(String gem) {
        ItemStack stack = new ItemStack(Items.BUNDLE);
        ItemStack nested = "Diamond".equals(gem) ? new ItemStack(Items.DIAMOND) : new ItemStack(Items.EMERALD);
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(nested)));
        return stack;
    }

    private static ItemStack randomStack(SplittableRandom random, int salt) {
        ItemStack stack = new ItemStack(salt % 3 == 0 ? Items.DIAMOND_SWORD : Items.NETHERITE_SWORD);
        if (random.nextBoolean()) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Name " + random.nextInt(3)));
        }
        if (random.nextBoolean()) {
            stack.set(DataComponents.DAMAGE, random.nextInt(100));
        }
        if (random.nextBoolean()) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("tier", random.nextInt(3));
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        if (random.nextBoolean()) {
            stack.set(DataComponents.REPAIR_COST, random.nextInt(4));
        }
        return stack;
    }

    private static String describe(ItemStack stack) {
        return stack.getItem() + "[" + stack.getComponents() + "]";
    }

    //endregion
}
