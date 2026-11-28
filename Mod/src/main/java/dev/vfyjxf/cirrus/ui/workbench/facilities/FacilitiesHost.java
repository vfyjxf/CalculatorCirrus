package dev.vfyjxf.cirrus.ui.workbench.facilities;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cirrus.planning.bridge.WorldFacilityRegistry;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Data access boundary of the facilities and rules studio.
 */
public interface FacilitiesHost {

    Logger logger = LoggerFactory.getLogger("CalculatorCirrus FacilitiesStudio");
    String CONFIG_FILE_NAME = "cirrus-facilities.json";

    record Transfer(boolean ok, Component message) {}

    WorldFacilityRegistry registry();

    boolean worldBound();

    Optional<CompiledRecipeGraph> graph();

    List<Namespace> systemIds();

    List<GuidanceRule> rules();

    List<CompiledRecipeKey> sessionRecipeKeys();

    void putRule(GuidanceRule rule);

    void removeRule(String ruleId);

    void toast(Component message);

    Path configDirectory();

    Transfer exportConfig();

    Transfer importConfig();

    default FacilitySnapshot facilitySnapshot() {
        return registry().facilitySnapshot();
    }

    default Path configFile() {
        return configDirectory().resolve(CONFIG_FILE_NAME);
    }

    default Component statusBadge() {
        return worldBound()
                ? Component.translatable("cirrus.ui.workbench.facilities.bound_world")
                : Component.translatable("cirrus.ui.workbench.facilities.detached_banner");
    }

    /**
     * Production implementation: facilities go to {@link WorldFacilityRegistry}, rules to the planning runtime.
     */
    final class Default implements FacilitiesHost {

        private final Consumer<Component> toastSink;
        private final Path configDirectory;
        private final AtomicReference<WorldFacilityRegistry> detached = new AtomicReference<>();
        private volatile Supplier<List<CompiledRecipeKey>> sessionRecipeSource = List::of;

        public Default(Consumer<Component> toastSink) {
            this(toastSink, FMLPaths.CONFIGDIR.get());
        }

        public Default(Consumer<Component> toastSink, Path configDirectory) {
            this.toastSink = toastSink == null ? message -> {} : toastSink;
            this.configDirectory = Objects.requireNonNull(configDirectory, "configDirectory");
        }

        @Override
        public WorldFacilityRegistry registry() {
            Optional<WorldFacilityRegistry> installed = WorldFacilityRegistry.installed();
            if (installed.isPresent()) {
                return installed.get();
            }
            return detached
                    .updateAndGet(previous -> previous != null ? previous : WorldFacilityRegistry.createDetached());
        }

        @Override
        public boolean worldBound() {
            return WorldFacilityRegistry.installed().isPresent();
        }

        @Override
        public Optional<CompiledRecipeGraph> graph() {
            return CirrusPlanningRuntime.shared().flatMap(CirrusPlanningRuntime::currentGraph);
        }

        @Override
        public List<Namespace> systemIds() {
            TreeSet<Namespace> ids = new TreeSet<>(Comparator.comparing(Namespace::toString));
            try {
                for (RecipeSystem<?> system : RecipeApi.instance().allSystems()) {
                    if (system != null) {
                        ids.add(system.systemId());
                    }
                }
            } catch (RuntimeException | LinkageError error) {
                logger.debug("Recipe systems are not available yet: {}", error.toString());
            }
            return List.copyOf(ids);
        }

        @Override
        public List<GuidanceRule> rules() {
            Map<String, GuidanceRule> merged = new LinkedHashMap<>();
            for (GuidanceRule rule : registry().worldSaveRules()) {
                merged.put(rule.ruleId(), rule);
            }
            CirrusPlanningRuntime.shared().ifPresent(runtime -> {
                for (GuidanceRule rule : runtime.guidanceStore().rulesInScope(GuidanceScope.session)) {
                    merged.putIfAbsent(rule.ruleId(), rule);
                }
            });
            return List.copyOf(merged.values());
        }

        @Override
        public List<CompiledRecipeKey> sessionRecipeKeys() {
            List<CompiledRecipeKey> keys = sessionRecipeSource.get();
            return keys == null ? List.of() : List.copyOf(keys);
        }

        public void setSessionRecipeSource(Supplier<List<CompiledRecipeKey>> source) {
            this.sessionRecipeSource = source == null ? List::of : source;
        }

        @Override
        public void putRule(GuidanceRule rule) {
            Objects.requireNonNull(rule, "rule");
            CirrusPlanningRuntime.sharedOrCreate().guidanceStore().putRule(rule);
            if (rule.scope() == GuidanceScope.worldSave) {
                registry().putWorldSaveRule(rule);
            }
        }

