package dev.vfyjxf.cirrus.api.planning.human;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;

import java.util.List;
import java.util.Objects;

public record ConflictDiagnosis(
    ComponentStackKey blockedResource,
    List<String> minimalConflictRuleIds,
    String suggestedResolutionKey
) {

    public ConflictDiagnosis {
        Objects.requireNonNull(blockedResource, "blockedResource");
        Objects.requireNonNull(minimalConflictRuleIds, "minimalConflictRuleIds");
        Objects.requireNonNull(suggestedResolutionKey, "suggestedResolutionKey");
        minimalConflictRuleIds = List.copyOf(minimalConflictRuleIds);
    }
}
