package dev.vfyjxf.cirrus.api.planning.context;

import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public record FacilitySnapshot(
    List<OwnedMachineGroup> ownedMachines,
    List<AutomatedLine> automatedLines,
    List<AutomatedRecipeCapability> automatedRecipes,
    Set<String> unlockedSystems
) {

    public static final FacilitySnapshot empty = new FacilitySnapshot(List.of(), List.of(), List.of(), Set.of());

    public FacilitySnapshot {
        Objects.requireNonNull(ownedMachines, "ownedMachines");
        Objects.requireNonNull(automatedLines, "automatedLines");
        Objects.requireNonNull(automatedRecipes, "automatedRecipes");
        Objects.requireNonNull(unlockedSystems, "unlockedSystems");
        ownedMachines = List.copyOf(ownedMachines);
        automatedLines = List.copyOf(automatedLines);
        automatedRecipes = List.copyOf(automatedRecipes);
        if (unlockedSystems.isEmpty()) {
            unlockedSystems = Set.of();
        } else {
            TreeSet<String> sorted = new TreeSet<>();
            for (String s : unlockedSystems) {
                sorted.add(Objects.requireNonNull(s));
            }
            unlockedSystems = Collections.unmodifiableSet(sorted);
        }
    }

    public List<OwnedMachineGroup> ownedMachinesFor(MachineCapabilityId cap) {
        Objects.requireNonNull(cap, "cap");
        List<OwnedMachineGroup> out = new ArrayList<>();
        for (OwnedMachineGroup g : ownedMachines) {
            if (g.count() > 0 && g.capability().equals(cap)) {
                out.add(g);
            }
        }
        return out;
    }

    public int ownedCountOf(String machineId) {
        Objects.requireNonNull(machineId, "machineId");
        int sum = 0;
        for (OwnedMachineGroup g : ownedMachines) {
            if (g.machineId().equals(machineId)) {
                sum += g.count();
            }
        }
        return sum;
    }

    public List<AutomatedLine> automatedLinesFor(ResourceKey output) {
        Objects.requireNonNull(output, "output");
        List<AutomatedLine> out = new ArrayList<>();
        for (AutomatedLine line : automatedLines) {
            if (line.output().equals(output)) {
                out.add(line);
            }
        }
        return out;
    }

    public Optional<AutomatedRecipeCapability> automatedRecipe(CompiledRecipeKey key) {
        Objects.requireNonNull(key, "key");
        for (AutomatedRecipeCapability cap : automatedRecipes) {
            if (cap.recipeKey().equals(key)) {
                return Optional.of(cap);
            }
        }
        return Optional.empty();
    }
}
