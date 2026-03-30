package dev.vfyjxf.cirrus.ui.widget.execution;

import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionTelemetrySnapshot;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.jetbrains.annotations.Nullable;

/**
 * Standard interaction contract for execution-overlay hosts.
 * <p>
 * This keeps overlay control wiring reusable across workbench overlays,
 * standalone test screens, and future machine UIs.
 */
public interface ExecutionOverlayController extends ExecutionOverlayHost {

    record OverlayMaterialRequirement(ItemStack stack, long required, long available, long missing) {
        public OverlayMaterialRequirement {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            if (!stack.isEmpty()) {
                stack.setCount(1);
            }
            required = Math.max(0L, required);
            available = Math.max(0L, available);
            missing = Math.max(0L, missing);
        }
    }

    record OverlayTargetProgress(ItemStack stack, long required, long current) {
        public OverlayTargetProgress {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            if (!stack.isEmpty()) {
                stack.setCount(1);
            }
            required = Math.max(0L, required);
            current = Math.max(0L, current);
        }
    }

    //region Session
    @Override
    ExecutionSession executionOverlaySession();
    //endregion

    //region Actions
    void overlayAdvanceSteps(int steps);

    void overlayToggleAutoAdvance();

    void overlayReset();
    //endregion

    //region State
    boolean overlayAutoAdvanceEnabled();

    double overlayRatePerSecond();

    void overlaySetRatePerSecond(double value);

    ExecutionTelemetrySnapshot overlayTelemetrySnapshot();

    default ImmutableList<OverlayMaterialRequirement> overlayMaterialRequirements() {
        return Lists.immutable.empty();
    }

    default @Nullable OverlayTargetProgress overlayTargetProgress() {
        return null;
    }
    //endregion
}
