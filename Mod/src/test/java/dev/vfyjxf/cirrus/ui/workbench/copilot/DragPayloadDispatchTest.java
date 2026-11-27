package dev.vfyjxf.cirrus.ui.workbench.copilot;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.human.AnswerPromptFeedback;
import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPromptKind;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.OverrideStockFeedback;
import dev.vfyjxf.cirrus.api.planning.human.PinResourceRecipeFeedback;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.RejectStepFeedback;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.service.PlanningDiagnostics;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.ui.workbench.layout.DagNodeBox;
import dev.vfyjxf.cirrus.ui.workbench.layout.PlanDagLayout;
import dev.vfyjxf.cirrus.ui.workbench.layout.PlanDagLayoutResult;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.DragPayload;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHost;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyles;
import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.acquire;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.binding;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.coal;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.cost;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.craft;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.furnace;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.graph;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.ironIngot;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.ironOre;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.plan;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.provision;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.result;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.smeltIron;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.stepSnapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UI-TC-28: every drop zone for the four DND-04/05/06/07 payload kinds, cancel safety and the six feedback shortcuts. */
class DragPayloadDispatchTest {

    private static ProductionPlan tradePlan() {
        return plan(
            "plan-a",
            "cirrus.strategy.balanced",
            List.of(
                acquire("s-acq", ironOre, 64L),
                provision("s-prov", furnace, 2),
                craft(
                    "s-craft",
                    smeltIron,
                    List.of("s-acq"),
                    Map.of(0, binding(ironOre)),
                    Optional.of(furnace),
                    cost(8.0, 60.0, 0.0, 0.0, 0.0)
                )
            ),
            cost(20.0, 60.0, 8.0, 4.0, 2.0)
        );
    }

    private static PlanningResult readyResult() {
        return result(PlanningStatus.feasible, List.of(tradePlan()), List.of(), Optional.empty());
    }

    @Test
    void resourceDragCoversEveryDropTarget() {
        CoPilotTestFixtures.FakeHost host = new CoPilotTestFixtures.FakeHost(
            CoPilotTestFixtures.snapshot(readyResult())
        );
        CoPilotTestFixtures.FakeSink sink = new CoPilotTestFixtures.FakeSink();
        DragPayload.ResourceDrag drag = new DragPayload.ResourceDrag(ironOre, 5L, "ledger");
        CoPilotDnd.DropContext ctx = CoPilotDnd.DropContext.of(false).withSink(sink);

        assertTrue(CoPilotDnd.accept(host, CoPilotDnd.DropTarget.canvasBlank, drag, ctx).handled());
        assertEquals(List.of(new TargetDemand(ironOre, 5L)), sink.added);
        assertEquals(1, host.toasts.size());
        assertEquals(0, host.feedbacks.size());

        assertTrue(CoPilotDnd.accept(host, CoPilotDnd.DropTarget.mainTargetSlot, drag, ctx).handled());
        assertEquals(List.of(new TargetDemand(ironOre, 5L)), sink.replaced);

        assertTrue(
            CoPilotDnd.accept(
                host,
                CoPilotDnd.DropTarget.acquireStep,
                drag,
                new CoPilotDnd.DropContext("s-acq", false, sink)
            ).handled()
        );
        assertEquals(1, host.feedbacks.size());
        assertTrue(host.feedbacks.get(0) instanceof OverrideStockFeedback);
        OverrideStockFeedback override = (OverrideStockFeedback) host.feedbacks.get(0);
        assertEquals(ironOre, override.resource());
        assertEquals(5L, override.newAvailableAmount());

        assertTrue(CoPilotDnd.accept(host, CoPilotDnd.DropTarget.freeSourceSlot, drag, ctx).handled());
        ApplyRulesFeedback freeRules = (ApplyRulesFeedback) host.feedbacks.get(1);
        assertEquals(1, freeRules.rules().size());
        ResourceRule pin = (ResourceRule) freeRules.rules().get(0);
        assertEquals(RuleStrength.hardPin, pin.strength());
        assertTrue(pin.treatAsFreeBaseSource());
        assertEquals(GuidanceScope.session, pin.scope());
        assertEquals(ironOre, pin.resource());

        assertTrue(CoPilotDnd.accept(host, CoPilotDnd.DropTarget.banSlot, drag, ctx).handled());
        ResourceRule ban = (ResourceRule) ((ApplyRulesFeedback) host.feedbacks.get(2)).rules().get(0);
        assertEquals(RuleStrength.hardBan, ban.strength());
        assertFalse(ban.treatAsFreeBaseSource());

        assertTrue(
            CoPilotDnd.accept(host, CoPilotDnd.DropTarget.banSlot, drag, CoPilotDnd.DropContext.of(true).withSink(sink))
                    .handled()
        );
        ApplyRulesFeedback persisted = (ApplyRulesFeedback) host.feedbacks.get(3);
        assertTrue(persisted.persistToWorld());
        assertEquals(GuidanceScope.worldSave, persisted.rules().get(0).scope());
    }

