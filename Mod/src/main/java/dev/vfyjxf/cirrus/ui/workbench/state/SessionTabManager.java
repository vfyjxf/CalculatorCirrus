package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

public final class SessionTabManager implements AutoCloseable {

    public static final int maxTabs = 8;

    @FunctionalInterface
    public interface ControllerFactory {

        PlannerWorkbenchController create(SessionTabState state);
    }

    private final ControllerFactory factory;
    private final LinkedHashMap<String, PlannerWorkbenchController> tabs = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private @Nullable String activeTabId;
    private long tabSequence;
    private boolean closed;

    public SessionTabManager(ControllerFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public static SessionTabManager forRuntime(
        CirrusPlanningRuntime runtime,
        @Nullable Player player,
        Executor executor
    ) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(executor, "executor");
        return new SessionTabManager(state -> PlannerWorkbenchController.forRuntime(state, runtime, player, executor));
    }

    public synchronized List<PlannerWorkbenchController> tabs() {
        return List.copyOf(tabs.values());
    }

    public synchronized @Nullable PlannerWorkbenchController activeTab() {
        return activeTabId == null ? null : tabs.get(activeTabId);
    }

    public synchronized @Nullable String activeTabId() {
        return activeTabId;
    }

    public PlannerWorkbenchController openOrFocusTab(
        List<TargetDemand> targets,
        @Nullable RecipeEntry<?> primaryRecipe
    ) {
        List<TargetDemand> copy = targets == null ? List.of() : List.copyOf(targets);
        PlannerWorkbenchController target;
        boolean fresh = false;
        synchronized (this) {
            ensureOpen();
            target = copy.isEmpty() ? null : findTabWithDemands(copy);
            if (target == null) {
                if (tabs.size() >= maxTabs) {
                    throw new IllegalStateException("SessionTabManager supports at most " + maxTabs + " tabs");
                }
                SessionTabState state = new SessionTabState(nextTabId());
                state.setTargets(copy, primaryRecipe);
                PlannerWorkbenchController created = factory.create(state);
                register(created);
                tabs.put(created.tabId(), created);
                target = created;
                fresh = true;
            }
            activeTabId = target.tabId();
        }
        WorkbenchUiSnapshot snap = target.snapshot();
        boolean needsInitialPlan = !copy.isEmpty() && snap.result() == null && !snap.planningInFlight();
        if (fresh || target.state().staleGraph() || needsInitialPlan) {
            target.replan();
        }
        notifyListeners();
        return target;
    }

    public void activate(String tabId) {
        PlannerWorkbenchController target;
        boolean replan;
        synchronized (this) {
            ensureOpen();
            target = tabs.get(tabId);
            if (target == null) {
                return;
            }
            activeTabId = tabId;
            WorkbenchUiSnapshot snap = target.snapshot();
            replan = target.state().staleGraph()
                    || (!target.state().targets().isEmpty() && snap.result() == null && !snap.planningInFlight());
        }
        if (replan) {
            target.replan();
        }
        notifyListeners();
    }

    public PlannerWorkbenchController forkActiveTab() {
        PlannerWorkbenchController source;
        PlannerWorkbenchController fork;
        synchronized (this) {
            ensureOpen();
            source = activeTab();
            if (source == null) {
                throw new IllegalStateException("No active tab to fork");
            }
            if (tabs.size() >= maxTabs) {
                throw new IllegalStateException("SessionTabManager supports at most " + maxTabs + " tabs");
            }
            SessionTabState forkState = source.state().copyAs(nextTabId());
            fork = factory.create(forkState);
            register(fork);
            tabs.put(fork.tabId(), fork);
            activeTabId = fork.tabId();
        }
        fork.replan();
        notifyListeners();
        return fork;
    }

