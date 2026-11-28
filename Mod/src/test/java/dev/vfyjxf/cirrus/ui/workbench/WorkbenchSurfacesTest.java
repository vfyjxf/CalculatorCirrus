package dev.vfyjxf.cirrus.ui.workbench;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.GraphCompilationStats;
import dev.vfyjxf.cirrus.api.planning.graph.SccTopology;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.planning.human.DecisionOption;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPromptKind;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.api.planning.service.PlanningDiagnostics;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningSession;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.planning.adapter.OfflineContentTypes;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeSink;
import dev.vfyjxf.cirrus.ui.workbench.copilot.PromptCardWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.SpatialDagCanvasWidget;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.PlanningBackend;
import dev.vfyjxf.cirrus.ui.workbench.state.PlanningRequestSource;
import dev.vfyjxf.cirrus.ui.workbench.state.SessionTabManager;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHost;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.client.gui.Font;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI-TC-28 wiring: surfaces translate Co-Pilot subjects and target drops into planner calls.
 */
class WorkbenchSurfacesTest {

    private static final ComponentStackKey iron = OfflineContentTypes.item("minecraft:iron_ingot");
    private static final ComponentStackKey coal = OfflineContentTypes.item("minecraft:coal");

    @Test
    void previewKindMapsEverySubjectKindExplicitly() {
        assertEquals(
            SpatialDagCanvasWidget.PreviewKind.banRecipe,
            WorkbenchSurfaces.previewKind(PromptCardWidget.PreviewKind.banRecipe)
        );
        assertEquals(
            SpatialDagCanvasWidget.PreviewKind.ownMachine,
            WorkbenchSurfaces.previewKind(PromptCardWidget.PreviewKind.ownMachine)
        );
        assertEquals(
            SpatialDagCanvasWidget.PreviewKind.buildMachine,
            WorkbenchSurfaces.previewKind(PromptCardWidget.PreviewKind.buildMachine)
        );
        assertEquals(
            SpatialDagCanvasWidget.PreviewKind.pickVariant,
            WorkbenchSurfaces.previewKind(PromptCardWidget.PreviewKind.pickVariant)
        );
        assertEquals(
            SpatialDagCanvasWidget.PreviewKind.treatFree,
            WorkbenchSurfaces.previewKind(PromptCardWidget.PreviewKind.treatFree)
        );
        assertNull(WorkbenchSurfaces.previewKind(null));
    }

    @Test
    void appendTargetMergesSameResourceAndSaturates() {
        List<TargetDemand> merged = WorkbenchSurfaces
                .appendTarget(List.of(new TargetDemand(iron, 16)), new TargetDemand(iron, 4));
        assertEquals(List.of(new TargetDemand(iron, 20)), merged);

        List<TargetDemand> appended = WorkbenchSurfaces
                .appendTarget(List.of(new TargetDemand(iron, 16)), new TargetDemand(coal, 8));
        assertEquals(2, appended.size());
        assertEquals(new TargetDemand(iron, 16), appended.get(0));
        assertEquals(new TargetDemand(coal, 8), appended.get(1));

        List<TargetDemand> saturated = WorkbenchSurfaces
                .appendTarget(List.of(new TargetDemand(iron, Long.MAX_VALUE - 1)), new TargetDemand(iron, 64));
        assertEquals(Long.MAX_VALUE, saturated.get(0).amount());
    }

    @Test
    void surfacesInstallExposesDocksAndEmptyChromeSink() {
        SessionTabManager tabs = new SessionTabManager(state -> {
            throw new IllegalStateException("no backend");
        });
        WorkbenchShellWidget shell = new WorkbenchShellWidget(new EmptyProvider(), tabs, WorkbenchMode.planner);
        try {
            WorkbenchIntegrationProvider provider = new WorkbenchIntegrationProvider();
            WorkbenchSurfaces surfaces = WorkbenchSurfaces.install(shell, provider);
            assertTrue(surfaces.leftDock() != null && surfaces.rightDock() != null);
            assertNotSame(surfaces.leftDock(), surfaces.rightDock());
            assertEquals(ChromeSink.empty, surfaces.chromeSink());
            surfaces.acceptChromeSink(null);
            assertEquals(ChromeSink.empty, surfaces.chromeSink());
        } finally {
            shell.shutdown();
        }
    }

