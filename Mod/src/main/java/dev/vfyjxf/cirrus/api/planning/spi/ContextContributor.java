package dev.vfyjxf.cirrus.api.planning.spi;

import dev.vfyjxf.cirrus.api.planning.context.ContextAttributeView;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;

/** SPI 1: Injects domain/mod context attributes into {@link PlanningContext}. */
public interface ContextContributor {
    String contributorId();

    void contribute(ContextAttributeView.Builder target, PlanningContext baseContext);
}
