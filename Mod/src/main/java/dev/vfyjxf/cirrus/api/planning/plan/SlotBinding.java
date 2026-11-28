package dev.vfyjxf.cirrus.api.planning.plan;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;

import java.util.List;
import java.util.Objects;

public record SlotBinding(ComponentStackKey chosenResource, List<ComponentStackKey> equivalentAlternatives) {

    public SlotBinding {
        Objects.requireNonNull(chosenResource, "chosenResource");
        Objects.requireNonNull(equivalentAlternatives, "equivalentAlternatives");
        equivalentAlternatives = List.copyOf(equivalentAlternatives);
    }
}