    public void closeTab(String tabId) {
        PlannerWorkbenchController removed;
        PlannerWorkbenchController nextActive = null;
        synchronized (this) {
            if (closed) {
                return;
            }
            removed = tabs.remove(tabId);
            if (removed == null) {
                return;
            }
            if (Objects.equals(activeTabId, tabId)) {
                activeTabId = tabs.isEmpty() ? null : lastTabId();
                nextActive = activeTab();
            }
        }
        removed.close();
        if (nextActive != null && nextActive.state().staleGraph()) {
            nextActive.replan();
        }
        notifyListeners();
    }

    public void closeOthers(String keepTabId) {
        List<PlannerWorkbenchController> toClose = new ArrayList<>();
        PlannerWorkbenchController keep;
        synchronized (this) {
            if (closed) {
                return;
            }
            keep = tabs.get(keepTabId);
            if (keep == null) {
                return;
            }
            Iterator<Map.Entry<String, PlannerWorkbenchController>> iterator = tabs.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, PlannerWorkbenchController> entry = iterator.next();
                if (!entry.getKey().equals(keepTabId)) {
                    toClose.add(entry.getValue());
                    iterator.remove();
                }
            }
            activeTabId = keepTabId;
        }
        for (PlannerWorkbenchController tab : toClose) {
            tab.close();
        }
        if (keep.state().staleGraph()) {
            keep.replan();
        }
        notifyListeners();
    }

    public void rename(String tabId, String title) {
        synchronized (this) {
            if (closed) {
                return;
            }
            PlannerWorkbenchController tab = tabs.get(tabId);
            if (tab == null) {
                return;
            }
            tab.state().setTabTitle(title);
        }
        notifyListeners();
    }

    public void closeAll() {
        List<PlannerWorkbenchController> all;
        synchronized (this) {
            all = new ArrayList<>(tabs.values());
            tabs.clear();
            activeTabId = null;
        }
        for (PlannerWorkbenchController tab : all) {
            tab.close();
        }
        notifyListeners();
    }

    public void onRuntimeGraphVersionChanged(long newVersion) {
        List<PlannerWorkbenchController> snapshot;
        PlannerWorkbenchController active;
        synchronized (this) {
            snapshot = List.copyOf(tabs.values());
            active = activeTab();
        }
        for (PlannerWorkbenchController tab : snapshot) {
            tab.onGraphVersionChanged(newVersion);
        }
        if (active != null && newVersion > 0L && !active.state().targets().isEmpty()) {
            active.replan();
        }
        notifyListeners();
    }

    public void addListener(Runnable listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    @Override
    public void close() {
        List<PlannerWorkbenchController> all;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            all = new ArrayList<>(tabs.values());
            tabs.clear();
            activeTabId = null;
        }
        for (PlannerWorkbenchController tab : all) {
            tab.close();
        }
        notifyListeners();
    }

    private void register(PlannerWorkbenchController controller) {
        controller.addListener(() -> {
            if (isActive(controller)) {
                notifyListeners();
            }
        });
    }

    private synchronized boolean isActive(PlannerWorkbenchController controller) {
        return tabs.get(controller.tabId()) == controller && controller.tabId().equals(activeTabId);
    }

    private @Nullable PlannerWorkbenchController findTabWithDemands(List<TargetDemand> demands) {
        for (PlannerWorkbenchController tab : tabs.values()) {
            if (sameDemands(tab.state().targets(), demands)) {
                return tab;
            }
        }
        return null;
    }

    private String nextTabId() {
        return "tab-" + (++tabSequence);
    }

    private @Nullable String lastTabId() {
        String last = null;
        for (String id : tabs.keySet()) {
            last = id;
        }
        return last;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("SessionTabManager is closed");
        }
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {}
        }
    }

    private static boolean sameDemands(List<TargetDemand> left, List<TargetDemand> right) {
        return normalize(left).equals(normalize(right));
    }

    private static Map<ComponentStackKey, Long> normalize(List<TargetDemand> demands) {
        TreeMap<ComponentStackKey, Long> merged = new TreeMap<>();
        for (TargetDemand demand : demands) {
            merged.merge(demand.resource(), demand.amount(), Math::addExact);
        }
        return merged;
    }
}
