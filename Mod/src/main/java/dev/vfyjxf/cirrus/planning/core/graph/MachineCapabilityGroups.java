package dev.vfyjxf.cirrus.planning.core.graph;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystemTags;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure tag/structure-driven machine capability grouping (zero hardcoded Minecraft/mod names).
 */
public final class MachineCapabilityGroups {

    private MachineCapabilityGroups() {}

    public static MachineCapabilityId capabilityOf(Namespace systemId) {
        if (systemId == null) {
            return new MachineCapabilityId(CirrusNamespaces.ofMod("capability/system/unknown/unknown"));
        }
        return new MachineCapabilityId(
            CirrusNamespaces.ofMod("capability/system/" + systemId.root() + "/" + systemId.path())
        );
    }

    public static MachineCapabilityId capabilityOfMachine(MachineEntry machine) {
        if (machine == null) {
            return new MachineCapabilityId(CirrusNamespaces.ofMod("capability/machine/unknown"));
        }
        return machine.contentKey()
                .map(k -> {
                    var loc = k.location();
                    if (loc != null) {
                        return new MachineCapabilityId(
                            CirrusNamespaces.ofMod("capability/machine/" + loc.getNamespace() + "/" + loc.getPath())
                        );
                    }
                    return new MachineCapabilityId(CirrusNamespaces.ofMod("capability/machine/custom"));
                })
                .orElseGet(() -> new MachineCapabilityId(CirrusNamespaces.ofMod("capability/machine/custom")));
    }

    public static List<MachineEntry> orderMachines(Namespace systemId, List<MachineEntry> rawMachines) {
        String ownerMod = extractSystemOwnerMod(systemId);
        LinkedHashSet<MachineEntry> unique = new LinkedHashSet<>();
        if (rawMachines != null) {
            for (MachineEntry m : rawMachines) {
                if (m != null) {
                    unique.add(m);
                }
            }
        }
        List<MachineEntry> sorted = new ArrayList<>(unique);
        sorted.sort(
            Comparator.<MachineEntry>comparingInt(m -> machineOrderRank(m, ownerMod))
                    .thenComparing(MachineEntry.comparator)
        );
        return List.copyOf(sorted);
    }

    private static int machineOrderRank(MachineEntry machine, @Nullable String ownerMod) {
        if (ownerMod == null) {
            return 1;
        }
        return machine.contentKey()
                .map(ComponentStackKey::namespace)
                .filter(ownerMod::equals)
                .isPresent() ? 0 : 1;
    }

    private static @Nullable String extractSystemOwnerMod(@Nullable Namespace systemId) {
        if (systemId == null) return null;
        if ("jei".equals(systemId.root()) && systemId.path().startsWith("category/")) {
            int start = "category/".length();
            int nextSlash = systemId.path().indexOf('/', start);
            return nextSlash > start ? systemId.path().substring(start, nextSlash) : null;
        }
        return systemId.root();
    }

    public static boolean isHandCraftable(
        Namespace systemId,
        Set<Namespace> systemTags,
        List<MachineEntry> rawMachines,
        int inputSlotCount
    ) {
        if (systemTags != null && systemTags.contains(RecipeSystemTags.handOperable)) {
            return true;
        }
        return (rawMachines == null || rawMachines.isEmpty()) && inputSlotCount <= 4;
    }

    public static boolean isHandCraftable(Namespace systemId, List<MachineEntry> rawMachines, int inputSlotCount) {
        return isHandCraftable(systemId, Set.of(), rawMachines, inputSlotCount);
    }
}
