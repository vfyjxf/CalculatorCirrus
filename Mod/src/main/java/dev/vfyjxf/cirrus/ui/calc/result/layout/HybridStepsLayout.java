package dev.vfyjxf.cirrus.ui.calc.result.layout;

import dev.vfyjxf.cirrus.ui.calc.result.ResultStepsState;
import dev.vfyjxf.cirrus.ui.calc.result.StepTree;

import java.util.List;

/**
 * Hybrid layout: same flat card list as {@link ListStepsLayout},
 * intended to be rendered with a {@code DependencyGraphWidget} minimap at the top.
 */
public final class HybridStepsLayout implements ResultStepsLayout {

    private final ListStepsLayout delegate = new ListStepsLayout();

    @Override
    public List<StepBlock> build(StepTree tree, ResultStepsState state) {
        return delegate.build(tree, state);
    }
}