    @Test
    void machineDragDeclaresOwnershipOnlyOnMatchingSteps() {
        CoPilotTestFixtures.FakeHost host = new CoPilotTestFixtures.FakeHost(
            CoPilotTestFixtures.snapshot(readyResult())
        );
        DragPayload.MachineDrag drag = new DragPayload.MachineDrag(furnace, 1);

        assertFalse(
            CoPilotDnd.accept(
                host,
                CoPilotDnd.DropTarget.acquireStep,
                drag,
                new CoPilotDnd.DropContext("s-acq", false, null)
            ).handled()
        );
        assertEquals(0, host.dispatchCount());

        assertTrue(
            CoPilotDnd.accept(
                host,
                CoPilotDnd.DropTarget.provisionStep,
                drag,
                new CoPilotDnd.DropContext("s-prov", false, null)
            ).handled()
        );
        assertEquals(1, host.feedbacks.size());
        DeclareOwnedMachineFeedback declare = (DeclareOwnedMachineFeedback) host.feedbacks.get(0);
        assertEquals(furnace, declare.machineId());
        assertEquals(1, declare.ownedCount());
        assertFalse(declare.persistToWorld());

        assertTrue(
            CoPilotDnd.accept(
                host,
                CoPilotDnd.DropTarget.craftStep,
                drag,
                new CoPilotDnd.DropContext("s-craft", true, null)
            ).handled()
        );
        assertTrue(((DeclareOwnedMachineFeedback) host.feedbacks.get(1)).persistToWorld());

        assertFalse(
            CoPilotDnd.accept(
                host,
                CoPilotDnd.DropTarget.provisionStep,
                new DragPayload.MachineDrag("ae2:charger", 1),
                new CoPilotDnd.DropContext("s-prov", false, null)
            ).handled()
        );
        assertEquals(2, host.feedbacks.size());
    }

    @Test
    void recipeDragPinsTheMatchingRecipe() {
        CoPilotTestFixtures.FakeHost host = new CoPilotTestFixtures.FakeHost(
            CoPilotTestFixtures.snapshot(readyResult())
        );
        DragPayload.RecipeDrag drag = new DragPayload.RecipeDrag(ironIngot, smeltIron);

        assertTrue(
            CoPilotDnd.accept(
                host,
                CoPilotDnd.DropTarget.craftStep,
                drag,
                new CoPilotDnd.DropContext("s-craft", false, null)
            ).handled()
        );
        PinResourceRecipeFeedback pin = (PinResourceRecipeFeedback) host.feedbacks.get(0);
        assertEquals(ironIngot, pin.resource());
        assertEquals(smeltIron, pin.recipeKey());

        assertFalse(
            CoPilotDnd.accept(
                host,
                CoPilotDnd.DropTarget.acquireStep,
                drag,
                new CoPilotDnd.DropContext("s-acq", false, null)
            ).handled()
        );
        assertEquals(1, host.feedbacks.size());

        assertTrue(
            CoPilotDnd.accept(
                host,
                CoPilotDnd.DropTarget.acquireStep,
                new DragPayload.RecipeDrag(ironOre, smeltIron),
                new CoPilotDnd.DropContext("s-acq", false, null)
            ).handled()
        );
        assertEquals(2, host.feedbacks.size());

        CompiledRecipeKey other = CoPilotTestFixtures.key("minecraft:crafting", "minecraft:iron_block");
        assertFalse(
            CoPilotDnd.accept(
                host,
                CoPilotDnd.DropTarget.craftStep,
                new DragPayload.RecipeDrag(ironOre, other),
                new CoPilotDnd.DropContext("s-craft", false, null)
            ).handled()
        );
        assertEquals(2, host.feedbacks.size());
    }

