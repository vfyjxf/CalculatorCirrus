package dev.vfyjxf.cirrus.api.planning.plan;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.List;
import java.util.Objects;

public record SlotBinding(ResourceKey chosenResource, List<ResourceKey> equivalentAlternatives) {

    public SlotBinding {
        Objects.requireNonNull(chosenResource, "chosenResource");
        Objects.requireNonNull(equivalentAlternatives, "equivalentAlternatives");
        equivalentAlternatives = List.copyOf(equivalentAlternatives);
    }
}
