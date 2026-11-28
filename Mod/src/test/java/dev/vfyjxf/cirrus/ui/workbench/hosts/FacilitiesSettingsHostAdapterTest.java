package dev.vfyjxf.cirrus.ui.workbench.hosts;

import com.google.gson.JsonObject;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.cost.ObjectivePreset;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.planning.bridge.WorldFacilityRegistry;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.facilities.FacilitiesHost;
import dev.vfyjxf.cirrus.ui.workbench.settings.ProgressionPreset;
import dev.vfyjxf.cirrus.ui.workbench.settings.SettingsHost;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Facilities and settings host adapter: the detached fallback flag, config getters and runtime telemetry reads never throw.
 */
class FacilitiesSettingsHostAdapterTest {

    @Test
    void facilitiesHostMarksTheDetachedFallback() {
        WorldFacilityRegistry registry = WorldFacilityRegistry.createDetached();
        List<Component> toasts = new ArrayList<>();
        ShellFacilitiesHost host = new ShellFacilitiesHost(
            new FacilitiesHost.Detached(registry, toasts::add, Path.of(".")),
            toasts::add,
            Optional::empty,
            List::of
        );

        assertFalse(host.worldBound());
        assertSame(registry, host.registry());
        assertTrue(host.detachedFallback());
        assertNotNull(host.facilitySnapshot());
        assertTrue(host.graph().isEmpty());
        assertTrue(host.sessionRecipeKeys().isEmpty());
        assertTrue(host.rules().isEmpty());
        assertTrue(host.systemIds().isEmpty());
        assertNotNull(host.configDirectory());
        assertNotNull(host.statusBadge());
        FacilitiesHost.Transfer export = host.exportConfig();
        assertNotNull(export.message());
        FacilitiesHost.Transfer importAttempt = host.importConfig();
        assertNotNull(importAttempt.message());
    }

    @Test
    void worldBoundDelegateIsNotFlaggedAsDetached() {
        List<Component> toasts = new ArrayList<>();
        ShellFacilitiesHost host = new ShellFacilitiesHost(
            new BoundFacilitiesHost(WorldFacilityRegistry.createDetached()),
            toasts::add
        );

        assertTrue(host.worldBound());
        assertNotNull(host.registry());
        assertFalse(host.detachedFallback());
    }

    @Test
    void toastSinkReceivesComponentMessages() {
        List<Component> toasts = new ArrayList<>();
        ShellFacilitiesHost host = new ShellFacilitiesHost(
            new FacilitiesHost.Detached(WorldFacilityRegistry.createDetached(), toasts::add, Path.of(".")),
            toasts::add
        );

        Component message = Component.literal("facility-toast");
        host.toast(message);

        assertEquals(List.of(message), toasts);
    }

    @Test
    void sessionRecipeKeysComeFromTheSelectedPlanSteps() {
        CompiledRecipeKey craft = HostsTestFixtures.recipeKey("minecraft:crafting", "minecraft:piston");
        CompiledRecipeKey convert = HostsTestFixtures.recipeKey("create:pressing", "create:iron_sheet");
        List<PlanStep> steps = List.of(
            new AcquireStep(
                "s001",
                List.of(),
                HostsTestFixtures.ironOre,
                8L,
                AcquisitionKind.manualGather,
                HostsTestFixtures.cost()
            ),
            new CraftStep(
                "s002",
                List.of("s001"),
                craft,
                1L,
                Optional.empty(),
                false,
                Map.of(),
                Map.of(),
                HostsTestFixtures.cost()
            ),
            new ConvertStep("s003", List.of("s002"), convert, 1L, Map.of(), HostsTestFixtures.cost())
        );
        ProductionPlan plan = new ProductionPlan(
            "plan-keys",
            "cirrus.strategy.balanced",
            steps,
            PreparationSummary.empty,
            List.of(),
            HostsTestFixtures.cost(),
            new VerificationCertificate(true, true, true, true, true, true, List.of())
        );
        WorkbenchUiSnapshot snapshot = HostsTestFixtures.snapshotWithPlan(plan);

        assertEquals(List.of(craft, convert), ShellFacilitiesHost.sessionRecipeKeysOf(snapshot));
        assertTrue(ShellFacilitiesHost.sessionRecipeKeysOf(null).isEmpty());
    }

