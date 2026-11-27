package dev.vfyjxf.cirrus.ui.workbench.settings;

import com.google.gson.JsonElement;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Data access boundary of the settings and diagnostics page.
 */
public interface SettingsHost {

    Logger logger = LoggerFactory.getLogger("CalculatorCirrus SettingsStudio");
    String DIAGNOSTICS_FILE_NAME = "cirrus-planner-stats.json";

    record Transfer(boolean ok, Component message) {}

    Optional<CirrusPlanningRuntime.PublishedSnapshot> published();

    boolean plannerAvailable();

    boolean compiling();

    long lastCompileMillis();

    CompletableFuture<Optional<Long>> recompile();

    void toast(Component message);

    Path logDirectory();

    Transfer writeDiagnostics(String fileName, JsonElement payload);

    default Transfer writeDiagnostics(JsonElement payload) {
        return writeDiagnostics(DIAGNOSTICS_FILE_NAME, payload);
    }

    /**
     * Production implementation: reads the shared planning runtime and writes diagnostics into the game log directory.
     */
    final class Default implements SettingsHost {

        private final Consumer<Component> toastSink;
        private final Path logDirectory;
        private final AtomicBoolean compiling = new AtomicBoolean(false);
        private volatile long lastCompileMillis;

        public Default(Consumer<Component> toastSink) {
            this(toastSink, FMLPaths.GAMEDIR.get().resolve("logs"));
        }

        public Default(Consumer<Component> toastSink, Path logDirectory) {
            this.toastSink = toastSink == null ? message -> {} : toastSink;
            this.logDirectory = Objects.requireNonNull(logDirectory, "logDirectory");
        }

        @Override
        public Optional<CirrusPlanningRuntime.PublishedSnapshot> published() {
            return CirrusPlanningRuntime.shared().flatMap(CirrusPlanningRuntime::currentSnapshot);
        }

        @Override
        public boolean plannerAvailable() {
            return CirrusPlanningRuntime.shared().isPresent();
        }

        @Override
        public boolean compiling() {
            return compiling.get();
        }

        @Override
        public long lastCompileMillis() {
            return lastCompileMillis;
        }

        @Override
        public CompletableFuture<Optional<Long>> recompile() {
            if (!compiling.compareAndSet(false, true)) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            long startedAt = System.nanoTime();
            CompletableFuture<Optional<Long>> result = new CompletableFuture<>();
            CompletableFuture<Optional<CirrusPlanningRuntime.PublishedSnapshot>> reload;
            try {
                reload = CirrusPlanningRuntime.sharedOrCreate().reloadFromRuntimeAsync();
            } catch (RuntimeException | LinkageError error) {
                compiling.set(false);
                logger.warn("Planner reload was rejected: {}", error.toString());
                result.complete(Optional.empty());
                return result;
            }
            reload.whenComplete((snapshot, error) -> {
                long elapsed = (System.nanoTime() - startedAt) / 1_000_000L;
                compiling.set(false);
                if (error != null || snapshot == null || snapshot.isEmpty()) {
                    result.complete(Optional.empty());
                    return;
                }
                lastCompileMillis = elapsed;
                result.complete(Optional.of(elapsed));
            });
            return result;
        }

        @Override
        public void toast(Component message) {
            toastSink.accept(message);
        }

        @Override
        public Path logDirectory() {
            return logDirectory;
        }

        @Override
        public Transfer writeDiagnostics(String fileName, JsonElement payload) {
            Objects.requireNonNull(payload, "payload");
            Path file = logDirectory.resolve(fileName);
            try {
                Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(file, payload.toString(), StandardCharsets.UTF_8);
                return new Transfer(
                    true,
                    Component.translatable(
                        "cirrus.ui.workbench.settings.export_stats_ok",
                        Component.literal(file.toAbsolutePath().toString())
                    )
                );
            } catch (IOException | RuntimeException error) {
                logger.warn("Failed writing planner diagnostics to {}", file, error);
                return new Transfer(
                    false,
                    Component.translatable(
                        "cirrus.ui.workbench.settings.export_stats_failed",
                        Component.literal(String.valueOf(error.getMessage()))
                    )
                );
            }
        }
    }

    /**
     * Runtime-free implementation without snapshots or recompilation, used by headless tests and offline previews.
     */
    final class Detached implements SettingsHost {

        private final Consumer<Component> toastSink;
        private final Path logDirectory;

        public Detached() {
            this(message -> {}, Path.of("."));
        }

        public Detached(Consumer<Component> toastSink, Path logDirectory) {
            this.toastSink = toastSink == null ? message -> {} : toastSink;
            this.logDirectory = Objects.requireNonNull(logDirectory, "logDirectory");
        }

        @Override
        public Optional<CirrusPlanningRuntime.PublishedSnapshot> published() {
            return Optional.empty();
        }

        @Override
        public boolean plannerAvailable() {
            return false;
        }

        @Override
        public boolean compiling() {
            return false;
        }

        @Override
        public long lastCompileMillis() {
            return 0L;
        }

        @Override
        public CompletableFuture<Optional<Long>> recompile() {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public void toast(Component message) {
            toastSink.accept(message);
        }

        @Override
        public Path logDirectory() {
            return logDirectory;
        }

        @Override
        public Transfer writeDiagnostics(String fileName, JsonElement payload) {
            return new Transfer(
                false,
                Component
                        .translatable("cirrus.ui.workbench.settings.export_stats_failed", Component.literal("detached"))
            );
        }
    }
}
