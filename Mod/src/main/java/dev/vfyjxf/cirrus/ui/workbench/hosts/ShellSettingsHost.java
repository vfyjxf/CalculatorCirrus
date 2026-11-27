package dev.vfyjxf.cirrus.ui.workbench.hosts;

import com.google.gson.JsonElement;
import dev.vfyjxf.cirrus.api.planning.cost.ObjectivePreset;
import dev.vfyjxf.cirrus.api.planning.graph.GraphCompilationStats;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.planning.bridge.CirrusCatalogAdapter;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.settings.ProgressionPreset;
import dev.vfyjxf.cirrus.ui.workbench.settings.SettingsHost;
import dev.vfyjxf.cirrus.ui.workbench.state.DagEdgeStyle;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Host adapter for settings and diagnostics: runtime telemetry goes to {@link SettingsHost}, the shell adds config read/write and the diagnostic export callback.
 */
public final class ShellSettingsHost implements SettingsHost {

    private final SettingsHost delegate;
    private final Consumer<Component> toastSink;

    /**
     * Production wiring: degrades to the runtime-free implementation when the game log directory is unavailable.
     */
    public static ShellSettingsHost forShell(Consumer<Component> toastSink) {
        Consumer<Component> sink = toastSink == null ? message -> {} : toastSink;
        try {
            return new ShellSettingsHost(new SettingsHost.Default(sink), sink);
        } catch (RuntimeException | LinkageError unavailable) {
            return new ShellSettingsHost(new SettingsHost.Detached(sink, Path.of("logs")), sink);
        }
    }

    public ShellSettingsHost(SettingsHost delegate, Consumer<Component> toastSink) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.toastSink = toastSink == null ? message -> {} : toastSink;
    }

    @Override
    public Optional<CirrusPlanningRuntime.PublishedSnapshot> published() {
        try {
            return delegate.published();
        } catch (RuntimeException | LinkageError unavailable) {
            return Optional.empty();
        }
    }

    @Override
    public boolean plannerAvailable() {
        return delegate.plannerAvailable();
    }

    @Override
    public boolean compiling() {
        return delegate.compiling();
    }

    @Override
    public long lastCompileMillis() {
        return delegate.lastCompileMillis();
    }

    @Override
    public CompletableFuture<Optional<Long>> recompile() {
        return delegate.recompile();
    }

    @Override
    public void toast(Component message) {
        if (message != null) {
            toastSink.accept(message);
        }
    }

    @Override
    public Path logDirectory() {
        return delegate.logDirectory();
    }

    @Override
    public Transfer writeDiagnostics(String fileName, JsonElement payload) {
        return delegate.writeDiagnostics(fileName, payload);
    }

    /* region read-only diagnostic telemetry snapshot */

    public Optional<CirrusPlanningRuntime.PublishedSnapshot> publishedSnapshot() {
        return published();
    }

    public long publishedVersion() {
        return published().map(CirrusPlanningRuntime.PublishedSnapshot::version).orElse(0L);
    }

    public long attachEpoch() {
        return published().map(CirrusPlanningRuntime.PublishedSnapshot::attachEpoch).orElse(0L);
    }

    public CirrusCatalogAdapter.@Nullable CaptureStats captureStats() {
        return published().map(CirrusPlanningRuntime.PublishedSnapshot::captureStats).orElse(null);
    }

    public @Nullable GraphCompilationStats graphStats() {
        return published().map(CirrusPlanningRuntime.PublishedSnapshot::graph).map(graph -> graph.stats()).orElse(null);
    }

    /* endregion */

    /* region settings read/write (nine workbench_* options) */

    public WorkbenchThemeMode theme() {
        return ExecutionOverlayClientConfig.workbenchTheme();
    }

    public void theme(WorkbenchThemeMode theme) {
        ExecutionOverlayClientConfig.setWorkbenchTheme(theme);
    }

    public boolean reducedMotion() {
        return ExecutionOverlayClientConfig.workbenchReducedMotion();
    }

    public void reducedMotion(boolean enabled) {
        ExecutionOverlayClientConfig.setWorkbenchReducedMotion(enabled);
    }

    public DagLayoutMode defaultDagLayout() {
        return ExecutionOverlayClientConfig.workbenchDefaultDagLayout();
    }

    public void defaultDagLayout(DagLayoutMode layout) {
        ExecutionOverlayClientConfig.setWorkbenchDefaultDagLayout(layout);
    }

    public DagEdgeStyle defaultDagEdgeStyle() {
        return ExecutionOverlayClientConfig.workbenchDefaultDagEdgeStyle();
    }

    public void defaultDagEdgeStyle(DagEdgeStyle style) {
        ExecutionOverlayClientConfig.setWorkbenchDefaultDagEdgeStyle(style);
    }

    public ObjectivePreset defaultPreset() {
        return ExecutionOverlayClientConfig.workbenchDefaultPreset();
    }

    public void defaultPreset(ObjectivePreset preset) {
        ExecutionOverlayClientConfig.setWorkbenchDefaultPreset(preset);
    }

    public int softBudgetMs() {
        return ExecutionOverlayClientConfig.workbenchSoftBudgetMs();
    }

    public void softBudgetMs(int milliseconds) {
        ExecutionOverlayClientConfig.setWorkbenchSoftBudgetMs(milliseconds);
    }

    public int hardBudgetMs() {
        return ExecutionOverlayClientConfig.workbenchHardBudgetMs();
    }

    public void hardBudgetMs(int milliseconds) {
        ExecutionOverlayClientConfig.setWorkbenchHardBudgetMs(milliseconds);
    }

    public boolean defaultPersistWorld() {
        return ExecutionOverlayClientConfig.workbenchDefaultPersistWorld();
    }

    public void defaultPersistWorld(boolean enabled) {
        ExecutionOverlayClientConfig.setWorkbenchDefaultPersistWorld(enabled);
    }

    public ProgressionPreset progressionPreset() {
        return ExecutionOverlayClientConfig.workbenchProgressionPreset();
    }

    public void progressionPreset(ProgressionPreset preset) {
        ExecutionOverlayClientConfig.setWorkbenchProgressionPreset(preset);
    }

    public boolean gridSnap() {
        return ExecutionOverlayClientConfig.workbenchGridSnap();
    }

    public void gridSnap(boolean enabled) {
        ExecutionOverlayClientConfig.setWorkbenchGridSnap(enabled);
    }

    /* endregion */

    /* region diagnostic export callbacks */

    public Transfer exportDiagnostics(JsonElement payload) {
        return exportDiagnostics(DIAGNOSTICS_FILE_NAME, payload);
    }

    public Transfer exportDiagnostics(String fileName, JsonElement payload) {
        Transfer transfer = writeDiagnostics(fileName, payload);
        toast(transfer.message());
        return transfer;
    }

    /* endregion */

    public SettingsHost delegate() {
        return delegate;
    }
}