    @Test
    void planCompareDragActivatesDiffAndClassifiesSteps() {
        CoPilotTestFixtures.FakeHost host = new CoPilotTestFixtures.FakeHost(
            CoPilotTestFixtures.snapshot(readyResult())
        );
        DragPayload.PlanCompareDrag drag = new DragPayload.PlanCompareDrag("plan-b", "cirrus.strategy.min_human_time");

        assertTrue(
            CoPilotDnd.accept(host, CoPilotDnd.DropTarget.compareSlot, drag, CoPilotDnd.DropContext.of(false)).handled()
        );
        assertEquals(List.of("plan-b"), host.comparedPlans);

        assertFalse(
            CoPilotDnd.accept(host, CoPilotDnd.DropTarget.canvasBlank, drag, CoPilotDnd.DropContext.of(false)).handled()
        );
        assertEquals(1, host.dispatchCount());

        ProductionPlan base = tradePlan();
        ProductionPlan compare = plan(
            "plan-b",
            "cirrus.strategy.min_human_time",
            List.of(acquire("s-acq", ironOre, 64L), provision("s-other", furnace, 4)),
            cost(12.0, 30.0, 16.0, 4.0, 2.0)
        );
        PlanDagLayoutResult layout = PlanDagLayout
                .compute(base, compare, graph(), DagLayoutMode.horizontalLr, Set.of(), Map.of(), Set.of());
        Map<String, DagNodeBox.DiffStatus> statuses = new HashMap<>();
        for (DagNodeBox node : layout.nodes()) {
            statuses.put(node.stepId(), node.diffStatus());
        }
        assertEquals(DagNodeBox.DiffStatus.kept, statuses.get("s-acq"));
        assertEquals(DagNodeBox.DiffStatus.added, statuses.get("s-craft"));
        assertEquals(DagNodeBox.DiffStatus.added, statuses.get("s-prov"));
        assertEquals(DagNodeBox.DiffStatus.removed, statuses.get("s-other"));

        PlanningResult twoPlans = result(
            PlanningStatus.feasible,
            List.of(tradePlan(), compare),
            List.of(),
            Optional.empty()
        );
        CoPilotTestFixtures.FakeHost compareHost = new CoPilotTestFixtures.FakeHost(
            CoPilotTestFixtures.compareSnapshot(twoPlans, "plan-a", "plan-b")
        );
        LeftPlanDockWidget dock = new LeftPlanDockWidget(compareHost);
        assertTrue(dock.compareMode());
        List<PromptDeltaFormat.Chip> chips = dock.compareChips();
        assertFalse(chips.isEmpty());
        assertEquals(3, chips.size());
        for (PromptDeltaFormat.Chip chip : chips) {
            assertNotNull(chip.label());
        }
    }

    @Test
    void cancelledOrInvalidReleaseDispatchesNothing() {
        CoPilotTestFixtures.FakeHost host = new CoPilotTestFixtures.FakeHost(
            CoPilotTestFixtures.snapshot(readyResult())
        );
        CoPilotTestFixtures.FakeSink sink = new CoPilotTestFixtures.FakeSink();
        DragPayload.ResourceDrag drag = new DragPayload.ResourceDrag(ironOre, 5L, "ledger");
        CoPilotDnd.DropContext ctx = CoPilotDnd.DropContext.of(false).withSink(sink);

        assertFalse(CoPilotDnd.accept(host, CoPilotDnd.DropTarget.invalid, drag, ctx).handled());
        assertFalse(CoPilotDnd.accept(host, CoPilotDnd.DropTarget.canvasBlank, null, ctx).handled());
        assertFalse(
            CoPilotDnd.acceptResource(
                host,
                CoPilotDnd.DropTarget.freeSourceSlot,
                new DragPayload.MachineDrag(furnace, 1),
                ctx
            ).handled()
        );
        assertFalse(
            CoPilotDnd.accept(
                host,
                CoPilotDnd.DropTarget.acquireStep,
                new DragPayload.ResourceDrag(coal, 1L, "ledger"),
                new CoPilotDnd.DropContext("s-acq", false, sink)
            ).handled()
        );
        assertFalse(
            CoPilotDnd.accept(
                host,
                CoPilotDnd.DropTarget.craftStep,
                new DragPayload.ResourceDrag(ironOre, 1L, "ledger"),
                new CoPilotDnd.DropContext("s-craft", false, sink)
            ).handled()
        );

        Rect zone = new Rect(0, 0, 20, 20);
        assertFalse(CoPilotDnd.consumable(drag, true, new FloatPos(10.0, 10.0), zone));
        assertFalse(CoPilotDnd.consumable(drag, false, new FloatPos(90.0, 90.0), zone));
        assertFalse(CoPilotDnd.consumable(null, false, new FloatPos(10.0, 10.0), zone));
        assertTrue(CoPilotDnd.consumable(drag, false, new FloatPos(10.0, 10.0), zone));

        assertEquals(0, host.dispatchCount());
        assertEquals(0, sink.calls());
    }

