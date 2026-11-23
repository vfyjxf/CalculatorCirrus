package dev.vfyjxf.cirrus.api.planning.human;

import java.util.List;

public interface HumanGuidanceStore {

    void putRule(GuidanceRule rule);

    void removeRule(String ruleId);

    List<GuidanceRule> rulesInScope(GuidanceScope scope);

    HumanGuidanceView snapshotMergedView(List<GuidanceRule> sessionOverrides, List<GuidanceRule> queryOverrides);
}
