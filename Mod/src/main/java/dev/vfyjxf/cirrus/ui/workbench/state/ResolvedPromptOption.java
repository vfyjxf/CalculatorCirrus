package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.human.DecisionOption;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record ResolvedPromptOption(
    DecisionOption raw,
    @Nullable ComponentStackKey variantResource,
    @Nullable MachineEntry machine,
    CostVector delta,
    boolean hasFollowUp
) {

    public ResolvedPromptOption {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(delta, "delta");
    }
}
