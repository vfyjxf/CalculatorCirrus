package dev.vfyjxf.cirrus.ui.workbench.hosts;

import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cirrus.planning.bridge.WorldFacilityRegistry;
import dev.vfyjxf.cirrus.ui.workbench.facilities.FacilitiesHost;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Host adapter for the facilities studio: the shell handles toasts, session recipe import and the runtime graph; every other read and write goes to the {@link FacilitiesHost} implementation.
 */
public final class ShellFacilitiesHost implements FacilitiesHost {

    private final FacilitiesHost delegate;
    private final Consumer<Component> toastSink;
    private final Supplier<Optional<CompiledRecipeGraph>> graphSource;
    private final AtomicBoolean detachedFallback = new AtomicBoolean();
    private volatile Supplier<List<CompiledRecipeKey>> sessionRecipeSource;

    /**
     * Production wiring: prefers the real facility registry and degrades to a detached registry when the config directory is unavailable.
     */
    public static ShellFacilitiesHost forShell(Consumer<Component> toastSink) {
        Consumer<Component> sink = toastSink == null ? message -> {} : toastSink;
        ShellFacilitiesHost host;
        try {
            host = new ShellFacilitiesHost(new FacilitiesHost.Default(sink), sink);
        } catch (RuntimeException | LinkageError unavailable) {
            host = new ShellFacilitiesHost(
                new FacilitiesHost.Detached(WorldFacilityRegistry.createDetached(), sink, Path.of(".")),
                sink
            );
            host.detachedFallback.set(true);
        }
        return host;
    }

    public ShellFacilitiesHost(FacilitiesHost delegate, Consumer<Component> toastSink) {
        this(delegate, toastSink, ShellFacilitiesHost::runtimeGraph, List::of);
    }

    public ShellFacilitiesHost(
        FacilitiesHost delegate,
        Consumer<Component> toastSink,
        Supplier<Optional<CompiledRecipeGraph>> graphSource,
        Supplier<List<CompiledRecipeKey>> sessionRecipeSource
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.toastSink = toastSink == null ? message -> {} : toastSink;
        this.graphSource = graphSource == null ? Optional::empty : graphSource;
        this.sessionRecipeSource = sessionRecipeSource == null ? List::of : sessionRecipeSource;
    }

    @Override
    public WorldFacilityRegistry registry() {
        WorldFacilityRegistry registry = delegate.registry();
        if (!delegate.worldBound()) {
            detachedFallback.set(true);
        }
        return registry;
    }

    @Override
    public boolean worldBound() {
        return delegate.worldBound();
    }

    @Override
    public Optional<CompiledRecipeGraph> graph() {
        Optional<CompiledRecipeGraph> graph = graphSource.get();
        return graph == null ? Optional.empty() : graph;
    }

    @Override
    public List<Namespace> systemIds() {
        return delegate.systemIds();
    }

    @Override
    public List<GuidanceRule> rules() {
        return delegate.rules();
    }

    @Override
    public List<CompiledRecipeKey> sessionRecipeKeys() {
        Supplier<List<CompiledRecipeKey>> source = sessionRecipeSource;
        List<CompiledRecipeKey> keys = source == null ? List.of() : source.get();
        return keys == null ? List.of() : List.copyOf(keys);
    }

    @Override
    public void putRule(GuidanceRule rule) {
        delegate.putRule(rule);
    }

    @Override
    public void removeRule(String ruleId) {
        delegate.removeRule(ruleId);
    }

    @Override
    public void toast(Component message) {
        if (message != null) {
            toastSink.accept(message);
        }
    }

    @Override
    public Path configDirectory() {
        return delegate.configDirectory();
    }

    @Override
    public Transfer exportConfig() {
        return delegate.exportConfig();
    }

    @Override
    public Transfer importConfig() {
        return delegate.importConfig();
    }

    /**
     * Whether the registry is the degraded instance ({@code createDetached()} supplies it when no world save is mounted).
     */
    public boolean detachedFallback() {
        return detachedFallback.get();
    }

    public FacilitiesHost delegate() {
        return delegate;
    }

    public void sessionRecipeSource(@Nullable Supplier<List<CompiledRecipeKey>> source) {
        this.sessionRecipeSource = source == null ? List::of : source;
    }

    /**
     * Collects automatable recipe keys from the selected plan of the current snapshot (craft and convert steps).
     */
    public static List<CompiledRecipeKey> sessionRecipeKeysOf(@Nullable WorkbenchUiSnapshot snapshot) {
        if (snapshot == null) {
            return List.of();
        }
        ProductionPlan plan = snapshot.selectedPlan().orElse(null);
        if (plan == null) {
            return List.of();
        }
        Set<CompiledRecipeKey> keys = new LinkedHashSet<>();
        for (PlanStep step : plan.steps()) {
            if (step instanceof CraftStep craft && craft.recipeKey() != null) {
                keys.add(craft.recipeKey());
            } else if (step instanceof ConvertStep convert && convert.conversionRecipeKey() != null) {
                keys.add(convert.conversionRecipeKey());
            }
        }
        return List.copyOf(keys);
    }

    private static Optional<CompiledRecipeGraph> runtimeGraph() {
        try {
            CirrusPlanningRuntime runtime = CirrusPlanningRuntime.sharedIfCreated();
            return runtime == null ? Optional.empty() : runtime.currentGraph();
        } catch (RuntimeException | LinkageError unavailable) {
            return Optional.empty();
        }
    }
}
