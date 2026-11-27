package dev.vfyjxf.cirrus.api.planning.service;

import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;

import java.util.List;
import java.util.Objects;

public record PlanningResult(
    PlanningStatus status,
    List<ProductionPlan> paretoPlans,
    List<ProductionPlan> conditionalPlans,
    List<DecisionPrompt> decisionPrompts,
    PlanningDiagnostics diagnostics
) {

    public PlanningResult(
        PlanningStatus status,
        List<ProductionPlan> paretoPlans,
        List<DecisionPrompt> decisionPrompts,
        PlanningDiagnostics diagnostics
    ) {
        this(status, paretoPlans, List.of(), decisionPrompts, diagnostics);
    }

    public PlanningResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(paretoPlans, "paretoPlans");
        Objects.requireNonNull(conditionalPlans, "conditionalPlans");
        Objects.requireNonNull(decisionPrompts, "decisionPrompts");
        Objects.requireNonNull(diagnostics, "diagnostics");
        paretoPlans = List.copyOf(paretoPlans);
        conditionalPlans = List.copyOf(conditionalPlans);
        decisionPrompts = List.copyOf(decisionPrompts);
    }
}
