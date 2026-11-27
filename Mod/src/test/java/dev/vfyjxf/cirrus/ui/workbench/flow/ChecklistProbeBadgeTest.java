package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionProbeResult;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.flow.ExecutionChecklistWidget.ChecklistSummary;
import dev.vfyjxf.cirrus.ui.workbench.flow.ExecutionChecklistWidget.ExecutionReadiness;
import dev.vfyjxf.cirrus.ui.workbench.flow.ExecutionChecklistWidget.PhaseGroup;
import dev.vfyjxf.cirrus.ui.workbench.flow.ExecutionChecklistWidget.ProbeSources;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChecklistProbeBadgeTest {

    private static final Namespace interaction = Namespace.of("cirrus_test", "bench");

    private static PlanStep step(ProductionPlan plan, String stepId) {
        for (PlanStep candidate : plan.steps()) {
            if (candidate.stepId().equals(stepId)) {
                return candidate;
            }
        }
        throw new AssertionError("missing step " + stepId);
    }

    private static ExecutionChecklistWidget.ReadinessProbe stagedProbe() {
        return step -> switch (step.stepId()) {
            case "c001" -> ExecutionReadiness.autoReady;
            case "c002" -> ExecutionReadiness.guidedReady;
            default -> ExecutionReadiness.manual;
        };
    }

    private static ExecutionChecklistWidget widget(
        FlowTestFixtures.FakeHost host,
        ExecutionChecklistWidget.@Nullable SendHandler handler
    ) {
        ExecutionChecklistWidget widget = new ExecutionChecklistWidget(host, handler);
        widget.setProbe(stagedProbe());
        widget.refresh();
        return widget;
    }

    @AfterEach
    void clearProbeSources() {
        ProbeSources.clear();
    }

    @Test
    void probeResultsMapToThreeReadinessStates() {
        assertEquals(
            ExecutionReadiness.autoReady,
            ProbeSources.mapProbe(new InteractionProbeResult.Supported(interaction, true, false))
        );
        assertEquals(
            ExecutionReadiness.guidedReady,
            ProbeSources.mapProbe(new InteractionProbeResult.Supported(interaction, false, true))
        );
        assertEquals(ExecutionReadiness.manual, ProbeSources.mapProbe(new InteractionProbeResult.Unsupported(null)));
        assertEquals(ExecutionReadiness.manual, ProbeSources.mapProbe(null));
    }

    @Test
    void threeStatesRenderThreeDifferentBadges() {
        Set<String> keys = new HashSet<>();
        Set<Integer> colors = new HashSet<>();
        for (ExecutionReadiness readiness : ExecutionReadiness.values()) {
            keys.add(ExecutionChecklistWidget.badgeKey(readiness).key());
            colors.add(ExecutionChecklistWidget.badgeColor(CirrusPalette.obsidianBlueprint, readiness));
        }
        assertEquals(3, keys.size(), "each readiness state owns a distinct lang key");
        assertEquals(3, colors.size(), "each readiness state owns a distinct accent color");
        assertEquals(
            CirrusLangs.Workbench.Checklist.probeAuto.key(),
            ExecutionChecklistWidget.badgeKey(ExecutionReadiness.autoReady).key()
        );
        assertEquals(
            CirrusLangs.Workbench.Checklist.probeGuide.key(),
            ExecutionChecklistWidget.badgeKey(ExecutionReadiness.guidedReady).key()
        );
        assertEquals(
            CirrusLangs.Workbench.Checklist.probeManual.key(),
            ExecutionChecklistWidget.badgeKey(ExecutionReadiness.manual).key()
        );
        assertEquals(
            CirrusPalette.obsidianBlueprint.costS(),
            ExecutionChecklistWidget.badgeColor(CirrusPalette.obsidianBlueprint, ExecutionReadiness.autoReady)
        );
        assertEquals(
            CirrusPalette.obsidianBlueprint.costH(),
            ExecutionChecklistWidget.badgeColor(CirrusPalette.obsidianBlueprint, ExecutionReadiness.manual)
        );
    }

    @Test
    void unmappedProbesFallBackToManual() {
        ProbeSources.clear();
        assertEquals(ExecutionReadiness.manual, ProbeSources.readinessOf((CompiledRecipeKey) null));
        assertEquals(ExecutionReadiness.manual, ProbeSources.readinessOf((PlanStep) null));
        assertEquals(
            ExecutionReadiness.manual,
            ProbeSources.readinessOf(step(FlowTestFixtures.checklistPlan(), "p001")),
            "task steps are never auto executed"
        );
        assertTrue(ProbeSources.resolveInteraction(null).isEmpty());

        ProbeSources.installEntryResolver(key -> Optional.empty());
        assertEquals(
            ExecutionReadiness.manual,
            ProbeSources.readinessOf(FlowTestFixtures.smeltIron),
            "missing recipe entry falls back to MANUAL"
        );
        assertEquals(
            ExecutionReadiness.manual,
            ProbeSources.readinessOf(step(FlowTestFixtures.checklistPlan(), "c001")),
            "missing interaction adapter falls back to MANUAL"
        );
    }

    @Test
    void widgetMapsEachStepThroughTheInjectedProbe() {
        ExecutionChecklistWidget widget = widget(new FlowTestFixtures.FakeHost(FlowTestFixtures.checklistPlan()), null);
        ProductionPlan plan = FlowTestFixtures.checklistPlan();

        assertEquals(ExecutionReadiness.autoReady, widget.readinessOf(step(plan, "c001")));
        assertEquals(ExecutionReadiness.guidedReady, widget.readinessOf(step(plan, "c002")));
        assertEquals(ExecutionReadiness.manual, widget.readinessOf(step(plan, "t001")));
        assertEquals(ExecutionReadiness.manual, widget.readinessOf(step(plan, "p001")));
        assertNotEquals(
            ExecutionChecklistWidget.badgeKey(widget.readinessOf(step(plan, "c001"))).key(),
            ExecutionChecklistWidget.badgeKey(widget.readinessOf(step(plan, "c002"))).key()
        );
    }

    @Test
    void summaryCountsBatchesSupportAndPreFlightTasks() {
        ChecklistSummary summary = ExecutionChecklistWidget.summarize(FlowTestFixtures.checklistPlan(), stagedProbe());
        assertEquals(3, summary.recipeBatches());
        assertEquals(1, summary.autoSupported());
        assertEquals(3, summary.recipeSteps());
        assertEquals(2, summary.preTasks());
        assertEquals(2, summary.guidedOrManual());

        ExecutionChecklistWidget widget = widget(new FlowTestFixtures.FakeHost(FlowTestFixtures.checklistPlan()), null);
        assertEquals(summary, widget.summary());
        assertTrue(widget.summaryText().getContents() instanceof TranslatableContents);
        var translatable = (TranslatableContents) widget.summaryText().getContents();
        assertEquals("cirrus.ui.workbench.checklist.summary", translatable.getKey());
        assertEquals(4, translatable.getArgs().length);
    }

    @Test
    void stepsSplitIntoFourPhases() {
        List<PhaseGroup> groups = ExecutionChecklistWidget.phaseGroupsOf(FlowTestFixtures.checklistPlan());
        assertEquals(4, groups.size());
        assertEquals(List.of("p001"), ids(groups.get(0)));
        assertEquals(List.of("p002"), ids(groups.get(1)));
        assertEquals(List.of("c001", "c002"), ids(groups.get(2)));
        assertEquals(List.of("t001"), ids(groups.get(3)));
        for (int index = 0; index < groups.size(); index++) {
            assertEquals(index, groups.get(index).phaseIndex());
        }
        assertEquals("cirrus.ui.workbench.checklist.phase.prepare", ExecutionChecklistWidget.phaseKey(0).key());
        assertEquals("cirrus.ui.workbench.checklist.phase.final", ExecutionChecklistWidget.phaseKey(3).key());
    }

    @Test
    void checkboxProgressIsClientLocalOnly() {
        ExecutionChecklistWidget widget = widget(new FlowTestFixtures.FakeHost(FlowTestFixtures.checklistPlan()), null);
        assertEquals(5, widget.cardCount());
        assertEquals(0.0, widget.completionFraction());

        widget.toggleChecked("c001");
        assertTrue(widget.isChecked("c001"));
        assertEquals(1, widget.checkedCount());
        assertEquals(0.2, widget.completionFraction(), 1e-9);

        widget.toggleChecked("c001");
        assertFalse(widget.isChecked("c001"));
        assertEquals(0.0, widget.completionFraction());
        widget.toggleChecked(null);
        assertFalse(widget.isChecked(null));
    }

    @Test
    void primaryActionForwardsThePlanAndTheCloseFlag() {
        List<ProductionPlan> sent = new ArrayList<>();
        List<Boolean> closeFlags = new ArrayList<>();
        FlowTestFixtures.FakeHost host = new FlowTestFixtures.FakeHost(FlowTestFixtures.checklistPlan());
        ExecutionChecklistWidget widget = widget(host, (plan, closeOnSend) -> {
            sent.add(plan);
            closeFlags.add(closeOnSend);
        });

        assertTrue(widget.canSend());
        assertTrue(widget.closeOnSend(), "close-after-send defaults to true");
        assertTrue(widget.requestSend());
        assertEquals(1, sent.size());
        assertEquals("plan-checklist", sent.get(0).planId());
        assertEquals(List.of(true), closeFlags);
        assertEquals(List.of(CirrusLangs.Workbench.Topbar.sentToast), host.toasts());

        widget.setCloseOnSend(false);
        assertTrue(widget.requestSend());
        assertEquals(List.of(true, false), closeFlags);
    }

    @Test
    void primaryActionIsDisabledWithoutASendHandler() {
        FlowTestFixtures.FakeHost host = new FlowTestFixtures.FakeHost(FlowTestFixtures.checklistPlan());
        ExecutionChecklistWidget widget = widget(host, null);
        assertFalse(widget.canSend());
        assertFalse(widget.requestSend());
        assertTrue(host.toasts().isEmpty());
        widget.setSendHandler((plan, closeOnSend) -> {});
        assertTrue(widget.canSend());
        assertTrue(widget.requestSend());
    }

    @Test
    void checklistAutoPopulatesWhenPlanArrivesAfterInitialEmptyMount() {
        FlowTestFixtures.FakeHost host = new FlowTestFixtures.FakeHost(
            FlowTestFixtures.emptySnapshot(),
            CirrusPalette.obsidianBlueprint
        );
        ExecutionChecklistWidget widget = widget(host, null);
        assertEquals(0, widget.cardCount());

        host.setSnapshot(FlowTestFixtures.snapshot(FlowTestFixtures.checklistPlan()));
        widget.tick();

        assertEquals(5, widget.cardCount());
        assertEquals(3, widget.summary().recipeBatches());
    }

    private static List<String> ids(PhaseGroup group) {
        List<String> ids = new ArrayList<>();
        for (PlanStep step : group.steps()) {
            ids.add(step.stepId());
        }
        return ids;
    }
}
