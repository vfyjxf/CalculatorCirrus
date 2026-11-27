package dev.vfyjxf.cirrus.ui.workbench.settings;

import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;

import java.util.List;
import java.util.Set;

public enum ProgressionPreset {
    overworld("overworld", List.of(), List.of("minecraft:overworld")), nether(
        "nether",
        List.of("minecraft:nether"),
        List.of("minecraft:overworld", "minecraft:the_nether")
    ), theEnd(
        "the_end",
        List.of("minecraft:nether", "minecraft:the_end"),
        List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")
    );

    private final String id;
    private final List<String> unlockedStages;
    private final List<String> accessibleDimensions;

    ProgressionPreset(String id, List<String> unlockedStages, List<String> accessibleDimensions) {
        this.id = id;
        this.unlockedStages = unlockedStages;
        this.accessibleDimensions = accessibleDimensions;
    }

    public String id() {
        return id;
    }

    public LangEntry labelKey() {
        return LangEntry.of("cirrus.ui.workbench.settings.progression." + id);
    }

    public List<String> unlockedStages() {
        return unlockedStages;
    }

    public List<String> accessibleDimensions() {
        return accessibleDimensions;
    }

    public ProgressionState toProgressionState() {
        return new ProgressionState(Set.copyOf(unlockedStages), Set.copyOf(accessibleDimensions));
    }

    public static ProgressionPreset defaultPreset() {
        return overworld;
    }
}
