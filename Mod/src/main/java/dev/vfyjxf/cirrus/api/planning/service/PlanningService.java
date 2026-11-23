package dev.vfyjxf.cirrus.api.planning.service;

public interface PlanningService {

    PlanningResult planOnce(PlanningRequest request);

    PlanningSession openSession(PlanningRequest initialRequest);
}