    @Test
    void compareSlotGeometryGatesThePlanDrop() {
        CoPilotTestFixtures.FakeHost host = new CoPilotTestFixtures.FakeHost(
            CoPilotTestFixtures.snapshot(readyResult())
        );
        LeftPlanDockWidget dock = new LeftPlanDockWidget(host);
        DragPayload.PlanCompareDrag drag = new DragPayload.PlanCompareDrag("plan-b", "cirrus.strategy.balanced");

        assertFalse(dock.dropCompareAt(new DragPayload.MachineDrag(furnace, 1), 0.5, 5.0));
        assertEquals(0, host.dispatchCount());

        assertFalse(dock.dropCompareAt(drag, 5.0, 5.0));
        assertEquals(0, host.dispatchCount());

        assertTrue(dock.dropCompareAt(drag, 0.5, 5.0));
        assertEquals(1, host.comparedPlans.size());
    }

    @Test
    void leftPlanDockAllowsSwitchingToScatterWithZeroOrOnePlan() {
        CoPilotTestFixtures.FakeHost emptyHost = new CoPilotTestFixtures.FakeHost(
            CoPilotTestFixtures.snapshot(
                new PlanningResult(
                    PlanningStatus.feasible,
                    List.of(),
                    List.of(),
                    new PlanningDiagnostics(1L, 0, 0, 0, 0, false, Optional.empty())
                )
            )
        );
        LeftPlanDockWidget emptyDock = new LeftPlanDockWidget(emptyHost);
        emptyDock.setMode(LeftPlanDockWidget.Mode.scatter);
        assertEquals(LeftPlanDockWidget.Mode.scatter, emptyDock.mode());

        CoPilotTestFixtures.FakeHost singleHost = new CoPilotTestFixtures.FakeHost(
            CoPilotTestFixtures.snapshot(readyResult())
        );
        LeftPlanDockWidget singleDock = new LeftPlanDockWidget(singleHost);
        singleDock.setMode(LeftPlanDockWidget.Mode.scatter);
        assertEquals(LeftPlanDockWidget.Mode.scatter, singleDock.mode());
    }

