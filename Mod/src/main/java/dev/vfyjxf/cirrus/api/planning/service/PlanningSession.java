package dev.vfyjxf.cirrus.api.planning.service;

import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;

public interface PlanningSession extends AutoCloseable {

    PlanningResult currentResult();

    PlanningResult refine(HumanFeedback feedback);

    PlanningResult undo();

    PlanningResult redo();

    @Override
    void close();
}
