package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic complex routing datasets used by integration tests.
 */
final class RoutingComplexDataset {

    private static final Item[] tokenPool = {
        Items.OAK_LOG,
        Items.OAK_PLANKS,
        Items.STICK,
        Items.COBBLESTONE,
        Items.GRAVEL,
        Items.FLINT,
        Items.IRON_INGOT,
        Items.REDSTONE,
        Items.GLASS,
        Items.REPEATER,
        Items.COMPARATOR,
        Items.COMPASS,
        Items.SPYGLASS,
        Items.ENDER_PEARL,
        Items.BLAZE_POWDER,
        Items.ENDER_EYE,
        Items.BEACON,
        Items.NETHER_STAR,
        Items.CHEST,
        Items.HOPPER,
        Items.DROPPER,
        Items.DISPENSER,
        Items.PISTON,
        Items.OBSERVER,
        Items.LECTERN,
        Items.ANVIL,
        Items.RAIL,
        Items.POWERED_RAIL,
        Items.DETECTOR_RAIL,
        Items.ACTIVATOR_RAIL,
        Items.SMOKER,
        Items.BLAST_FURNACE,
        Items.FURNACE,
        Items.BELL,
        Items.LODESTONE,
        Items.RECOVERY_COMPASS
    };

    private RoutingComplexDataset() {
    }

