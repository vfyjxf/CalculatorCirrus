package dev.vfyjxf.cirrus.api.planning.plan;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.cost.ScoreBreakdown;

import java.util.List;
import java.util.Objects;

public record ProductionPlan(
    String planId,
    String strategyLabelKey,
    List<PlanStep> steps,
    PreparationSummary preparation,
    List<ByproductEntry> surplusByproducts,
    CostVector totalCost,
    VerificationCertificate certificate,
    ScoreBreakdown scoreBreakdown,
    RegimeSignature regimeSignature,
    boolean conditionalDraft,
    List<String> missingEvidenceKeys
) {

    public ProductionPlan(
        String planId,
        String strategyLabelKey,
        List<PlanStep> steps,
        PreparationSummary preparation,
        List<ByproductEntry> surplusByproducts,
        CostVector totalCost,
        VerificationCertificate certificate
    ) {
        this(
            planId,
            strategyLabelKey,
            steps,
            preparation,
            surplusByproducts,
            totalCost,
            certificate,
            ScoreBreakdown.empty,
            RegimeSignature.baseline,
            false,
            List.of()
        );
    }

    public ProductionPlan {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(strategyLabelKey, "strategyLabelKey");
        Objects.requireNonNull(steps, "steps");
        Objects.requireNonNull(preparation, "preparation");
        Objects.requireNonNull(surplusByproducts, "surplusByproducts");
        Objects.requireNonNull(totalCost, "totalCost");
        Objects.requireNonNull(certificate, "certificate");
        Objects.requireNonNull(scoreBreakdown, "scoreBreakdown");
        Objects.requireNonNull(regimeSignature, "regimeSignature");
        Objects.requireNonNull(missingEvidenceKeys, "missingEvidenceKeys");
        steps = List.copyOf(steps);
        surplusByproducts = List.copyOf(surplusByproducts);
        missingEvidenceKeys = List.copyOf(missingEvidenceKeys);
    }

    public ProductionPlan withScoring(ScoreBreakdown breakdown, RegimeSignature regime) {
        return new ProductionPlan(
            planId,
            strategyLabelKey,
            steps,
            preparation,
            surplusByproducts,
            totalCost,
            certificate,
            breakdown,
            regime,
            conditionalDraft,
            missingEvidenceKeys
        );
    }

    public ProductionPlan asConditionalDraft(List<String> evidenceKeys) {
        return new ProductionPlan(
            planId,
            strategyLabelKey,
            steps,
            preparation,
            surplusByproducts,
            totalCost,
            certificate,
            scoreBreakdown,
            regimeSignature,
            true,
            evidenceKeys
        );
    }
}
