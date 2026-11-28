package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;

public sealed interface PaletteAction permits PaletteAction.SetTarget, PaletteAction.AddToBasket,
        PaletteAction.FocusStep, PaletteAction.ApplyFeedback, PaletteAction.RunCommand {

    record SetTarget(ComponentStackKey resource) implements PaletteAction {}

    record AddToBasket(ComponentStackKey resource) implements PaletteAction {}

    record FocusStep(String stepId) implements PaletteAction {}

    record ApplyFeedback(HumanFeedback feedback) implements PaletteAction {}

    record RunCommand(String commandId) implements PaletteAction {}
}