    @Test
    void sessionRecipeSourceCanBeSwappedByTheShell() {
        ShellFacilitiesHost host = new ShellFacilitiesHost(
            new FacilitiesHost.Detached(WorldFacilityRegistry.createDetached(), message -> {}, Path.of(".")),
            message -> {}
        );
        CompiledRecipeKey key = HostsTestFixtures.recipeKey("minecraft:smelting", "minecraft:iron_ingot");

        host.sessionRecipeSource(() -> List.of(key));

        assertEquals(List.of(key), host.sessionRecipeKeys());

        host.sessionRecipeSource(null);

        assertTrue(host.sessionRecipeKeys().isEmpty());
    }

    @Test
    void settingsHostReadsRuntimeTelemetryWithoutNpe() {
        List<Component> toasts = new ArrayList<>();
        ShellSettingsHost host = new ShellSettingsHost(
            new SettingsHost.Detached(toasts::add, Path.of("logs")),
            toasts::add
        );

        assertTrue(host.published().isEmpty());
        assertTrue(host.publishedSnapshot().isEmpty());
        assertEquals(0L, host.publishedVersion());
        assertEquals(0L, host.attachEpoch());
        assertNull(host.captureStats());
        assertNull(host.graphStats());
        assertFalse(host.plannerAvailable());
        assertFalse(host.compiling());
        assertEquals(0L, host.lastCompileMillis());
        assertTrue(host.recompile().join().isEmpty());
        assertEquals(Path.of("logs"), host.logDirectory());
        assertNotNull(host.delegate());
    }

    @Test
    void settingsHostExposesWorkbenchConfigGetters() {
        ShellSettingsHost host = new ShellSettingsHost(
            new SettingsHost.Detached(message -> {}, Path.of("logs")),
            message -> {}
        );

        assertEquals(WorkbenchThemeMode.obsidianBlueprint, host.theme());
        assertFalse(host.reducedMotion());
        assertEquals(DagLayoutMode.horizontalLr, host.defaultDagLayout());
        assertEquals(ObjectivePreset.autoSmart, host.defaultPreset());
        assertEquals(80, host.softBudgetMs());
        assertEquals(250, host.hardBudgetMs());
        assertFalse(host.defaultPersistWorld());
        assertEquals(ProgressionPreset.overworld, host.progressionPreset());
        assertTrue(host.gridSnap());
    }

    @Test
    void diagnosticsExportWritesAndToasts() {
        List<Component> toasts = new ArrayList<>();
        ShellSettingsHost host = new ShellSettingsHost(
            new SettingsHost.Detached(toasts::add, Path.of("logs")),
            toasts::add
        );

        SettingsHost.Transfer transfer = host.exportDiagnostics(new JsonObject());

        assertFalse(transfer.ok());
        assertEquals(1, toasts.size());
        assertSame(transfer.message(), toasts.get(0));
    }

    private static final class BoundFacilitiesHost implements FacilitiesHost {

        private final WorldFacilityRegistry registry;

        private BoundFacilitiesHost(WorldFacilityRegistry registry) {
            this.registry = registry;
        }

        @Override
        public WorldFacilityRegistry registry() {
            return registry;
        }

        @Override
        public boolean worldBound() {
            return true;
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
            return List.of();
        }

        @Override
        public List<CompiledRecipeKey> sessionRecipeKeys() {
            return List.of();
        }

        @Override
        public void putRule(GuidanceRule rule) {}

        @Override
        public void removeRule(String ruleId) {}

        @Override
        public void toast(Component message) {}

        @Override
        public Path configDirectory() {
            return Path.of(".");
        }

        @Override
        public Transfer exportConfig() {
            return new Transfer(true, Component.literal("ok"));
        }

        @Override
        public Transfer importConfig() {
            return new Transfer(true, Component.literal("ok"));
        }

        @Override
        public FacilitySnapshot facilitySnapshot() {
            return FacilitySnapshot.empty;
        }
    }
}
