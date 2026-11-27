package dev.vfyjxf.cirrus.planning.core.cost;

import dev.vfyjxf.cirrus.api.planning.cost.SystemCostTier;

import java.util.Set;

/**
 * Pure tag-driven system cost tier mapping (ZERO hardcoded mod/system names!).
 * Uses Cirrus RecipeSystemTags passed via recipe metadata.
 */
public final class SystemCostTiers {

    public record TierDefaults(
        double humanSecondsPerBatch,
        double passiveSecondsPerBatch,
        double machineSetupCapital,
        double machineSetupHumanSeconds,
        double lineSetupCapital,
        double lineSetupHumanSeconds
    ) {}

    private static final TierDefaults t0 = new TierDefaults(3.0, 0.0, 2.0, 5.0, 18.0, 35.0);
    private static final TierDefaults t1 = new TierDefaults(2.0, 10.0, 15.0, 20.0, 35.0, 55.0);
    private static final TierDefaults t2 = new TierDefaults(1.5, 6.0, 60.0, 90.0, 90.0, 140.0);
    private static final TierDefaults t3 = new TierDefaults(1.0, 4.0, 180.0, 300.0, 220.0, 360.0);
    private static final TierDefaults t4 = new TierDefaults(5.0, 20.0, 500.0, 900.0, 600.0, 1000.0);

    private static final String tagHandOperable = "calculator_cirrus:system/hand_operable";
    private static final String tagVanilla = "calculator_cirrus:system/vanilla";
    private static final String tagAutomatable = "calculator_cirrus:system/automatable";
    private static final String tagHeavyMultiblock = "calculator_cirrus:system/heavy_multiblock";
    private static final String tagOptIn = "calculator_cirrus:system/opt_in";

    private SystemCostTiers() {}

    public static SystemCostTier tierOfSystemTags(Set<String> systemTags) {
        if (systemTags == null || systemTags.isEmpty()) {
            return SystemCostTier.t2MidTechSingleMachine;
        }
        if (systemTags.contains(tagHeavyMultiblock) || systemTags.contains(tagOptIn)) {
            return SystemCostTier.t4HeavySpecialized;
        }
        if (systemTags.contains(tagHandOperable)) {
            return SystemCostTier.t0HandWorkshop;
        }
        if (systemTags.contains(tagVanilla)) {
            return SystemCostTier.t1BasicStaticMachine;
        }
        if (systemTags.contains(tagAutomatable)) {
            return SystemCostTier.t3AutomatedFactoryLine;
        }
        return SystemCostTier.t2MidTechSingleMachine;
    }

    public static SystemCostTier tierOfSystem(String systemId) {
        return tierOfSystemTags(Set.of());
    }

    public static TierDefaults defaultsFor(SystemCostTier tier) {
        return switch (tier) {
            case t0HandWorkshop -> t0;
            case t1BasicStaticMachine -> t1;
            case t2MidTechSingleMachine -> t2;
            case t3AutomatedFactoryLine -> t3;
            case t4HeavySpecialized -> t4;
        };
    }

    public static int systemPriority(Set<String> systemTags) {
        if (systemTags == null || systemTags.isEmpty()) {
            return 3;
        }
        if (systemTags.contains(tagHandOperable) && systemTags.contains(tagVanilla)) return 0;
        if (systemTags.contains(tagVanilla)) return 1;
        if (systemTags.contains(tagHandOperable)) return 2;
        return 3;
    }

    public static int systemPriority(String systemId) {
        return 2;
    }
}