    @Test
    void planningCompletionBeforeMountDoesNotCrashSceneMount() {
        DecisionPrompt prompt = new DecisionPrompt(
            "prompt-1",
            DecisionPromptKind.values()[0],
            "prompt.title",
            "prompt.explain",
            0.9,
            List.of(
                new DecisionOption("accept", "option.accept", CostVector.zero, List.of()),
                new DecisionOption("reject", "option.reject", CostVector.zero, List.of())
            )
        );
        PlanningBackend backend = request -> {
            TargetDemand first = request.targets().get(0);
            ProductionPlan plan = new ProductionPlan(
                "plan-1",
                "cirrus.strategy.balanced",
                List.of(
                    new AcquireStep(
                        "s001",
                        List.of(),
                        first.resource(),
                        first.amount(),
                        AcquisitionKind.manualGather,
                        CostVector.zero
                    )
                ),
                PreparationSummary.empty,
                List.of(),
                CostVector.zero,
                new VerificationCertificate(true, true, true, true, true, true, List.of())
            );
            PlanningResult result = new PlanningResult(
                PlanningStatus.feasible,
                List.of(plan),
                List.of(prompt),
                new PlanningDiagnostics(4L, 1, 1, 0, 0, false, Optional.empty())
            );
            return new StaticSession(result);
        };
        AcquisitionOracle oracle = (key, missing, progression) -> Optional.empty();
        PlanningContext context = new PlanningContext(
            InventorySnapshot.empty,
            FacilitySnapshot.empty,
            oracle,
            DefaultHumanGuidanceView.empty(),
            ProgressionState.overworldDefault
        );
        PlanningRequestSource source = spec -> new PlanningRequest(
            spec.targets(),
            context,
            spec.profile(),
            spec.budget()
        );
        SessionTabManager tabs = new SessionTabManager(
            state -> new PlannerWorkbenchController(state, backend, source, Runnable::run)
        );
        CompiledRecipeGraph graph = new EmptyGraph();
        WorkbenchShellWidget shell = new WorkbenchShellWidget(new EmptyProvider(), tabs, WorkbenchMode.planner) {
            @Override
            public CompiledRecipeGraph activeGraph() {
                return graph;
            }
        };
        WorkbenchSurfaces surfaces = WorkbenchSurfaces.install(shell, new WorkbenchIntegrationProvider());
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.useStyle(sizeFull());
        root.addWidget(shell);
        Scene scene = new Scene(root);
        SceneHost host = new SceneHost() {
            @Override
            @SuppressWarnings("NullAway")
            public Font font() {
                return null;
            }

            @Override
            public int width() {
                return 1280;
            }

            @Override
            public int height() {
                return 720;
            }
        };
        try {
            scene.init();
            tabs.openOrFocusTab(List.of(new TargetDemand(iron, 4L)), null);
            PlannerWorkbenchController active = tabs.activeTab();
            assertNotNull(active);
            active.onGraphVersionChanged(1L);
            active.replan();

            assertDoesNotThrow(() -> scene.mount(SceneContext.create(host)));
            shell.applyViewport(1280f, 720f);
            scene.setLayoutArea(1280f, 720f);
            scene.tick();
            scene.stabilize();
            scene.layout();
            root.applyLayout();

            assertEquals(1, surfaces.rightDock().promptCount());
            assertEquals(1, surfaces.rightDock().promptPage().children().size());
            assertTrue(surfaces.rightDock().promptPage().children().get(0).lifecycle().mounted());
        } finally {
            scene.destroy();
            shell.shutdown();
        }
    }

    private static final class EmptyGraph implements CompiledRecipeGraph {
        @Override
        public long catalogVersion() {
            return 1L;
        }

        @Override
        public SystemAdmission admissionOf(Namespace systemId) {
            return SystemAdmission.enabled;
        }

        @Override
        public List<CompiledRecipe> producersOf(ComponentStackKey output) {
            return List.of();
        }

        @Override
        public List<CompiledRecipe> consumersOf(ComponentStackKey input) {
            return List.of();
        }

        @Override
        public List<CompiledCycleMacro> cycleMacrosProducing(ComponentStackKey output) {
            return List.of();
        }

        @Override
        public Optional<CompiledRecipe> recipeByCompactId(int compactId) {
            return Optional.empty();
        }

        @Override
        public Optional<CompiledRecipe> recipeByKey(CompiledRecipeKey key) {
            return Optional.empty();
        }

        @Override
        public Set<ComponentStackKey> allProducibleResources() {
            return Set.of(iron);
        }

        @Override
        public SccTopology sccTopology() {
            return new SccTopology() {
                @Override
                public int sccIdOf(ComponentStackKey resource) {
                    return 0;
                }

                @Override
                public int sccSize(int sccId) {
                    return 0;
                }

                @Override
                public boolean isInNonTrivialScc(ComponentStackKey resource) {
                    return false;
                }

                @Override
                public Set<ComponentStackKey> membersOfScc(int sccId) {
                    return Set.of();
                }

                @Override
                public int largestSccSize() {
                    return 0;
                }
            };
        }

        @Override
        public GraphCompilationStats stats() {
            return new GraphCompilationStats(0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    private record StaticSession(PlanningResult result) implements PlanningSession {
        @Override
        public PlanningResult currentResult() {
            return result;
        }

        @Override
        public PlanningResult refine(HumanFeedback feedback) {
            return result;
        }

        @Override
        public PlanningResult undo() {
            return result;
        }

        @Override
        public PlanningResult redo() {
            return result;
        }

        @Override
        public void close() {}
    }

    private static final class EmptyProvider implements WorkspaceProvider {
        @Override
        public Widget plannerWorkspace(WorkbenchShellContext ctx) {
            return new WidgetGroup<>();
        }

        @Override
        public Widget facilitiesWorkspace(WorkbenchShellContext ctx) {
            return new WidgetGroup<>();
        }

        @Override
        public Widget settingsWorkspace(WorkbenchShellContext ctx) {
            return new WidgetGroup<>();
        }

        @Override
        public Widget databaseWorkspace(@Nullable WorkbenchShellContext ctx) {
            return new WidgetGroup<>();
        }
    }
}
