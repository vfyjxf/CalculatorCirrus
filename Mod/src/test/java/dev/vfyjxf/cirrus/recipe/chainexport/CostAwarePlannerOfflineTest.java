package dev.vfyjxf.cirrus.recipe.chainexport;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeCostHeuristic;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleDefinition;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleProfile;
import dev.vfyjxf.cirrus.api.recipe.routing.service.DiagnosticLevel;
import dev.vfyjxf.cirrus.api.recipe.routing.service.LoopPolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingBudget;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline correctness tests for {@link dev.vfyjxf.cirrus.recipe.routing.planner.CostAwarePlanner}
 * on small synthetic graphs built with the registry-free offline content model
 * (no Minecraft server needed): quantity ratios, cycle handling, sole junk
 * producers, cost-aware source selection, byproduct netting, tag inputs,
 * budget exhaustion and determinism.
 */
class CostAwarePlannerOfflineTest {

    private OfflineContent.Session session;
    private OfflineContent.IdStackType item;

    @BeforeAll
    static void bootstrap() throws Exception {
        ChainExportMain.ensureVanillaBootstrap();
    }

    @BeforeEach
    void setUp() {
        session = OfflineContent.newSession();
        item = session.stackType("item");
        // stacks built below must resolve their variant type through the
        // ContentApi, so the family has to be registered before construction
        session.attachRegistry();
    }

    @AfterEach
    void tearDown() {
        session.close();
    }

    // region graph builder

    /** id -> (amount) input/output spec; chance defaults to guaranteed. */
    private record Def(String id, String[] inputs, String[] outputs) {
    }

    private record Built(RoutingService service, Map<String, RecipeEntry<ExportPackLoader.ExportRecipe>> entries) {
    }

    /** Specs are {@code "id x amount"} strings, e.g. {@code "mod:a x2"}. */
    private static Def def(String id, String[] inputs, String[] outputs) {
        return new Def(id, inputs, outputs);
    }

    private Built build(Def... defs) {
        List<ExportPackLoader.ExportRecipe> recipes = new ArrayList<>();
        Map<String, RecipeEntry<ExportPackLoader.ExportRecipe>> entries = new LinkedHashMap<>();
        List<RecipeEntry<?>> all = new ArrayList<>();
        int index = 0;
        for (Def d : defs) {
            List<InputContent> inputs = new ArrayList<>();
            for (String spec : d.inputs()) {
                inputs.add(stackIn(spec));
            }
            List<OutputContent> outputs = new ArrayList<>();
            for (String spec : d.outputs()) {
                outputs.add(stackOut(spec));
            }
            ExportPackLoader.ExportRecipe recipe = new ExportPackLoader.ExportRecipe(
                index++, "test:crafting", "custom", d.id(),
                ResourceLocation.tryParse(d.id()),
                List.of(), List.of(), List.of(),
                Lists.immutable.ofAll(inputs), Lists.immutable.ofAll(outputs));
            recipes.add(recipe);
        }
        ExportPackLoader.ExportRecipeSystem system = new ExportPackLoader.ExportRecipeSystem(
            Namespace.of("test", "crafting"), recipes);
        RecipeApiRegistryBuilderShim.attach(session, system);
        for (ExportPackLoader.ExportRecipe recipe : recipes) {
            RecipeEntry<ExportPackLoader.ExportRecipe> entry = RecipeEntry.of(system, recipe);
            entries.put(recipe.recipeId, entry);
            all.add(entry);
        }
        session.attachRegistry();
        RecipeGraph graph = RecipeGraph.buildGraph(Lists.immutable.ofAll(all));
        RoutingService service = new RoutingService(graph);
        for (RuleDefinition rule : dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleDefinitions.defaultCollection().rules()) {
            service.registerRuleDefinition(rule);
        }
        return new Built(service, entries);
    }

    private InputContent stackIn(String spec) {
        Parsed parsed = Parsed.of(spec);
        ContentStack<String, String> stack = ContentStack.fromStack(item, parsed.id, parsed.amount);
        return InputContent.fromStack(stack);
    }

    private OutputContent stackOut(String spec) {
        Parsed parsed = Parsed.of(spec);
        ContentStack<String, String> stack = ContentStack.fromStack(item, parsed.id, parsed.amount);
        return OutputContent.fromStack(stack);
    }

    private record Parsed(String id, long amount) {
        static Parsed of(String spec) {
            int at = spec.indexOf(" x");
            if (at < 0) {
                return new Parsed(spec.trim(), 1);
            }
            return new Parsed(spec.substring(0, at).trim(), Long.parseLong(spec.substring(at + 2).trim()));
        }
    }

