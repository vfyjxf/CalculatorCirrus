package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningSession;

@FunctionalInterface
public interface PlanningBackend {

    PlanningSession openSession(PlanningRequest request);
}
