package dev.vfyjxf.cirrus.planning.bridge;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.BaseBoundarySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.BaseContract;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.spi.BaseBoundaryProvider;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Externalized Minecraft/modpack domain defaults (living in bridge layer, not in pure core).
 * Declares:
 * - Protected high-value / rare items forbidden from automatic gathering (diamond, emerald, ancient_debris, etc.)
 * - Certified common surface natural leaves (oak_log, cobblestone, sand, gravel, clay_ball, etc.)
 * - Default Base boundary contracts with calibrated non-zero relative prices & partial orderings.
 */
public final class DefaultDomainProfileProvider implements BaseBoundaryProvider {

    public static final DefaultDomainProfileProvider instance = new DefaultDomainProfileProvider();

    private static volatile @Nullable BaseBoundarySnapshot cachedDefaultSnapshot;

    private DefaultDomainProfileProvider() {}

    @Override
    public String providerId() {
        return "cirrus:default_domain_profile";
    }

    @Override
    public BaseBoundarySnapshot provide(CompiledRecipeGraph graph, PlanningContext context) {
        return defaultSnapshot();
    }

    public static BaseBoundarySnapshot defaultSnapshot() {
        BaseBoundarySnapshot snap = cachedDefaultSnapshot;
        if (snap != null) {
            return snap;
        }
        synchronized (DefaultDomainProfileProvider.class) {
            if (cachedDefaultSnapshot == null) {
                cachedDefaultSnapshot = buildDefaultSnapshot();
            }
            return cachedDefaultSnapshot;
        }
    }

