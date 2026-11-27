package dev.vfyjxf.cirrus.ui.workbench.tooltip;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface OreUiTooltipProvider {

    @Nullable
    OreUiRichTooltip richTooltipAt(double localX, double localY);
}
