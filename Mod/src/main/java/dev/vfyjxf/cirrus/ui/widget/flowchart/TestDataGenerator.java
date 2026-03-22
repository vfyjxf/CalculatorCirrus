package dev.vfyjxf.cirrus.ui.widget.flowchart;

import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessEdge;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessGraph;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessGroup;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode.MachineInfo;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode.MaterialSlot;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode.MaterialType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * Generates an extremely complex process flow graph simulating a multi-stage
 * industrial manufacturing pipeline (inspired by GregTech-style processing chains).
 * <p>
 * All material slots reference real vanilla Minecraft ItemStacks / FluidStacks so that
 * cards and tooltips can render actual item textures.
 * <p>
 * 52 nodes across 8 stages, 100+ edges, featuring: fan-out, fan-in,
 * shared intermediates, feedback loops, parallel branches, multi-output recipes,
 * cross-layer long edges, and probabilistic outputs.
 */
public final class TestDataGenerator {

    private TestDataGenerator() {}

    // ─────────── Cached stacks (created once, shared) ───────────

    // Ores & raw materials
    private static final ItemStack IRON_ORE       = new ItemStack(Items.IRON_ORE);
    private static final ItemStack COPPER_ORE     = new ItemStack(Items.COPPER_ORE);
    private static final ItemStack GOLD_ORE       = new ItemStack(Items.GOLD_ORE);
    private static final ItemStack COAL_ORE       = new ItemStack(Items.COAL_ORE);
    private static final ItemStack REDSTONE_ORE   = new ItemStack(Items.REDSTONE_ORE);
    private static final ItemStack LAPIS_ORE      = new ItemStack(Items.LAPIS_ORE);
    private static final ItemStack DIAMOND_ORE    = new ItemStack(Items.DEEPSLATE_DIAMOND_ORE);
    private static final ItemStack QUARTZ_ORE     = new ItemStack(Items.NETHER_QUARTZ_ORE);

    // Raw
    private static final ItemStack RAW_IRON       = new ItemStack(Items.RAW_IRON);
    private static final ItemStack RAW_COPPER     = new ItemStack(Items.RAW_COPPER);
    private static final ItemStack RAW_GOLD       = new ItemStack(Items.RAW_GOLD);
    private static final ItemStack COAL           = new ItemStack(Items.COAL);
    private static final ItemStack REDSTONE       = new ItemStack(Items.REDSTONE);
    private static final ItemStack LAPIS          = new ItemStack(Items.LAPIS_LAZULI);
    private static final ItemStack DIAMOND        = new ItemStack(Items.DIAMOND);
    private static final ItemStack QUARTZ         = new ItemStack(Items.QUARTZ);
    private static final ItemStack COBBLESTONE    = new ItemStack(Items.COBBLESTONE);

    // Ingots & nuggets
    private static final ItemStack IRON_INGOT     = new ItemStack(Items.IRON_INGOT);
    private static final ItemStack IRON_NUGGET    = new ItemStack(Items.IRON_NUGGET);
    private static final ItemStack COPPER_INGOT   = new ItemStack(Items.COPPER_INGOT);
    private static final ItemStack GOLD_INGOT     = new ItemStack(Items.GOLD_INGOT);
    private static final ItemStack GOLD_NUGGET    = new ItemStack(Items.GOLD_NUGGET);
    private static final ItemStack NETHERITE_SCRAP = new ItemStack(Items.NETHERITE_SCRAP);
    private static final ItemStack NETHERITE_INGOT = new ItemStack(Items.NETHERITE_INGOT);

    // Processed materials
    private static final ItemStack CHARCOAL       = new ItemStack(Items.CHARCOAL);
    private static final ItemStack GLOWSTONE_DUST = new ItemStack(Items.GLOWSTONE_DUST);
    private static final ItemStack BLAZE_POWDER   = new ItemStack(Items.BLAZE_POWDER);
    private static final ItemStack GUNPOWDER      = new ItemStack(Items.GUNPOWDER);
    private static final ItemStack PRISMARINE_SHARD = new ItemStack(Items.PRISMARINE_SHARD);
    private static final ItemStack AMETHYST_SHARD = new ItemStack(Items.AMETHYST_SHARD);
    private static final ItemStack ECHO_SHARD     = new ItemStack(Items.ECHO_SHARD);
    private static final ItemStack FLINT          = new ItemStack(Items.FLINT);
    private static final ItemStack CLAY_BALL      = new ItemStack(Items.CLAY_BALL);
    private static final ItemStack BRICK          = new ItemStack(Items.BRICK);
    private static final ItemStack SLIME_BALL     = new ItemStack(Items.SLIME_BALL);
    private static final ItemStack BONE_MEAL      = new ItemStack(Items.BONE_MEAL);
    private static final ItemStack SUGAR          = new ItemStack(Items.SUGAR);
    private static final ItemStack NETHER_BRICK   = new ItemStack(Items.NETHER_BRICK);
    private static final ItemStack BLAZE_ROD      = new ItemStack(Items.BLAZE_ROD);

    // Blocks & formed materials
    private static final ItemStack IRON_BLOCK     = new ItemStack(Items.IRON_BLOCK);
    private static final ItemStack GOLD_BLOCK     = new ItemStack(Items.GOLD_BLOCK);
    private static final ItemStack COPPER_BLOCK   = new ItemStack(Items.COPPER_BLOCK);
    private static final ItemStack LAPIS_BLOCK    = new ItemStack(Items.LAPIS_BLOCK);
    private static final ItemStack DIAMOND_BLOCK  = new ItemStack(Items.DIAMOND_BLOCK);
    private static final ItemStack QUARTZ_BLOCK   = new ItemStack(Items.QUARTZ_BLOCK);
    private static final ItemStack GLASS          = new ItemStack(Items.GLASS);
    private static final ItemStack GLASS_PANE     = new ItemStack(Items.GLASS_PANE);
    private static final ItemStack IRON_BARS      = new ItemStack(Items.IRON_BARS);
    private static final ItemStack CHAIN          = new ItemStack(Items.CHAIN);
    private static final ItemStack LIGHTNING_ROD  = new ItemStack(Items.LIGHTNING_ROD);
    private static final ItemStack HEAVY_WEIGHTED_PRESSURE_PLATE = new ItemStack(Items.HEAVY_WEIGHTED_PRESSURE_PLATE);
    private static final ItemStack LIGHT_WEIGHTED_PRESSURE_PLATE = new ItemStack(Items.LIGHT_WEIGHTED_PRESSURE_PLATE);
    private static final ItemStack SAND           = new ItemStack(Items.SAND);

    // Redstone & components
    private static final ItemStack REDSTONE_TORCH = new ItemStack(Items.REDSTONE_TORCH);
    private static final ItemStack REPEATER       = new ItemStack(Items.REPEATER);
    private static final ItemStack COMPARATOR     = new ItemStack(Items.COMPARATOR);
    private static final ItemStack PISTON         = new ItemStack(Items.PISTON);
    private static final ItemStack STICKY_PISTON  = new ItemStack(Items.STICKY_PISTON);
    private static final ItemStack HOPPER         = new ItemStack(Items.HOPPER);
    private static final ItemStack DROPPER        = new ItemStack(Items.DROPPER);
    private static final ItemStack DISPENSER      = new ItemStack(Items.DISPENSER);
    private static final ItemStack OBSERVER       = new ItemStack(Items.OBSERVER);
    private static final ItemStack CLOCK          = new ItemStack(Items.CLOCK);
    private static final ItemStack COMPASS        = new ItemStack(Items.COMPASS);
    private static final ItemStack SPYGLASS       = new ItemStack(Items.SPYGLASS);

    // Advanced items
    private static final ItemStack ENDER_PEARL    = new ItemStack(Items.ENDER_PEARL);
    private static final ItemStack ENDER_EYE      = new ItemStack(Items.ENDER_EYE);
    private static final ItemStack NETHER_STAR    = new ItemStack(Items.NETHER_STAR);
    private static final ItemStack BEACON         = new ItemStack(Items.BEACON);
    private static final ItemStack CONDUIT        = new ItemStack(Items.CONDUIT);
    private static final ItemStack ENCHANTING_TABLE = new ItemStack(Items.ENCHANTING_TABLE);
    private static final ItemStack ANVIL          = new ItemStack(Items.ANVIL);
    private static final ItemStack BREWING_STAND  = new ItemStack(Items.BREWING_STAND);
    private static final ItemStack END_CRYSTAL    = new ItemStack(Items.END_CRYSTAL);
    private static final ItemStack RESPAWN_ANCHOR = new ItemStack(Items.RESPAWN_ANCHOR);