    static Scenario largeScenario() {
        List<RoutingTestFixtures.FakeRecipe> recipes = new ArrayList<>();

        recipes.add(recipe("core_log_to_plank", in(Items.OAK_LOG, 1), out(Items.OAK_PLANKS, 6)));
        recipes.add(recipe("core_plank_to_stick", in(Items.OAK_PLANKS, 2), out(Items.STICK, 4)));
        recipes.add(recipe("core_stick_glass_to_frame", in(Items.STICK, 8), in(Items.GLASS, 1), out(Items.ITEM_FRAME, 1)));
        recipes.add(recipe("core_cobble_to_gravel", in(Items.COBBLESTONE, 1), out(Items.GRAVEL, 1)));
        recipes.add(recipe("core_gravel_to_flint", in(Items.GRAVEL, 1), out(Items.FLINT, 1)));
        recipes.add(recipe("core_iron_stick_to_comparator", in(Items.IRON_INGOT, 2), in(Items.STICK, 2), out(Items.COMPARATOR, 1)));
        recipes.add(recipe("core_flint_redstone_to_repeater", in(Items.FLINT, 1), in(Items.REDSTONE, 2), out(Items.REPEATER, 1)));
        recipes.add(recipe("core_comparator_repeater_to_compass", in(Items.COMPARATOR, 1), in(Items.REPEATER, 1), out(Items.COMPASS, 1)));
        recipes.add(recipe("core_glass_flint_to_spyglass", in(Items.GLASS, 4), in(Items.FLINT, 1), out(Items.SPYGLASS, 1)));
        recipes.add(recipe("core_spyglass_compass_to_pearl", in(Items.SPYGLASS, 1), in(Items.COMPASS, 1), out(Items.ENDER_PEARL, 1)));
        recipes.add(recipe("core_pearl_blaze_to_eye", in(Items.ENDER_PEARL, 1), in(Items.BLAZE_POWDER, 1), out(Items.ENDER_EYE, 1)));
        recipes.add(recipe("core_frame_repeater_compass_to_beacon", in(Items.ITEM_FRAME, 1), in(Items.REPEATER, 1), in(Items.COMPASS, 1), out(Items.BEACON, 1)));
        recipes.add(recipe("core_beacon_eye_to_target", in(Items.BEACON, 1), in(Items.ENDER_EYE, 1), out(Items.NETHER_STAR, 1)));

        // Alternative usable paths.
        recipes.add(recipe("alt_iron_redstone_to_compass", in(Items.IRON_INGOT, 4), in(Items.REDSTONE, 4), out(Items.COMPASS, 1)));
        recipes.add(recipe("alt_redstone_glass_to_pearl", in(Items.REDSTONE, 4), in(Items.GLASS, 2), out(Items.ENDER_PEARL, 1)));
        recipes.add(recipe("alt_redstone_plank_to_repeater", in(Items.REDSTONE, 1), in(Items.OAK_PLANKS, 1), out(Items.REPEATER, 1)));

        // Cycles and amplification traps.
        recipes.add(recipe("cycle_stick_to_plank", in(Items.STICK, 1), out(Items.OAK_PLANKS, 1)));
        recipes.add(recipe("cycle_plank_to_stick", in(Items.OAK_PLANKS, 1), out(Items.STICK, 1)));
        recipes.add(recipe("cycle_pearl_to_redstone", in(Items.ENDER_PEARL, 1), out(Items.REDSTONE, 2)));
        recipes.add(recipe("cycle_redstone_to_pearl", in(Items.REDSTONE, 3), out(Items.ENDER_PEARL, 1)));

        // Large junk cluster that should be filtered by source policy.
        Random noise = new Random(20260301L);
        for (int i = 0; i < 90; i++) {
            Item input = tokenPool[noise.nextInt(tokenPool.length)];
            Item output = tokenPool[noise.nextInt(tokenPool.length)];
            long inAmount = 1 + noise.nextInt(4);
            long outAmount = 1 + noise.nextInt(3);
            recipes.add(recipe("junk_" + i, in(input, inAmount), out(output, outAmount)));
        }

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 96)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.COBBLESTONE, 96)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.IRON_INGOT, 64)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.REDSTONE, 128)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.GLASS, 96)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.BLAZE_POWDER, 32)));

        return new Scenario(recipes.toArray(RoutingTestFixtures.FakeRecipe[]::new), stock, "core_beacon_eye_to_target", 6);
    }

    static Scenario generatedScenario(long seed) {
        return generatedScenario(seed, 12, 30, 6);
    }

    static Scenario generatedScenario(long seed, int baseChainLength, int junkCount, int cyclePairCount) {
        Random random = new Random(seed);
        List<RoutingTestFixtures.FakeRecipe> recipes = new ArrayList<>();

        int chainLength = Math.max(6, baseChainLength + random.nextInt(5));
        int offset = random.nextInt(tokenPool.length);
        for (int i = 0; i < chainLength; i++) {
            Item input = tokenPool[(offset + i) % tokenPool.length];
            Item output = tokenPool[(offset + i + 1) % tokenPool.length];
            long inAmount = 1 + random.nextInt(3);
            long outAmount = 1 + random.nextInt(4);
            recipes.add(recipe("seed_" + seed + "_chain_" + i, in(input, inAmount), out(output, outAmount)));
        }

        // Branch alternatives.
        for (int i = 0; i < chainLength; i++) {
            Item bridgeInput = tokenPool[(offset + Math.max(0, i - 1)) % tokenPool.length];
            Item bridgeOutput = tokenPool[(offset + Math.min(chainLength, i + 1)) % tokenPool.length];
            recipes.add(recipe(
                "seed_" + seed + "_alt_" + i,
                in(bridgeInput, 1 + random.nextInt(3)),
                out(bridgeOutput, 1 + random.nextInt(2))
            ));
        }

        // Local cycles and noisy junk.
        for (int i = 0; i < junkCount; i++) {
            Item a = tokenPool[random.nextInt(tokenPool.length)];
            Item b = tokenPool[random.nextInt(tokenPool.length)];
            recipes.add(recipe("seed_" + seed + "_junk_" + i, in(a, 1 + random.nextInt(3)), out(b, 1 + random.nextInt(3))));
        }
        for (int i = 0; i < cyclePairCount; i++) {
            Item a = tokenPool[random.nextInt(tokenPool.length)];
            Item b = tokenPool[random.nextInt(tokenPool.length)];
            recipes.add(recipe("seed_" + seed + "_cycle_ab_" + i, in(a, 1 + random.nextInt(2)), out(b, 1 + random.nextInt(2))));
            recipes.add(recipe("seed_" + seed + "_cycle_ba_" + i, in(b, 1 + random.nextInt(2)), out(a, 1 + random.nextInt(2))));
        }

        Item initial = tokenPool[offset % tokenPool.length];
        String targetRecipeId = "seed_" + seed + "_chain_" + (chainLength - 1);
        int targetTimes = 2 + random.nextInt(4);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(initial, 512)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.REDSTONE, 128)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.IRON_INGOT, 128)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.GLASS, 128)));

        return new Scenario(recipes.toArray(RoutingTestFixtures.FakeRecipe[]::new), stock, targetRecipeId, targetTimes);
    }

    static Scenario megaScenario() {
        List<RoutingTestFixtures.FakeRecipe> recipes = new ArrayList<>();

        // Keep the guaranteed core chain so mega scenarios always have a valid route.
        recipes.add(recipe("mega_core_log_to_plank", in(Items.OAK_LOG, 1), out(Items.OAK_PLANKS, 6)));
        recipes.add(recipe("mega_core_plank_to_stick", in(Items.OAK_PLANKS, 2), out(Items.STICK, 4)));
        recipes.add(recipe("mega_core_stick_glass_to_frame", in(Items.STICK, 8), in(Items.GLASS, 1), out(Items.ITEM_FRAME, 1)));
        recipes.add(recipe("mega_core_cobble_to_gravel", in(Items.COBBLESTONE, 1), out(Items.GRAVEL, 1)));
        recipes.add(recipe("mega_core_gravel_to_flint", in(Items.GRAVEL, 1), out(Items.FLINT, 1)));
        recipes.add(recipe("mega_core_iron_stick_to_comparator", in(Items.IRON_INGOT, 2), in(Items.STICK, 2), out(Items.COMPARATOR, 1)));
        recipes.add(recipe("mega_core_flint_redstone_to_repeater", in(Items.FLINT, 1), in(Items.REDSTONE, 2), out(Items.REPEATER, 1)));
        recipes.add(recipe("mega_core_comparator_repeater_to_compass", in(Items.COMPARATOR, 1), in(Items.REPEATER, 1), out(Items.COMPASS, 1)));
        recipes.add(recipe("mega_core_glass_flint_to_spyglass", in(Items.GLASS, 4), in(Items.FLINT, 1), out(Items.SPYGLASS, 1)));
        recipes.add(recipe("mega_core_spyglass_compass_to_pearl", in(Items.SPYGLASS, 1), in(Items.COMPASS, 1), out(Items.ENDER_PEARL, 1)));
        recipes.add(recipe("mega_core_pearl_blaze_to_eye", in(Items.ENDER_PEARL, 1), in(Items.BLAZE_POWDER, 1), out(Items.ENDER_EYE, 1)));
        recipes.add(recipe("mega_core_frame_repeater_compass_to_beacon", in(Items.ITEM_FRAME, 1), in(Items.REPEATER, 1), in(Items.COMPASS, 1), out(Items.BEACON, 1)));
        recipes.add(recipe("mega_core_beacon_eye_to_target", in(Items.BEACON, 1), in(Items.ENDER_EYE, 1), out(Items.NETHER_STAR, 1)));

        // Dense adversarial decoy cluster: many recipes produce key outputs but require hard-to-meet inputs.
        Random decoy = new Random(20260302L);
        Item[] keyOutputs = {Items.REPEATER, Items.COMPARATOR, Items.COMPASS, Items.ENDER_PEARL, Items.BEACON, Items.NETHER_STAR};
        Item[] hardInputs = {Items.DIAMOND, Items.EMERALD, Items.NETHERITE_INGOT, Items.DRAGON_BREATH, Items.SHULKER_SHELL, Items.ELYTRA};
        for (int i = 0; i < 180; i++) {
            Item output = keyOutputs[decoy.nextInt(keyOutputs.length)];
            Item inA = hardInputs[decoy.nextInt(hardInputs.length)];
            Item inB = tokenPool[decoy.nextInt(tokenPool.length)];
            recipes.add(recipe(
                "mega_decoy_" + i,
                in(inA, 1 + decoy.nextInt(3)),
                in(inB, 1 + decoy.nextInt(3)),
                out(output, 1 + decoy.nextInt(2))
            ));
        }

        // Cycle-rich local networks to stress active-path handling.
        for (int i = 0; i < 40; i++) {
            Item a = tokenPool[i % tokenPool.length];
            Item b = tokenPool[(i + 7) % tokenPool.length];
            Item c = tokenPool[(i + 13) % tokenPool.length];
            recipes.add(recipe("mega_cycle_a_to_b_" + i, in(a, 1 + (i % 2)), out(b, 1)));
            recipes.add(recipe("mega_cycle_b_to_c_" + i, in(b, 1 + (i % 3)), out(c, 1)));
            recipes.add(recipe("mega_cycle_c_to_a_" + i, in(c, 1 + (i % 2)), out(a, 1)));
        }

        // Massive junk region; intended to be hard-excluded by source policy.
        Random junk = new Random(20260303L);
        for (int i = 0; i < 260; i++) {
            Item input = tokenPool[junk.nextInt(tokenPool.length)];
            Item output = tokenPool[junk.nextInt(tokenPool.length)];
            recipes.add(recipe("junk_mega_" + i, in(input, 1 + junk.nextInt(4)), out(output, 1 + junk.nextInt(3))));
        }

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 768)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.COBBLESTONE, 768)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.IRON_INGOT, 512)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.REDSTONE, 1024)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.GLASS, 768)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.BLAZE_POWDER, 320)));

        return new Scenario(recipes.toArray(RoutingTestFixtures.FakeRecipe[]::new), stock, "mega_core_beacon_eye_to_target", 24);
    }

    private static RoutingTestFixtures.FakeRecipe recipe(String id, InputContent input, OutputContent output) {
        return new RoutingTestFixtures.FakeRecipe(id, List.of(input), List.of(output));
    }

    private static RoutingTestFixtures.FakeRecipe recipe(String id, InputContent in1, InputContent in2, OutputContent out1) {
        return new RoutingTestFixtures.FakeRecipe(id, List.of(in1, in2), List.of(out1));
    }

    private static RoutingTestFixtures.FakeRecipe recipe(
        String id,
        InputContent in1,
        InputContent in2,
        InputContent in3,
        OutputContent out1
    ) {
        return new RoutingTestFixtures.FakeRecipe(id, List.of(in1, in2, in3), List.of(out1));
    }

    private static InputContent in(Item item, long amount) {
        return RoutingTestFixtures.in(item, amount);
    }

    private static OutputContent out(Item item, long amount) {
        return RoutingTestFixtures.out(item, amount);
    }

    record Scenario(
        RoutingTestFixtures.FakeRecipe[] recipes,
        Stock stock,
        String targetRecipeId,
        int targetTimes
    ) {
    }
}
