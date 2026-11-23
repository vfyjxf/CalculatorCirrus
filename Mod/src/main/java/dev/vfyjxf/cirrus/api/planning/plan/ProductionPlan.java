package dev.vfyjxf.cirrus.api.planning.plan;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;

import java.util.List;
import java.util.Objects;

public record ProductionPlan(
        String planId,
        String strategyLabelKey,
        List<PlanStep> steps,
        PreparationSummary preparation,
        List<ByproductEntry> surplusByproducts,
        CostVector totalCost,
        VerificationCertificate certificate
) {

    public ProductionPlan {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(strategyLabelKey, "strategyLabelKey");
        Objects.requireNonNull(steps, "steps");
        Objects.requireNonNull(preparation, "preparation");
        Objects.requireNonNull(surplusByproducts, "surplusByproducts");
        Objects.requireNonNull(totalCost, "totalCost");
        Objects.requireNonNull(certificate, "certificate");
        steps = List.copyOf(steps);
        surplusByproducts = List.copyOf(surplusByproducts);
    }
}
