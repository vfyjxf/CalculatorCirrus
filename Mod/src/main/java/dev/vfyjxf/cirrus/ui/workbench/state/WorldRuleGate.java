package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;

interface WorldRuleGate {

    boolean available();

    void putRule(GuidanceRule rule);

    void removeRule(String ruleId);

    void declareOwnedMachine(DeclareOwnedMachineFeedback feedback);
}
