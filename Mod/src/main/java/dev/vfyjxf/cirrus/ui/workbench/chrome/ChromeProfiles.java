package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.cost.CostWeights;
import dev.vfyjxf.cirrus.api.planning.cost.ObjectivePreset;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * Pure preset, weight and budget conversions shared by the chrome widgets.
 */
public final class ChromeProfiles {

    public static final int weightAxes = 5;
    public static final double weightMin = 0.0;
    public static final double weightMax = 8.0;
    private static final double budgetScale = 2.0;
    private static final int planLimitStep = 2;

    private ChromeProfiles() {}

    public static PlanningProfile forPreset(ObjectivePreset preset, PlanningProfile current) {
        Objects.requireNonNull(preset, "preset");
        PlanningProfile base = current == null ? PlanningProfile.defaultBalanced() : current;
        return new PlanningProfile(
            presetId(preset),
            preset,
            CostWeights.forPreset(preset),
            base.diversityThreshold(),
            base.maxMachineRecursionDepth()
        );
    }

    public static PlanningProfile withWeights(PlanningProfile current, CostWeights weights) {
        Objects.requireNonNull(weights, "weights");
        PlanningProfile base = current == null ? PlanningProfile.defaultBalanced() : current;
        return new PlanningProfile(
            base.profileId(),
            base.preset(),
            weights,
            base.diversityThreshold(),
            base.maxMachineRecursionDepth()
        );
    }

    public static String presetId(ObjectivePreset preset) {
        return "preset_" + Objects.requireNonNull(preset, "preset").name().toLowerCase(Locale.ROOT);
    }

    public static double axisWeight(CostWeights weights, int axis) {
        CostWeights source = weights == null ? CostWeights.forPreset(ObjectivePreset.autoSmart) : weights;
        return switch (axis) {
            case 0 -> source.humanActiveWeight();
            case 1 -> source.passiveWaitWeight();
            case 2 -> source.machineSetupWeight();
            case 3 -> source.materialScarcityWeight();
            case 4 -> source.cognitiveFrictionWeight();
            default -> throw new IllegalArgumentException("axis must be in [0, 4], got " + axis);
        };
    }

    public static CostWeights replaceAxis(CostWeights weights, int axis, double value) {
        double[] axes = new double[weightAxes];
        for (int index = 0; index < weightAxes; index++) {
            axes[index] = axisWeight(weights, index);
        }
        axes[axis] = clampWeight(value);
        return new CostWeights(axes[0], axes[1], axes[2], axes[3], axes[4]);
    }

    public static double clampWeight(double value) {
        if (!Double.isFinite(value)) {
            return weightMin;
        }
        return Math.max(weightMin, Math.min(weightMax, value));
    }

    public static PlanningBudget deepened(PlanningBudget budget) {
        PlanningBudget base = budget == null ? PlanningBudget.defaultInteractive() : budget;
        Duration soft = scale(base.softTimeLimit());
        Duration hard = scale(base.hardTimeLimit());
        return new PlanningBudget(
            soft,
            hard,
            base.maxSubgraphNodes(),
            base.maxSubgraphRecipes(),
            base.maxReturnedPlans() + planLimitStep,
            base.maxDecisionPrompts(),
            base.enableMachineSetCoverRefinement(),
            base.enableBatchCouplingRefinement(),
            base.maxProducersPerResource(),
            base.searchWorkBudget()
        );
    }

    private static Duration scale(Duration value) {
        long scaled = Math.max(1L, (long) (value.toMillis() * budgetScale));
        return Duration.ofMillis(scaled);
    }
}