        @Override
        public void removeRule(String ruleId) {
            if (ruleId == null || ruleId.isBlank()) {
                return;
            }
            CirrusPlanningRuntime.shared().ifPresent(runtime -> runtime.guidanceStore().removeRule(ruleId));
            registry().removeWorldSaveRule(ruleId);
        }

        @Override
        public void toast(Component message) {
            toastSink.accept(message);
        }

        @Override
        public Path configDirectory() {
            return configDirectory;
        }

        @Override
        public Transfer exportConfig() {
            Optional<JsonElement> encoded = registry().encodeToJson();
            Path file = configFile();
            if (encoded.isEmpty()) {
                return new Transfer(
                    false,
                    Component.translatable("cirrus.ui.workbench.facilities.export_failed", Component.literal("encode"))
                );
            }
            try {
                Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(file, encoded.get().toString(), StandardCharsets.UTF_8);
                return new Transfer(
                    true,
                    Component.translatable(
                        "cirrus.ui.workbench.facilities.export_ok",
                        Component.literal(file.toAbsolutePath().toString())
                    )
                );
            } catch (IOException | RuntimeException error) {
                logger.warn("Failed exporting facility config to {}", file, error);
                return new Transfer(
                    false,
                    Component.translatable(
                        "cirrus.ui.workbench.facilities.export_failed",
                        Component.literal(String.valueOf(error.getMessage()))
                    )
                );
            }
        }

        @Override
        public Transfer importConfig() {
            Path file = configFile();
            if (!Files.isRegularFile(file)) {
                return new Transfer(
                    false,
                    Component.translatable(
                        "cirrus.ui.workbench.facilities.import_failed",
                        Component.literal(file.toAbsolutePath().toString())
                    )
                );
            }
            try {
                JsonElement element = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (!element.isJsonObject()) {
                    return new Transfer(
                        false,
                        Component.translatable(
                            "cirrus.ui.workbench.facilities.import_failed",
                            Component.literal(file.toAbsolutePath().toString())
                        )
                    );
                }
                WorldFacilityRegistry loaded = WorldFacilityRegistry.loadFromJson(element);
                registry().loadPersistedState(loaded.toPersistedState());
                return new Transfer(
                    true,
                    Component.translatable(
                        "cirrus.ui.workbench.facilities.import_ok",
                        Component.literal(file.toAbsolutePath().toString())
                    )
                );
            } catch (IOException | RuntimeException error) {
                logger.warn("Failed importing facility config from {}", file, error);
                return new Transfer(
                    false,
                    Component.translatable(
                        "cirrus.ui.workbench.facilities.import_failed",
                        Component.literal(String.valueOf(error.getMessage()))
                    )
                );
            }
        }
    }

    /**
     * Implementation without runtime dependencies: it owns a registry directly.
     */
    final class Detached implements FacilitiesHost {

        private final WorldFacilityRegistry registry;
        private final Consumer<Component> toastSink;
        private final Path configDirectory;

        public Detached(WorldFacilityRegistry registry) {
            this(registry, message -> {}, Path.of("."));
        }

        public Detached(WorldFacilityRegistry registry, Consumer<Component> toastSink, Path configDirectory) {
            this.registry = Objects.requireNonNull(registry, "registry");
            this.toastSink = toastSink == null ? message -> {} : toastSink;
            this.configDirectory = Objects.requireNonNull(configDirectory, "configDirectory");
        }

        @Override
        public WorldFacilityRegistry registry() {
            return registry;
        }

        @Override
        public boolean worldBound() {
            return false;
        }

        @Override
        public Optional<CompiledRecipeGraph> graph() {
            return Optional.empty();
        }

        @Override
        public List<Namespace> systemIds() {
            return List.of();
        }

        @Override
        public List<GuidanceRule> rules() {
            return registry.worldSaveRules();
        }

        @Override
        public List<CompiledRecipeKey> sessionRecipeKeys() {
            return List.of();
        }

        @Override
        public void putRule(GuidanceRule rule) {
            registry.putWorldSaveRule(rule);
        }

        @Override
        public void removeRule(String ruleId) {
            registry.removeWorldSaveRule(ruleId);
        }

        @Override
        public void toast(Component message) {
            toastSink.accept(message);
        }

        @Override
        public Path configDirectory() {
            return configDirectory;
        }

        @Override
        public Transfer exportConfig() {
            return new Transfer(
                false,
                Component.translatable("cirrus.ui.workbench.facilities.export_failed", Component.literal("detached"))
            );
        }

        @Override
        public Transfer importConfig() {
            return new Transfer(
                false,
                Component.translatable("cirrus.ui.workbench.facilities.import_failed", Component.literal("detached"))
            );
        }
    }
}
