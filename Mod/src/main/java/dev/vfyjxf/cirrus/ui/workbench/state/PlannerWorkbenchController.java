package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.human.AnswerPromptFeedback;
import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.human.OverrideStockFeedback;
import dev.vfyjxf.cirrus.api.planning.human.PinResourceRecipeFeedback;
import dev.vfyjxf.cirrus.api.planning.human.RejectStepFeedback;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningSession;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

public final class PlannerWorkbenchController implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus WorkbenchController");

    public static final String errorWorldPersistUnavailable = "cirrus.ui.workbench.toast.world_persist_unavailable";

    private static final String labelInitial = "cirrus.ui.workbench.timeline.initial";
    private static final String labelPromoteOne = "cirrus.ui.workbench.timeline.promote_world";
    private static final String labelPromoteAll = "cirrus.ui.workbench.timeline.promote_all_world";
    private static final String labelRelax = "cirrus.ui.workbench.action.relax_one";
    private static final String labelOwnMachine = "cirrus.ui.workbench.action.declare_owned";
    private static final String labelOverrideStock = "cirrus.ui.workbench.action.override_stock";
    private static final String labelRejectStep = "cirrus.ui.workbench.action.ban_recipe";
    private static final String labelPinRecipe = "cirrus.ui.workbench.action.replace_recipe";
    private static final String labelPrompt = "cirrus.ui.workbench.copilot.tab_prompts";

    private final SessionTabState state;
    private final PlanningBackend backend;
    private final PlanningRequestSource source;
    private final Executor executor;

    private final Object lock = new Object();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger openSessions = new AtomicInteger();
    private final AtomicInteger closedSessions = new AtomicInteger();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<WorkbenchUiSnapshot> snapshotRef;

    private final List<HistoryEntry> undoHistory = new ArrayList<>();
    private final List<HistoryEntry> redoHistory = new ArrayList<>();

    private @Nullable SessionHandle activeHandle;
    private @Nullable PlanningResult result;
    private @Nullable PlanningResult lastFeasibleResult;
    private @Nullable String lastErrorMessage;
    private @Nullable WorldRuleGate worldRuleGate;
    private @Nullable LongSupplier runtimeVersionSupplier;
    private boolean planningInFlight;
    private long historySequence;

    public PlannerWorkbenchController(
        SessionTabState state,
        PlanningBackend backend,
        PlanningRequestSource source,
        Executor executor
    ) {
        this.state = Objects.requireNonNull(state, "state");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.source = Objects.requireNonNull(source, "source");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.snapshotRef = new AtomicReference<>(buildSnapshotLocked());
    }

    public static PlannerWorkbenchController forRuntime(
        SessionTabState state,
        CirrusPlanningRuntime runtime,
        @Nullable Player player,
        Executor executor
    ) {
        return PlanningRuntimeBridge.create(state, runtime, player, executor);
    }

    public String tabId() {
        return state.tabId();
    }

    public SessionTabState state() {
        return state;
    }

    public WorkbenchUiSnapshot snapshot() {
        return Objects.requireNonNull(snapshotRef.get());
    }

    public void addListener(Runnable listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    public int openSessionCount() {
        return openSessions.get();
    }

    public int closedSessionCount() {
        return closedSessions.get();
    }

    public void setTargets(List<TargetDemand> targets, @Nullable RecipeEntry<?> primaryRecipe) {
        List<TargetDemand> copy = targets == null ? List.of() : List.copyOf(targets);
        boolean plan;
        long gen = -1L;
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            refreshRuntimeVersionLocked();
            state.setTargets(copy, primaryRecipe);
            state.clearManualOffsets();
            state.setExpandedLoopStepIds(Set.of());
            state.setSelectedStepIds(Set.of());
            state.setHoveredStep(null);
            state.setHoveredResource(null);
            state.resetViewState();
            undoHistory.clear();
            redoHistory.clear();
            generation.incrementAndGet();
            if (copy.isEmpty() || state.runtimeGraphVersion() == 0L) {
                planningInFlight = false;
                result = null;
                lastFeasibleResult = null;
                lastErrorMessage = null;
                state.setSelectedPlanId(null);
                state.setComparePlanId(null);
                plan = false;
            } else {
                planningInFlight = true;
                plan = true;
                gen = generation.get();
            }
        }
        publish();
        if (plan) {
            submitPlan(gen, null);
        } else {
            closeActiveAsync();
        }
    }

    public void replan() {
        boolean plan;
        long gen = -1L;
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            refreshRuntimeVersionLocked();
            if (state.targets().isEmpty() || state.runtimeGraphVersion() == 0L) {
                planningInFlight = false;
                plan = false;
            } else {
                undoHistory.clear();
                redoHistory.clear();
                gen = generation.incrementAndGet();
                planningInFlight = true;
                plan = true;
            }
        }
        publish();
        if (plan) {
            submitPlan(gen, null);
        }
    }

    public void setProfile(PlanningProfile profile) {
        Objects.requireNonNull(profile, "profile");
        settingsChanged(() -> state.setProfile(profile));
    }

    public void setBudget(PlanningBudget budget) {
        Objects.requireNonNull(budget, "budget");
        settingsChanged(() -> state.setBudget(budget));
    }

    public void setUseLiveStock(boolean useLiveStock) {
        settingsChanged(() -> state.setUseLiveStock(useLiveStock));
    }

    public void setSessionRules(List<GuidanceRule> rules) {
        List<GuidanceRule> copy = rules == null ? List.of() : List.copyOf(rules);
        settingsChanged(() -> state.setSessionRules(copy));
    }

    public void resetSession() {
        boolean plan;
        long gen = -1L;
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            state.setSessionRules(List.of());
            state.clearManualOffsets();
            state.setExpandedLoopStepIds(Set.of());
            state.setSelectedStepIds(Set.of());
            state.setSelectedPlanId(null);
            state.setComparePlanId(null);
            state.resetViewState();
            state.setGuidedPauseDismissed(false);
            undoHistory.clear();
            redoHistory.clear();
            lastErrorMessage = null;
            refreshRuntimeVersionLocked();
            if (state.targets().isEmpty() || state.runtimeGraphVersion() == 0L) {
                planningInFlight = false;
                plan = false;
            } else {
                gen = generation.incrementAndGet();
                planningInFlight = true;
                plan = true;
            }
        }
        publish();
        if (plan) {
            submitPlan(gen, null);
        }
    }

    public void selectPlan(@Nullable String planId) {
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            state.setSelectedPlanId(planId);
        }
        publish();
    }

    public void setComparePlan(@Nullable String planId) {
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            state.setComparePlanId(planId);
        }
        publish();
    }

    public void setFlowViewMode(FlowViewMode mode) {
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            state.setFlowViewMode(mode);
        }
        publish();
    }

    public void setDagLayoutMode(DagLayoutMode mode) {
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            state.setDagLayoutMode(mode);
        }
        publish();
    }

    public void setSelectedSteps(Set<String> stepIds) {
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            state.setSelectedStepIds(stepIds);
        }
        publish();
    }

    public void setHoveredStep(@Nullable String stepId) {
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            state.setHoveredStep(stepId);
        }
        publish();
    }

    public void setHoveredResource(@Nullable ResourceKey resource) {
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            state.setHoveredResource(resource);
        }
        publish();
    }

    public void toggleLoopExpanded(String stepId) {
        Objects.requireNonNull(stepId, "stepId");
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            Set<String> next = new TreeSet<>(state.expandedLoopStepIds());
            if (!next.add(stepId)) {
                next.remove(stepId);
            }
            state.setExpandedLoopStepIds(next);
        }
        publish();
    }

    public void setManualOffset(String stepId, FloatPos offset) {
        Objects.requireNonNull(stepId, "stepId");
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            if (offset == null) {
                state.removeManualOffset(stepId);
            } else {
                state.setManualOffset(stepId, offset);
            }
        }
        publish();
    }

    public void clearManualOffsets() {
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            state.clearManualOffsets();
        }
        publish();
    }

    public void refine(HumanFeedback feedback, @Nullable HistoryEntry entry) {
        Objects.requireNonNull(feedback, "feedback");
        HistoryEntry effective = entry != null ? entry : synthesizeHistory(feedback);
        long gen;
        synchronized (lock) {
            if (closed.get() || activeHandle == null || state.targets().isEmpty()) {
                return;
            }
            gen = generation.incrementAndGet();
            planningInFlight = true;
            redoHistory.clear();
        }
        publish();
        try {
            executor.execute(() -> runRefine(gen, feedback, effective));
        } catch (RejectedExecutionException rejected) {
            failCurrent(gen);
        }
    }

    public void jumpToHistoryIndex(int targetIndex) {
        long gen;
        synchronized (lock) {
            if (closed.get() || activeHandle == null) {
                return;
            }
            gen = generation.incrementAndGet();
            planningInFlight = false;
        }
        try {
            executor.execute(() -> runJumpToHistory(gen, targetIndex));
        } catch (RejectedExecutionException rejected) {
            failCurrent(gen);
        }
    }

    public void relaxConflictRules(List<String> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return;
        }
        HistoryEntry entry = null;
        long gen = -1L;
        boolean changed = false;
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            List<GuidanceRule> remaining = new ArrayList<>(state.sessionRules());
            for (String ruleId : ruleIds) {
                if (ruleId == null || ruleId.isEmpty()) {
                    continue;
                }
                GuidanceRule sessionRule = findRule(remaining, ruleId);
                if (sessionRule != null && sessionRule.scope() != GuidanceScope.worldSave) {
                    remaining.remove(sessionRule);
                    changed = true;
                    continue;
                }
                if (worldRuleGate != null && worldRuleGate.available()) {
                    worldRuleGate.removeRule(ruleId);
                    remaining.removeIf(rule -> rule.ruleId().equals(ruleId));
                    changed = true;
                } else {
                    lastErrorMessage = errorWorldPersistUnavailable;
                }
            }
            if (changed) {
                state.setSessionRules(remaining);
                entry = newHistory(labelRelax, List.of(), false, HistoryEntry.HistoryKind.relaxRules);
                gen = generation.incrementAndGet();
                planningInFlight = true;
            }
        }
        publish();
        if (changed) {
            submitPlan(gen, entry);
        }
    }

    public void promoteSessionRulesToWorld(@Nullable String singleRuleId) {
        HistoryEntry entry = null;
        long gen = -1L;
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            if (worldRuleGate == null || !worldRuleGate.available()) {
                lastErrorMessage = errorWorldPersistUnavailable;
            } else {
                List<GuidanceRule> remaining = new ArrayList<>(state.sessionRules());
                List<GuidanceRule> promoted = new ArrayList<>();
                for (GuidanceRule rule : remaining) {
                    if (singleRuleId != null && !singleRuleId.equals(rule.ruleId())) {
                        continue;
                    }
                    if (rule.scope() == GuidanceScope.worldSave) {
                        continue;
                    }
                    promoted.add(rule);
                }
                if (!promoted.isEmpty()) {
                    List<GuidanceRule> scoped = new ArrayList<>(promoted.size());
                    for (GuidanceRule rule : promoted) {
                        GuidanceRule worldRule = GuidanceRuleScopes.withScope(rule, GuidanceScope.worldSave);
                        worldRuleGate.putRule(worldRule);
                        scoped.add(worldRule);
                    }
                    remaining.removeAll(promoted);
                    state.setSessionRules(remaining);
                    entry = newHistory(
                        singleRuleId == null ? labelPromoteAll : labelPromoteOne,
                        scoped,
                        true,
                        HistoryEntry.HistoryKind.promoteWorld
                    );
                    gen = generation.incrementAndGet();
                    planningInFlight = true;
                }
            }
        }
        publish();
        if (entry != null) {
            submitPlan(gen, entry);
        }
    }

    public void onGraphVersionChanged(long newVersion) {
        long version = Math.max(0L, newVersion);
        boolean publish;
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            long previous = state.runtimeGraphVersion();
            state.setRuntimeGraphVersion(version);
            if (version == 0L) {
                state.setStaleGraph(false);
                result = null;
                lastFeasibleResult = null;
                planningInFlight = false;
                undoHistory.clear();
                redoHistory.clear();
                state.setSelectedPlanId(null);
                state.setComparePlanId(null);
            } else {
                boolean becameAvailable = previous == 0L && result == null && !state.targets().isEmpty();
                state.setStaleGraph((previous != 0L && previous != version) || becameAvailable);
            }
            publish = previous != version;
        }
        if (publish) {
            publish();
        }
        if (version == 0L) {
            closeActiveAsync();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        generation.incrementAndGet();
        SessionHandle toClose;
        synchronized (lock) {
            toClose = activeHandle;
            activeHandle = null;
            planningInFlight = false;
            snapshotRef.set(buildSnapshotLocked());
        }
        Runnable closeTask = () -> {
            if (toClose != null) {
                toClose.close();
            }
        };
        try {
            executor.execute(closeTask);
        } catch (RejectedExecutionException rejected) {
            closeTask.run();
        }
    }

    void bindWorldRuleGate(@Nullable WorldRuleGate gate) {
        synchronized (lock) {
            this.worldRuleGate = gate;
        }
    }

    void bindRuntimeVersionSupplier(@Nullable LongSupplier supplier) {
        synchronized (lock) {
            this.runtimeVersionSupplier = supplier;
        }
    }

    private void refreshRuntimeVersionLocked() {
        if (state.runtimeGraphVersion() != 0L || runtimeVersionSupplier == null) {
            return;
        }
        long liveVersion = Math.max(0L, runtimeVersionSupplier.getAsLong());
        if (liveVersion > 0L) {
            state.setRuntimeGraphVersion(liveVersion);
        }
    }

    private void settingsChanged(Runnable mutation) {
        boolean plan;
        long gen = -1L;
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            mutation.run();
            refreshRuntimeVersionLocked();
            generation.incrementAndGet();
            undoHistory.clear();
            redoHistory.clear();
            if (!state.targets().isEmpty() && state.runtimeGraphVersion() != 0L) {
                gen = generation.get();
                planningInFlight = true;
                plan = true;
            } else {
                planningInFlight = false;
                plan = false;
            }
        }
        publish();
        if (plan) {
            submitPlan(gen, null);
        } else {
            closeActiveAsync();
        }
    }

    private void submitPlan(long gen, @Nullable HistoryEntry entryOnSuccess) {
        PlanningRequestSpec spec;
        synchronized (lock) {
            if (!isCurrentLocked(gen)) {
                return;
            }
            spec = new PlanningRequestSpec(
                state.targets(),
                state.profile(),
                state.budget(),
                state.useLiveStock(),
                state.reservedLedger(),
                state.progression(),
                state.sessionRules()
            );
        }
        try {
            executor.execute(() -> runPlan(gen, spec, entryOnSuccess));
        } catch (RejectedExecutionException rejected) {
            failCurrent(gen);
        }
    }

    private void runPlan(long gen, PlanningRequestSpec spec, @Nullable HistoryEntry entryOnSuccess) {
        SessionHandle handle = null;
        try {
            PlanningRequest request = source.build(spec);
            handle = openTracked(request);
            if (!isCurrent(gen)) {
                handle.close();
                return;
            }
            PlanningResult res = handle.session.currentResult();
            boolean applied = false;
            synchronized (lock) {
                if (isCurrentLocked(gen)) {
                    swapActiveLocked(handle);
                    handle = null;
                    undoHistory.clear();
                    redoHistory.clear();
                    if (entryOnSuccess != null) {
                        undoHistory.add(entryOnSuccess);
                    }
                    applyResultLocked(res);
                    state.setStaleGraph(false);
                    planningInFlight = false;
                    lastErrorMessage = null;
                    applied = true;
                }
            }
            if (!applied) {
                return;
            }
            publish();
        } catch (Throwable failure) {
            if (handle != null) {
                handle.close();
            }
            boolean current;
            synchronized (lock) {
                current = isCurrentLocked(gen);
                if (current) {
                    planningInFlight = false;
                }
            }
            if (current) {
                logger.warn("Workbench planning failed for tab {}", state.tabId(), failure);
                publish();
            }
        }
    }

    private void runRefine(long gen, HumanFeedback feedback, HistoryEntry entry) {
        SessionHandle handle;
        synchronized (lock) {
            if (!isCurrentLocked(gen) || activeHandle == null) {
                return;
            }
            handle = activeHandle;
        }
        PlanningResult res;
        try {
            res = handle.session.refine(feedback);
        } catch (Throwable failure) {
            logger.warn("Workbench refine failed for tab {}", state.tabId(), failure);
            failCurrent(gen);
            return;
        }
        boolean applied = false;
        synchronized (lock) {
            if (isCurrentLocked(gen)) {
                applyResultLocked(res);
                appendSessionRulesLocked(entry);
                undoHistory.add(entry);
                redoHistory.clear();
                planningInFlight = false;
                lastErrorMessage = null;
                persistWorldSideEffectsLocked(feedback, entry);
                applied = true;
            }
        }
        if (applied) {
            publish();
        }
    }

    private void runJumpToHistory(long gen, int targetIndex) {
        boolean applied = false;
        synchronized (lock) {
            if (!isCurrentLocked(gen) || activeHandle == null) {
                return;
            }
            PlanningSession session = activeHandle.session;
            int current = undoHistory.size();
            int target = Math.max(0, Math.min(targetIndex, current + redoHistory.size()));
            while (current > target) {
                session.undo();
                HistoryEntry entry = undoHistory.remove(undoHistory.size() - 1);
                redoHistory.add(0, entry);
                current--;
            }
            while (current < target) {
                if (redoHistory.isEmpty()) {
                    break;
                }
                session.redo();
                HistoryEntry entry = redoHistory.remove(0);
                undoHistory.add(entry);
                current++;
            }
            applyResultLocked(session.currentResult());
            planningInFlight = false;
            lastErrorMessage = null;
            applied = true;
        }
        if (applied) {
            publish();
        }
    }

    private void failCurrent(long gen) {
        boolean publish = false;
        synchronized (lock) {
            if (isCurrentLocked(gen)) {
                planningInFlight = false;
                publish = true;
            }
        }
        if (publish) {
            publish();
        }
    }

    private void closeActiveAsync() {
        SessionHandle toClose;
        synchronized (lock) {
            toClose = activeHandle;
            activeHandle = null;
        }
        if (toClose == null) {
            return;
        }
        Runnable closeTask = toClose::close;
        try {
            executor.execute(closeTask);
        } catch (RejectedExecutionException rejected) {
            closeTask.run();
        }
    }

    private SessionHandle openTracked(PlanningRequest request) {
        PlanningSession session = backend.openSession(request);
        if (session == null) {
            throw new IllegalStateException("PlanningBackend returned a null session");
        }
        return new SessionHandle(session);
    }

    private void swapActiveLocked(SessionHandle next) {
        SessionHandle previous = activeHandle;
        activeHandle = next;
        if (previous != null) {
            previous.close();
        }
    }

    private void applyResultLocked(PlanningResult res) {
        result = res;
        if (!res.paretoPlans().isEmpty()) {
            lastFeasibleResult = res;
            String selected = state.selectedPlanId();
            boolean selectedValid = selected != null
                    && res.paretoPlans().stream().anyMatch(plan -> plan.planId().equals(selected));
            if (!selectedValid) {
                state.setSelectedPlanId(res.paretoPlans().get(0).planId());
            }
            String compare = state.comparePlanId();
            boolean compareValid = compare != null
                    && res.paretoPlans().stream().anyMatch(plan -> plan.planId().equals(compare));
            if (!compareValid) {
                state.setComparePlanId(null);
            }
        } else {
            state.setSelectedPlanId(null);
            state.setComparePlanId(null);
        }
    }

    private void appendSessionRulesLocked(HistoryEntry entry) {
        if (entry.rules().isEmpty()) {
            return;
        }
        Map<String, GuidanceRule> merged = new LinkedHashMap<>();
        for (GuidanceRule rule : state.sessionRules()) {
            merged.put(rule.ruleId(), rule);
        }
        for (GuidanceRule rule : entry.rules()) {
            if (rule.scope() != GuidanceScope.worldSave) {
                merged.put(rule.ruleId(), rule);
            }
        }
        state.setSessionRules(new ArrayList<>(merged.values()));
    }

    private void persistWorldSideEffectsLocked(HumanFeedback feedback, HistoryEntry entry) {
        Map<String, GuidanceRule> worldRules = new LinkedHashMap<>();
        if (entry.persistedToWorld()) {
            for (GuidanceRule rule : entry.rules()) {
                worldRules.put(rule.ruleId(), rule);
            }
        }
        if (feedback instanceof ApplyRulesFeedback apply && apply.persistToWorld()) {
            for (GuidanceRule rule : apply.rules()) {
                worldRules.put(rule.ruleId(), rule);
            }
        }
        if (!worldRules.isEmpty()) {
            if (worldRuleGate == null || !worldRuleGate.available()) {
                lastErrorMessage = errorWorldPersistUnavailable;
            } else {
                for (GuidanceRule rule : worldRules.values()) {
                    worldRuleGate.putRule(GuidanceRuleScopes.withScope(rule, GuidanceScope.worldSave));
                }
            }
        }
        if (feedback instanceof DeclareOwnedMachineFeedback declare && declare.persistToWorld()) {
            if (worldRuleGate == null || !worldRuleGate.available()) {
                lastErrorMessage = errorWorldPersistUnavailable;
            } else {
                worldRuleGate.declareOwnedMachine(declare);
            }
        }
    }

    private HistoryEntry synthesizeHistory(HumanFeedback feedback) {
        String label = "";
        List<GuidanceRule> rules = List.of();
        boolean persisted = false;
        HistoryEntry.HistoryKind kind;
        if (feedback instanceof AnswerPromptFeedback prompt) {
            kind = HistoryEntry.HistoryKind.answerPrompt;
            label = labelPrompt;
            persisted = prompt.persistToWorld();
        } else if (feedback instanceof ApplyRulesFeedback apply) {
            kind = HistoryEntry.HistoryKind.applyRules;
            rules = apply.rules();
            persisted = apply.persistToWorld();
        } else if (feedback instanceof DeclareOwnedMachineFeedback declare) {
            kind = HistoryEntry.HistoryKind.ownMachine;
            label = labelOwnMachine;
            persisted = declare.persistToWorld();
        } else if (feedback instanceof OverrideStockFeedback) {
            kind = HistoryEntry.HistoryKind.overrideStock;
            label = labelOverrideStock;
        } else if (feedback instanceof RejectStepFeedback reject) {
            kind = HistoryEntry.HistoryKind.rejectStep;
            label = labelRejectStep;
            persisted = reject.persistToWorld();
        } else if (feedback instanceof PinResourceRecipeFeedback pin) {
            kind = HistoryEntry.HistoryKind.pinRecipe;
            label = labelPinRecipe;
            persisted = pin.persistToWorld();
        } else {
            label = labelInitial;
            kind = HistoryEntry.HistoryKind.initial;
        }
        return newHistory(label, rules, persisted, kind);
    }

    private HistoryEntry newHistory(
        String labelKey,
        List<GuidanceRule> rules,
        boolean persistedToWorld,
        HistoryEntry.HistoryKind kind
    ) {
        long sequence = ++historySequence;
        return new HistoryEntry("hist-" + sequence, labelKey, rules, persistedToWorld, kind, sequence);
    }

    private static @Nullable GuidanceRule findRule(List<GuidanceRule> rules, String ruleId) {
        for (GuidanceRule rule : rules) {
            if (rule.ruleId().equals(ruleId)) {
                return rule;
            }
        }
        return null;
    }

    private boolean isCurrent(long gen) {
        return !closed.get() && generation.get() == gen;
    }

    private boolean isCurrentLocked(long gen) {
        return !closed.get() && generation.get() == gen;
    }

    private WorkbenchUiSnapshot buildSnapshotLocked() {
        return new WorkbenchUiSnapshot(
            state.tabId(),
            state.tabTitle(),
            computePhaseLocked(),
            planningInFlight,
            state.runtimeGraphVersion(),
            state.targets(),
            state.profile(),
            state.budget(),
            state.useLiveStock(),
            state.reservedLedger(),
            state.progression(),
            result,
            lastFeasibleResult,
            state.selectedPlanId(),
            state.comparePlanId(),
            state.selectedStepIds(),
            state.hoveredStepId(),
            state.hoveredResource(),
            state.flowViewMode(),
            state.dagLayoutMode(),
            state.expandedLoopStepIds(),
            state.manualNodeOffsets(),
            undoHistory,
            redoHistory,
            state.sessionRules(),
            lastErrorMessage
        );
    }

    private WorkbenchPhase computePhaseLocked() {
        if (closed.get()) {
            return WorkbenchPhase.closed;
        }
        if (state.runtimeGraphVersion() == 0L) {
            return WorkbenchPhase.noGraph;
        }
        if (state.targets().isEmpty()) {
            return WorkbenchPhase.idleNoTarget;
        }
        if (planningInFlight) {
            return WorkbenchPhase.planning;
        }
        if (result == null) {
            return WorkbenchPhase.idleNoTarget;
        }
        return switch (result.status()) {
            case feasible -> WorkbenchPhase.readyPareto;
            case conditional, noPlanWithinBudget -> WorkbenchPhase.readyBudgetHit;
            case infeasibleByHumanRules -> WorkbenchPhase.conflictInfeasible;
            case provenInfeasible -> WorkbenchPhase.unreachable;
            case pending -> WorkbenchPhase.planning;
        };
    }

    private void publish() {
        WorkbenchUiSnapshot snapshot;
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            snapshot = buildSnapshotLocked();
            snapshotRef.set(snapshot);
        }
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {}
        }
    }

    private final class SessionHandle {

        private final PlanningSession session;
        private final AtomicBoolean sessionClosed = new AtomicBoolean();

        private SessionHandle(PlanningSession session) {
            this.session = session;
            openSessions.incrementAndGet();
        }

        private void close() {
            if (sessionClosed.compareAndSet(false, true)) {
                closedSessions.incrementAndGet();
                session.close();
            }
        }
    }
}
