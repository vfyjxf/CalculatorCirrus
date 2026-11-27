package dev.vfyjxf.cirrus.planning.core.human;

import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceStore;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class LayeredHumanGuidanceStore implements HumanGuidanceStore {

    private final EnumMap<GuidanceScope, LinkedHashMap<String, StampedRule>> byScope = new EnumMap<>(
        GuidanceScope.class
    );
    private final @Nullable Consumer<GuidanceRule> worldSaveListener;
    private long seq;

    public LayeredHumanGuidanceStore() {
        this(null);
    }

    public LayeredHumanGuidanceStore(@Nullable Consumer<GuidanceRule> worldSaveListener) {
        this.worldSaveListener = worldSaveListener;
        for (GuidanceScope scope : GuidanceScope.values()) {
            byScope.put(scope, new LinkedHashMap<>());
        }
    }

    @Override
    public synchronized void putRule(GuidanceRule rule) {
        Objects.requireNonNull(rule, "rule");
        LinkedHashMap<String, StampedRule> rows = scopeMap(rule.scope());
        rows.remove(rule.ruleId());
        rows.put(rule.ruleId(), new StampedRule(rule, ++seq));
        if (rule.scope() == GuidanceScope.worldSave && worldSaveListener != null) {
            worldSaveListener.accept(rule);
        }
    }

    @Override
    public synchronized void removeRule(String ruleId) {
        Objects.requireNonNull(ruleId, "ruleId");
        for (LinkedHashMap<String, StampedRule> scopeMap : byScope.values()) {
            scopeMap.remove(ruleId);
        }
    }

    @Override
    public synchronized List<GuidanceRule> rulesInScope(GuidanceScope scope) {
        Objects.requireNonNull(scope, "scope");
        LinkedHashMap<String, StampedRule> rows = scopeMap(scope);
        List<GuidanceRule> out = new ArrayList<>(rows.size());
        for (StampedRule sr : rows.values()) {
            out.add(sr.rule);
        }
        return List.copyOf(out);
    }

    @Override
    public synchronized HumanGuidanceView snapshotMergedView(
        List<GuidanceRule> sessionOverrides,
        List<GuidanceRule> queryOverrides
    ) {
        List<GuidanceRule> ordered = new ArrayList<>();
        for (StampedRule sr : scopeMap(GuidanceScope.modpackDefault).values()) {
            ordered.add(sr.rule);
        }
        for (StampedRule sr : scopeMap(GuidanceScope.worldSave).values()) {
            ordered.add(sr.rule);
        }
        for (StampedRule sr : scopeMap(GuidanceScope.session).values()) {
            ordered.add(sr.rule);
        }
        if (sessionOverrides != null) {
            ordered.addAll(sessionOverrides);
        }
        for (StampedRule sr : scopeMap(GuidanceScope.queryLocal).values()) {
            ordered.add(sr.rule);
        }
        if (queryOverrides != null) {
            ordered.addAll(queryOverrides);
        }
        return DefaultHumanGuidanceView.of(ordered);
    }

    /** The constructor materializes every scope, so this lookup never misses. */
    private LinkedHashMap<String, StampedRule> scopeMap(GuidanceScope scope) {
        return Objects.requireNonNull(byScope.get(scope));
    }

    private record StampedRule(GuidanceRule rule, long sequence) {}
}
