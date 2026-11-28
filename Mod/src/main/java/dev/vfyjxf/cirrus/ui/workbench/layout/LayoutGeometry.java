package dev.vfyjxf.cirrus.ui.workbench.layout;

import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.TreeMap;

final class LayoutGeometry {

    static final float layerGap = 86.0f;
    static final float nodeGap = 20.0f;
    static final float rowGap = 68.0f;
    static final float colGap = 22.0f;

    static final float acquireWidth = 172.0f;
    static final float acquireHeight = 58.0f;
    static final float craftWidth = 208.0f;
    static final float craftHeight = 86.0f;
    static final float convertWidth = 184.0f;
    static final float convertHeight = 72.0f;
    static final float cycleCollapsedWidth = 204.0f;
    static final float cycleCollapsedHeight = 84.0f;
    static final float provisionWidth = 188.0f;
    static final float provisionHeight = 70.0f;
    static final float setupWidth = 192.0f;
    static final float setupHeight = 70.0f;
    static final float linePullWidth = 172.0f;
    static final float linePullHeight = 58.0f;

    static final float innerCardWidth = 128.0f;
    static final float innerCardHeight = 40.0f;
    static final float innerCardGap = 8.0f;
    static final float innerPadding = 12.0f;
    static final float innerTopBar = 26.0f;

    static final float swimlanePadding = 10.0f;
    static final float swimlaneGap = 12.0f;

    private static final float roundScale = 100.0f;

    private LayoutGeometry() {}

    record NodeSize(float width, float height, boolean expanded) {}

    static float round(double value) {
        if (!Double.isFinite(value)) {
            return 0.0f;
        }
        return (float) (Math.round(value * roundScale) / roundScale);
    }

    static double duration(PlanStep step) {
        double h = step.stepCost().humanActiveSeconds();
        double p = step.stepCost().passiveWaitSeconds();
        return Math.max(0.5, h + p);
    }

    static NodeSize sizeOf(PlanStep step, boolean expand) {
        return switch (step) {
            case AcquireStep ignored -> new NodeSize(acquireWidth, acquireHeight, false);
            case LinePullStep ignored -> new NodeSize(linePullWidth, linePullHeight, false);
            case ProvisionMachineStep ignored -> new NodeSize(provisionWidth, provisionHeight, false);
            case SetupLineStep ignored -> new NodeSize(setupWidth, setupHeight, false);
            case CraftStep ignored -> new NodeSize(craftWidth, craftHeight, false);
            case ConvertStep ignored -> new NodeSize(convertWidth, convertHeight, false);
            case CycleLoopStep loop -> expand && !loop.singleIterationTemplate().isEmpty()
                    ? expandedLoopSize(loop)
                    : new NodeSize(cycleCollapsedWidth, cycleCollapsedHeight, false);
        };
    }

    private static NodeSize expandedLoopSize(CycleLoopStep loop) {
        int cards = loop.singleIterationTemplate().size();
        float width = innerPadding * 2.0f + cards * innerCardWidth + (cards - 1) * innerCardGap;
        float height = innerTopBar + innerPadding * 2.0f + innerCardHeight;
        return new NodeSize(width, height, true);
    }

    static int stepTypeOrdinal(PlanStep step) {
        return switch (step) {
            case AcquireStep ignored -> 0;
            case LinePullStep ignored -> 1;
            case ProvisionMachineStep ignored -> 2;
            case SetupLineStep ignored -> 3;
            case CraftStep ignored -> 4;
            case ConvertStep ignored -> 5;
            case CycleLoopStep ignored -> 6;
        };
    }

    static int compareSteps(PlanStep a, PlanStep b) {
        int byType = Integer.compare(stepTypeOrdinal(a), stepTypeOrdinal(b));
        return byType != 0 ? byType : a.stepId().compareTo(b.stepId());
    }

    @Nullable
    static Namespace systemIdOf(PlanStep step) {
        return switch (step) {
            case CraftStep craft -> craft.recipeKey().systemId();
            case ConvertStep convert -> convert.conversionRecipeKey().systemId();
            case SetupLineStep setup -> setup.systemId();
            case CycleLoopStep loop -> dominantLoopSystemId(loop);
            case AcquireStep ignored -> null;
            case LinePullStep ignored -> null;
            case ProvisionMachineStep ignored -> null;
        };
    }

    private static @Nullable Namespace dominantLoopSystemId(CycleLoopStep loop) {
        TreeMap<Namespace, Integer> counts = new TreeMap<>(Comparator.comparing(Namespace::toString));
        for (CraftStep template : loop.singleIterationTemplate()) {
            counts.merge(template.recipeKey().systemId(), 1, Integer::sum);
        }
        Namespace best = null;
        int bestCount = -1;
        for (var entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    @Nullable
    static MachineEntry primaryMachineOf(PlanStep step) {
        return switch (step) {
            case CraftStep craft -> craft.chosenMachine().orElse(null);
            case ProvisionMachineStep provision -> provision.machine();
            case SetupLineStep setup -> setup.primaryMachine();
            case AcquireStep ignored -> null;
            case LinePullStep ignored -> null;
            case ConvertStep ignored -> null;
            case CycleLoopStep ignored -> null;
        };
    }

}