    @Test
    void stepInspectorDispatchesTheSixFeedbackKinds() {
        PlanningResult ready = readyResult();
        CoPilotTestFixtures.FakeGraph recipeGraph = (CoPilotTestFixtures.FakeGraph) graph();

        CoPilotTestFixtures.FakeHost craftHost = new CoPilotTestFixtures.FakeHost(stepSnapshot(ready, "s-craft"));
        StepInspectorWidget craftInspector = new StepInspectorWidget(craftHost).graph(recipeGraph);
        assertEquals("s-craft", Objects.requireNonNull(craftInspector.selectedStep()).stepId());
        assertTrue(craftInspector.availableActions().contains(StepInspectorWidget.Action.pinRecipe));
        assertTrue(craftInspector.perform(StepInspectorWidget.Action.applyRules));
        assertTrue(craftInspector.perform(StepInspectorWidget.Action.declareOwned));
        assertTrue(craftInspector.perform(StepInspectorWidget.Action.rejectStep));
        assertTrue(craftInspector.perform(StepInspectorWidget.Action.pinRecipe));
        assertEquals(4, craftHost.feedbacks.size());
        assertTrue(craftHost.feedbacks.get(0) instanceof ApplyRulesFeedback);
        assertEquals(
            RuleStrength.hardBan,
            ((RecipeRule) ((ApplyRulesFeedback) craftHost.feedbacks.get(0)).rules().get(0)).strength()
        );
        assertTrue(craftHost.feedbacks.get(1) instanceof DeclareOwnedMachineFeedback);
        assertTrue(craftHost.feedbacks.get(2) instanceof RejectStepFeedback);
        PinResourceRecipeFeedback pin = (PinResourceRecipeFeedback) craftHost.feedbacks.get(3);
        assertEquals(ironIngot, pin.resource());
        assertEquals(smeltIron, pin.recipeKey());

        CoPilotTestFixtures.FakeHost acquireHost = new CoPilotTestFixtures.FakeHost(stepSnapshot(ready, "s-acq"));
        StepInspectorWidget acquireInspector = new StepInspectorWidget(acquireHost).graph(recipeGraph);
        assertTrue(acquireInspector.perform(StepInspectorWidget.Action.overrideStock));
        OverrideStockFeedback override = (OverrideStockFeedback) acquireHost.feedbacks.get(0);
        assertEquals(ironOre, override.resource());
        assertEquals(64L, override.newAvailableAmount());

        CoPilotTestFixtures.FakeHost answerHost = new CoPilotTestFixtures.FakeHost(stepSnapshot(ready, "s-acq"));
        StepInspectorWidget answerInspector = new StepInspectorWidget(answerHost).graph(recipeGraph).prompts(
            List.of(
                CoPilotTestFixtures.resolved(
                    CoPilotTestFixtures.prompt(
                        "prompt:expensive_leaf:[minecraft:item]minecraft:iron_ore",
                        DecisionPromptKind.baseOrSupplyBoundary,
                        0.6,
                        CoPilotTestFixtures.option("treat_free", CostVector.zero, List.of()),
                        CoPilotTestFixtures.option("keep_gather", CostVector.zero, List.of())
                    ),
                    ironOre,
                    null,
                    null,
                    null,
                    null,
                    Set.of("s-acq")
                )
            )
        );
        assertTrue(answerInspector.availableActions().contains(StepInspectorWidget.Action.answer));
        assertTrue(answerInspector.perform(StepInspectorWidget.Action.answer));
        assertTrue(answerHost.feedbacks.get(0) instanceof AnswerPromptFeedback);
        assertEquals("treat_free", ((AnswerPromptFeedback) answerHost.feedbacks.get(0)).chosenOptionId());
    }

    @Test
    void alternativeProducersDrawerPinsTheChosenRecipe() {
        CoPilotTestFixtures.FakeHost host = new CoPilotTestFixtures.FakeHost(
            CoPilotTestFixtures.snapshot(readyResult())
        );
        AlternativeProducersDrawerWidget drawer = new AlternativeProducersDrawerWidget(host).graph(graph())
                .resource(ironIngot);
        assertEquals(1, drawer.rowCount());
        assertEquals(smeltIron, drawer.producers().get(0).key());

        assertTrue(drawer.pin(smeltIron));
        PinResourceRecipeFeedback pin = (PinResourceRecipeFeedback) host.feedbacks.get(0);
        assertEquals(ironIngot, pin.resource());
        assertEquals(smeltIron, pin.recipeKey());
        assertFalse(pin.persistToWorld());

        AlternativeProducersDrawerWidget worldDrawer = new AlternativeProducersDrawerWidget(host).graph(graph())
                .resource(ironIngot).persistToWorld(true);
        assertTrue(worldDrawer.pin(smeltIron));
        assertTrue(((PinResourceRecipeFeedback) host.feedbacks.get(1)).persistToWorld());
    }

    @Test
    void machineStripListsPlanMachines() {
        CoPilotTestFixtures.FakeHost host = new CoPilotTestFixtures.FakeHost(
            CoPilotTestFixtures.snapshot(readyResult())
        );
        RightCoPilotDockWidget dock = new RightCoPilotDockWidget(host, subject -> {});
        RightCoPilotDockWidget.MachineStrip strip = dock.new MachineStrip();
        assertEquals(List.of(furnace), strip.machines());
        assertEquals(furnace, strip.machineAt(8, strip.chipRect(0).y() + 4));
        assertEquals(26, strip.chipRect(0).width());
    }