    // Extra items for expanded chain
    private static final ItemStack OBSIDIAN       = new ItemStack(Items.OBSIDIAN);
    private static final ItemStack BOOK           = new ItemStack(Items.BOOK);
    private static final ItemStack HEART_SEA      = new ItemStack(Items.HEART_OF_THE_SEA);
    private static final ItemStack NAUTILUS       = new ItemStack(Items.NAUTILUS_SHELL);
    private static final ItemStack GHAST_TEAR     = new ItemStack(Items.GHAST_TEAR);
    private static final ItemStack CRYING_OBS     = new ItemStack(Items.CRYING_OBSIDIAN);
    private static final ItemStack OAK_PLANKS     = new ItemStack(Items.OAK_PLANKS);
    private static final ItemStack STICK          = new ItemStack(Items.STICK);
    private static final ItemStack STONE          = new ItemStack(Items.STONE);
    private static final ItemStack CHEST          = new ItemStack(Items.CHEST);
    private static final ItemStack BOW            = new ItemStack(Items.BOW);
    private static final ItemStack EMERALD        = new ItemStack(Items.EMERALD);
    private static final ItemStack EMERALD_BLOCK  = new ItemStack(Items.EMERALD_BLOCK);
    private static final ItemStack COPPER_RAW     = new ItemStack(Items.RAW_COPPER);
    private static final ItemStack DRAGON_HEAD    = new ItemStack(Items.DRAGON_HEAD);
    private static final ItemStack WITHER_ROSE    = new ItemStack(Items.WITHER_ROSE);
    private static final ItemStack SCULK_SENSOR   = new ItemStack(Items.SCULK_SENSOR);
    private static final ItemStack SCULK_CATALYST = new ItemStack(Items.SCULK_CATALYST);
    private static final ItemStack SCULK_SHRIEKER = new ItemStack(Items.SCULK_SHRIEKER);
    private static final ItemStack REINFORCED_DEEPSLATE = new ItemStack(Items.REINFORCED_DEEPSLATE);
    private static final ItemStack RECOVERY_COMPASS = new ItemStack(Items.RECOVERY_COMPASS);
    private static final ItemStack LODESTONE      = new ItemStack(Items.LODESTONE);
    private static final ItemStack DAYLIGHT_DET   = new ItemStack(Items.DAYLIGHT_DETECTOR);
    private static final ItemStack TARGET_BLOCK   = new ItemStack(Items.TARGET);
    private static final ItemStack TNT            = new ItemStack(Items.TNT);
    private static final ItemStack FIREWORK       = new ItemStack(Items.FIREWORK_ROCKET);
    private static final ItemStack PAPER          = new ItemStack(Items.PAPER);
    private static final ItemStack LEATHER        = new ItemStack(Items.LEATHER);
    private static final ItemStack SHIELD         = new ItemStack(Items.SHIELD);
    private static final ItemStack CROSSBOW       = new ItemStack(Items.CROSSBOW);
    private static final ItemStack TRIPWIRE_HOOK  = new ItemStack(Items.TRIPWIRE_HOOK);
    private static final ItemStack TRAPPED_CHEST  = new ItemStack(Items.TRAPPED_CHEST);
    private static final ItemStack RAIL           = new ItemStack(Items.RAIL);
    private static final ItemStack POWERED_RAIL   = new ItemStack(Items.POWERED_RAIL);
    private static final ItemStack DETECTOR_RAIL  = new ItemStack(Items.DETECTOR_RAIL);
    private static final ItemStack ACTIVATOR_RAIL = new ItemStack(Items.ACTIVATOR_RAIL);
    private static final ItemStack MINECART       = new ItemStack(Items.MINECART);
    private static final ItemStack HOPPER_MINECART= new ItemStack(Items.HOPPER_MINECART);
    private static final ItemStack TNT_MINECART   = new ItemStack(Items.TNT_MINECART);
    private static final ItemStack CHEST_MINECART = new ItemStack(Items.CHEST_MINECART);

    // Aggregator module outputs
    private static final ItemStack REDSTONE_BLOCK  = new ItemStack(Items.REDSTONE_BLOCK);
    private static final ItemStack ENDER_CHEST     = new ItemStack(Items.ENDER_CHEST);
    private static final ItemStack NETHERITE_BLOCK = new ItemStack(Items.NETHERITE_BLOCK);
    private static final ItemStack TOTEM           = new ItemStack(Items.TOTEM_OF_UNDYING);

    // Fluids
    private static final FluidStack WATER = new FluidStack(Fluids.WATER, 1000);
    private static final FluidStack LAVA  = new FluidStack(Fluids.LAVA, 1000);

    // ─────────── Graph construction ───────────

