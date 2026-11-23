package dev.vfyjxf.cirrus.api.planning.service;

import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;

import java.util.List;
import java.util.Objects;

public record PlanningResult(
        PlanningStatus status,
        List<ProductionPlan> paretoPlans,
        List<DecisionPrompt> decisionPrompts,
        PlanningDiagnostics diagnostics
) {

    public PlanningResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(paretoPlans, "paretoPlans");
        Objects.requireNonNull(decisionPrompts, "decisionPrompts");
        Objects.requireNonNull(diagnostics, "diagnostics");
        paretoPlans = List.copyOf(paretoPlans);
        decisionPrompts = List.copyOf(decisionPrompts);
    }
}
