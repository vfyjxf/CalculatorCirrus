package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPromptKind;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.coal;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.cobble;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.cost;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.demand;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.diamond;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.graph;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.inscriber;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.ironIngot;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.oakLog;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.option;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.paper;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.prompt;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.result;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.sevenTypePlan;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.snapshot;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandPaletteEngineTest {

    private static final CompiledRecipeGraph graph = graph();

    private static WorkbenchUiSnapshot plannedSnapshot() {
        return snapshot(
            List.of(demand(ironIngot, 1L)),
            result(PlanningStatus.feasible, List.of(sevenTypePlan()), prompts())
        );
    }

    private static List<DecisionPrompt> prompts() {
        return List.of(
            prompt(
                "p1",
                DecisionPromptKind.machineAvailability,
                0.9,
                option("a", cost(0.0, 0.0), List.of()),
                option("b", cost(0.0, 0.0), List.of())
            ),
            prompt(
                "p2",
                DecisionPromptKind.materialFamilyAnchor,
                0.8,
                option("a", cost(0.0, 0.0), List.of()),
                option("b", cost(0.0, 0.0), List.of())
            ),
            prompt(
                "p3",
                DecisionPromptKind.baseOrSupplyBoundary,
                0.7,
                option("a", cost(0.0, 0.0), List.of()),
                option("b", cost(0.0, 0.0), List.of())
            ),
            prompt(
                "p4",
                DecisionPromptKind.suspiciousJeiVerify,
                0.6,
                option("a", cost(0.0, 0.0), List.of()),
                option("b", cost(0.0, 0.0), List.of())
            )
        );
    }

    private static List<PaletteItem> query(
        String raw,
        WorkbenchUiSnapshot snapshot,
        @Nullable CompiledRecipeGraph graph,
        int limit
    ) {
        return CommandPaletteEngine.query(raw, snapshot, graph, limit);
    }

    private static List<String> ids(List<PaletteItem> items) {
        return items.stream().map(PaletteItem::id).toList();
    }

    private static List<PaletteItem> withAction(List<PaletteItem> items, Class<?> actionType) {
        return items.stream().filter(item -> actionType.isInstance(item.primaryAction())).toList();
    }

    private static <T> T as(Object value, Class<T> type) {
        assertTrue(type.isInstance(value), "expected " + type.getSimpleName() + " but was " + value);
        return type.cast(value);
    }

    private static String commandId(PaletteItem item) {
        return as(item.primaryAction(), PaletteAction.RunCommand.class).commandId();
    }

    private static String resourceId(String resourceId) {
        return "res:minecraft:item|" + resourceId;
    }

    private static String byproductId(String resourceId) {
        return "byproduct:minecraft:item|" + resourceId;
    }

    private static String ownedMachineId(String resourceId) {
        return "fb:own:minecraft:item|" + resourceId;
    }

    private static String freeResourceId(String resourceId) {
        return "fb:free:minecraft:item|" + resourceId;
    }

    @Test
    void noPrefixRanksPlanStepsBeforeResourcesAndCommands() {
        List<PaletteItem> items = query("iron", plannedSnapshot(), graph, 50);

        Set<String> expectedSteps = Set
                .of("step:s001", "step:s004", "step:s005", "step:s006", "step:s007", "step:s008");
        assertEquals(expectedSteps, Set.copyOf(ids(withAction(items, PaletteAction.FocusStep.class))));

        Set<String> expectedResources = Set.of(
            resourceId("minecraft:iron_ore"),
            resourceId("minecraft:iron_block"),
            resourceId("minecraft:iron_ingot"),
            resourceId("create:iron_sheet")
        );
        assertEquals(expectedResources, Set.copyOf(ids(withAction(items, PaletteAction.SetTarget.class))));
        assertEquals(10, items.size(), "six plan steps plus four producible iron resources");

        int lastStepIndex = -1;
        int firstResourceIndex = Integer.MAX_VALUE;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).primaryAction() instanceof PaletteAction.FocusStep) {
                lastStepIndex = i;
            }
            if (items.get(i).primaryAction() instanceof PaletteAction.SetTarget) {
                firstResourceIndex = Math.min(firstResourceIndex, i);
            }
        }
        assertTrue(lastStepIndex >= 0);
        assertTrue(lastStepIndex < firstResourceIndex, "current plan steps must be ranked first");

        for (PaletteItem item : items) {
            assertNotNull(item.displayText());
            assertNotNull(item.primaryAction());
            assertEquals(
                item.primaryAction() instanceof PaletteAction.FocusStep
                        ? CommandPaletteEngine.categoryStep
                        : CommandPaletteEngine.categoryResource,
                item.categoryKey()
            );
        }
    }

    @Test
    void noPrefixResourceItemsOfferShiftBasketAction() {
        List<PaletteItem> resources = withAction(
            query("iron_ingot", plannedSnapshot(), graph, 50),
            PaletteAction.SetTarget.class
        );

        assertEquals(List.of(resourceId("minecraft:iron_ingot")), ids(resources));
        PaletteItem item = resources.get(0);
        assertEquals(CommandPaletteEngine.categoryResource, item.categoryKey());
        assertEquals(ironIngot, item.iconResource());
        assertEquals(new PaletteAction.SetTarget(ironIngot), item.primaryAction());
        assertEquals(new PaletteAction.AddToBasket(ironIngot), item.shiftAction());
        assertEquals("minecraft:iron_ingot", item.displayText());
    }

    @Test
    void hashPrefixSearchesOnlyPlanNodes() {
        List<PaletteItem> items = query("#furnace", plannedSnapshot(), graph, 50);

        assertEquals(List.of("step:s003", "step:s004"), ids(items));
        assertEquals(CommandPaletteEngine.categoryStep, items.get(0).categoryKey());

        List<PaletteItem> cogwheel = query("#cogwheel", plannedSnapshot(), graph, 50);
        assertEquals(List.of("step:s009", byproductId("create:cogwheel")), ids(cogwheel));
        PaletteItem byproduct = cogwheel.get(1);
        assertEquals(new PaletteAction.FocusStep("s009"), byproduct.primaryAction());
        assertEquals(CommandPaletteEngine.categoryStep, byproduct.categoryKey());
    }

    @Test
    void hashPrefixMatchesRecipeAndMachineText() {
        assertEquals(List.of("step:s004"), ids(query("#smelting", plannedSnapshot(), graph, 50)));
        assertEquals(List.of("step:s007"), ids(query("#mechanical_press", plannedSnapshot(), graph, 50)));
        assertEquals(List.of("step:s005", "step:s006"), ids(query("#iron_block", plannedSnapshot(), graph, 50)));
        assertTrue(query("#nothing-matches-this", plannedSnapshot(), graph, 50).isEmpty());
    }

    @Test
    void atPrefixPinsModRulesThenFiltersResourcesByNamespace() {
        List<PaletteItem> items = query("@ae2", plannedSnapshot(), graph, 50);

        assertEquals(
            List.of(
                "cmd:mod.prefer:ae2",
                "cmd:mod.penalize:ae2",
                "cmd:mod.ban:ae2",
                resourceId("ae2:certus_quartz_crystal"),
                resourceId("ae2:inscriber")
            ),
            ids(items)
        );

        assertEquals("mod.prefer:ae2", commandId(items.get(0)));
        assertEquals("mod.penalize:ae2", commandId(items.get(1)));
        assertEquals("mod.ban:ae2", commandId(items.get(2)));
        assertEquals(CommandPaletteEngine.categoryCommand, items.get(0).categoryKey());

        for (PaletteItem resource : items.subList(3, items.size())) {
            as(resource.primaryAction(), PaletteAction.SetTarget.class);
            as(Objects.requireNonNull(resource.shiftAction()), PaletteAction.AddToBasket.class);
            assertEquals("ae2", Objects.requireNonNull(resource.iconResource()).namespace());
        }
    }

    @Test
    void atPrefixFallsBackToNamespacePrefixAndSurvivesNullGraph() {
        List<PaletteItem> items = query("@cr", plannedSnapshot(), graph, 50);
        assertEquals(
            List.of(
                "cmd:mod.prefer:cr",
                "cmd:mod.penalize:cr",
                "cmd:mod.ban:cr",
                resourceId("create:cogwheel"),
                resourceId("create:iron_sheet"),
                resourceId("create:shaft")
            ),
            ids(items)
        );

        List<PaletteItem> noGraph = query("@ae2", plannedSnapshot(), null, 50);
        assertEquals(List.of("cmd:mod.prefer:ae2", "cmd:mod.penalize:ae2", "cmd:mod.ban:ae2"), ids(noGraph));
        assertTrue(query("@", plannedSnapshot(), graph, 50).isEmpty());
    }

    @Test
    void gtPrefixEnumeratesEveryBuiltinCommand() {
        List<PaletteItem> all = query(">", plannedSnapshot(), graph, 100);

        assertEquals(CommandPaletteEngine.builtinCommandIds().size(), all.size());
        assertEquals(
            CommandPaletteEngine.builtinCommandIds(),
            all.stream().map(CommandPaletteEngineTest::commandId).toList()
        );
        assertEquals("cmd:view.dag", all.get(0).id());
        assertEquals("cmd:workspace.settings", all.get(all.size() - 1).id());
        assertTrue(all.stream().allMatch(item -> CommandPaletteEngine.categoryCommand.equals(item.categoryKey())));
        assertEquals(
            List.of("view.dag", "view.ledger", "view.checklist"),
            all.subList(0, 3).stream().map(CommandPaletteEngineTest::commandId).toList()
        );
    }

    @Test
    void gtPrefixGroupsThreeViewsThreeLayoutsFivePresetsAndFourWorkspaces() {
        List<PaletteItem> views = query(">视图", plannedSnapshot(), graph, 100); // NOPMD - Chinese palette input under test
        assertEquals(3, views.size());

        List<PaletteItem> layouts = query(">布局", plannedSnapshot(), graph, 100); // NOPMD - Chinese palette input under test
        assertEquals(3, layouts.size());

        List<PaletteItem> presets = query(">预设", plannedSnapshot(), graph, 100); // NOPMD - Chinese palette input under test
        assertEquals(5, presets.size());

        List<PaletteItem> workspaces = query(">工作区", plannedSnapshot(), graph, 100); // NOPMD - Chinese palette input under test
        assertEquals(4, workspaces.size());
    }

    @Test
    void gtPrefixMatchesChineseAliasesAndIsCaseInsensitive() {
        assertEquals(List.of("cmd:theme.toggle"), ids(query(">暗色", plannedSnapshot(), graph, 10))); // NOPMD - Chinese palette input under test
        assertEquals(List.of("cmd:zen.toggle"), ids(query(">禅", plannedSnapshot(), graph, 10))); // NOPMD - Chinese palette input under test
        assertEquals(List.of("cmd:preset.no_new_machines"), ids(query(">机器", plannedSnapshot(), graph, 10))); // NOPMD - Chinese palette input under test
        assertEquals(List.of("cmd:action.deepen"), ids(query(">深化", plannedSnapshot(), graph, 10))); // NOPMD - Chinese palette input under test
        assertEquals(List.of("cmd:action.recompile"), ids(query(">重编译", plannedSnapshot(), graph, 10))); // NOPMD - Chinese palette input under test
        assertEquals(List.of("cmd:action.export_json"), ids(query(">导出", plannedSnapshot(), graph, 10))); // NOPMD - Chinese palette input under test
        assertEquals(List.of("cmd:history.undo"), ids(query(">undo", plannedSnapshot(), graph, 10)));
        assertEquals(List.of("cmd:view.ledger"), ids(query(">ledger", plannedSnapshot(), graph, 10)));
        assertEquals(List.of("cmd:view.dag"), ids(query(">spatial", plannedSnapshot(), graph, 10)));
        assertEquals(
            ids(query(">zen", plannedSnapshot(), graph, 10)),
            ids(query(">ZEN", plannedSnapshot(), graph, 10))
        );
        assertEquals(
            ids(query(">暗色", plannedSnapshot(), graph, 10)), // NOPMD - Chinese palette input under test
            ids(query("> 暗色 ", plannedSnapshot(), graph, 10)) // NOPMD - Chinese palette input under test
        );
    }

    @Test
    void bangPrefixParsesHumanFeedbackPreviews() {
        List<PaletteItem> own = query("!own ae2:inscriber", plannedSnapshot(), graph, 10);
        assertEquals(1, own.size());
        assertEquals(ownedMachineId("ae2:inscriber"), own.get(0).id());
        assertEquals("own 1× ae2:inscriber", own.get(0).displayText());
        PaletteAction.ApplyFeedback ownAction = as(own.get(0).primaryAction(), PaletteAction.ApplyFeedback.class);
        DeclareOwnedMachineFeedback declare = as(ownAction.feedback(), DeclareOwnedMachineFeedback.class);
        assertEquals(MachineEntry.fromKey(inscriber), declare.machine());
        assertEquals(1, declare.ownedCount());
        assertFalse(declare.persistToWorld());

        List<PaletteItem> free = query("!free cobble", plannedSnapshot(), graph, 10);
        assertEquals(1, free.size());
        assertEquals(freeResourceId("minecraft:cobble"), free.get(0).id());
        assertEquals(cobble, free.get(0).iconResource());
        PaletteAction.ApplyFeedback freeAction = as(free.get(0).primaryAction(), PaletteAction.ApplyFeedback.class);
        ApplyRulesFeedback rules = as(freeAction.feedback(), ApplyRulesFeedback.class);
        assertFalse(rules.persistToWorld());
        assertEquals(1, rules.rules().size());
        ResourceRule freeRule = as(rules.rules().get(0), ResourceRule.class);
        assertEquals(cobble, freeRule.resource());
        assertEquals(GuidanceScope.session, freeRule.scope());
        assertEquals(RuleStrength.hardPin, freeRule.strength());
        assertTrue(freeRule.treatAsFreeBaseSource());

        assertEquals(
            freeResourceId("minecraft:iron_ingot"),
            query("!free minecraft:iron_ingot", plannedSnapshot(), graph, 10).get(0).id()
        );
        assertEquals(ownedMachineId("ae2:inscriber"), query("!拥有 ae2:inscriber", plannedSnapshot(), graph, 10).get(0).id()); // NOPMD - Chinese palette input under test
        assertEquals(freeResourceId("minecraft:cobble"), query("! 免费 cobble", plannedSnapshot(), graph, 10).get(0).id()); // NOPMD - Chinese palette input under test
    }

    @Test
    void bangPrefixRejectsIncompleteOrUnknownInput() {
        assertTrue(query("!", plannedSnapshot(), graph, 10).isEmpty());
        assertTrue(query("!own", plannedSnapshot(), graph, 10).isEmpty());
        assertTrue(query("!free", plannedSnapshot(), graph, 10).isEmpty());
        assertTrue(query("!banish cobble", plannedSnapshot(), graph, 10).isEmpty());
        assertTrue(query("!free   ", plannedSnapshot(), graph, 10).isEmpty());
    }

    @Test
    void emptyInputReturnsThreeRecommendationGroups() {
        WorkbenchUiSnapshot snapshot = snapshot(
            List.of(
                demand(ironIngot, 6L),
                demand(coal, 5L),
                demand(diamond, 4L),
                demand(paper, 3L),
                demand(cobble, 2L),
                demand(oakLog, 1L)
            ),
            result(PlanningStatus.feasible, List.of(sevenTypePlan()), prompts())
        );

        List<PaletteItem> items = query("", snapshot, graph, 100);

        assertEquals(
            List.of(
                resourceId("minecraft:iron_ingot"),
                resourceId("minecraft:coal"),
                resourceId("minecraft:diamond"),
                resourceId("minecraft:paper"),
                "cmd:copilot.focus:p1",
                "cmd:copilot.focus:p2",
                "cmd:copilot.focus:p3",
                "cmd:view.dag",
                "cmd:action.deepen",
                "cmd:theme.toggle",
                "cmd:zen.toggle"
            ),
            ids(items)
        );

        assertEquals(CommandPaletteEngine.categoryRecent, items.get(0).categoryKey());
        assertEquals(CommandPaletteEngine.categoryPrompt, items.get(4).categoryKey());
        assertEquals(CommandPaletteEngine.categoryCommand, items.get(7).categoryKey());
        assertEquals("copilot.focus:p1", commandId(items.get(4)));
        assertEquals(new PaletteAction.AddToBasket(ironIngot), items.get(0).shiftAction());
        assertEquals(ids(items).subList(0, 5), ids(query("", snapshot, graph, 5)));
    }

    @Test
    void limitCaseInsensitivityAndOrderingAreDeterministic() {
        List<PaletteItem> first = query("iron", plannedSnapshot(), graph, 50);
        List<PaletteItem> second = query("iron", plannedSnapshot(), graph, 50);
        List<PaletteItem> upper = query("IRON", plannedSnapshot(), graph, 50);

        assertEquals(ids(first), ids(second), "same input must produce the same order");
        assertEquals(ids(first), ids(upper), "matching must be case-insensitive");
        assertFalse(first.isEmpty());
        for (int i = 1; i < first.size(); i++) {
            PaletteItem previous = first.get(i - 1);
            PaletteItem current = first.get(i);
            assertTrue(
                previous.score() > current.score()
                        || (previous.score() == current.score() && previous.id().compareTo(current.id()) < 0),
                "ordering must be score-descending then id-ascending at index " + i
            );
        }

        assertEquals(ids(first).subList(0, 3), ids(query("iron", plannedSnapshot(), graph, 3)));
        assertEquals(ids(first).subList(0, 1), ids(query("iron", plannedSnapshot(), graph, 1)));
        assertTrue(query("iron", plannedSnapshot(), graph, 0).isEmpty());
        assertTrue(query("iron", plannedSnapshot(), graph, -2).isEmpty());
        assertTrue(query("zzzz-no-such-thing", plannedSnapshot(), graph, 50).isEmpty());
    }

    @Test
    void nullGraphStillServesPlanStepsAndCommands() {
        WorkbenchUiSnapshot withPlan = plannedSnapshot();

        assertEquals(List.of("step:s004"), ids(query("s004", withPlan, null, 10)));
        List<PaletteItem> commands = query("view", withPlan, null, 10);
        assertFalse(commands.isEmpty());

        WorkbenchUiSnapshot empty = snapshot(List.of(), null);
        assertDoesNotThrow(() -> query("iron", empty, null, 10));
        assertTrue(query("iron", empty, null, 10).isEmpty());
        assertTrue(query("#furnace", empty, null, 10).isEmpty());
        assertEquals(3, query("@ae2", empty, null, 10).size());
        assertEquals(
            List.of("cmd:view.dag", "cmd:action.deepen", "cmd:theme.toggle", "cmd:zen.toggle"),
            ids(query("", empty, null, 100))
        );

        List<PaletteItem> free = query("!free cobble", empty, graph, 10);
        PaletteAction.ApplyFeedback action = as(free.get(0).primaryAction(), PaletteAction.ApplyFeedback.class);
        ApplyRulesFeedback feedback = as(action.feedback(), ApplyRulesFeedback.class);
        assertEquals(cobble, as(feedback.rules().get(0), ResourceRule.class).resource());
    }

}
