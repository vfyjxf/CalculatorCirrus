package dev.vfyjxf.cirrus.planning;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOption;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
import dev.vfyjxf.cirrus.api.planning.context.BaseBoundarySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.ContextAttributeView;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.RawIngredientSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawOutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.api.planning.tag.CanonicalRecipeTag;
import dev.vfyjxf.cirrus.api.planning.tag.RecipeEvidenceTier;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.planning.adapter.OfflineContentTypes;
import dev.vfyjxf.cirrus.planning.bridge.DefaultDomainProfileProvider;
import dev.vfyjxf.cirrus.planning.bridge.PlanningContextFactory;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultAcquisitionOracle;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import dev.vfyjxf.cirrus.planning.core.service.DefaultPlanningService;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.compile;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.item;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.out;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.slot;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.tagSlot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end feasibility regression tests using the live {@link PlanningContextFactory}
 * (with {@link DefaultDomainProfileProvider#defaultSnapshot()}) on complex vanilla & modded recipes.
 */
class ComplexRecipeFeasibilityTest {

    private static RawRecipeRecord taggedRec(
        String systemId,
        String id,
        List<String> machines,
        List<RawIngredientSlot> ins,
        List<RawOutputSlot> outs,
        Set<String> systemTags
    ) {
        return taggedRec(systemId, id, machines, ins, outs, systemTags, Set.of());
    }

    private static RawRecipeRecord taggedRec(
        String systemId,
        String id,
        List<String> machines,
        List<RawIngredientSlot> ins,
        List<RawOutputSlot> outs,
        Set<String> systemTags,
        Set<CanonicalRecipeTag> canonicalTags
    ) {
        List<MachineEntry> machineEntries = machines.stream().map(OfflineContentTypes::machine).toList();
        Set<Namespace> systemNamespaces = systemTags.stream()
                .map(Namespace::parse)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new RawRecipeRecord(
            Namespace.parse(systemId),
            "vanilla",
            Optional.of(ResourceLocation.parse(id)),
            machineEntries,
            ins,
            outs,
            Map.of(),
            0L,
            systemNamespaces,
            canonicalTags,
            RecipeEvidenceTier.tierA,
            1.0,
            Optional.empty(),
            false
        );
    }

    private static PlanningContext offlineDomainContext(
        CompiledRecipeGraph graph,
        Set<ComponentStackKey> certifiedNatural,
        Set<ComponentStackKey> protectedRare
    ) {
        BaseBoundarySnapshot boundary = new BaseBoundarySnapshot(
            Map.of(),
            Set.of(),
            protectedRare,
            certifiedNatural,
            List.of()
        );
        AcquisitionOracle oracle = new DefaultAcquisitionOracle(graph, boundary, DefaultHumanGuidanceView.empty(), true);
        return new PlanningContext(
            InventorySnapshot.empty,
            FacilitySnapshot.empty,
            boundary,
            oracle,
            DefaultHumanGuidanceView.empty(),
            ProgressionState.overworldDefault,
            ContextAttributeView.empty
        );
    }

    private static CompiledRecipeGraph buildRealisticVanillaAndModdedGraph() {
        Set<String> craftTags = Set.of("calculator_cirrus:system/vanilla", "calculator_cirrus:system/hand_operable");
        Set<String> smeltTags = Set.of("calculator_cirrus:system/vanilla");

        ComponentStackKey oakLog = item("minecraft:oak_log");
        ComponentStackKey oakPlanks = item("minecraft:oak_planks");
        ComponentStackKey stick = item("minecraft:stick");
        ComponentStackKey cobble = item("minecraft:cobblestone");
        ComponentStackKey stone = item("minecraft:stone");
        ComponentStackKey smoothStone = item("minecraft:smooth_stone");
        ComponentStackKey sand = item("minecraft:sand");
        ComponentStackKey glass = item("minecraft:glass");
        ComponentStackKey furnace = item("minecraft:furnace");
        ComponentStackKey blastFurnace = item("minecraft:blast_furnace");
        ComponentStackKey chest = item("minecraft:chest");

        ComponentStackKey rawIron = item("minecraft:raw_iron");
        ComponentStackKey ironOre = item("minecraft:iron_ore");
        ComponentStackKey deepslateIronOre = item("minecraft:deepslate_iron_ore");
        ComponentStackKey ironIngot = item("minecraft:iron_ingot");
        ComponentStackKey ironBlock = item("minecraft:iron_block");

        ComponentStackKey rawGold = item("minecraft:raw_gold");
        ComponentStackKey goldIngot = item("minecraft:gold_ingot");

        ComponentStackKey coal = item("minecraft:coal");
        ComponentStackKey coalOre = item("minecraft:coal_ore");
        ComponentStackKey redstone = item("minecraft:redstone");
        ComponentStackKey redstoneOre = item("minecraft:redstone_ore");
        ComponentStackKey quartz = item("minecraft:quartz");
        ComponentStackKey quartzOre = item("minecraft:nether_quartz_ore");
        ComponentStackKey lapis = item("minecraft:lapis_lazuli");
        ComponentStackKey lapisOre = item("minecraft:lapis_ore");

        ComponentStackKey diamond = item("minecraft:diamond");
        ComponentStackKey diamondOre = item("minecraft:diamond_ore");
        ComponentStackKey obsidian = item("minecraft:obsidian");
        ComponentStackKey sugarCane = item("minecraft:sugar_cane");
        ComponentStackKey leather = item("minecraft:leather");
        ComponentStackKey paper = item("minecraft:paper");
        ComponentStackKey book = item("minecraft:book");

        ComponentStackKey blazeRod = item("minecraft:blaze_rod");
        ComponentStackKey blazePowder = item("minecraft:blaze_powder");
        ComponentStackKey enderPearl = item("minecraft:ender_pearl");
        ComponentStackKey enderEye = item("minecraft:ender_eye");
        ComponentStackKey netherStar = item("minecraft:nether_star");
        ComponentStackKey ancientDebris = item("minecraft:ancient_debris");
        ComponentStackKey netheriteScrap = item("minecraft:netherite_scrap");
        ComponentStackKey netheriteIngot = item("minecraft:netherite_ingot");
        ComponentStackKey netheriteBlock = item("minecraft:netherite_block");

        ComponentStackKey torch = item("minecraft:torch");
        ComponentStackKey redstoneTorch = item("minecraft:redstone_torch");
        ComponentStackKey piston = item("minecraft:piston");
        ComponentStackKey observer = item("minecraft:observer");
        ComponentStackKey comparator = item("minecraft:comparator");
        ComponentStackKey hopper = item("minecraft:hopper");
        ComponentStackKey minecart = item("minecraft:minecart");
        ComponentStackKey hopperMinecart = item("minecraft:hopper_minecart");
        ComponentStackKey enchantingTable = item("minecraft:enchanting_table");
        ComponentStackKey brewingStand = item("minecraft:brewing_stand");
        ComponentStackKey enderChest = item("minecraft:ender_chest");
        ComponentStackKey beacon = item("minecraft:beacon");
        ComponentStackKey anvil = item("minecraft:anvil");

        // Modded raw ore with reversible packing cycle + processing
        ComponentStackKey rawZinc = item("create:raw_zinc");
        ComponentStackKey rawZincBlock = item("create:raw_zinc_block");
        ComponentStackKey zincIngot = item("create:zinc_ingot");
        ComponentStackKey brassCasing = item("create:brass_casing");

        // High-fanout exotic leaf tag (10 zero-producer members)
        List<ComponentStackKey> flowers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            flowers.add(item("mod:exotic_flower_" + i));
        }
        ComponentStackKey floralCrown = item("mod:floral_crown");

        // Choice recipe accepting iron OR diamond (planner must avoid protected rare diamond)
        ComponentStackKey shinyBadge = item("mod:shiny_badge");

        List<RawRecipeRecord> records = List.of(
            taggedRec("minecraft:crafting", "minecraft:oak_planks", List.of(), List.of(slot(oakLog, 1)), List.of(out(oakPlanks, 4)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:stick", List.of(), List.of(slot(oakPlanks, 2)), List.of(out(stick, 4)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:furnace", List.of(), List.of(slot(cobble, 8)), List.of(out(furnace, 1)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:chest", List.of(), List.of(slot(oakPlanks, 8)), List.of(out(chest, 1)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:blast_furnace", List.of(), List.of(slot(ironIngot, 5), slot(furnace, 1), slot(smoothStone, 3)), List.of(out(blastFurnace, 1)), craftTags),
            taggedRec("minecraft:smelting", "minecraft:stone", List.of("minecraft:furnace"), List.of(slot(cobble, 1)), List.of(out(stone, 1)), smeltTags),
            taggedRec("minecraft:smelting", "minecraft:smooth_stone", List.of("minecraft:furnace"), List.of(slot(stone, 1)), List.of(out(smoothStone, 1)), smeltTags),
            taggedRec("minecraft:smelting", "minecraft:glass", List.of("minecraft:furnace"), List.of(slot(sand, 1)), List.of(out(glass, 1)), smeltTags),

            // Blasting recipes alphabetically before smelting recipes!
            taggedRec("minecraft:blasting", "minecraft:iron_ingot_from_blasting_deepslate_iron_ore", List.of("minecraft:blast_furnace"), List.of(slot(deepslateIronOre, 1)), List.of(out(ironIngot, 1)), smeltTags),
            taggedRec("minecraft:blasting", "minecraft:iron_ingot_from_blasting_iron_ore", List.of("minecraft:blast_furnace"), List.of(slot(ironOre, 1)), List.of(out(ironIngot, 1)), smeltTags),
            taggedRec("minecraft:blasting", "minecraft:iron_ingot_from_blasting_raw_iron", List.of("minecraft:blast_furnace"), List.of(slot(rawIron, 1)), List.of(out(ironIngot, 1)), smeltTags),
            taggedRec("minecraft:smelting", "minecraft:iron_ingot_from_smelting_deepslate_iron_ore", List.of("minecraft:furnace"), List.of(slot(deepslateIronOre, 1)), List.of(out(ironIngot, 1)), smeltTags),
            taggedRec("minecraft:smelting", "minecraft:iron_ingot_from_smelting_iron_ore", List.of("minecraft:furnace"), List.of(slot(ironOre, 1)), List.of(out(ironIngot, 1)), smeltTags),
            taggedRec("minecraft:smelting", "minecraft:iron_ingot_from_smelting_raw_iron", List.of("minecraft:furnace"), List.of(slot(rawIron, 1)), List.of(out(ironIngot, 1)), smeltTags),
            taggedRec("minecraft:crafting", "minecraft:iron_block", List.of(), List.of(slot(ironIngot, 9)), List.of(out(ironBlock, 1)), craftTags, Set.of(CanonicalRecipeTag.packingUnpacking)),
            taggedRec("minecraft:crafting", "minecraft:iron_ingot_from_iron_block", List.of(), List.of(slot(ironBlock, 1)), List.of(out(ironIngot, 9)), craftTags, Set.of(CanonicalRecipeTag.packingUnpacking)),

            taggedRec("minecraft:smelting", "minecraft:gold_ingot_from_smelting_raw_gold", List.of("minecraft:furnace"), List.of(slot(rawGold, 1)), List.of(out(goldIngot, 1)), smeltTags),

            // Silk-touch ore smelting recipes that previously deadlocked certified natural drops!
            taggedRec("minecraft:smelting", "minecraft:coal_from_smelting_coal_ore", List.of("minecraft:furnace"), List.of(slot(coalOre, 1)), List.of(out(coal, 1)), smeltTags),
            taggedRec("minecraft:smelting", "minecraft:redstone_from_smelting_redstone_ore", List.of("minecraft:furnace"), List.of(slot(redstoneOre, 1)), List.of(out(redstone, 1)), smeltTags),
            taggedRec("minecraft:smelting", "minecraft:quartz_from_smelting", List.of("minecraft:furnace"), List.of(slot(quartzOre, 1)), List.of(out(quartz, 1)), smeltTags),
            taggedRec("minecraft:smelting", "minecraft:lapis_from_smelting_lapis_ore", List.of("minecraft:furnace"), List.of(slot(lapisOre, 1)), List.of(out(lapis, 1)), smeltTags),
            taggedRec("minecraft:smelting", "minecraft:diamond_from_smelting_diamond_ore", List.of("minecraft:furnace"), List.of(slot(diamondOre, 1)), List.of(out(diamond, 1)), smeltTags),

            // Complex vanilla recipes
            taggedRec("minecraft:crafting", "minecraft:torch", List.of(), List.of(slot(coal, 1), slot(stick, 1)), List.of(out(torch, 4)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:redstone_torch", List.of(), List.of(slot(redstone, 1), slot(stick, 1)), List.of(out(redstoneTorch, 1)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:piston", List.of(), List.of(slot(oakPlanks, 3), slot(cobble, 4), slot(ironIngot, 1), slot(redstone, 1)), List.of(out(piston, 1)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:observer", List.of(), List.of(slot(cobble, 6), slot(redstone, 2), slot(quartz, 1)), List.of(out(observer, 1)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:comparator", List.of(), List.of(slot(redstoneTorch, 3), slot(quartz, 1), slot(stone, 3)), List.of(out(comparator, 1)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:hopper", List.of(), List.of(slot(ironIngot, 5), slot(chest, 1)), List.of(out(hopper, 1)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:minecart", List.of(), List.of(slot(ironIngot, 5)), List.of(out(minecart, 1)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:hopper_minecart", List.of(), List.of(slot(hopper, 1), slot(minecart, 1)), List.of(out(hopperMinecart, 1)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:paper", List.of(), List.of(slot(sugarCane, 3)), List.of(out(paper, 3)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:book", List.of(), List.of(slot(paper, 3), slot(leather, 1)), List.of(out(book, 1)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:enchanting_table", List.of(), List.of(slot(book, 1), slot(diamond, 2), slot(obsidian, 4)), List.of(out(enchantingTable, 1)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:brewing_stand", List.of(), List.of(slot(blazeRod, 1), slot(cobble, 3)), List.of(out(brewingStand, 1)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:blaze_powder", List.of(), List.of(slot(blazeRod, 1)), List.of(out(blazePowder, 2)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:ender_eye", List.of(), List.of(slot(enderPearl, 1), slot(blazePowder, 1)), List.of(out(enderEye, 1)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:ender_chest", List.of(), List.of(slot(obsidian, 8), slot(enderEye, 1)), List.of(out(enderChest, 1)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:beacon", List.of(), List.of(slot(glass, 5), slot(obsidian, 3), slot(netherStar, 1)), List.of(out(beacon, 1)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:anvil", List.of(), List.of(slot(ironBlock, 3), slot(ironIngot, 4)), List.of(out(anvil, 1)), craftTags),
            taggedRec("minecraft:smelting", "minecraft:netherite_scrap", List.of("minecraft:furnace"), List.of(slot(ancientDebris, 1)), List.of(out(netheriteScrap, 1)), smeltTags),
            taggedRec("minecraft:crafting", "minecraft:netherite_ingot", List.of(), List.of(slot(netheriteScrap, 4), slot(goldIngot, 4)), List.of(out(netheriteIngot, 1)), craftTags),
            taggedRec("minecraft:crafting", "minecraft:netherite_block", List.of(), List.of(slot(netheriteIngot, 9)), List.of(out(netheriteBlock, 1)), craftTags, Set.of(CanonicalRecipeTag.packingUnpacking)),
            taggedRec("minecraft:crafting", "minecraft:netherite_ingot_from_netherite_block", List.of(), List.of(slot(netheriteBlock, 1)), List.of(out(netheriteIngot, 9)), craftTags, Set.of(CanonicalRecipeTag.packingUnpacking)),

            // Modded raw material with storage block cycle
            taggedRec("minecraft:crafting", "create:raw_zinc_block", List.of(), List.of(slot(rawZinc, 9)), List.of(out(rawZincBlock, 1)), craftTags, Set.of(CanonicalRecipeTag.packingUnpacking)),
            taggedRec("minecraft:crafting", "create:raw_zinc_from_block", List.of(), List.of(slot(rawZincBlock, 1)), List.of(out(rawZinc, 9)), craftTags, Set.of(CanonicalRecipeTag.packingUnpacking)),
            taggedRec("minecraft:smelting", "create:zinc_ingot_from_raw_zinc", List.of("minecraft:furnace"), List.of(slot(rawZinc, 1)), List.of(out(zincIngot, 1)), smeltTags),
            taggedRec("minecraft:crafting", "create:brass_casing", List.of(), List.of(slot(zincIngot, 2), slot(oakPlanks, 4)), List.of(out(brassCasing, 1)), craftTags),

            // High-fanout flower tag & rare-vs-common choice
            taggedRec("minecraft:crafting", "mod:floral_crown", List.of(), List.of(tagSlot("mod:exotic_flowers", flowers, 3)), List.of(out(floralCrown, 1)), craftTags),
            taggedRec("minecraft:crafting", "mod:shiny_badge", List.of(), List.of(tagSlot("mod:shiny_gems", List.of(diamond, ironIngot), 1)), List.of(out(shinyBadge, 1)), craftTags)
        );
        return compile(records);
    }

    @Test
    void complexVanillaRecipesAreFeasibleUnderDefaultDomainProfile() {
        CompiledRecipeGraph graph = buildRealisticVanillaAndModdedGraph();
        PlanningContextFactory factory = new PlanningContextFactory(graph);
        DefaultPlanningService service = new DefaultPlanningService(graph);

        List<ComponentStackKey> targets = List.of(
            item("minecraft:torch"),
            item("minecraft:piston"),
            item("minecraft:observer"),
            item("minecraft:comparator"),
            item("minecraft:hopper_minecart"),
            item("minecraft:enchanting_table"),
            item("minecraft:brewing_stand"),
            item("minecraft:ender_chest"),
            item("minecraft:beacon"),
            item("minecraft:anvil"),
            item("minecraft:netherite_ingot"),
            item("minecraft:netherite_block")
        );

        for (ComponentStackKey target : targets) {
            PlanningRequest req = factory.createRequest(target, 1L, null, null, null);
            PlanningResult result = service.planOnce(req);
            assertEquals(
                PlanningStatus.feasible,
                result.status(),
                "Expected feasible plan for " + target + " but got " + result.status()
                        + " unmet=" + result.diagnostics().unmetResources()
            );
            assertFalse(result.paretoPlans().isEmpty(), "Expected non-empty plans for " + target);
            ProductionPlan primary = result.paretoPlans().get(0);
            assertTrue(
                primary.certificate().valid(),
                "Invalid plan for " + target + ": " + primary.certificate().violations()
            );
        }
    }

    @Test
    void hopperMinecartBuildsFurnaceNotDeadlockedBlastFurnaceAndCraftsPackedBlocks() {
        CompiledRecipeGraph graph = buildRealisticVanillaAndModdedGraph();
        PlanningContextFactory factory = new PlanningContextFactory(graph);
        DefaultPlanningService service = new DefaultPlanningService(graph);

        // Hopper minecart requires iron_ingot at depth >= 2; must craft via furnace + raw_iron.
        PlanningResult minecartRes = service.planOnce(
            factory.createRequest(item("minecraft:hopper_minecart"), 1L, null, null, null)
        );
        assertEquals(PlanningStatus.feasible, minecartRes.status());
        ProductionPlan minecartPlan = minecartRes.paretoPlans().get(0);
        boolean buildsFurnace = minecartPlan.steps().stream()
                .anyMatch(
                    s -> s instanceof ProvisionMachineStep pm
                            && pm.machine().contentKey().orElseThrow().equals(item("minecraft:furnace"))
                );
        assertTrue(buildsFurnace, "Expected hopper_minecart plan to provision minecraft:furnace");

        // Anvil & Netherite Block must NEVER shortcut-acquire packed blocks as raw leaves.
        PlanningResult anvilRes = service.planOnce(
            factory.createRequest(item("minecraft:anvil"), 1L, null, null, null)
        );
        assertEquals(PlanningStatus.feasible, anvilRes.status());
        ProductionPlan anvilPlan = anvilRes.paretoPlans().get(0);
        boolean acquiredIronBlock = anvilPlan.steps().stream()
                .anyMatch(s -> s instanceof AcquireStep a && a.resource().equals(item("minecraft:iron_block")));
        assertFalse(acquiredIronBlock, "Must craft iron_block from iron_ingot, not acquire it as a leaf");

        PlanningResult nethBlockRes = service.planOnce(
            factory.createRequest(item("minecraft:netherite_block"), 1L, null, null, null)
        );
        assertEquals(PlanningStatus.feasible, nethBlockRes.status());
        ProductionPlan nethBlockPlan = nethBlockRes.paretoPlans().get(0);
        boolean acquiredNethIngot = nethBlockPlan.steps().stream()
                .anyMatch(s -> s instanceof AcquireStep a && a.resource().equals(item("minecraft:netherite_ingot")));
        boolean acquiredDebris = nethBlockPlan.steps().stream()
                .anyMatch(s -> s instanceof AcquireStep a && a.resource().equals(item("minecraft:ancient_debris")));
        assertFalse(acquiredNethIngot, "Must craft netherite_ingot from scrap + gold");
        assertTrue(acquiredDebris, "Should gather ancient_debris at the true leaf");
    }

    @Test
    void moddedRawOreWithPackingCycleAndProtectedRarePenaltyWorkCleanly() {
        CompiledRecipeGraph graph = buildRealisticVanillaAndModdedGraph();
        DefaultPlanningService service = new DefaultPlanningService(graph);
        PlanningContext ctx = offlineDomainContext(
            graph,
            Set.of(item("create:raw_zinc")),
            Set.of(item("minecraft:diamond"))
        );

        PlanningResult modRes = service.planOnce(
            new PlanningRequest(
                List.of(new TargetDemand(item("create:brass_casing"), 2L)),
                ctx,
                PlanningProfile.defaultAutoSmart(),
                PlanningBudget.defaultInteractive()
            )
        );
        assertEquals(
            PlanningStatus.feasible,
            modRes.status(),
            () -> "brass_casing status=" + modRes.status() + " unmet=" + modRes.diagnostics().unmetResources()
                    + " prompts=" + modRes.decisionPrompts().size()
                    + " compiled=" + graph.stats().compiledRecipes() + "/" + graph.stats().totalRawRecipes()
                    + " brassProducers=" + graph.producersOf(item("create:brass_casing")).size()
        );
        ProductionPlan modPlan = modRes.paretoPlans().get(0);
        assertTrue(modPlan.certificate().valid());
        boolean acquiredRawZinc = modPlan.steps().stream()
                .anyMatch(s -> s instanceof AcquireStep a && a.resource().equals(item("create:raw_zinc")));
        boolean acquiredRawZincBlock = modPlan.steps().stream()
                .anyMatch(s -> s instanceof AcquireStep a && a.resource().equals(item("create:raw_zinc_block")));
        assertTrue(acquiredRawZinc, "Modded unpacked raw ore must be recognized as a natural leaf");
        assertFalse(acquiredRawZincBlock, "Packed raw ore block must not be acquired as a raw leaf");

        // Rare protection penalty must steer optional choice away from diamond toward iron_ingot.
        PlanningResult choiceRes = service.planOnce(
            new PlanningRequest(
                List.of(new TargetDemand(item("mod:shiny_badge"), 1L)),
                ctx,
                PlanningProfile.defaultAutoSmart(),
                PlanningBudget.defaultInteractive()
            )
        );
        assertEquals(PlanningStatus.feasible, choiceRes.status());
        ProductionPlan choicePlan = choiceRes.paretoPlans().get(0);
        boolean usedDiamond = choicePlan.steps().stream()
                .anyMatch(s -> s instanceof AcquireStep a && a.resource().equals(item("minecraft:diamond")));
        assertFalse(usedDiamond, "Protected rare penalty must prefer iron_ingot over diamond");
    }

    @Test
    void highFanoutLeafTagRelaxesPastBannedMembersAndGuidancePropagates() {
        CompiledRecipeGraph graph = buildRealisticVanillaAndModdedGraph();
        PlanningContextFactory factory = new PlanningContextFactory(graph);
        DefaultPlanningService service = new DefaultPlanningService(graph);

        List<GuidanceRule> bans = List.of(
            new ResourceRule("ban0", GuidanceScope.session, RuleStrength.hardBan, item("mod:exotic_flower_0"), false),
            new ResourceRule("ban1", GuidanceScope.session, RuleStrength.hardBan, item("mod:exotic_flower_1"), false),
            new ResourceRule("ban2", GuidanceScope.session, RuleStrength.hardBan, item("mod:exotic_flower_2"), false),
            new ResourceRule("ban3", GuidanceScope.session, RuleStrength.hardBan, item("mod:exotic_flower_3"), false)
        );
        var ctx = factory.createContext(null, null, null, StockLedger.empty, ProgressionState.overworldDefault, bans, List.of());
        PlanningResult res = service.planOnce(
            new PlanningRequest(
                List.of(new TargetDemand(item("mod:floral_crown"), 1L)),
                ctx,
                PlanningProfile.defaultAutoSmart(),
                PlanningBudget.defaultInteractive()
            )
        );
        assertEquals(PlanningStatus.feasible, res.status());
        assertTrue(res.paretoPlans().get(0).certificate().valid());

        // Verify DefaultAcquisitionOracle.withGuidance propagates bans.
        ComponentStackKey redstoneKey = ComponentStackKey.fromItemStack(new ItemStack(Items.REDSTONE))
                .orElseThrow();
        DefaultAcquisitionOracle oracle = new DefaultAcquisitionOracle(
            graph,
            DefaultDomainProfileProvider.defaultSnapshot(),
            null,
            true
        );
        assertTrue(oracle.assess(redstoneKey, 1L, ProgressionState.overworldDefault).isPresent());
        DefaultAcquisitionOracle bannedOracle = oracle.withGuidance(
            DefaultHumanGuidanceView.of(
                List.of(new ResourceRule("ban_rs", GuidanceScope.session, RuleStrength.hardBan, redstoneKey, false))
            )
        );
        Optional<AcquisitionOption> bannedOpt = bannedOracle.assess(
            redstoneKey,
            1L,
            ProgressionState.overworldDefault
        );
        assertTrue(bannedOpt.isEmpty());
    }
}
