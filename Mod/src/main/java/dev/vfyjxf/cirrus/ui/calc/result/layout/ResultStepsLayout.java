package dev.vfyjxf.cirrus.ui.calc.result.layout;

import dev.vfyjxf.cirrus.ui.calc.result.ResultStepsState;
import dev.vfyjxf.cirrus.ui.calc.result.StepTree;

import java.util.List;

public interface ResultStepsLayout {
    List<StepBlock> build(StepTree tree, ResultStepsState state);
}
