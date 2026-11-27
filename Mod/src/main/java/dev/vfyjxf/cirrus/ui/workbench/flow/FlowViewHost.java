package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;

/**
 * Workbench shell contract the three flow views depend on.
 */
public interface FlowViewHost {

    WorkbenchUiSnapshot snapshot();

    CirrusPalette palette();

    void dispatch(HumanFeedback feedback);

    void onFocusStep(String stepId);

    void toast(LangEntry entry);

    boolean isReducedMotion();
}
