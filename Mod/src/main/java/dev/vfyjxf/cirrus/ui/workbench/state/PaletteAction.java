package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

public sealed interface PaletteAction permits PaletteAction.SetTarget, PaletteAction.AddToBasket,
        PaletteAction.FocusStep, PaletteAction.ApplyFeedback, PaletteAction.RunCommand {

    record SetTarget(ResourceKey resource) implements PaletteAction {}

    record AddToBasket(ResourceKey resource) implements PaletteAction {}

    record FocusStep(String stepId) implements PaletteAction {}

    record ApplyFeedback(HumanFeedback feedback) implements PaletteAction {}

    record RunCommand(String commandId) implements PaletteAction {}
}
