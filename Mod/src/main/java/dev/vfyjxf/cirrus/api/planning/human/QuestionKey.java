package dev.vfyjxf.cirrus.api.planning.human;

import java.util.Objects;

/**
 * Idempotent deduplication key for human prompts:
 * ignores irrelevant inventory fluctuations and maps recipes sharing a capability to one key.
 */
public record QuestionKey(
    DecisionPromptKind kind,
    String subjectId,
    GuidanceScope defaultScope,
    long factVersion
) implements Comparable<QuestionKey> {

    public QuestionKey {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(defaultScope, "defaultScope");
    }

    public String stableString() {
        return kind.name() + ":" + subjectId + ":" + defaultScope.name() + ":v" + factVersion;
    }

    @Override
    public int compareTo(QuestionKey other) {
        int c = this.kind.compareTo(other.kind);
        if (c != 0) return c;
        c = this.subjectId.compareTo(other.subjectId);
        if (c != 0) return c;
        c = this.defaultScope.compareTo(other.defaultScope);
        if (c != 0) return c;
        return Long.compare(this.factVersion, other.factVersion);
    }
}
