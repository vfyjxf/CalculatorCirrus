package dev.vfyjxf.cirrus.planning.core.cost;

import dev.vfyjxf.cirrus.api.planning.cost.SystemCostTier;

public final class SystemCostTiers {

    public record TierDefaults(
            double humanSecondsPerBatch,
            double passiveSecondsPerBatch,
            double machineSetupCapital,
            double machineSetupHumanSeconds,
            double lineSetupCapital,
            double lineSetupHumanSeconds
    ) {
    }

    private static final TierDefaults T0 = new TierDefaults(3.0, 0.0, 2.0, 5.0, 18.0, 35.0);
    private static final TierDefaults T1 = new TierDefaults(2.0, 10.0, 15.0, 20.0, 35.0, 55.0);
    private static final TierDefaults T2 = new TierDefaults(1.5, 6.0, 60.0, 90.0, 90.0, 140.0);
    private static final TierDefaults T3 = new TierDefaults(1.0, 4.0, 180.0, 300.0, 220.0, 360.0);
    private static final TierDefaults T4 = new TierDefaults(5.0, 20.0, 500.0, 900.0, 600.0, 1000.0);

    private SystemCostTiers() {
    }

    public static SystemCostTier tierOfSystem(String systemId) {
        if (systemId == null || systemId.isEmpty()) {
            return SystemCostTier.T2_MID_TECH_SINGLE_MACHINE;
        }
        return switch (systemId) {
            case "minecraft:crafting", "minecraft:stonecutting" -> SystemCostTier.T0_HAND_WORKSHOP;
            case "minecraft:smelting", "minecraft:blasting", "minecraft:smoking",
                    "minecraft:campfire_cooking", "minecraft:smithing", "minecraft:brewing" ->
                    SystemCostTier.T1_BASIC_STATIC_MACHINE;
            default -> {
                if (systemId.contains("minecolonies")
                        || systemId.contains("modern_industrialization")
                        || systemId.contains("gregtech")) {
                    yield SystemCostTier.T4_HEAVY_SPECIALIZED;
                }
                if (systemId.contains("automatic_")
                        || systemId.contains("industrialforegoing")
                        || systemId.contains("mekanism")) {
                    yield SystemCostTier.T3_AUTOMATED_FACTORY_LINE;
                }
                yield SystemCostTier.T2_MID_TECH_SINGLE_MACHINE;
            }
        };
    }

    public static TierDefaults defaultsFor(SystemCostTier tier) {
        return switch (tier) {
            case T0_HAND_WORKSHOP -> T0;
            case T1_BASIC_STATIC_MACHINE -> T1;
            case T2_MID_TECH_SINGLE_MACHINE -> T2;
            case T3_AUTOMATED_FACTORY_LINE -> T3;
            case T4_HEAVY_SPECIALIZED -> T4;
        };
    }

    public static int systemPriority(String systemId) {
        if ("minecraft:crafting".equals(systemId)) return 0;
        if ("minecraft:smelting".equals(systemId)
                || "minecraft:blasting".equals(systemId)
                || "minecraft:smoking".equals(systemId)
                || "minecraft:stonecutting".equals(systemId)
                || "minecraft:smithing".equals(systemId)
                || "minecraft:campfire_cooking".equals(systemId)
                || "minecraft:brewing".equals(systemId)) {
            return 1;
        }
        if (systemId != null && systemId.startsWith("minecraft:")) return 2;
        return 3;
    }
}
