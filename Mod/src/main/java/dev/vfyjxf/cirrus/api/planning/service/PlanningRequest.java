package dev.vfyjxf.cirrus.api.planning.service;

import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

public record PlanningRequest(
        List<TargetDemand> targets,
        PlanningContext context,
        PlanningProfile profile,
        PlanningBudget budget
) {

    public PlanningRequest {
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(budget, "budget");
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("targets must not be empty");
        }
        TreeMap<ResourceKey, Long> merged = new TreeMap<>();
        for (TargetDemand td : targets) {
            Objects.requireNonNull(td, "target");
            merged.merge(td.resource(), td.amount(), Math::addExact);
        }
        List<TargetDemand> normalized = new ArrayList<>(merged.size());
        for (var e : merged.entrySet()) {
            normalized.add(new TargetDemand(e.getKey(), e.getValue()));
        }
        targets = List.copyOf(normalized);
    }
}
