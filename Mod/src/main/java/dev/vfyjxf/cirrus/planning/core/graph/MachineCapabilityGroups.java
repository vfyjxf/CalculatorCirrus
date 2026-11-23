package dev.vfyjxf.cirrus.planning.core.graph;

import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MachineCapabilityGroups {

    public static final MachineCapabilityId CAP_CRAFTING = new MachineCapabilityId("cirrus:capability/crafting");
    public static final MachineCapabilityId CAP_SMELTING = new MachineCapabilityId("cirrus:capability/smelting");
    public static final MachineCapabilityId CAP_BLASTING = new MachineCapabilityId("cirrus:capability/blasting");
    public static final MachineCapabilityId CAP_SMOKING = new MachineCapabilityId("cirrus:capability/smoking");
    public static final MachineCapabilityId CAP_STONECUTTING = new MachineCapabilityId("cirrus:capability/stonecutting");
    public static final MachineCapabilityId CAP_SMITHING = new MachineCapabilityId("cirrus:capability/smithing");

    private static final Map<String, MachineCapabilityId> WELL_KNOWN_SYSTEMS = Map.of(
            "minecraft:crafting", CAP_CRAFTING,
            "minecraft:smelting", CAP_SMELTING,
            "minecraft:blasting", CAP_BLASTING,
            "minecraft:smoking", CAP_SMOKING,
            "minecraft:stonecutting", CAP_STONECUTTING,
            "minecraft:smithing", CAP_SMITHING
    );

    private static final Map<MachineCapabilityId, String> DEFAULT_MACHINES = Map.of(
            CAP_CRAFTING, "minecraft:crafting_table",
            CAP_SMELTING, "minecraft:furnace",
            CAP_BLASTING, "minecraft:blast_furnace",
            CAP_SMOKING, "minecraft:smoker",
            CAP_STONECUTTING, "minecraft:stonecutter",
            CAP_SMITHING, "minecraft:smithing_table"
    );

    private static final Set<String> NON_HAND_VANILLA_SYSTEMS = Set.of(
            "minecraft:smelting",
            "minecraft:blasting",
            "minecraft:smoking",
            "minecraft:campfire_cooking",
            "minecraft:stonecutting",
            "minecraft:smithing",
            "minecraft:brewing"
    );

    private MachineCapabilityGroups() {
    }

    public static MachineCapabilityId capabilityOf(String systemId) {
        MachineCapabilityId wellKnown = WELL_KNOWN_SYSTEMS.get(systemId);
        if (wellKnown != null) {
            return wellKnown;
        }
        String normalized = systemId == null ? "unknown/unknown" : systemId.replace(':', '/');
        return new MachineCapabilityId("cirrus:capability/system/" + normalized);
    }

    public static String defaultMachineFor(MachineCapabilityId capability) {
        return DEFAULT_MACHINES.get(capability);
    }

    public static List<String> orderMachines(String systemId, List<String> rawMachines) {
        MachineCapabilityId cap = capabilityOf(systemId);
        String preferred = DEFAULT_MACHINES.get(cap);
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (rawMachines != null) {
            for (String m : rawMachines) {
                if (m != null && !m.isEmpty()) {
                    unique.add(m);
                }
            }
        }
        List<String> sorted = new ArrayList<>(unique);
        sorted.sort(Comparator
                .<String>comparingInt(m -> m.equals(preferred) ? 0 : (m.startsWith("minecraft:") ? 1 : 2))
                .thenComparing(Comparator.naturalOrder()));
        return List.copyOf(sorted);
    }

    public static boolean isHandCraftable(String systemId, List<String> rawMachines, int inputSlotCount) {
        if ("minecraft:crafting".equals(systemId)) {
            return true;
        }
        if (NON_HAND_VANILLA_SYSTEMS.contains(systemId)) {
            return false;
        }
        return (rawMachines == null || rawMachines.isEmpty()) && inputSlotCount <= 4;
    }
}
