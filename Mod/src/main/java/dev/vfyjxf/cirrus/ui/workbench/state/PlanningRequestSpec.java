package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;

import java.util.List;
import java.util.Objects;

public record PlanningRequestSpec(
    List<TargetDemand> targets,
    PlanningProfile profile,
    PlanningBudget budget,
    boolean useLiveStock,
    StockLedger reserved,
    ProgressionState progression,
    List<GuidanceRule> sessionRules
) {

    public PlanningRequestSpec {
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(reserved, "reserved");
        Objects.requireNonNull(progression, "progression");
        Objects.requireNonNull(sessionRules, "sessionRules");
        targets = List.copyOf(targets);
        sessionRules = List.copyOf(sessionRules);
    }
}
