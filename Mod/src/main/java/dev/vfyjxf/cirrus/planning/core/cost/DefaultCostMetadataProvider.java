package dev.vfyjxf.cirrus.planning.core.cost;

import dev.vfyjxf.cirrus.api.planning.cost.CostMetadataProvider;
import dev.vfyjxf.cirrus.api.planning.cost.SystemCostTier;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Namespace;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class DefaultCostMetadataProvider implements CostMetadataProvider {

    public static final DefaultCostMetadataProvider instance = new DefaultCostMetadataProvider();
    public static final Namespace timeValueKey = CirrusNamespaces.ofMod("time");

    public DefaultCostMetadataProvider() {}

    @Override
    public SystemCostTier tierOfSystem(Namespace systemId, Set<Namespace> systemTags) {
        return SystemCostTiers.tierOfSystemTags(systemTags);
    }

    @Override
    public double estimateHumanSecondsPerBatch(
        Namespace systemId,
        Set<Namespace> systemTags,
        Optional<MachineEntry> machine,
        boolean automated
    ) {
        if (automated) {
            return 0.5;
        }
        return SystemCostTiers.defaultsFor(tierOfSystem(systemId, systemTags)).humanSecondsPerBatch();
    }

    @Override
    public double estimatePassiveSecondsPerBatch(
        Namespace systemId,
        Set<Namespace> systemTags,
        Optional<MachineEntry> machine,
        Map<Namespace, Double> recipeValues
    ) {
        if (recipeValues != null) {
            Double ticks = recipeValues.get(timeValueKey);
            if (ticks != null && Double.isFinite(ticks) && ticks >= 0.0) {
                return ticks / 20.0;
            }
        }
        return SystemCostTiers.defaultsFor(tierOfSystem(systemId, systemTags)).passiveSecondsPerBatch();
    }

    @Override
    public double estimateMachineSetupHumanSeconds(MachineEntry machine, Namespace systemId, Set<Namespace> systemTags) {
        return SystemCostTiers.defaultsFor(tierOfSystem(systemId, systemTags)).machineSetupHumanSeconds();
    }

    @Override
    public double estimateMachineSetupCapital(MachineEntry machine, Namespace systemId, Set<Namespace> systemTags) {
        return SystemCostTiers.defaultsFor(tierOfSystem(systemId, systemTags)).machineSetupCapital();
    }

    @Override
    public double estimateLineSetupHumanSeconds(
        Namespace systemId,
        Set<Namespace> systemTags,
        MachineCapabilityId capability
    ) {
        return SystemCostTiers.defaultsFor(tierOfSystem(systemId, systemTags)).lineSetupHumanSeconds();
    }

    @Override
    public double estimateLineSetupCapital(
        Namespace systemId,
        Set<Namespace> systemTags,
        MachineCapabilityId capability
    ) {
        return SystemCostTiers.defaultsFor(tierOfSystem(systemId, systemTags)).lineSetupCapital();
    }
}