    /** Small shim attaching the offline recipe API for one system. */
    private static final class RecipeApiRegistryBuilderShim {
        static void attach(OfflineContent.Session session, ExportPackLoader.ExportRecipeSystem system) {
            dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder builder = new dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder();
            builder.registerSystem(system);
            session.attachRegistry();
            dev.vfyjxf.cirrus.recipe.RecipeApiImpl.attach(new dev.vfyjxf.cirrus.recipe.RecipeApiImpl(builder));
        }
    }

    private RoutingOutcome route(Built built, String targetId, int times, RecipeCostHeuristic heuristic) {
        RoutingService service = built.service();
        Namespace profileId = ChainExportMain.ruleFor("preview").toProfile().profileId();
        if (heuristic != null) {
            RuleProfile base = ChainExportMain.ruleFor("preview").toProfile();
            RuleProfile with = base.withCostHeuristic(heuristic);
            // distinct id so the plain preview profile stays registered untouched
            with = new RuleProfile(
                Namespace.of("test", "nb"),
                with.model(), with.hardRules(), with.scoreRules(), with.rankPolicy(), with.tieBreakPolicy(),
                with.explorePolicy(), with.unresolvedInputRule(), with.capabilities(),
                with.shortlistK(), with.rollbackWidth(), with.nearTieEpsilon(),
                with.amplificationSoftThreshold(), with.amplificationHardThreshold(),
                with.liftMultiSemanticChildShortages(), with.fastPathEligible(),
                null, heuristic);
            service.registerRuleProfile(with);
            profileId = with.profileId();
        }
        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.costAware)
            .targetRecipe(built.entries().get(targetId), times)
            .ruleProfileId(profileId)
            .sourcePolicyId(ChainExportMain.ruleFor("preview").sourcePolicy().policyId())
            .stock(dev.vfyjxf.cirrus.api.recipe.routing.Stock.empty())
            .loopPolicy(LoopPolicy.allowBoundedAmplification)
            .budget(RoutingBudget.assuredDefault())
            .diagnosticLevel(DiagnosticLevel.basic)
            .build();
        return service.route(request);
    }

    private static List<PlanAction.ExecuteRecipe> executes(RoutingOutcome outcome) {
        List<PlanAction.ExecuteRecipe> out = new ArrayList<>();
        for (PlanAction action : outcome.plan().actions().castToList()) {
            if (action instanceof PlanAction.ExecuteRecipe execute) {
                out.add(execute);
            }
        }
        return out;
    }

    private static List<PlanAction.PendingInput> pendings(RoutingOutcome outcome) {
        List<PlanAction.PendingInput> out = new ArrayList<>();
        for (PlanAction action : outcome.plan().actions().castToList()) {
            if (action instanceof PlanAction.PendingInput pending) {
                out.add(pending);
            }
        }
        return out;
    }

    private static Map<String, String> statsOf(RoutingOutcome outcome) {
        assertNotNull(outcome.trace(), "trace expected under basic diagnostics");
        return outcome.trace().stages().getLast().values().toMap();
    }

    // endregion

    @Test
    void quantityRatiosAreExact() {
        // target needs 4x B; recipe produces 3x B from 2x A -> exactly 2 crafts,
        // the 2 leftover B must not spawn new demands
        Built built = build(
            def("test:need_b", new String[]{"mod:b x4"}, new String[]{"mod:result x1"}),
            def("test:b_from_a", new String[]{"mod:a x2"}, new String[]{"mod:b x3"}));
        RoutingOutcome outcome = route(built, "test:need_b", 1, null);
        assertTrue(outcome.success(), "status=" + outcome.status());
        List<PlanAction.ExecuteRecipe> executes = executes(outcome);
        PlanAction.ExecuteRecipe bCraft = executes.stream()
            .filter(e -> e.recipe().recipe() instanceof ExportPackLoader.ExportRecipe r && r.recipeId.equals("test:b_from_a"))
            .findFirst().orElseThrow();
        assertEquals(2, bCraft.times(), "ceil(4/3) crafts expected");
        assertEquals(1, pendings(outcome).size(), "mod:a has no producer -> exactly one pending");
    }

    @Test
    void noGainCycleIsRejectedAndPends() {
        // X -> Y and Y -> X with no other producers: every branch cycle-rejects;
        // the pending escape hatch must complete the plan instead of looping
        Built built = build(
            def("test:need_x", new String[]{"mod:x x1"}, new String[]{"mod:result x1"}),
            def("test:x_from_y", new String[]{"mod:y x1"}, new String[]{"mod:x x1"}),
            def("test:y_from_x", new String[]{"mod:x x1"}, new String[]{"mod:y x1"}));
        RoutingOutcome outcome = route(built, "test:need_x", 1, null);
        assertTrue(outcome.success() || outcome.partial(),
            "status=" + outcome.status() + " stats=" + statsOf(outcome));
        assertTrue(pendings(outcome).stream().anyMatch(p -> true), "pending expected for the cyclic demand");
        long cycleRejects = Long.parseLong(statsOf(outcome).getOrDefault("cycleRejects", "0"));
        assertTrue(cycleRejects > 0, "cycle rejects expected, stats=" + statsOf(outcome));
    }

    @Test
    void amplificationCycleIsBoundedButUsable() {
        // 1x B + 1x A -> 3x B: net +2 B per craft; demand B x4 resolves with
        // 2 crafts, the self-input B is served by the craft's own surplus
        Built built = build(
            def("test:need_b", new String[]{"mod:b x4"}, new String[]{"mod:result x1"}),
            def("test:amp", new String[]{"mod:b x1", "mod:a x1"}, new String[]{"mod:b x3"}));
        RoutingOutcome outcome = route(built, "test:need_b", 1, null);
        assertTrue(outcome.success(), "status=" + outcome.status() + " stats=" + statsOf(outcome));
        PlanAction.ExecuteRecipe amp = executes(outcome).stream()
            .filter(e -> e.recipe().recipe() instanceof ExportPackLoader.ExportRecipe r && r.recipeId.equals("test:amp"))
            .findFirst().orElseThrow();
        assertTrue(amp.times() >= 2, "at least ceil(4/3) crafts expected");
    }

    @Test
    void soleJunkProducerStaysReachable() {
        // a huge penalty on the only producer must not make it infeasible
        Built built = build(
            def("test:need_x", new String[]{"mod:x x1"}, new String[]{"mod:result x1"}),
            def("test:x_source", new String[]{"mod:a x1"}, new String[]{"mod:x x1"}));
        RoutingOutcome outcome = route(built, "test:need_x", 1, recipeIndex -> 1_000_000.0);
        assertTrue(outcome.success(), "penalty must never affect feasibility, status=" + outcome.status());
        assertTrue(executes(outcome).stream()
            .anyMatch(e -> e.recipe().recipe() instanceof ExportPackLoader.ExportRecipe r && r.recipeId.equals("test:x_source")));
    }

    @Test
    void cheaperSourceWinsWithoutHeuristic() {
        // two producers of X: one direct (1 craft), one via an extra intermediate
        Built built = build(
            def("test:need_x", new String[]{"mod:x x1"}, new String[]{"mod:result x1"}),
            def("test:x_direct", new String[]{"mod:a x1"}, new String[]{"mod:x x1"}),
            def("test:x_via_y", new String[]{"mod:y x1"}, new String[]{"mod:x x1"}),
            def("test:y_from_a", new String[]{"mod:a x2"}, new String[]{"mod:y x1"}));
        RoutingOutcome outcome = route(built, "test:need_x", 1, null);
        assertTrue(outcome.success());
        List<String> chosen = executes(outcome).stream()
            .map(e -> ((ExportPackLoader.ExportRecipe) e.recipe().recipe()).recipeId).toList();
        assertTrue(chosen.contains("test:x_direct"), "cheapest route expected, got " + chosen);
    }

    @Test
    void penaltySteersToCleanerRoute() {
        // both routes end in the same pending leaf (a x1), so the choice is
        // decided by production steps + penalty alone
        Built built = build(
            def("test:need_x", new String[]{"mod:x x1"}, new String[]{"mod:result x1"}),
            def("test:x_direct", new String[]{"mod:a x1"}, new String[]{"mod:x x1"}),
            def("test:x_via_y", new String[]{"mod:y x1"}, new String[]{"mod:x x1"}),
            def("test:y_from_a", new String[]{"mod:a x1"}, new String[]{"mod:y x1"}));
        // penalize the direct (junk-leaning) producer heavily; the planner should
        // prefer the longer clean route (3 crafts + equal pending vs 2 crafts + 50)
        RoutingOutcome outcome = route(built, "test:need_x", 1, recipeIndex -> {
            String id = ((ExportPackLoader.ExportRecipe) built.service()
                .catalog().nodeAt(recipeIndex).recipe().recipe()).recipeId;
            return id.equals("test:x_direct") ? 50.0 : 0.0;
        });
        assertTrue(outcome.success());
        List<String> chosen = executes(outcome).stream()
            .map(e -> ((ExportPackLoader.ExportRecipe) e.recipe().recipe()).recipeId).toList();
        assertTrue(chosen.contains("test:x_via_y"),
            "penalized direct route should lose, got " + chosen
                + " profile=" + outcome.metrics().effectiveRuleProfileId()
                + " stats=" + statsOf(outcome));
    }

    @Test
    void byproductSurplusIsReused() {
        // R1: a -> 2x b + 1x c ; R2: c + d -> e ; target needs 2x b and 1x e.
        // The c byproduct from R1 must satisfy R2's c input (no c producer needed)
        Built built = build(
            def("test:target", new String[]{"mod:b x2", "mod:e x1"}, new String[]{"mod:result x1"}),
            def("test:r1", new String[]{"mod:a x1"}, new String[]{"mod:b x2", "mod:c x1"}),
            def("test:r2", new String[]{"mod:c x1", "mod:d x1"}, new String[]{"mod:e x1"}));
        RoutingOutcome outcome = route(built, "test:target", 1, null);
        assertTrue(outcome.success(), "status=" + outcome.status() + " stats=" + statsOf(outcome));
        PlanAction.ExecuteRecipe r2 = executes(outcome).stream()
            .filter(e -> e.recipe().recipe() instanceof ExportPackLoader.ExportRecipe r && r.recipeId.equals("test:r2"))
            .findFirst().orElseThrow();
        assertEquals(1, r2.times());
        assertTrue(pendings(outcome).stream().noneMatch(p ->
            p.input() instanceof dev.vfyjxf.cirrus.api.recipe.content.StackContent<?, ?> stack
                && "mod:c".equals(String.valueOf(stack.content().value()))),
            "mod:c must be satisfied from surplus, not pended");
    }

    @Test
    void tinyBudgetStillYieldsDivePlanWithReason() {
        // the greedy dive runs outside the node budget, so even a tiny budget
        // returns a complete dive plan; the exhausted budget is still recorded
        Built built = build(
            def("test:need_x", new String[]{"mod:x x1"}, new String[]{"mod:result x1"}),
            def("test:x_source", new String[]{"mod:a x1"}, new String[]{"mod:x x1"}));
        RoutingService service = built.service();
        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.costAware)
            .targetRecipe(built.entries().get("test:need_x"), 1)
            .ruleProfileId(ChainExportMain.ruleFor("preview").profile().profileId())
            .sourcePolicyId(ChainExportMain.ruleFor("preview").sourcePolicy().policyId())
            .stock(dev.vfyjxf.cirrus.api.recipe.routing.Stock.empty())
            .loopPolicy(LoopPolicy.allowBoundedAmplification)
            .budget(RoutingBudget.assuredDefault().withMaxGeneratedCandidates(1))
            .build();
        RoutingOutcome outcome = service.route(request);
        assertTrue(outcome.success() || outcome.partial(), "dive keeps completeness, status=" + outcome.status());
        assertNotEquals("none", outcome.metrics().budgetStopReason(), "budget exhaustion must be recorded");
    }

    @Test
    void deterministicAcrossRuns() {
        Built built = build(
            def("test:need_x", new String[]{"mod:x x2"}, new String[]{"mod:result x1"}),
            def("test:x_a", new String[]{"mod:a x1"}, new String[]{"mod:x x1"}),
            def("test:x_b", new String[]{"mod:d x1"}, new String[]{"mod:x x1"}));
        String first = planFingerprint(route(built, "test:need_x", 1, null));
        String second = planFingerprint(route(built, "test:need_x", 1, null));
        assertEquals(first, second, "identical requests must produce identical plans");
    }

    private static String planFingerprint(RoutingOutcome outcome) {
        StringBuilder sb = new StringBuilder();
        for (PlanAction action : outcome.plan().actions().castToList()) {
            if (action instanceof PlanAction.ExecuteRecipe execute) {
                sb.append("E:").append(((ExportPackLoader.ExportRecipe) execute.recipe().recipe()).recipeId)
                    .append('x').append(execute.times()).append(';');
            } else if (action instanceof PlanAction.PendingInput pending) {
                sb.append("P:").append(pending.input()).append(';');
            }
        }
        return sb.toString();
    }
}
