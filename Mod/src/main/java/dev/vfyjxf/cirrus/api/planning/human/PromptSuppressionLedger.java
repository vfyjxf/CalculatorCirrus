package dev.vfyjxf.cirrus.api.planning.human;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Idempotent prompt deduplication, exponential backoff (b0 * 2^j), and strict zero-repeat ledger.
 */
public final class PromptSuppressionLedger {

    public static final long defaultBackoffBase = 8L;

    public record ExposureRecord(long lastAskedQuerySeq, int unansweredCount) {}

    private final TreeSet<String> permanentlySuppressed = new TreeSet<>();
    private final TreeMap<String, ExposureRecord> unansweredExposures = new TreeMap<>();
    private final boolean strictZeroRepeat;
    private final long backoffBase;

    public PromptSuppressionLedger() {
        this(false, defaultBackoffBase);
    }

    public PromptSuppressionLedger(boolean strictZeroRepeat, long backoffBase) {
        this.strictZeroRepeat = strictZeroRepeat;
        this.backoffBase = Math.max(1L, backoffBase);
    }

    public void markAnsweredOrDismissed(QuestionKey key) {
        Objects.requireNonNull(key, "key");
        String id = key.stableString();
        permanentlySuppressed.add(id);
        unansweredExposures.remove(id);
    }

    public void recordShownUnanswered(QuestionKey key, long currentQuerySeq) {
        Objects.requireNonNull(key, "key");
        String id = key.stableString();
        if (permanentlySuppressed.contains(id)) {
            return;
        }
        ExposureRecord prev = unansweredExposures.get(id);
        int nextCount = prev == null ? 1 : Math.min(30, prev.unansweredCount() + 1);
        unansweredExposures.put(id, new ExposureRecord(currentQuerySeq, nextCount));
    }

    public boolean isSuppressed(QuestionKey key, long currentQuerySeq) {
        Objects.requireNonNull(key, "key");
        String id = key.stableString();
        if (permanentlySuppressed.contains(id)) {
            return true;
        }
        ExposureRecord rec = unansweredExposures.get(id);
        if (rec == null) {
            return false;
        }
        if (strictZeroRepeat) {
            return true;
        }
        long waitQueries = backoffBase << Math.min(20, Math.max(0, rec.unansweredCount() - 1));
        return currentQuerySeq < rec.lastAskedQuerySeq() + waitQueries;
    }

    public void invalidateForFactChange(DecisionPromptKind kind, String subjectId) {
        String prefix = kind.name() + ":" + subjectId + ":";
        permanentlySuppressed.removeIf(k -> k.startsWith(prefix));
        unansweredExposures.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public Set<String> permanentlySuppressedKeys() {
        return Collections.unmodifiableSet(permanentlySuppressed);
    }

    public Map<String, ExposureRecord> unansweredExposureSnapshot() {
        return Collections.unmodifiableMap(unansweredExposures);
    }
}