    @Test
    void sceneMouseDragFromResourceAndMachineStripsConsumesOnQuickDropZones() {
        CoPilotTestFixtures.FakeHost host = new CoPilotTestFixtures.FakeHost(
            CoPilotTestFixtures.snapshot(readyResult())
        );
        RightCoPilotDockWidget dock = new RightCoPilotDockWidget(host, subject -> {});
        dock.setTab(RightCoPilotDockWidget.Tab.quickDrop);

        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.useStyle(UIStyles.sizeOf(220, 360), UIStyles.flexColumn());
        root.addWidget(dock);
        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(new SceneHost() {
            @Override
            @SuppressWarnings("NullAway")
            public Font font() {
                return null;
            }

            @Override
            public int width() {
                return 220;
            }

            @Override
            public int height() {
                return 360;
            }
        }));
        try {
            scene.setRespectExclusions(false);
            scene.setLayoutArea(220, 360);
            scene.stabilize();
            scene.layout();
            root.applyLayout();

            Rect freeRect = dock.freeDropZone().absoluteBounds();
            Rect banRect = dock.banDropZone().absoluteBounds();
            Rect resRect = dock.planResourceStrip().absoluteBounds();
            Rect machRect = dock.machineStrip().absoluteBounds();
            assertTrue(freeRect.height() > 0 && banRect.height() > 0);
            assertTrue(resRect.height() > 0 && machRect.height() > 0);

            // Drag resource chip 0 -> Free Drop Zone and release on QuickDropZone
            double resChipX = resRect.x() + 12;
            double resChipY = resRect.y() + 24;
            double freeX = freeRect.x() + freeRect.width() * 0.5;
            double freeY = freeRect.y() + freeRect.height() * 0.5;
            assertTrue(scene.mouseClicked(resChipX, resChipY, 0));
            assertTrue(scene.mouseDragged(freeX, freeY, 0, freeX - resChipX, freeY - resChipY));
            assertTrue(dock.isStripDragging());
            assertTrue(
                scene.mouseReleased(freeX, freeY, 0),
                "releasing resource drag over Free QuickDropZone must consume"
            );
            assertFalse(dock.isStripDragging(), "drag state must clear after consumption");
            assertEquals(1, host.feedbacks.size());
            ResourceRule freeRule = (ResourceRule) ((ApplyRulesFeedback) host.feedbacks.get(0)).rules().get(0);
            assertEquals(RuleStrength.hardPin, freeRule.strength());
            assertTrue(freeRule.treatAsFreeBaseSource());

            // Drag resource chip 0 -> Ban Drop Zone
            double banX = banRect.x() + banRect.width() * 0.5;
            double banY = banRect.y() + banRect.height() * 0.5;
            assertTrue(scene.mouseClicked(resChipX, resChipY, 0));
            assertTrue(scene.mouseDragged(banX, banY, 0, banX - resChipX, banY - resChipY));
            assertTrue(
                scene.mouseReleased(banX, banY, 0),
                "releasing resource drag over Ban QuickDropZone must consume"
            );
            assertFalse(dock.isStripDragging());
            assertEquals(2, host.feedbacks.size());
            ResourceRule banRule = (ResourceRule) ((ApplyRulesFeedback) host.feedbacks.get(1)).rules().get(0);
            assertEquals(RuleStrength.hardBan, banRule.strength());

            // Drag machine chip 0 -> Free Drop Zone (declare owned)
            double machChipX = machRect.x() + 12;
            double machChipY = machRect.y() + 22;
            assertTrue(scene.mouseClicked(machChipX, machChipY, 0));
            assertTrue(scene.mouseDragged(freeX, freeY, 0, freeX - machChipX, freeY - machChipY));
            assertTrue(dock.isStripDragging());
            assertTrue(
                scene.mouseReleased(freeX, freeY, 0),
                "releasing machine drag over Free QuickDropZone must consume"
            );
            assertFalse(dock.isStripDragging());
            assertEquals(3, host.feedbacks.size());
            assertTrue(host.feedbacks.get(2) instanceof DeclareOwnedMachineFeedback);

            // Drag machine chip 0 -> Ban Drop Zone (hard ban machine)
            assertTrue(scene.mouseClicked(machChipX, machChipY, 0));
            assertTrue(scene.mouseDragged(banX, banY, 0, banX - machChipX, banY - machChipY));
            assertTrue(
                scene.mouseReleased(banX, banY, 0),
                "releasing machine drag over Ban QuickDropZone must consume"
            );
            assertFalse(dock.isStripDragging());
            assertEquals(4, host.feedbacks.size());
            MachineRule banMachine = (MachineRule) ((ApplyRulesFeedback) host.feedbacks.get(3)).rules().get(0);
            assertEquals(RuleStrength.hardBan, banMachine.strength());
            assertEquals(furnace, banMachine.machineId());
        } finally {
            scene.destroy();
        }
    }
}
