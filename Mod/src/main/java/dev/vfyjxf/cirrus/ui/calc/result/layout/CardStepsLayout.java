package dev.vfyjxf.cirrus.ui.calc.result.layout;

import dev.vfyjxf.cirrus.ui.calc.result.ResultStepsState;
import dev.vfyjxf.cirrus.ui.calc.result.StepNode;

public final class CardStepsLayout extends TreeStepsLayout {
    @Override
    protected boolean isFolded(ResultStepsState state, StepNode node) {
        return state.isCardFolded(node.id());
    }
}
