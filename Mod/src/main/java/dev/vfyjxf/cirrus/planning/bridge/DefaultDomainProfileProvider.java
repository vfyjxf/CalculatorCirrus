package dev.vfyjxf.cirrus.planning.bridge;

import dev.vfyjxf.cirrus.api.planning.context.BaseBoundarySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.BaseContract;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.spi.BaseBoundaryProvider;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Externalized Minecraft/modpack domain defaults (living in bridge layer, NOT in pure core!).
 * Declares:
 * - Protected high-value / rare items forbidden from automatic gathering (diamond, emerald, ancient_debris, etc.)
 * - Certified common surface natural leaves (oak_log, cobblestone, sand, gravel, clay_ball, etc.)
 * - Default Base boundary contracts with calibrated non-zero relative prices & partial orderings.
 */
public final class DefaultDomainProfileProvider implements BaseBoundaryProvider {

    public static final DefaultDomainProfileProvider instance = new DefaultDomainProfileProvider();

    private static final Set<ResourceKey> defaultProtectedRare = Set.of(
        ResourceKey.item("minecraft:diamond"),
        ResourceKey.item("minecraft:emerald"),
        ResourceKey.item("minecraft:ancient_debris"),
        ResourceKey.item("minecraft:netherite_scrap"),
        ResourceKey.item("minecraft:netherite_ingot"),
        ResourceKey.item("minecraft:nether_star"),
        ResourceKey.item("minecraft:dragon_egg"),
        ResourceKey.item("minecraft:elytra"),
        ResourceKey.item("minecraft:totem_of_undying"),
        ResourceKey.item("minecraft:heart_of_the_sea"),
        ResourceKey.item("minecraft:heavy_core"),
        ResourceKey.item("minecraft:shulker_shell"),
        ResourceKey.item("minecraft:echo_shard"),
        ResourceKey.item("minecraft:blaze_rod"),
        ResourceKey.item("minecraft:ender_pearl"),
        ResourceKey.item("minecraft:ghast_tear")
    );

    private static final Set<ResourceKey> defaultCertifiedNatural = Set.of(
        ResourceKey.item("minecraft:cobblestone"),
        ResourceKey.item("minecraft:cobbled_deepslate"),
        ResourceKey.item("minecraft:dirt"),
        ResourceKey.item("minecraft:sand"),
        ResourceKey.item("minecraft:red_sand"),
        ResourceKey.item("minecraft:gravel"),
        ResourceKey.item("minecraft:clay_ball"),
        ResourceKey.item("minecraft:netherrack"),
        ResourceKey.item("minecraft:soul_sand"),
        ResourceKey.item("minecraft:end_stone"),
        ResourceKey.item("minecraft:oak_log"),
        ResourceKey.item("minecraft:spruce_log"),
        ResourceKey.item("minecraft:birch_log"),
        ResourceKey.item("minecraft:jungle_log"),
        ResourceKey.item("minecraft:acacia_log"),
        ResourceKey.item("minecraft:dark_oak_log"),
        ResourceKey.item("minecraft:mangrove_log"),
        ResourceKey.item("minecraft:cherry_log"),
        ResourceKey.item("minecraft:bamboo"),
        ResourceKey.item("minecraft:sugar_cane"),
        ResourceKey.item("minecraft:wheat"),
        ResourceKey.item("minecraft:coal"),
        ResourceKey.item("minecraft:raw_copper"),
        ResourceKey.item("minecraft:raw_iron"),
        ResourceKey.item("minecraft:raw_gold"),
        ResourceKey.item("minecraft:redstone"),
        ResourceKey.item("minecraft:lapis_lazuli"),
        ResourceKey.item("minecraft:quartz"),
        ResourceKey.item("minecraft:string"),
        ResourceKey.item("minecraft:leather"),
        ResourceKey.item("minecraft:feather"),
        ResourceKey.item("minecraft:flint"),
        ResourceKey.item("minecraft:bone"),
        ResourceKey.item("minecraft:gunpowder"),
        ResourceKey.item("minecraft:slime_ball"),
        ResourceKey.item("minecraft:glowstone_dust"),
        ResourceKey.item("minecraft:water_bucket"),
        ResourceKey.item("minecraft:lava_bucket"),
        ResourceKey.fluid("minecraft:water"),
        ResourceKey.fluid("minecraft:lava")
    );

    private static final BaseBoundarySnapshot defaultSnapshot = buildDefaultSnapshot();

    private DefaultDomainProfileProvider() {}

    @Override
    public String providerId() {
        return "cirrus:default_domain_profile";
    }

    @Override
    public BaseBoundarySnapshot provide(CompiledRecipeGraph graph, PlanningContext context) {
        return defaultSnapshot;
    }

    public static BaseBoundarySnapshot defaultSnapshot() {
        return defaultSnapshot;
    }

    private static BaseBoundarySnapshot buildDefaultSnapshot() {
        TreeMap<ResourceKey, BaseContract> contracts = new TreeMap<>();
        putBase(contracts, ResourceKey.item("minecraft:cobblestone"), 0.5, 0.2, 0.1, 1.0);
        putBase(contracts, ResourceKey.item("minecraft:oak_log"), 1.0, 0.5, 0.2, 1.5);
        putBase(contracts, ResourceKey.fluid("minecraft:water"), 0.01, 0.005, 0.0, 0.2);

        List<BaseBoundarySnapshot.PartialOrderConstraint> orders = List.of(
            new BaseBoundarySnapshot.PartialOrderConstraint(
                ResourceKey.item("minecraft:cobblestone"),
                ResourceKey.item("minecraft:iron_ingot"),
                4.0
            ),
            new BaseBoundarySnapshot.PartialOrderConstraint(
                ResourceKey.item("minecraft:iron_ingot"),
                ResourceKey.item("minecraft:gold_ingot"),
                4.0
            ),
            new BaseBoundarySnapshot.PartialOrderConstraint(
                ResourceKey.item("minecraft:gold_ingot"),
                ResourceKey.item("minecraft:diamond"),
                12.0
            )
        );
        return new BaseBoundarySnapshot(
            contracts,
            Set.of(),
            defaultProtectedRare,
            defaultCertifiedNatural,
            orders
        );
    }

    private static void putBase(
        Map<ResourceKey, BaseContract> map,
        ResourceKey key,
        double unitSec,
        double floorSec,
        double scarcity,
        double fixedDelivery
    ) {
        map.put(
            key,
            new BaseContract(key, unitSec, floorSec, scarcity, fixedDelivery, Long.MAX_VALUE, false, "cirrus:default_base")
        );
    }
}
