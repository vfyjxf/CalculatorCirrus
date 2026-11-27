package dev.vfyjxf.cirrus.config;

import dev.vfyjxf.cirrus.api.planning.cost.CostWeights;
import dev.vfyjxf.cirrus.api.planning.cost.ObjectivePreset;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;

public enum CalculatorRuleSelection {
    preview, active, execution, strict, assured;

    public ObjectivePreset toObjectivePreset() {
        return switch (this) {
            case preview, active -> ObjectivePreset.autoSmart;
            case execution -> ObjectivePreset.quickHandcraft;
            case strict -> ObjectivePreset.stayAtBase;
            case assured -> ObjectivePreset.frugalMaterials;
        };
    }

    public PlanningProfile toPlanningProfile() {
        ObjectivePreset preset = toObjectivePreset();
        return switch (this) {
            case preview -> new PlanningProfile("cirrus:preview", preset, CostWeights.forPreset(preset), 0.25, 2);
            case active -> new PlanningProfile("cirrus:active", preset, CostWeights.forPreset(preset), 0.25, 3);
            case execution -> new PlanningProfile("cirrus:execution", preset, CostWeights.forPreset(preset), 0.20, 3);
            case strict -> new PlanningProfile("cirrus:strict", preset, CostWeights.forPreset(preset), 0.25, 0);
            case assured -> new PlanningProfile("cirrus:assured", preset, CostWeights.forPreset(preset), 0.20, 3);
        };
    }
}