    private static BaseBoundarySnapshot buildDefaultSnapshot() {
        Set<ComponentStackKey> protectedRare = Set.of(
            ComponentStackKey.ofItem(Items.DIAMOND),
            ComponentStackKey.ofItem(Items.EMERALD),
            ComponentStackKey.ofItem(Items.ANCIENT_DEBRIS),
            ComponentStackKey.ofItem(Items.NETHERITE_SCRAP),
            ComponentStackKey.ofItem(Items.NETHERITE_INGOT),
            ComponentStackKey.ofItem(Items.NETHER_STAR),
            ComponentStackKey.ofItem(Items.DRAGON_EGG),
            ComponentStackKey.ofItem(Items.ELYTRA),
            ComponentStackKey.ofItem(Items.TOTEM_OF_UNDYING),
            ComponentStackKey.ofItem(Items.HEART_OF_THE_SEA),
            ComponentStackKey.ofItem(Items.HEAVY_CORE),
            ComponentStackKey.ofItem(Items.SHULKER_SHELL),
            ComponentStackKey.ofItem(Items.ECHO_SHARD),
            ComponentStackKey.ofItem(Items.BLAZE_ROD),
            ComponentStackKey.ofItem(Items.ENDER_PEARL),
            ComponentStackKey.ofItem(Items.GHAST_TEAR)
        );

        Set<ComponentStackKey> certifiedNatural = Set.of(
            ComponentStackKey.ofItem(Items.COBBLESTONE),
            ComponentStackKey.ofItem(Items.COBBLED_DEEPSLATE),
            ComponentStackKey.ofItem(Items.DIRT),
            ComponentStackKey.ofItem(Items.SAND),
            ComponentStackKey.ofItem(Items.RED_SAND),
            ComponentStackKey.ofItem(Items.GRAVEL),
            ComponentStackKey.ofItem(Items.CLAY_BALL),
            ComponentStackKey.ofItem(Items.NETHERRACK),
            ComponentStackKey.ofItem(Items.SOUL_SAND),
            ComponentStackKey.ofItem(Items.END_STONE),
            ComponentStackKey.ofItem(Items.OAK_LOG),
            ComponentStackKey.ofItem(Items.SPRUCE_LOG),
            ComponentStackKey.ofItem(Items.BIRCH_LOG),
            ComponentStackKey.ofItem(Items.JUNGLE_LOG),
            ComponentStackKey.ofItem(Items.ACACIA_LOG),
            ComponentStackKey.ofItem(Items.DARK_OAK_LOG),
            ComponentStackKey.ofItem(Items.MANGROVE_LOG),
            ComponentStackKey.ofItem(Items.CHERRY_LOG),
            ComponentStackKey.ofItem(Items.CRIMSON_STEM),
            ComponentStackKey.ofItem(Items.WARPED_STEM),
            ComponentStackKey.ofItem(Items.BAMBOO),
            ComponentStackKey.ofItem(Items.SUGAR_CANE),
            ComponentStackKey.ofItem(Items.WHEAT),
            ComponentStackKey.ofItem(Items.CACTUS),
            ComponentStackKey.ofItem(Items.KELP),
            ComponentStackKey.ofItem(Items.OBSIDIAN),
            ComponentStackKey.ofItem(Items.CRYING_OBSIDIAN),
            ComponentStackKey.ofItem(Items.AMETHYST_SHARD),
            ComponentStackKey.ofItem(Items.COAL),
            ComponentStackKey.ofItem(Items.RAW_COPPER),
            ComponentStackKey.ofItem(Items.RAW_IRON),
            ComponentStackKey.ofItem(Items.RAW_GOLD),
            ComponentStackKey.ofItem(Items.REDSTONE),
            ComponentStackKey.ofItem(Items.LAPIS_LAZULI),
            ComponentStackKey.ofItem(Items.QUARTZ),
            ComponentStackKey.ofItem(Items.DIAMOND),
            ComponentStackKey.ofItem(Items.EMERALD),
            ComponentStackKey.ofItem(Items.STRING),
            ComponentStackKey.ofItem(Items.LEATHER),
            ComponentStackKey.ofItem(Items.FEATHER),
            ComponentStackKey.ofItem(Items.FLINT),
            ComponentStackKey.ofItem(Items.BONE),
            ComponentStackKey.ofItem(Items.GUNPOWDER),
            ComponentStackKey.ofItem(Items.SLIME_BALL),
            ComponentStackKey.ofItem(Items.GLOWSTONE_DUST),
            ComponentStackKey.ofItem(Items.NETHER_WART),
            ComponentStackKey.ofItem(Items.HONEYCOMB),
            ComponentStackKey.ofItem(Items.HONEY_BOTTLE),
            ComponentStackKey.ofItem(Items.INK_SAC),
            ComponentStackKey.ofItem(Items.GLOW_INK_SAC),
            ComponentStackKey.ofItem(Items.EGG),
            ComponentStackKey.ofItem(Items.MILK_BUCKET),
            ComponentStackKey.ofItem(Items.SNOWBALL),
            ComponentStackKey.ofItem(Items.ICE),
            ComponentStackKey.ofItem(Items.SPIDER_EYE),
            ComponentStackKey.ofItem(Items.ROTTEN_FLESH),
            ComponentStackKey.ofItem(Items.PHANTOM_MEMBRANE),
            ComponentStackKey.ofItem(Items.PRISMARINE_SHARD),
            ComponentStackKey.ofItem(Items.PRISMARINE_CRYSTALS),
            ComponentStackKey.ofItem(Items.NAUTILUS_SHELL),
            ComponentStackKey.ofItem(Items.BREEZE_ROD),
            ComponentStackKey.ofItem(Items.CHORUS_FRUIT),
            ComponentStackKey.ofItem(Items.WATER_BUCKET),
            ComponentStackKey.ofItem(Items.LAVA_BUCKET),
            ComponentStackKey.ofFluid(Fluids.WATER),
            ComponentStackKey.ofFluid(Fluids.LAVA)
        );

        TreeMap<ComponentStackKey, BaseContract> contracts = new TreeMap<>();
        putBase(contracts, ComponentStackKey.ofItem(Items.COBBLESTONE), 0.5, 0.2, 0.1, 1.0);
        putBase(contracts, ComponentStackKey.ofItem(Items.OAK_LOG), 1.0, 0.5, 0.2, 1.5);
        putBase(contracts, ComponentStackKey.ofFluid(Fluids.WATER), 0.01, 0.005, 0.0, 0.2);

        List<BaseBoundarySnapshot.PartialOrderConstraint> orders = List.of(
            new BaseBoundarySnapshot.PartialOrderConstraint(
                ComponentStackKey.ofItem(Items.COBBLESTONE),
                ComponentStackKey.ofItem(Items.IRON_INGOT),
                4.0
            ),
            new BaseBoundarySnapshot.PartialOrderConstraint(
                ComponentStackKey.ofItem(Items.IRON_INGOT),
                ComponentStackKey.ofItem(Items.GOLD_INGOT),
                4.0
            ),
            new BaseBoundarySnapshot.PartialOrderConstraint(
                ComponentStackKey.ofItem(Items.GOLD_INGOT),
                ComponentStackKey.ofItem(Items.DIAMOND),
                12.0
            )
        );
        return new BaseBoundarySnapshot(
            contracts,
            Set.of(),
            protectedRare,
            certifiedNatural,
            orders
        );
    }

    private static void putBase(
        Map<ComponentStackKey, BaseContract> map,
        ComponentStackKey key,
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
