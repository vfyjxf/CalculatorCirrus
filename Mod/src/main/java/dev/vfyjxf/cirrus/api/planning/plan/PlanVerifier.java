package dev.vfyjxf.cirrus.api.planning.plan;

import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;

public interface PlanVerifier {

    VerificationCertificate verify(ProductionPlan plan, PlanningRequest request);
}
