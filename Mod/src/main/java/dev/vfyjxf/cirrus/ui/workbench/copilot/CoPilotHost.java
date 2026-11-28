package dev.vfyjxf.cirrus.ui.workbench.copilot;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.state.HistoryEntry;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Every host capability the Co-Pilot dock widgets need; the workbench shell adapts them to the planning controller.
 */
public interface CoPilotHost {

    WorkbenchUiSnapshot snapshot();

    CirrusPalette palette();

    void refine(HumanFeedback feedback, @Nullable HistoryEntry entry);

    void jumpToHistory(int index);

    void relaxConflictRules(List<String> ruleIds);

    void promoteSessionRulesToWorld(@Nullable String singleRuleId);

    void selectPlan(@Nullable String planId);

    void setComparePlan(@Nullable String planId);

    void setSelectedSteps(Set<String> stepIds);

    void focusStepInDag(String stepId);

    void openAlternativesFor(ComponentStackKey resource);

    void toast(LangEntry message);

    boolean isReducedMotion();
}
