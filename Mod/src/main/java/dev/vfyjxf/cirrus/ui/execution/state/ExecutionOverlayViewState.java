package dev.vfyjxf.cirrus.ui.execution.state;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.ui.execution.page.RecipeActiveContext;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionTelemetrySnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriver;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayTraceSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayTargetProgress;
import org.jetbrains.annotations.Nullable;

public interface ExecutionOverlayViewState {
    //region snapshot

    @Nullable ExecutionDriver driver();

    @Nullable ExecutionDriverSnapshot snapshot();

    @Nullable ExecutionTelemetrySnapshot telemetry();

    @Nullable ExecutionOverlayTraceSnapshot trace();

    @Nullable OverlayTargetProgress targetProgress();

    boolean calculating();

    @Nullable RecipeActiveContext activeRecipeContext();
    //endregion

    //region target

    @Nullable String targetLabel();

    int targetTimes();

    void setTargetTimes(int times);

    void flushTargetTimes();

    RecipeContent targetContent();
    //endregion

    //region trace

    boolean traceEnabled();

    void setTraceEnabled(boolean enabled);
    //endregion
}

