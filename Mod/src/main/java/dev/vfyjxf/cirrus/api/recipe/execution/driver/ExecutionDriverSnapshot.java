package dev.vfyjxf.cirrus.api.recipe.execution.driver;

import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionTelemetrySnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayTraceSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayMaterialRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayTargetProgress;
import dev.vfyjxf.cirrus.api.recipe.execution.ReTranslationHint;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayViewModel;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;

public record ExecutionDriverSnapshot(
    @Nullable ExecutionSession session,
    @Nullable ExecutionOverlayViewModel viewModel,
    ExecutionTelemetrySnapshot telemetry,
    ImmutableList<OverlayMaterialRequirement> materials,
    @Nullable OverlayTargetProgress targetProgress,
    @Nullable ExecutionOverlayTraceSnapshot trace,
    double ratePerSecond,
    boolean autoAdvanceEnabled,
    @Nullable ReTranslationHint reTranslationHint
) {}