    /**
     * Creates a massive 90+ node test graph with 200+ edges.
     * 10 stages of processing, heavy fan-out/fan-in, shared intermediates,
     * cross-layer long edges, probabilistic outputs, catalysts, and parallel branches.
     */
    public static ProcessGraph createComplexGraph() {
        var g = new ProcessGraph();

        // ═══════════════════════════════════════════════════════
        //  STAGE 0 — Raw Material Extraction  (10 nodes)
        // ═══════════════════════════════════════════════════════

        g.addNode(node("ore_iron", "Iron Ore Crushing", "Macerator",
                List.of(MaterialSlot.item(IRON_ORE, 1)),
                List.of(MaterialSlot.item(RAW_IRON, 2),
                        MaterialSlot.item(COBBLESTONE, 1, 0.33)),
                100, 8, 1,
                List.of(new MachineInfo("Macerator", new ItemStack(Items.STONE)),
                        new MachineInfo("Ore Washer", new ItemStack(Items.CAULDRON)),
                        new MachineInfo("Hammer", new ItemStack(Items.IRON_PICKAXE)))));

        g.addNode(node("ore_copper", "Copper Ore Crushing", "Macerator",
                List.of(MaterialSlot.item(COPPER_ORE, 1)),
                List.of(MaterialSlot.item(RAW_COPPER, 2),
                        MaterialSlot.item(GOLD_NUGGET, 1, 0.10)),
                100, 8, 1,
                List.of(new MachineInfo("Macerator", new ItemStack(Items.STONE)),
                        new MachineInfo("Pulverizer", new ItemStack(Items.PISTON)))));

        g.addNode(node("ore_gold", "Gold Ore Crushing", "Macerator",
                List.of(MaterialSlot.item(GOLD_ORE, 1)),
                List.of(MaterialSlot.item(RAW_GOLD, 2),
                        MaterialSlot.item(PRISMARINE_SHARD, 1, 0.08)),
                100, 8, 1));

        g.addNode(node("ore_coal", "Coal Ore Processing", "Macerator",
                List.of(MaterialSlot.item(COAL_ORE, 1)),
                List.of(MaterialSlot.item(COAL, 3),
                        MaterialSlot.item(DIAMOND, 1, 0.02)),
                100, 8, 1));

        g.addNode(node("ore_redstone", "Redstone Ore Crushing", "Macerator",
                List.of(MaterialSlot.item(REDSTONE_ORE, 1)),
                List.of(MaterialSlot.item(REDSTONE, 6),
                        MaterialSlot.item(GLOWSTONE_DUST, 1, 0.15)),
                120, 8, 1));

        g.addNode(node("ore_lapis", "Lapis Lazuli Mining", "Macerator",
                List.of(MaterialSlot.item(LAPIS_ORE, 1)),
                List.of(MaterialSlot.item(LAPIS, 8),
                        MaterialSlot.item(AMETHYST_SHARD, 1, 0.12)),
                100, 8, 1));

        g.addNode(node("ore_diamond", "Diamond Ore Extraction", "Macerator",
                List.of(MaterialSlot.item(DIAMOND_ORE, 1)),
                List.of(MaterialSlot.item(DIAMOND, 2),
                        MaterialSlot.item(COAL, 1, 0.20)),
                200, 32, 2));

        g.addNode(node("ore_quartz", "Nether Quartz Mining", "Macerator",
                List.of(MaterialSlot.item(QUARTZ_ORE, 1)),
                List.of(MaterialSlot.item(QUARTZ, 4),
                        MaterialSlot.item(NETHERITE_SCRAP, 1, 0.05)),
                150, 16, 2));

        g.addNode(node("ore_emerald", "Emerald Ore Extraction", "Precision Drill",
                List.of(MaterialSlot.item(new ItemStack(Items.EMERALD_ORE), 1)),
                List.of(MaterialSlot.item(EMERALD, 2),
                        MaterialSlot.item(AMETHYST_SHARD, 1, 0.18)),
                250, 32, 2));

        g.addNode(node("sand_quarry", "Sand Quarry Operation", "Quarry",
                List.of(MaterialSlot.energy("fe",500)),
                List.of(MaterialSlot.item(SAND, 16),
                        MaterialSlot.item(CLAY_BALL, 4, 0.30),
                        MaterialSlot.item(FLINT, 2, 0.20)),
                80, 4, 1));

        // ═══════════════════════════════════════════════════════
        //  STAGE 1 — Primary Processing  (9 nodes)
        // ═══════════════════════════════════════════════════════

        g.addNode(node("wash_iron", "Iron Ore Washing", "Ore Washer",
                List.of(MaterialSlot.item(RAW_IRON, 2),
                        MaterialSlot.fluid(WATER, 1000)),
                List.of(MaterialSlot.item(IRON_NUGGET, 18),
                        MaterialSlot.item(CLAY_BALL, 2),
                        MaterialSlot.item(FLINT, 1, 0.25)),
                200, 16, 1));

        g.addNode(node("wash_copper", "Copper Ore Washing", "Ore Washer",
                List.of(MaterialSlot.item(RAW_COPPER, 2),
                        MaterialSlot.fluid(WATER, 1000)),
                List.of(MaterialSlot.item(COPPER_INGOT, 2),
                        MaterialSlot.item(CLAY_BALL, 1)),
                200, 16, 1));

        g.addNode(node("wash_gold", "Gold Ore Refining", "Chemical Bath",
                List.of(MaterialSlot.item(RAW_GOLD, 2),
                        MaterialSlot.fluid(LAVA, 500)),
                List.of(MaterialSlot.item(GOLD_NUGGET, 24),
                        MaterialSlot.item(NETHER_BRICK, 2, 0.30)),
                300, 32, 2));

        g.addNode(node("process_coal", "Coal Coking", "Coke Oven",
                List.of(MaterialSlot.item(COAL, 4)),
                List.of(MaterialSlot.item(CHARCOAL, 4),
                        MaterialSlot.item(GUNPOWDER, 2),
                        MaterialSlot.item(BLAZE_POWDER, 1, 0.15)),
                400, 16, 1));

        g.addNode(node("centrifuge_redstone", "Redstone Centrifuging", "Centrifuge",
                List.of(MaterialSlot.item(REDSTONE, 8)),
                List.of(MaterialSlot.item(GLOWSTONE_DUST, 3),
                        MaterialSlot.item(SUGAR, 2, 0.20)),
                300, 32, 2));

        g.addNode(node("enrich_lapis", "Lapis Enrichment", "Chemical Reactor",
                List.of(MaterialSlot.item(LAPIS, 8),
                        MaterialSlot.fluid(WATER, 500)),
                List.of(MaterialSlot.item(LAPIS_BLOCK, 2),
                        MaterialSlot.item(BONE_MEAL, 4)),
                250, 16, 1));

        g.addNode(node("cut_quartz", "Quartz Cutting", "Cutting Machine",
                List.of(MaterialSlot.item(QUARTZ, 4),
                        MaterialSlot.fluid(WATER, 200)),
                List.of(MaterialSlot.item(QUARTZ_BLOCK, 2),
                        MaterialSlot.item(AMETHYST_SHARD, 2)),
                200, 16, 2));

        g.addNode(node("pulp_wood", "Wood Pulping", "Pulverizer",
                List.of(MaterialSlot.item(OAK_PLANKS, 4),
                        MaterialSlot.fluid(WATER, 500)),
                List.of(MaterialSlot.item(PAPER, 6),
                        MaterialSlot.item(SUGAR, 1, 0.10)),
                150, 8, 1));

        g.addNode(node("emerald_polish", "Emerald Polishing", "Gem Polisher",
                List.of(MaterialSlot.item(EMERALD, 4),
                        MaterialSlot.catalyst(SAND, 1)),
                List.of(MaterialSlot.item(EMERALD, 5)),
                300, 32, 2));

        // ═══════════════════════════════════════════════════════
        //  STAGE 2 — Smelting & Alloying  (10 nodes)
        // ═══════════════════════════════════════════════════════

        g.addNode(node("smelt_iron", "Iron Ingot Smelting", "Blast Furnace",
                List.of(MaterialSlot.item(IRON_NUGGET, 9),
                        MaterialSlot.catalyst(CHARCOAL, 1)),
                List.of(MaterialSlot.item(IRON_INGOT, 1)),
                200, 16, 1));

        g.addNode(node("smelt_gold", "Gold Ingot Smelting", "Blast Furnace",
                List.of(MaterialSlot.item(GOLD_NUGGET, 9)),
                List.of(MaterialSlot.item(GOLD_INGOT, 1)),
                200, 16, 1));

        g.addNode(node("compress_iron", "Iron Block Pressing", "Compressor",
                List.of(MaterialSlot.item(IRON_INGOT, 9)),
                List.of(MaterialSlot.item(IRON_BLOCK, 1)),
                300, 32, 2));

        g.addNode(node("compress_gold", "Gold Block Pressing", "Compressor",
                List.of(MaterialSlot.item(GOLD_INGOT, 9)),
                List.of(MaterialSlot.item(GOLD_BLOCK, 1)),
                300, 32, 2));

        g.addNode(node("compress_diamond", "Diamond Block Pressing", "Compressor",
                List.of(MaterialSlot.item(DIAMOND, 9)),
                List.of(MaterialSlot.item(DIAMOND_BLOCK, 1)),
                400, 64, 3));

        g.addNode(node("compress_emerald", "Emerald Block Pressing", "Compressor",
                List.of(MaterialSlot.item(EMERALD, 9)),
                List.of(MaterialSlot.item(EMERALD_BLOCK, 1)),
                400, 64, 3));

        g.addNode(node("glass_smelting", "Glass Production", "Alloy Smelter",
                List.of(MaterialSlot.item(SAND, 4),
                        MaterialSlot.item(COAL, 1)),
                List.of(MaterialSlot.item(GLASS, 4)),
                100, 8, 1));

        g.addNode(node("brick_firing", "Brick Firing", "Alloy Smelter",
                List.of(MaterialSlot.item(CLAY_BALL, 4),
                        MaterialSlot.item(CHARCOAL, 1)),
                List.of(MaterialSlot.item(BRICK, 4)),
                150, 8, 1));

        g.addNode(node("alloy_netherite", "Netherite Alloying", "Electric Blast Furnace",
                List.of(MaterialSlot.item(NETHERITE_SCRAP, 4),
                        MaterialSlot.item(GOLD_INGOT, 4)),
                List.of(MaterialSlot.item(NETHERITE_INGOT, 1)),
                1200, 128, 4));

        g.addNode(node("obsidian_crystallize", "Obsidian Crystallization", "Crystallizer",
                List.of(MaterialSlot.fluid(WATER, 1000),
                        MaterialSlot.fluid(LAVA, 1000)),
                List.of(MaterialSlot.item(OBSIDIAN, 4),
                        MaterialSlot.item(CRYING_OBS, 1, 0.08)),
                600, 64, 3));

        // ═══════════════════════════════════════════════════════
        //  STAGE 3 — Material Forming  (10 nodes)
        // ═══════════════════════════════════════════════════════

        g.addNode(node("wire_iron", "Iron Wire Drawing", "Wiremill",
                List.of(MaterialSlot.item(IRON_INGOT, 1)),
                List.of(MaterialSlot.item(IRON_BARS, 4)),
                100, 8, 1));

        g.addNode(node("chain_iron", "Iron Chain Forming", "Wiremill",
                List.of(MaterialSlot.item(IRON_BARS, 2),
                        MaterialSlot.item(IRON_NUGGET, 4)),
                List.of(MaterialSlot.item(CHAIN, 4)),
                150, 16, 1));

        g.addNode(node("rod_copper", "Copper Rod Lathing", "Lathe",
                List.of(MaterialSlot.item(COPPER_INGOT, 2)),
                List.of(MaterialSlot.item(LIGHTNING_ROD, 2)),
                120, 16, 1));

        g.addNode(node("plate_iron", "Iron Plate Bending", "Bending Machine",
                List.of(MaterialSlot.item(IRON_INGOT, 2)),
                List.of(MaterialSlot.item(HEAVY_WEIGHTED_PRESSURE_PLATE, 1)),
                150, 16, 1));

        g.addNode(node("plate_gold", "Gold Plate Bending", "Bending Machine",
                List.of(MaterialSlot.item(GOLD_INGOT, 2)),
                List.of(MaterialSlot.item(LIGHT_WEIGHTED_PRESSURE_PLATE, 1)),
                150, 16, 2));

        g.addNode(node("cut_glass", "Glass Pane Cutting", "Cutting Machine",
                List.of(MaterialSlot.item(GLASS, 3),
                        MaterialSlot.fluid(WATER, 100)),
                List.of(MaterialSlot.item(GLASS_PANE, 12)),
                100, 8, 1));

        g.addNode(node("form_copper_block", "Copper Block Pressing", "Compressor",
                List.of(MaterialSlot.item(COPPER_INGOT, 9)),
                List.of(MaterialSlot.item(COPPER_BLOCK, 1)),
                200, 16, 1));

        g.addNode(node("rail_forming", "Rail Forming", "Wiremill",
                List.of(MaterialSlot.item(IRON_INGOT, 3),
                        MaterialSlot.item(STICK, 1)),
                List.of(MaterialSlot.item(RAIL, 16)),
                200, 16, 1));

        g.addNode(node("leather_tanning", "Leather Tanning", "Chemical Bath",
                List.of(MaterialSlot.item(new ItemStack(Items.RABBIT_HIDE), 4),
                        MaterialSlot.fluid(WATER, 500)),
                List.of(MaterialSlot.item(LEATHER, 4)),
                300, 16, 1));

        g.addNode(node("string_spinning", "String Spinning", "Wiremill",
                List.of(MaterialSlot.item(new ItemStack(Items.COBWEB), 2)),
                List.of(MaterialSlot.item(new ItemStack(Items.STRING), 8)),
                100, 8, 1));

        // ═══════════════════════════════════════════════════════
        //  STAGE 4 — Redstone Engineering  (8 nodes)
        // ═══════════════════════════════════════════════════════

        g.addNode(node("redstone_torch", "Redstone Torch Assembly", "Assembler",
                List.of(MaterialSlot.item(REDSTONE, 1),
                        MaterialSlot.item(STICK, 1)),
                List.of(MaterialSlot.item(REDSTONE_TORCH, 2)),
                60, 8, 1));

        g.addNode(node("repeater_asm", "Repeater Assembly", "Assembler",
                List.of(MaterialSlot.item(REDSTONE_TORCH, 2),
                        MaterialSlot.item(REDSTONE, 1),
                        MaterialSlot.item(STONE, 3)),
                List.of(MaterialSlot.item(REPEATER, 1)),
                150, 16, 1));

        g.addNode(node("comparator_asm", "Comparator Assembly", "Assembler",
                List.of(MaterialSlot.item(REDSTONE_TORCH, 3),
                        MaterialSlot.item(QUARTZ, 1),
                        MaterialSlot.item(STONE, 3)),
                List.of(MaterialSlot.item(COMPARATOR, 1)),
                200, 16, 2));

        g.addNode(node("piston_asm", "Piston Assembly", "Assembler",
                List.of(MaterialSlot.item(IRON_INGOT, 1),
                        MaterialSlot.item(REDSTONE, 1),
                        MaterialSlot.item(COBBLESTONE, 4),
                        MaterialSlot.item(OAK_PLANKS, 3)),
                List.of(MaterialSlot.item(PISTON, 1)),
                200, 16, 1));

        g.addNode(node("sticky_piston", "Sticky Piston Assembly", "Assembler",
                List.of(MaterialSlot.item(PISTON, 1),
                        MaterialSlot.item(SLIME_BALL, 1)),
                List.of(MaterialSlot.item(STICKY_PISTON, 1)),
                100, 8, 1));

        g.addNode(node("observer_asm", "Observer Assembly", "Assembler",
                List.of(MaterialSlot.item(COBBLESTONE, 6),
                        MaterialSlot.item(REDSTONE, 2),
                        MaterialSlot.item(QUARTZ, 1)),
                List.of(MaterialSlot.item(OBSERVER, 1)),
                200, 16, 2));

        g.addNode(node("daylight_det_asm", "Daylight Detector Assembly", "Assembler",
                List.of(MaterialSlot.item(GLASS, 3),
                        MaterialSlot.item(QUARTZ, 3),
                        MaterialSlot.item(OAK_PLANKS, 3)),
                List.of(MaterialSlot.item(DAYLIGHT_DET, 1)),
                200, 16, 2));

        g.addNode(node("tripwire_asm", "Tripwire Hook Crafting", "Assembler",
                List.of(MaterialSlot.item(IRON_INGOT, 1),
                        MaterialSlot.item(STICK, 1),
                        MaterialSlot.item(OAK_PLANKS, 1)),
                List.of(MaterialSlot.item(TRIPWIRE_HOOK, 2)),
                80, 8, 1));

        // ═══════════════════════════════════════════════════════
        //  STAGE 5 — Component Assembly  (12 nodes)
        // ═══════════════════════════════════════════════════════

        g.addNode(node("hopper_asm", "Hopper Assembly", "Assembler",
                List.of(MaterialSlot.item(IRON_INGOT, 5),
                        MaterialSlot.item(CHEST, 1)),
                List.of(MaterialSlot.item(HOPPER, 1)),
                200, 16, 1));

        g.addNode(node("dropper_asm", "Dropper Assembly", "Assembler",
                List.of(MaterialSlot.item(COBBLESTONE, 7),
                        MaterialSlot.item(REDSTONE, 1)),
                List.of(MaterialSlot.item(DROPPER, 1)),
                200, 16, 1));

        g.addNode(node("dispenser_asm", "Dispenser Assembly", "Assembler",
                List.of(MaterialSlot.item(DROPPER, 1),
                        MaterialSlot.item(BOW, 1)),
                List.of(MaterialSlot.item(DISPENSER, 1)),
                300, 32, 2));

        g.addNode(node("clock_asm", "Clock Precision Assembly", "Precision Assembler",
                List.of(MaterialSlot.item(GOLD_INGOT, 4),
                        MaterialSlot.item(REDSTONE, 1)),
                List.of(MaterialSlot.item(CLOCK, 1)),
                300, 32, 2));

        g.addNode(node("compass_asm", "Compass Assembly", "Precision Assembler",
                List.of(MaterialSlot.item(IRON_INGOT, 4),
                        MaterialSlot.item(REDSTONE, 1)),
                List.of(MaterialSlot.item(COMPASS, 1)),
                300, 32, 2));

        g.addNode(node("spyglass_asm", "Spyglass Crafting", "Precision Assembler",
                List.of(MaterialSlot.item(COPPER_INGOT, 2),
                        MaterialSlot.item(AMETHYST_SHARD, 1)),
                List.of(MaterialSlot.item(SPYGLASS, 1)),
                200, 16, 2));

        g.addNode(node("ender_eye_asm", "Eye of Ender Synthesis", "Chemical Reactor",
                List.of(MaterialSlot.item(ENDER_PEARL, 1),
                        MaterialSlot.item(BLAZE_POWDER, 1)),
                List.of(MaterialSlot.item(ENDER_EYE, 1)),
                400, 64, 3));

        g.addNode(node("brewing_stand_asm", "Brewing Stand Assembly", "Assembler",
                List.of(MaterialSlot.item(BLAZE_ROD, 1),
                        MaterialSlot.item(COBBLESTONE, 3)),
                List.of(MaterialSlot.item(BREWING_STAND, 1)),
                200, 32, 2));

        g.addNode(node("tnt_asm", "TNT Production", "Chemical Mixer",
                List.of(MaterialSlot.item(GUNPOWDER, 5),
                        MaterialSlot.item(SAND, 4)),
                List.of(MaterialSlot.item(TNT, 1)),
                200, 16, 1));

        g.addNode(node("target_block_asm", "Target Block Crafting", "Assembler",
                List.of(MaterialSlot.item(REDSTONE, 4),
                        MaterialSlot.item(new ItemStack(Items.HAY_BLOCK), 1)),
                List.of(MaterialSlot.item(TARGET_BLOCK, 1)),
                150, 16, 1));

        g.addNode(node("book_binding", "Book Binding", "Assembler",
                List.of(MaterialSlot.item(PAPER, 3),
                        MaterialSlot.item(LEATHER, 1)),
                List.of(MaterialSlot.item(BOOK, 1)),
                100, 8, 1));

        g.addNode(node("minecart_asm", "Minecart Forging", "Assembler",
                List.of(MaterialSlot.item(IRON_INGOT, 5)),
                List.of(MaterialSlot.item(MINECART, 1)),
                200, 16, 1));

        // ═══════════════════════════════════════════════════════
        //  STAGE 6 — Advanced Assembly  (12 nodes)
        // ═══════════════════════════════════════════════════════

        g.addNode(node("anvil_asm", "Anvil Forging", "Assembly Line",
                List.of(MaterialSlot.item(IRON_BLOCK, 3),
                        MaterialSlot.item(IRON_INGOT, 4)),
                List.of(MaterialSlot.item(ANVIL, 1)),
                600, 32, 2));

        g.addNode(node("enchant_table", "Enchanting Table Construction", "Assembly Line",
                List.of(MaterialSlot.item(DIAMOND, 2),
                        MaterialSlot.item(OBSIDIAN, 4),
                        MaterialSlot.item(BOOK, 1)),
                List.of(MaterialSlot.item(ENCHANTING_TABLE, 1)),
                800, 64, 3));

        g.addNode(node("beacon_asm", "Beacon Construction", "Assembly Line",
                List.of(MaterialSlot.item(NETHER_STAR, 1),
                        MaterialSlot.item(GLASS, 5),
                        MaterialSlot.item(OBSIDIAN, 3)),
                List.of(MaterialSlot.item(BEACON, 1)),
                1200, 128, 4));

        g.addNode(node("conduit_asm", "Conduit Construction", "Assembly Line",
                List.of(MaterialSlot.item(HEART_SEA, 1),
                        MaterialSlot.item(NAUTILUS, 8)),
                List.of(MaterialSlot.item(CONDUIT, 1)),
                1000, 128, 4));

        g.addNode(node("end_crystal_asm", "End Crystal Synthesis", "Chemical Reactor",
                List.of(MaterialSlot.item(ENDER_EYE, 1),
                        MaterialSlot.item(GHAST_TEAR, 1),
                        MaterialSlot.item(GLASS, 7)),
                List.of(MaterialSlot.item(END_CRYSTAL, 1)),
                600, 64, 3));

        g.addNode(node("lodestone_asm", "Lodestone Fabrication", "Assembly Line",
                List.of(MaterialSlot.item(new ItemStack(Items.CHISELED_STONE_BRICKS), 8),
                        MaterialSlot.item(NETHERITE_INGOT, 1)),
                List.of(MaterialSlot.item(LODESTONE, 1)),
                800, 64, 3));

        g.addNode(node("recovery_compass_asm", "Recovery Compass Assembly", "Precision Assembler",
                List.of(MaterialSlot.item(COMPASS, 1),
                        MaterialSlot.item(ECHO_SHARD, 8)),
                List.of(MaterialSlot.item(RECOVERY_COMPASS, 1)),
                600, 64, 3));

        g.addNode(node("shield_asm", "Shield Crafting", "Assembler",
                List.of(MaterialSlot.item(OAK_PLANKS, 6),
                        MaterialSlot.item(IRON_INGOT, 1)),
                List.of(MaterialSlot.item(SHIELD, 1)),
                200, 16, 1));

        g.addNode(node("crossbow_asm", "Crossbow Assembly", "Assembler",
                List.of(MaterialSlot.item(STICK, 3),
                        MaterialSlot.item(IRON_INGOT, 1),
                        MaterialSlot.item(new ItemStack(Items.STRING), 2),
                        MaterialSlot.item(TRIPWIRE_HOOK, 1)),
                List.of(MaterialSlot.item(CROSSBOW, 1)),
                300, 16, 2));

        g.addNode(node("trapped_chest_asm", "Trapped Chest Assembly", "Assembler",
                List.of(MaterialSlot.item(CHEST, 1),
                        MaterialSlot.item(TRIPWIRE_HOOK, 1)),
                List.of(MaterialSlot.item(TRAPPED_CHEST, 1)),
                100, 8, 1));

        g.addNode(node("powered_rail_asm", "Powered Rail Crafting", "Assembler",
                List.of(MaterialSlot.item(GOLD_INGOT, 6),
                        MaterialSlot.item(STICK, 1),
                        MaterialSlot.item(REDSTONE, 1)),
                List.of(MaterialSlot.item(POWERED_RAIL, 6)),
                200, 16, 2));

        g.addNode(node("firework_asm", "Firework Rocket Assembly", "Chemical Mixer",
                List.of(MaterialSlot.item(GUNPOWDER, 3),
                        MaterialSlot.item(PAPER, 1),
                        MaterialSlot.item(GLOWSTONE_DUST, 2)),
                List.of(MaterialSlot.item(FIREWORK, 3)),
                150, 16, 1));

        // ═══════════════════════════════════════════════════════
        //  STAGE 7 — Sculk & Rail Systems  (8 nodes)
        // ═══════════════════════════════════════════════════════

        g.addNode(node("sculk_sensor_asm", "Sculk Sensor Cultivation", "Bio Reactor",
                List.of(MaterialSlot.item(ECHO_SHARD, 2),
                        MaterialSlot.item(REDSTONE, 4),
                        MaterialSlot.item(AMETHYST_SHARD, 2)),
                List.of(MaterialSlot.item(SCULK_SENSOR, 1)),
                800, 64, 3));

        g.addNode(node("sculk_catalyst_asm", "Sculk Catalyst Growth", "Bio Reactor",
                List.of(MaterialSlot.item(SCULK_SENSOR, 1),
                        MaterialSlot.item(BONE_MEAL, 8),
                        MaterialSlot.item(ECHO_SHARD, 4)),
                List.of(MaterialSlot.item(SCULK_CATALYST, 1)),
                1200, 128, 4));

        g.addNode(node("sculk_shrieker_asm", "Sculk Shrieker Fabrication", "Bio Reactor",
                List.of(MaterialSlot.item(SCULK_CATALYST, 1),
                        MaterialSlot.item(AMETHYST_SHARD, 4),
                        MaterialSlot.item(ECHO_SHARD, 2)),
                List.of(MaterialSlot.item(SCULK_SHRIEKER, 1)),
                1600, 256, 5));

        g.addNode(node("detector_rail_asm", "Detector Rail Crafting", "Assembler",
                List.of(MaterialSlot.item(IRON_INGOT, 6),
                        MaterialSlot.item(STONE, 1),
                        MaterialSlot.item(REDSTONE, 1)),
                List.of(MaterialSlot.item(DETECTOR_RAIL, 6)),
                200, 16, 1));

        g.addNode(node("activator_rail_asm", "Activator Rail Crafting", "Assembler",
                List.of(MaterialSlot.item(IRON_INGOT, 6),
                        MaterialSlot.item(STICK, 2),
                        MaterialSlot.item(REDSTONE_TORCH, 1)),
                List.of(MaterialSlot.item(ACTIVATOR_RAIL, 6)),
                200, 16, 1));

        g.addNode(node("hopper_minecart_asm", "Hopper Minecart Assembly", "Assembler",
                List.of(MaterialSlot.item(HOPPER, 1),
                        MaterialSlot.item(MINECART, 1)),
                List.of(MaterialSlot.item(HOPPER_MINECART, 1)),
                200, 16, 2));

        g.addNode(node("tnt_minecart_asm", "TNT Minecart Assembly", "Assembler",
                List.of(MaterialSlot.item(TNT, 1),
                        MaterialSlot.item(MINECART, 1)),
                List.of(MaterialSlot.item(TNT_MINECART, 1)),
                200, 16, 2));

        g.addNode(node("chest_minecart_asm", "Chest Minecart Assembly", "Assembler",
                List.of(MaterialSlot.item(CHEST, 1),
                        MaterialSlot.item(MINECART, 1)),
                List.of(MaterialSlot.item(CHEST_MINECART, 1)),
                200, 16, 2));

        // ═══════════════════════════════════════════════════════
        //  STAGE 8 — High-Tech Integration  (5 nodes)
        // ═══════════════════════════════════════════════════════

        g.addNode(node("respawn_anchor", "Respawn Anchor Fabrication", "Assembly Line",
                List.of(MaterialSlot.item(CRYING_OBS, 6),
                        MaterialSlot.item(GLOWSTONE_DUST, 12),
                        MaterialSlot.fluid(LAVA, 4000)),
                List.of(MaterialSlot.item(RESPAWN_ANCHOR, 1)),
                1600, 256, 5));

        g.addNode(node("nether_star_synth", "Nether Star Synthesis", "Fusion Reactor",
                List.of(MaterialSlot.item(DIAMOND_BLOCK, 1),
                        MaterialSlot.item(NETHERITE_INGOT, 4),
                        MaterialSlot.item(ECHO_SHARD, 8),
                        MaterialSlot.item(BLAZE_POWDER, 16)),
                List.of(MaterialSlot.item(NETHER_STAR, 1)),
                6400, 2048, 7));

        g.addNode(node("reinforced_deep", "Reinforced Deepslate Forging", "Dimensional Forge",
                List.of(MaterialSlot.item(SCULK_SHRIEKER, 2),
                        MaterialSlot.item(NETHERITE_INGOT, 2),
                        MaterialSlot.item(OBSIDIAN, 8),
                        MaterialSlot.item(ECHO_SHARD, 4)),
                List.of(MaterialSlot.item(REINFORCED_DEEPSLATE, 1)),
                3200, 512, 6));

        g.addNode(node("wither_rose_synth", "Wither Rose Synthesis", "Bio Reactor",
                List.of(MaterialSlot.item(WITHER_ROSE, 1),
                        MaterialSlot.item(BONE_MEAL, 16),
                        MaterialSlot.item(NETHER_STAR, 1)),
                List.of(MaterialSlot.item(WITHER_ROSE, 4)),
                2400, 256, 5));

        g.addNode(node("dragon_head_synth", "Dragon Head Reconstruction", "Assembly Line",
                List.of(MaterialSlot.item(OBSIDIAN, 8),
                        MaterialSlot.item(END_CRYSTAL, 4),
                        MaterialSlot.item(ENDER_EYE, 4),
                        MaterialSlot.item(NETHER_STAR, 2)),
                List.of(MaterialSlot.item(DRAGON_HEAD, 1),
                        MaterialSlot.item(ECHO_SHARD, 2, 0.25)),
                4800, 1024, 6));

        // ═══════════════════════════════════════════════════════
        //  STAGE 8.5 — System Integration  (4 aggregator nodes)
        // ═══════════════════════════════════════════════════════

        g.addNode(node("automation_core", "Automation Core Assembly", "Quantum Computer",
                List.of(MaterialSlot.item(REPEATER, 4),
                        MaterialSlot.item(COMPARATOR, 4),
                        MaterialSlot.item(STICKY_PISTON, 2),
                        MaterialSlot.item(OBSERVER, 4),
                        MaterialSlot.item(DAYLIGHT_DET, 2),
                        MaterialSlot.item(TARGET_BLOCK, 1),
                        MaterialSlot.item(LIGHTNING_ROD, 2)),
                List.of(MaterialSlot.item(REDSTONE_BLOCK, 4)),
                2400, 512, 6));

        g.addNode(node("military_array", "Military Array Assembly", "War Factory",
                List.of(MaterialSlot.item(SHIELD, 2),
                        MaterialSlot.item(CROSSBOW, 4),
                        MaterialSlot.item(DISPENSER, 4),
                        MaterialSlot.item(TNT_MINECART, 2),
                        MaterialSlot.item(FIREWORK, 8)),
                List.of(MaterialSlot.item(TOTEM, 2)),
                3200, 512, 6));

        g.addNode(node("logistics_network", "Logistics Network Assembly", "Logistics Hub",
                List.of(MaterialSlot.item(RAIL, 16),
                        MaterialSlot.item(POWERED_RAIL, 8),
                        MaterialSlot.item(DETECTOR_RAIL, 4),
                        MaterialSlot.item(ACTIVATOR_RAIL, 4),
                        MaterialSlot.item(HOPPER_MINECART, 2),
                        MaterialSlot.item(CHEST_MINECART, 2),
                        MaterialSlot.item(TRAPPED_CHEST, 1)),
                List.of(MaterialSlot.item(ENDER_CHEST, 2)),
                2400, 512, 6));

        g.addNode(node("structural_matrix", "Structural Matrix Fabrication", "Matter Compiler",
                List.of(MaterialSlot.item(CHAIN, 8),
                        MaterialSlot.item(HEAVY_WEIGHTED_PRESSURE_PLATE, 4),
                        MaterialSlot.item(LIGHT_WEIGHTED_PRESSURE_PLATE, 4),
                        MaterialSlot.item(GLASS_PANE, 16),
                        MaterialSlot.item(COPPER_BLOCK, 2),
                        MaterialSlot.item(GOLD_BLOCK, 1),
                        MaterialSlot.item(EMERALD_BLOCK, 1),
                        MaterialSlot.item(BRICK, 8),
                        MaterialSlot.item(ANVIL, 1),
                        MaterialSlot.item(BREWING_STAND, 1),
                        MaterialSlot.item(CLOCK, 1),
                        MaterialSlot.item(SPYGLASS, 1),
                        MaterialSlot.item(WITHER_ROSE, 4)),
                List.of(MaterialSlot.item(NETHERITE_BLOCK, 2)),
                4800, 1024, 7));

        // ═══════════════════════════════════════════════════════
        //  STAGE 9 — Ultimate Fabrication  (1 node)
        // ═══════════════════════════════════════════════════════

        g.addNode(node("ultimate_machine", "Matter Fabrication Array", "Dimensional Assembler",
                List.of(MaterialSlot.item(BEACON, 2),
                        MaterialSlot.item(CONDUIT, 1),
                        MaterialSlot.item(DRAGON_HEAD, 1),
                        MaterialSlot.item(REINFORCED_DEEPSLATE, 4),
                        MaterialSlot.item(RESPAWN_ANCHOR, 2),
                        MaterialSlot.item(NETHER_STAR, 4),
                        MaterialSlot.item(SCULK_SHRIEKER, 2),
                        MaterialSlot.item(LODESTONE, 2),
                        MaterialSlot.item(RECOVERY_COMPASS, 1),
                        MaterialSlot.item(ENCHANTING_TABLE, 1),
                        MaterialSlot.item(REDSTONE_BLOCK, 4),
                        MaterialSlot.item(TOTEM, 2),
                        MaterialSlot.item(ENDER_CHEST, 2),
                        MaterialSlot.item(NETHERITE_BLOCK, 2)),
                List.of(MaterialSlot.item(new ItemStack(Items.DRAGON_EGG), 1)),
                25600, 32768, 9,
                List.of(new MachineInfo("Dimensional Assembler", new ItemStack(Items.RESPAWN_ANCHOR)),
                        new MachineInfo("Creative Fabricator", new ItemStack(Items.COMMAND_BLOCK)),
                        new MachineInfo("Quantum Entangler", new ItemStack(Items.END_PORTAL_FRAME)))));

        // ═══════════════════════════════════════════════════════
        //  EDGES — Material Flow Connections  (200+ edges)
        // ═══════════════════════════════════════════════════════

        // ── Stage 0 → Stage 1 ──
        edge(g, "ore_iron",     "wash_iron",            "Raw Iron",     2, MaterialType.ITEM);
        edge(g, "ore_copper",   "wash_copper",          "Raw Copper",   2, MaterialType.ITEM);
        edge(g, "ore_gold",     "wash_gold",            "Raw Gold",     2, MaterialType.ITEM);
        edge(g, "ore_coal",     "process_coal",         "Coal",         4, MaterialType.ITEM);
        edge(g, "ore_redstone", "centrifuge_redstone",  "Redstone",     8, MaterialType.ITEM);
        edge(g, "ore_lapis",    "enrich_lapis",         "Lapis Lazuli", 8, MaterialType.ITEM);
        edge(g, "ore_quartz",   "cut_quartz",           "Quartz",       4, MaterialType.ITEM);
        edge(g, "ore_emerald",  "emerald_polish",       "Emerald",      4, MaterialType.ITEM);
        edge(g, "sand_quarry",  "glass_smelting",       "Sand",         4, MaterialType.ITEM);
        edge(g, "sand_quarry",  "tnt_asm",              "Sand",         4, MaterialType.ITEM);
        edge(g, "sand_quarry",  "brick_firing",         "Clay Ball",    4, MaterialType.ITEM);

        // ── Stage 1 → Stage 2 ──
        edge(g, "wash_iron",    "smelt_iron",       "Iron Nugget",    18, MaterialType.ITEM);
        edge(g, "wash_gold",    "smelt_gold",       "Gold Nugget",    24, MaterialType.ITEM);
        edge(g, "wash_iron",    "brick_firing",     "Clay Ball",       2, MaterialType.ITEM);
        edge(g, "wash_copper",  "brick_firing",     "Clay Ball",       1, MaterialType.ITEM);
        edge(g, "process_coal", "smelt_iron",       "Charcoal",        1, MaterialType.ITEM);
        edge(g, "process_coal", "glass_smelting",   "Coal",            1, MaterialType.ITEM);
        edge(g, "process_coal", "brick_firing",     "Charcoal",        1, MaterialType.ITEM);
        edge(g, "smelt_gold",   "alloy_netherite",  "Gold Ingot",      4, MaterialType.ITEM);
        edge(g, "ore_quartz",   "alloy_netherite",  "Netherite Scrap", 4, MaterialType.ITEM);
        edge(g, "emerald_polish","compress_emerald", "Emerald",        9, MaterialType.ITEM);

        // ── Stage 2 → Stage 3 ──
        edge(g, "smelt_iron",     "wire_iron",          "Iron Ingot",    1, MaterialType.ITEM);
        edge(g, "smelt_iron",     "plate_iron",         "Iron Ingot",    2, MaterialType.ITEM);
        edge(g, "smelt_iron",     "compress_iron",      "Iron Ingot",    9, MaterialType.ITEM);
        edge(g, "smelt_iron",     "rail_forming",       "Iron Ingot",    3, MaterialType.ITEM);
        edge(g, "smelt_gold",     "plate_gold",         "Gold Ingot",    2, MaterialType.ITEM);
        edge(g, "smelt_gold",     "compress_gold",      "Gold Ingot",    9, MaterialType.ITEM);
        edge(g, "wash_copper",    "rod_copper",         "Copper Ingot",  2, MaterialType.ITEM);
        edge(g, "wash_copper",    "form_copper_block",  "Copper Ingot",  9, MaterialType.ITEM);
        edge(g, "glass_smelting", "cut_glass",          "Glass",         3, MaterialType.ITEM);
        edge(g, "ore_diamond",    "compress_diamond",   "Diamond",       9, MaterialType.ITEM);

        // ── Stage 3 → Stage 3 (cross) ──
        edge(g, "wire_iron", "chain_iron", "Iron Bars", 2, MaterialType.ITEM);

        // ── Stage 0/1 → Stage 4 (redstone engineering) ──
        edge(g, "ore_redstone", "redstone_torch",   "Redstone", 1, MaterialType.ITEM);
        edge(g, "ore_redstone", "piston_asm",       "Redstone", 1, MaterialType.ITEM);
        edge(g, "ore_redstone", "observer_asm",     "Redstone", 2, MaterialType.ITEM);
        edge(g, "ore_redstone", "daylight_det_asm", "Quartz",   3, MaterialType.ITEM);
        edge(g, "ore_iron",     "piston_asm",       "Cobblestone", 4, MaterialType.ITEM);
        edge(g, "cut_quartz",   "comparator_asm",   "Quartz",   1, MaterialType.ITEM);
        edge(g, "cut_quartz",   "observer_asm",     "Quartz",   1, MaterialType.ITEM);
        edge(g, "cut_quartz",   "daylight_det_asm", "Quartz",   3, MaterialType.ITEM);
        edge(g, "smelt_iron",   "tripwire_asm",     "Iron Ingot", 1, MaterialType.ITEM);
        edge(g, "glass_smelting","daylight_det_asm", "Glass",    3, MaterialType.ITEM);

        // ── Stage 4 → Stage 4 (chain) ──
        edge(g, "redstone_torch", "repeater_asm",   "Redstone Torch", 2, MaterialType.ITEM);
        edge(g, "redstone_torch", "comparator_asm", "Redstone Torch", 3, MaterialType.ITEM);
        edge(g, "piston_asm",     "sticky_piston",  "Piston",         1, MaterialType.ITEM);
        edge(g, "redstone_torch", "activator_rail_asm", "Redstone Torch", 1, MaterialType.ITEM);

        // ── Stage 2/3/4 → Stage 5 (component assembly) ──
        edge(g, "smelt_iron",   "hopper_asm",    "Iron Ingot",     5, MaterialType.ITEM);
        edge(g, "smelt_iron",   "compass_asm",   "Iron Ingot",     4, MaterialType.ITEM);
        edge(g, "smelt_iron",   "minecart_asm",  "Iron Ingot",     5, MaterialType.ITEM);
        edge(g, "smelt_gold",   "clock_asm",     "Gold Ingot",     4, MaterialType.ITEM);
        edge(g, "wash_copper",  "spyglass_asm",  "Copper Ingot",   2, MaterialType.ITEM);
        edge(g, "cut_quartz",   "spyglass_asm",  "Amethyst Shard", 1, MaterialType.ITEM);
        edge(g, "ore_redstone", "clock_asm",     "Redstone",       1, MaterialType.ITEM);
        edge(g, "ore_redstone", "compass_asm",   "Redstone",       1, MaterialType.ITEM);
        edge(g, "ore_redstone", "dropper_asm",   "Redstone",       1, MaterialType.ITEM);
        edge(g, "ore_redstone", "target_block_asm", "Redstone",    4, MaterialType.ITEM);
        edge(g, "dropper_asm",  "dispenser_asm", "Dropper",        1, MaterialType.ITEM);
        edge(g, "process_coal", "ender_eye_asm",    "Blaze Powder",  1, MaterialType.ITEM);
        edge(g, "process_coal", "brewing_stand_asm", "Blaze Rod",   1, MaterialType.ITEM);
        edge(g, "process_coal", "tnt_asm",        "Gunpowder",      5, MaterialType.ITEM);
        edge(g, "process_coal", "firework_asm",   "Gunpowder",      3, MaterialType.ITEM);
        edge(g, "pulp_wood",    "book_binding",   "Paper",           3, MaterialType.ITEM);
        edge(g, "pulp_wood",    "firework_asm",   "Paper",           1, MaterialType.ITEM);
        edge(g, "leather_tanning","book_binding",  "Leather",        1, MaterialType.ITEM);
        edge(g, "centrifuge_redstone","firework_asm","Glowstone Dust",2, MaterialType.ITEM);

        // ── Stage 5/3/2 → Stage 6 (advanced assembly) ──
        edge(g, "compress_iron",    "anvil_asm",        "Iron Block",    3, MaterialType.ITEM);
        edge(g, "smelt_iron",       "anvil_asm",        "Iron Ingot",    4, MaterialType.ITEM);
        edge(g, "ore_diamond",      "enchant_table",    "Diamond",       2, MaterialType.ITEM);
        edge(g, "obsidian_crystallize","enchant_table",  "Obsidian",     4, MaterialType.ITEM);
        edge(g, "book_binding",     "enchant_table",    "Book",          1, MaterialType.ITEM);
        edge(g, "glass_smelting",   "beacon_asm",       "Glass",         5, MaterialType.ITEM);
        edge(g, "glass_smelting",   "end_crystal_asm",  "Glass",         7, MaterialType.ITEM);
        edge(g, "ender_eye_asm",    "end_crystal_asm",  "Eye of Ender", 1, MaterialType.ITEM);
        edge(g, "obsidian_crystallize","beacon_asm",     "Obsidian",     3, MaterialType.ITEM);
        edge(g, "alloy_netherite",  "lodestone_asm",    "Netherite Ingot", 1, MaterialType.ITEM);
        edge(g, "compass_asm",     "recovery_compass_asm", "Compass",   1, MaterialType.ITEM);
        edge(g, "smelt_iron",      "shield_asm",        "Iron Ingot",   1, MaterialType.ITEM);
        edge(g, "string_spinning", "crossbow_asm",      "String",       2, MaterialType.ITEM);
        edge(g, "tripwire_asm",    "crossbow_asm",      "Tripwire Hook",1, MaterialType.ITEM);
        edge(g, "smelt_iron",      "crossbow_asm",      "Iron Ingot",   1, MaterialType.ITEM);
        edge(g, "tripwire_asm",    "trapped_chest_asm", "Tripwire Hook",1, MaterialType.ITEM);
        edge(g, "smelt_gold",      "powered_rail_asm",  "Gold Ingot",   6, MaterialType.ITEM);
        edge(g, "ore_redstone",    "powered_rail_asm",  "Redstone",     1, MaterialType.ITEM);

        // ── Stage 6 → Stage 7 (sculk & rail systems) ──
        edge(g, "ore_redstone",      "sculk_sensor_asm",    "Redstone",      4, MaterialType.ITEM);
        edge(g, "ore_lapis",         "sculk_sensor_asm",    "Amethyst Shard",2, MaterialType.ITEM);
        edge(g, "sculk_sensor_asm",  "sculk_catalyst_asm",  "Sculk Sensor",  1, MaterialType.ITEM);
        edge(g, "enrich_lapis",      "sculk_catalyst_asm",  "Bone Meal",     8, MaterialType.ITEM);
        edge(g, "sculk_catalyst_asm","sculk_shrieker_asm",  "Sculk Catalyst",1, MaterialType.ITEM);
        edge(g, "smelt_iron",        "detector_rail_asm",   "Iron Ingot",    6, MaterialType.ITEM);
        edge(g, "ore_redstone",      "detector_rail_asm",   "Redstone",      1, MaterialType.ITEM);
        edge(g, "smelt_iron",        "activator_rail_asm",  "Iron Ingot",    6, MaterialType.ITEM);
        edge(g, "hopper_asm",        "hopper_minecart_asm", "Hopper",        1, MaterialType.ITEM);
        edge(g, "minecart_asm",      "hopper_minecart_asm", "Minecart",      1, MaterialType.ITEM);
        edge(g, "tnt_asm",           "tnt_minecart_asm",    "TNT",           1, MaterialType.ITEM);
        edge(g, "minecart_asm",      "tnt_minecart_asm",    "Minecart",      1, MaterialType.ITEM);
        edge(g, "minecart_asm",      "chest_minecart_asm",  "Minecart",      1, MaterialType.ITEM);

        // ── Stage 7/6 → Stage 8 (high-tech) ──
        edge(g, "centrifuge_redstone","respawn_anchor",      "Glowstone Dust",  12, MaterialType.ITEM);
        edge(g, "obsidian_crystallize","respawn_anchor",     "Crying Obsidian",  6, MaterialType.ITEM);
        edge(g, "compress_diamond",   "nether_star_synth",   "Diamond Block",    1, MaterialType.ITEM);
        edge(g, "alloy_netherite",    "nether_star_synth",   "Netherite Ingot",  4, MaterialType.ITEM);
        edge(g, "process_coal",       "nether_star_synth",   "Blaze Powder",    16, MaterialType.ITEM);
        edge(g, "sculk_shrieker_asm", "reinforced_deep",     "Sculk Shrieker",   2, MaterialType.ITEM);
        edge(g, "alloy_netherite",    "reinforced_deep",     "Netherite Ingot",   2, MaterialType.ITEM);
        edge(g, "obsidian_crystallize","reinforced_deep",    "Obsidian",          8, MaterialType.ITEM);
        edge(g, "nether_star_synth",  "wither_rose_synth",   "Nether Star",      1, MaterialType.ITEM);
        edge(g, "enrich_lapis",       "wither_rose_synth",   "Bone Meal",       16, MaterialType.ITEM);
        edge(g, "obsidian_crystallize","dragon_head_synth",  "Obsidian",         8, MaterialType.ITEM);
        edge(g, "end_crystal_asm",    "dragon_head_synth",   "End Crystal",      4, MaterialType.ITEM);
        edge(g, "ender_eye_asm",      "dragon_head_synth",   "Eye of Ender",     4, MaterialType.ITEM);
        edge(g, "nether_star_synth",  "dragon_head_synth",   "Nether Star",      2, MaterialType.ITEM);
        edge(g, "nether_star_synth",  "beacon_asm",          "Nether Star",      1, MaterialType.ITEM);

        // ── Echo Shard cross-layer long edges ──
        edge(g, "ore_diamond",  "sculk_sensor_asm",      "Echo Shard",  2, MaterialType.ITEM);
        edge(g, "ore_diamond",  "sculk_catalyst_asm",    "Echo Shard",  4, MaterialType.ITEM);
        edge(g, "ore_diamond",  "sculk_shrieker_asm",    "Echo Shard",  2, MaterialType.ITEM);
        edge(g, "ore_diamond",  "recovery_compass_asm",  "Echo Shard",  8, MaterialType.ITEM);
        edge(g, "ore_diamond",  "nether_star_synth",     "Echo Shard",  8, MaterialType.ITEM);
        edge(g, "ore_diamond",  "reinforced_deep",       "Echo Shard",  4, MaterialType.ITEM);

        // ── Cross-layer long edges ──
        edge(g, "ore_coal",     "glass_smelting",     "Coal",         1, MaterialType.ITEM);
        edge(g, "ore_iron",     "hopper_asm",         "Cobblestone",  7, MaterialType.ITEM);
        edge(g, "ore_iron",     "observer_asm",       "Cobblestone",  6, MaterialType.ITEM);
        edge(g, "ore_iron",     "dropper_asm",        "Cobblestone",  7, MaterialType.ITEM);
        edge(g, "ore_lapis",    "enchant_table",      "Lapis Block",  2, MaterialType.ITEM);
        edge(g, "enrich_lapis", "spyglass_asm",       "Bone Meal",    2, MaterialType.ITEM);
        edge(g, "sand_quarry",  "emerald_polish",     "Sand",         1, MaterialType.ITEM);

        // ── Dead-end → Aggregators (Stage 8.5) ──
        // → automation_core
        edge(g, "repeater_asm",     "automation_core",   "Repeater",         4, MaterialType.ITEM);
        edge(g, "comparator_asm",   "automation_core",   "Comparator",       4, MaterialType.ITEM);
        edge(g, "sticky_piston",    "automation_core",   "Sticky Piston",    2, MaterialType.ITEM);
        edge(g, "observer_asm",     "automation_core",   "Observer",         4, MaterialType.ITEM);
        edge(g, "daylight_det_asm", "automation_core",   "Daylight Detector",2, MaterialType.ITEM);
        edge(g, "target_block_asm", "automation_core",   "Target Block",     1, MaterialType.ITEM);
        edge(g, "rod_copper",       "automation_core",   "Lightning Rod",    2, MaterialType.ITEM);
        // → military_array
        edge(g, "shield_asm",       "military_array",    "Shield",           2, MaterialType.ITEM);
        edge(g, "crossbow_asm",     "military_array",    "Crossbow",         4, MaterialType.ITEM);
        edge(g, "dispenser_asm",    "military_array",    "Dispenser",        4, MaterialType.ITEM);
        edge(g, "tnt_minecart_asm", "military_array",    "TNT Minecart",     2, MaterialType.ITEM);
        edge(g, "firework_asm",     "military_array",    "Firework Rocket",  8, MaterialType.ITEM);
        // → logistics_network
        edge(g, "rail_forming",       "logistics_network","Rail",           16, MaterialType.ITEM);
        edge(g, "powered_rail_asm",   "logistics_network","Powered Rail",    8, MaterialType.ITEM);
        edge(g, "detector_rail_asm",  "logistics_network","Detector Rail",   4, MaterialType.ITEM);
        edge(g, "activator_rail_asm", "logistics_network","Activator Rail",  4, MaterialType.ITEM);
        edge(g, "hopper_minecart_asm","logistics_network","Hopper Minecart", 2, MaterialType.ITEM);
        edge(g, "chest_minecart_asm", "logistics_network","Chest Minecart",  2, MaterialType.ITEM);
        edge(g, "trapped_chest_asm",  "logistics_network","Trapped Chest",   1, MaterialType.ITEM);
        // → structural_matrix
        edge(g, "chain_iron",       "structural_matrix", "Chain",            8, MaterialType.ITEM);
        edge(g, "plate_iron",       "structural_matrix", "Pressure Plate",   4, MaterialType.ITEM);
        edge(g, "plate_gold",       "structural_matrix", "Light Plate",      4, MaterialType.ITEM);
        edge(g, "cut_glass",        "structural_matrix", "Glass Pane",      16, MaterialType.ITEM);
        edge(g, "form_copper_block","structural_matrix", "Copper Block",     2, MaterialType.ITEM);
        edge(g, "compress_gold",    "structural_matrix", "Gold Block",       1, MaterialType.ITEM);
        edge(g, "compress_emerald", "structural_matrix", "Emerald Block",    1, MaterialType.ITEM);
        edge(g, "brick_firing",     "structural_matrix", "Brick",            8, MaterialType.ITEM);
        edge(g, "anvil_asm",        "structural_matrix", "Anvil",            1, MaterialType.ITEM);
        edge(g, "brewing_stand_asm","structural_matrix", "Brewing Stand",    1, MaterialType.ITEM);
        edge(g, "clock_asm",        "structural_matrix", "Clock",            1, MaterialType.ITEM);
        edge(g, "spyglass_asm",     "structural_matrix", "Spyglass",         1, MaterialType.ITEM);
        edge(g, "wither_rose_synth","structural_matrix", "Wither Rose",      4, MaterialType.ITEM);

        // ── Aggregators + Stage 8 → Stage 9 (ultimate) ──
        edge(g, "automation_core",     "ultimate_machine",   "Automation Core",     4, MaterialType.ITEM);
        edge(g, "military_array",      "ultimate_machine",   "Military Array",      2, MaterialType.ITEM);
        edge(g, "logistics_network",   "ultimate_machine",   "Logistics Network",   2, MaterialType.ITEM);
        edge(g, "structural_matrix",   "ultimate_machine",   "Structural Matrix",   2, MaterialType.ITEM);
        edge(g, "beacon_asm",          "ultimate_machine",   "Beacon",              2, MaterialType.ITEM);
        edge(g, "conduit_asm",         "ultimate_machine",   "Conduit",             1, MaterialType.ITEM);
        edge(g, "dragon_head_synth",   "ultimate_machine",   "Dragon Head",         1, MaterialType.ITEM);
        edge(g, "reinforced_deep",     "ultimate_machine",   "Reinforced Deepslate",4, MaterialType.ITEM);
        edge(g, "respawn_anchor",      "ultimate_machine",   "Respawn Anchor",      2, MaterialType.ITEM);
        edge(g, "nether_star_synth",   "ultimate_machine",   "Nether Star",         4, MaterialType.ITEM);
        edge(g, "sculk_shrieker_asm",  "ultimate_machine",   "Sculk Shrieker",      2, MaterialType.ITEM);
        edge(g, "lodestone_asm",       "ultimate_machine",   "Lodestone",           2, MaterialType.ITEM);
        edge(g, "recovery_compass_asm","ultimate_machine",   "Recovery Compass",    1, MaterialType.ITEM);
        edge(g, "enchant_table",       "ultimate_machine",   "Enchanting Table",    1, MaterialType.ITEM);

        // ═══════════════════════════════════════════════════════
        //  GROUPS — Compound subgraph structure  (10 stages)
        // ═══════════════════════════════════════════════════════

        // Group: Ore Extraction (Stage 0)
        g.addGroup(new ProcessGroup("grp_extraction", "Ore Extraction",
                List.of("ore_iron", "ore_copper", "ore_gold", "ore_coal",
                        "ore_redstone", "ore_lapis", "ore_diamond", "ore_quartz",
                        "ore_emerald", "sand_quarry"),
                0xFF55AA77));

        // Group: Primary Processing (Stage 1)
        g.addGroup(new ProcessGroup("grp_primary", "Primary Processing",
                List.of("wash_iron", "wash_copper", "wash_gold",
                        "process_coal", "centrifuge_redstone", "enrich_lapis",
                        "cut_quartz", "pulp_wood", "emerald_polish"),
                0xFF6699BB));

        // Group: Smelting & Alloying (Stage 2)
        g.addGroup(new ProcessGroup("grp_smelting", "Smelting & Alloying",
                List.of("smelt_iron", "smelt_gold", "compress_iron", "compress_gold",
                        "compress_diamond", "compress_emerald", "glass_smelting",
                        "brick_firing", "alloy_netherite", "obsidian_crystallize"),
                0xFFCC8844));

        // Group: Material Forming (Stage 3)
        g.addGroup(new ProcessGroup("grp_forming", "Material Forming",
                List.of("wire_iron", "chain_iron", "rod_copper", "plate_iron",
                        "plate_gold", "cut_glass", "form_copper_block",
                        "rail_forming", "leather_tanning", "string_spinning"),
                0xFF88BB55));

        // Group: Redstone Engineering (Stage 4)
        g.addGroup(new ProcessGroup("grp_redstone", "Redstone Engineering",
                List.of("redstone_torch", "repeater_asm", "comparator_asm",
                        "piston_asm", "sticky_piston", "observer_asm",
                        "daylight_det_asm", "tripwire_asm"),
                0xFFCC5566));

        // Group: Component Assembly (Stage 5)
        g.addGroup(new ProcessGroup("grp_components", "Component Assembly",
                List.of("hopper_asm", "dropper_asm", "dispenser_asm",
                        "clock_asm", "compass_asm", "spyglass_asm",
                        "ender_eye_asm", "brewing_stand_asm",
                        "tnt_asm", "target_block_asm", "book_binding", "minecart_asm"),
                0xFF5588CC));

        // Group: Advanced Assembly (Stage 6)
        g.addGroup(new ProcessGroup("grp_advanced", "Advanced Assembly",
                List.of("anvil_asm", "enchant_table", "beacon_asm",
                        "conduit_asm", "end_crystal_asm", "lodestone_asm",
                        "recovery_compass_asm", "shield_asm", "crossbow_asm",
                        "trapped_chest_asm", "powered_rail_asm", "firework_asm"),
                0xFF9966BB));

        // Group: Sculk & Rail Systems (Stage 7)
        g.addGroup(new ProcessGroup("grp_sculk_rail", "Sculk & Rail Systems",
                List.of("sculk_sensor_asm", "sculk_catalyst_asm", "sculk_shrieker_asm",
                        "detector_rail_asm", "activator_rail_asm",
                        "hopper_minecart_asm", "tnt_minecart_asm", "chest_minecart_asm"),
                0xFF77AACC));

        // Group: High-Tech Integration (Stage 8)
        g.addGroup(new ProcessGroup("grp_hightech", "High-Tech Integration",
                List.of("respawn_anchor", "nether_star_synth", "reinforced_deep",
                        "wither_rose_synth", "dragon_head_synth"),
                0xFFBB66AA));

        // Group: System Integration (Stage 8.5)
        g.addGroup(new ProcessGroup("grp_integration", "System Integration",
                List.of("automation_core", "military_array",
                        "logistics_network", "structural_matrix"),
                0xFFDDAA44));

        // Group: Ultimate Fabrication (Stage 9)
        g.addGroup(new ProcessGroup("grp_ultimate", "Ultimate Fabrication",
                List.of("ultimate_machine"),
                0xFFFF5555));

        return g;
    }

    // ───────── Helpers ─────────

    private static ProcessNode node(String id, String name, String machine,
                                     List<MaterialSlot> inputs, List<MaterialSlot> outputs,
                                     int duration, int euPerTick, int tier) {
        return new ProcessNode(id, name, machine, inputs, outputs, duration, euPerTick, tier);
    }

    private static ProcessNode node(String id, String name, String machine,
                                     List<MaterialSlot> inputs, List<MaterialSlot> outputs,
                                     int duration, int euPerTick, int tier,
                                     List<MachineInfo> machineOptions) {
        return new ProcessNode(id, name, machine, inputs, outputs,
                duration, euPerTick, tier, machineOptions);
    }

    private static void edge(ProcessGraph g, String from, String to,
                              String material, int amount, MaterialType type) {
        g.addEdge(new ProcessEdge(from, to, material, amount, type));
    }
}
