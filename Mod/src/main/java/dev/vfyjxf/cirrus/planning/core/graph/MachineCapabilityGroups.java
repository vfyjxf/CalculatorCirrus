package dev.vfyjxf.cirrus.planning.core.graph;

import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure tag/structure-driven machine capability grouping (ZERO hardcoded Minecraft/mod names!).
 */
public final class MachineCapabilityGroups {

    private static final String tagHandOperable = "calculator_cirrus:system/hand_operable";

    private MachineCapabilityGroups() {}

    public static MachineCapabilityId capabilityOf(String systemId) {
        String normalized = systemId == null || systemId.isEmpty() ? "unknown/unknown" : systemId.replace(':', '/');
        return new MachineCapabilityId("cirrus:capability/system/" + normalized);
    }

    public static List<String> orderMachines(String systemId, List<String> rawMachines) {
        String ownerMod = extractSystemOwnerMod(systemId);
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (rawMachines != null) {
            for (String m : rawMachines) {
                if (m != null && !m.isEmpty()) {
                    unique.add(m);
                }
            }
        }
        List<String> sorted = new ArrayList<>(unique);
        sorted.sort(
            Comparator.<String>comparingInt(m -> machineOrderRank(m, ownerMod))
                    .thenComparing(Comparator.naturalOrder())
        );
        return List.copyOf(sorted);
    }

    private static int machineOrderRank(String machineId, @Nullable String ownerMod) {
        if (ownerMod != null && machineId.startsWith(ownerMod + ":")) {
            return 0;
        }
        return 1;
    }

    private static @Nullable String extractSystemOwnerMod(String systemId) {
        if (systemId == null || systemId.isEmpty()) return null;
        int slash = systemId.indexOf('/');
        if (slash > 0 && systemId.startsWith("jei:category/")) {
            int start = "jei:category/".length();
            int nextSlash = systemId.indexOf('/', start);
            return nextSlash > start ? systemId.substring(start, nextSlash) : null;
        }
        int colon = systemId.indexOf(':');
        return colon > 0 ? systemId.substring(0, colon) : null;
    }

    public static boolean isHandCraftable(
        String systemId,
        Set<String> systemTags,
        List<String> rawMachines,
        int inputSlotCount
    ) {
        if (systemTags != null && systemTags.contains(tagHandOperable)) {
            return true;
        }
        return (rawMachines == null || rawMachines.isEmpty()) && inputSlotCount <= 4;
    }

    public static boolean isHandCraftable(String systemId, List<String> rawMachines, int inputSlotCount) {
        return isHandCraftable(systemId, Set.of(), rawMachines, inputSlotCount);
    }
}
