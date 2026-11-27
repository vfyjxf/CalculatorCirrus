package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;

import java.util.List;
import java.util.Objects;

public record HistoryEntry(
    String entryId,
    String labelKey,
    List<GuidanceRule> rules,
    boolean persistedToWorld,
    HistoryKind kind,
    long sequence
) {

    public enum HistoryKind {
        initial, answerPrompt, applyRules, ownMachine, overrideStock, rejectStep, pinRecipe, relaxRules, promoteWorld
    }

    public HistoryEntry {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(labelKey, "labelKey");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(kind, "kind");
        rules = List.copyOf(rules);
    }
}
