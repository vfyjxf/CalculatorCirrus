package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.planning.bridge.CirrusContentBridge;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CommandPaletteEngine {

    public static final String categoryStep = "cirrus.ui.workbench.palette.category.current_node";
    public static final String categoryResource = "cirrus.ui.workbench.palette.category.set_target";
    public static final String categoryCommand = "cirrus.ui.workbench.palette.category.command";
    public static final String categoryPrompt = "cirrus.ui.workbench.palette.category.prompt";
    public static final String categoryRecent = "cirrus.ui.workbench.palette.category.recent_target";

    public static final String modPreferPrefix = "mod.prefer:";
    public static final String modPenalizePrefix = "mod.penalize:";
    public static final String modBanPrefix = "mod.ban:";
    public static final String promptFocusPrefix = "copilot.focus:";

    public static final String stepItemPrefix = "step:";
    public static final String resourceItemPrefix = "res:";
    public static final String byproductItemPrefix = "byproduct:";
    public static final String commandItemPrefix = "cmd:";
    public static final String feedbackItemPrefix = "fb:";

    private static final int stepBase = 3000;
    private static final int byproductBase = 2500;
    private static final int resourceBase = 2000;
    private static final int commandBase = 1000;
    private static final int modRuleBase = 3000;
    private static final int recentBase = 3000;
    private static final int promptBase = 2000;
    private static final int frequentBase = 1000;

    private static final int scoreExact = 1000;
    private static final int scorePrefix = 800;
    private static final int scoreSubstring = 600;
    private static final int scoreSubstringDecayCap = 60;

    private static final int limitRecentTargets = 4;
    private static final int limitPrompts = 3;

    private static final List<String> frequentCommandIds = List
            .of("view.dag", "action.deepen", "theme.toggle", "zen.toggle");

    private static final Comparator<PaletteItem> resultOrder = Comparator.comparingInt(PaletteItem::score).reversed()
            .thenComparing(PaletteItem::id);

    /** Command table; the Chinese keywords are search aliases so Chinese input reaches the same command. */
    private static final List<CommandSpec> commands = List.of(
        command("view.dag", "Spatial DAG View", "dag", "spatial", "视图", "空间图"), // NOPMD - Chinese search alias
        command("view.ledger", "Preparation Ledger", "ledger", "preparation", "视图", "备料", "总账"), // NOPMD - Chinese search alias
        command("view.checklist", "Execution Checklist", "checklist", "execution", "视图", "执行", "检查"), // NOPMD - Chinese search alias
        command("layout.lr", "Horizontal Layout", "horizontal", "lr", "布局", "横向", "水平"), // NOPMD - Chinese search alias
        command("layout.tb", "Vertical Layout", "vertical", "tb", "布局", "纵向", "垂直"), // NOPMD - Chinese search alias
        command("layout.swimlane", "Swimlane Layout", "swimlane", "lane", "布局", "泳道"), // NOPMD - Chinese search alias
        command("preset.balanced", "Preset: Balanced", "preset", "balanced", "预设", "均衡", "平衡"), // NOPMD - Chinese search alias
        command(
            "preset.min_human_time",
            "Preset: Min Active Time",
            "preset",
            "human",
            "active",
            "time",
            "预设", // NOPMD - Chinese search alias
            "人工", // NOPMD - Chinese search alias
            "最少人工" // NOPMD - Chinese search alias
        ),
        command(
            "preset.no_new_machines",
            "Preset: No New Machines",
            "preset",
            "machines",
            "machine",
            "预设", // NOPMD - Chinese search alias
            "机器", // NOPMD - Chinese search alias
            "不建新机" // NOPMD - Chinese search alias
        ),
        command(
            "preset.invest_automation",
            "Preset: Invest Automation",
            "preset",
            "automation",
            "预设", // NOPMD - Chinese search alias
            "自动化", // NOPMD - Chinese search alias
            "投资" // NOPMD - Chinese search alias
        ),
        command(
            "preset.min_raw_materials",
            "Preset: Min Raw Materials",
            "preset",
            "materials",
            "raw",
            "预设", // NOPMD - Chinese search alias
            "材料", // NOPMD - Chinese search alias
            "原料" // NOPMD - Chinese search alias
        ),
        command("theme.toggle", "Toggle Theme", "theme", "dark", "light", "主题", "暗色", "亮色", "切换主题"), // NOPMD - Chinese search alias
        command("zen.toggle", "Toggle Zen Mode", "zen", "focus", "禅", "专注", "沉浸"), // NOPMD - Chinese search alias
        command("action.deepen", "Deepen Plan", "deepen", "refine", "深化", "放宽"), // NOPMD - Chinese search alias
        command("action.export_json", "Export Plan JSON", "export", "json", "导出", "导出json"), // NOPMD - Chinese search alias
        command(
            "action.recompile",
            "Recompile Recipe Graph",
            "recompile",
            "compile",
            "reload",
            "重编译", // NOPMD - Chinese search alias
            "编译", // NOPMD - Chinese search alias
            "重载" // NOPMD - Chinese search alias
        ),
        command("history.undo", "Undo Last Decision", "undo", "revert", "撤销", "回退"), // NOPMD - Chinese search alias
        command("history.redo", "Redo Decision", "redo", "重做"), // NOPMD - Chinese search alias
        command("workspace.planner", "Workspace: Planner", "workspace", "planner", "工作区", "规划"), // NOPMD - Chinese search alias
        command("workspace.facilities", "Workspace: Facilities", "workspace", "facilities", "工作区", "设施"), // NOPMD - Chinese search alias
        command("workspace.database", "Workspace: Database", "workspace", "database", "工作区", "数据库"), // NOPMD - Chinese search alias
        command("workspace.settings", "Workspace: Settings", "workspace", "settings", "工作区", "设置") // NOPMD - Chinese search alias
    );

    private static final Map<String, CommandSpec> commandsById = indexCommands();

    private CommandPaletteEngine() {}

    public static List<PaletteItem> query(
        String rawInput,
        WorkbenchUiSnapshot snapshot,
        @Nullable CompiledRecipeGraph graph,
        int limit
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (limit <= 0) {
            return List.of();
        }
        String raw = rawInput == null ? "" : rawInput.trim();
        List<PaletteItem> items = raw.isEmpty() ? recommend(snapshot) : dispatch(raw, snapshot, graph);
        return finalizeItems(items, limit);
    }

    public static List<ResourceKey> fullCatalog(@Nullable CompiledRecipeGraph graph) {
        LinkedHashSet<ResourceKey> catalog = new LinkedHashSet<>();
        if (graph != null) {
            catalog.addAll(graph.allProducibleResources());
        }
        catalog.addAll(CirrusContentBridge.enumerateRegisteredResources());
        return List.copyOf(catalog);
    }

    public static List<PaletteItem> queryResources(
        @Nullable String rawInput,
        @Nullable CompiledRecipeGraph graph,
        int limit
    ) {
        if (limit <= 0) {
            return List.of();
        }
        List<ResourceKey> catalog = fullCatalog(graph);
        if (catalog.isEmpty()) {
            return List.of();
        }
        Set<ResourceKey> producible = graph == null ? Set.of() : graph.allProducibleResources();
        String raw = rawInput == null ? "" : rawInput.trim();
        if (raw.isEmpty()) {
            List<ResourceKey> all = new ArrayList<>(catalog);
            all.sort((left, right) -> {
                boolean leftInGraph = producible.contains(left);
                boolean rightInGraph = producible.contains(right);
                if (leftInGraph != rightInGraph) {
                    return leftInGraph ? -1 : 1;
                }
                int leftProducers = graph == null ? 0 : graph.producersOf(left).size();
                int rightProducers = graph == null ? 0 : graph.producersOf(right).size();
                if ((leftProducers > 0) != (rightProducers > 0)) {
                    return leftProducers > 0 ? -1 : 1;
                }
                boolean leftVanilla = "minecraft".equals(namespaceOf(left));
                boolean rightVanilla = "minecraft".equals(namespaceOf(right));
                if (leftVanilla != rightVanilla) {
                    return leftVanilla ? -1 : 1;
                }
                return left.resourceId().compareTo(right.resourceId());
            });
            int cap = Math.min(limit, all.size());
            List<PaletteItem> items = new ArrayList<>(cap);
            for (int i = 0; i < cap; i++) {
                items.add(targetItem(all.get(i), resourceBase + (cap - i), categoryResource));
            }
            return List.copyOf(items);
        }
        String query = lower(raw);
        boolean modOnly = query.startsWith("@");
        String modNeedle = modOnly ? query.substring(1).trim() : "";
        List<PaletteItem> items = new ArrayList<>();
        for (ResourceKey resource : catalog) {
            int score;
            if (modOnly) {
                String ns = namespaceOf(resource);
                if (modNeedle.isEmpty()) {
                    score = scoreSubstring;
                } else if (ns.equals(modNeedle)) {
                    score = scoreExact;
                } else if (ns.startsWith(modNeedle)) {
                    score = scorePrefix;
                } else if (ns.contains(modNeedle)) {
                    score = scoreSubstring;
                } else {
                    score = -1;
                }
            } else {
                score = matchResource(query, resource);
            }
            if (score < 0) {
                continue;
            }
            int graphBonus = producible.contains(resource) ? 300 : 0;
            int producerBonus = (graph != null && !graph.producersOf(resource).isEmpty()) ? 120 : 0;
            items.add(targetItem(resource, resourceBase + score + graphBonus + producerBonus, categoryResource));
        }
        return finalizeItems(items, limit);
    }

    public static List<String> builtinCommandIds() {
        List<String> ids = new ArrayList<>(commands.size());
        for (CommandSpec spec : commands) {
            ids.add(spec.id());
        }
        return List.copyOf(ids);
    }

    private static List<PaletteItem> dispatch(
        String raw,
        WorkbenchUiSnapshot snapshot,
        @Nullable CompiledRecipeGraph graph
    ) {
        char marker = raw.charAt(0);
        String body = raw.substring(1).trim();
        return switch (marker) {
            case '#' -> planStepItems(snapshot, body);
            case '@' -> modItems(body, graph);
            case '>' -> commandItems(body);
            case '!' -> feedbackItems(body, graph);
            default -> {
                String query = lower(raw);
                List<PaletteItem> items = new ArrayList<>();
                items.addAll(planStepItems(snapshot, query));
                if (graph != null) {
                    items.addAll(resourceItems(graph, query));
                }
                items.addAll(frequentCommandItems(query));
                yield items;
            }
        };
    }

    private static List<PaletteItem> recommend(WorkbenchUiSnapshot snapshot) {
        List<PaletteItem> items = new ArrayList<>();
        items.addAll(recentTargetItems(snapshot));
        items.addAll(pendingPromptItems(snapshot));
        for (int i = 0; i < frequentCommandIds.size(); i++) {
            CommandSpec spec = commandsById.get(frequentCommandIds.get(i));
            if (spec != null) {
                items.add(commandItem(spec, frequentBase - i));
            }
        }
        return items;
    }

    private static List<PaletteItem> recentTargetItems(WorkbenchUiSnapshot snapshot) {
        List<TargetDemand> targets = new ArrayList<>(snapshot.targets());
        targets.sort(
            Comparator.comparingLong(TargetDemand::amount).reversed()
                    .thenComparing(demand -> demand.resource().resourceId())
        );
        int count = Math.min(limitRecentTargets, targets.size());
        List<PaletteItem> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(targetItem(targets.get(i).resource(), recentBase - i, categoryRecent));
        }
        return items;
    }

    private static List<PaletteItem> pendingPromptItems(WorkbenchUiSnapshot snapshot) {
        PlanningResult result = snapshot.result();
        if (result == null || result.decisionPrompts().isEmpty()) {
            return List.of();
        }
        List<DecisionPrompt> prompts = new ArrayList<>(result.decisionPrompts());
        prompts.sort(
            Comparator.comparingDouble(DecisionPrompt::leverageScore).reversed().thenComparing(DecisionPrompt::promptId)
        );
        int count = Math.min(limitPrompts, prompts.size());
        List<PaletteItem> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            DecisionPrompt prompt = prompts.get(i);
            String commandId = promptFocusPrefix + prompt.promptId();
            items.add(
                new PaletteItem(
                    commandItemPrefix + commandId,
                    categoryPrompt,
                    prompt.titleKey(),
                    null,
                    null,
                    promptBase - i,
                    new PaletteAction.RunCommand(commandId),
                    null
                )
            );
        }
        return items;
    }

    private static List<PaletteItem> planStepItems(WorkbenchUiSnapshot snapshot, String rawQuery) {
        ProductionPlan plan = snapshot.selectedPlan().orElse(null);
        if (plan == null) {
            return List.of();
        }
        String query = lower(rawQuery);
        List<PaletteItem> items = new ArrayList<>();
        for (PlanStep step : plan.steps()) {
            int score = match(query, stepSearchText(step));
            if (score < 0) {
                continue;
            }
            items.add(
                new PaletteItem(
                    stepItemPrefix + step.stepId(),
                    categoryStep,
                    step.stepId(),
                    null,
                    stepIconResource(step),
                    stepBase + score,
                    new PaletteAction.FocusStep(step.stepId()),
                    null
                )
            );
        }
        for (var byproduct : plan.surplusByproducts()) {
            int score = match(query, byproduct.resource().resourceId());
            if (score < 0) {
                continue;
            }
            items.add(
                new PaletteItem(
                    byproductItemPrefix + byproduct.resource().shortString(),
                    categoryStep,
                    byproduct.resource().shortString(),
                    null,
                    byproduct.resource(),
                    byproductBase + score,
                    new PaletteAction.FocusStep(byproduct.producedByStepId()),
                    null
                )
            );
        }
        return items;
    }

    private static List<PaletteItem> resourceItems(CompiledRecipeGraph graph, String query) {
        List<PaletteItem> items = new ArrayList<>();
        for (ResourceKey resource : graph.allProducibleResources()) {
            int score = matchResource(query, resource);
            if (score < 0) {
                continue;
            }
            items.add(targetItem(resource, resourceBase + score, categoryResource));
        }
        return items;
    }

    private static int matchResource(String query, ResourceKey resource) {
        String id = resource.resourceId();
        int best = match(query, id);
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        best = Math.max(best, match(query, path));
        String spaced = path.replace('_', ' ').replace('/', ' ');
        if (!spaced.equals(path)) {
            best = Math.max(best, match(query, spaced));
        }
        String displayName = localizedResourceName(resource);
        if (!displayName.isEmpty()) {
            best = Math.max(best, match(query, displayName));
        }
        if (best >= 0) {
            return best;
        }
        if (query.indexOf(' ') >= 0) {
            String[] tokens = query.split("\\s+");
            String combined = lower(id) + " " + lower(spaced) + (displayName.isEmpty() ? "" : " " + lower(displayName));
            int tokenScore = scoreSubstring;
            for (String token : tokens) {
                if (token.isEmpty()) {
                    continue;
                }
                if (!combined.contains(token)) {
                    return -1;
                }
                tokenScore -= 8;
            }
            return Math.max(100, tokenScore);
        }
        return -1;
    }

    private static String localizedResourceName(ResourceKey resource) {
        try {
            ContentStack<?, ?> stack = CoPilotPaint.stackOf(resource, 1L);
            if (stack == null || stack.isEmpty()) {
                return "";
            }
            Component name = CoPilotPaint.stackName(stack, Component.empty());
            return name == null ? "" : name.getString();
        } catch (RuntimeException | LinkageError unavailable) {
            return "";
        }
    }

    private static List<PaletteItem> modItems(String rawQuery, @Nullable CompiledRecipeGraph graph) {
        if (rawQuery.isEmpty()) {
            return List.of();
        }
        String namespace = lower(rawQuery);
        List<PaletteItem> items = new ArrayList<>();
        items.add(modRuleItem(namespace, "Prefer @" + namespace, modPreferPrefix, 30));
        items.add(modRuleItem(namespace, "Penalize @" + namespace, modPenalizePrefix, 20));
        items.add(modRuleItem(namespace, "Ban @" + namespace, modBanPrefix, 10));
        if (graph == null) {
            return items;
        }
        List<ResourceKey> exact = new ArrayList<>();
        List<ResourceKey> prefixed = new ArrayList<>();
        for (ResourceKey resource : graph.allProducibleResources()) {
            String candidate = namespaceOf(resource);
            if (candidate.equals(namespace)) {
                exact.add(resource);
            } else if (candidate.startsWith(namespace)) {
                prefixed.add(resource);
            }
        }
        for (ResourceKey resource : exact.isEmpty() ? prefixed : exact) {
            items.add(targetItem(resource, resourceBase, categoryResource));
        }
        return items;
    }

    private static PaletteItem modRuleItem(String namespace, String displayText, String commandPrefix, int rankBonus) {
        String commandId = commandPrefix + namespace;
        return new PaletteItem(
            commandItemPrefix + commandId,
            categoryCommand,
            displayText,
            null,
            null,
            modRuleBase + rankBonus,
            new PaletteAction.RunCommand(commandId),
            null
        );
    }

    private static List<PaletteItem> commandItems(String rawQuery) {
        String query = lower(rawQuery);
        List<PaletteItem> items = new ArrayList<>(commands.size());
        for (int i = 0; i < commands.size(); i++) {
            CommandSpec spec = commands.get(i);
            if (query.isEmpty()) {
                items.add(commandItem(spec, commandBase + commands.size() - i));
                continue;
            }
            int score = commandScore(query, spec);
            if (score >= 0) {
                items.add(commandItem(spec, commandBase + score));
            }
        }
        return items;
    }

    private static List<PaletteItem> frequentCommandItems(String query) {
        List<PaletteItem> items = new ArrayList<>(frequentCommandIds.size());
        for (String id : frequentCommandIds) {
            CommandSpec spec = commandsById.get(id);
            if (spec == null) {
                continue;
            }
            int score = commandScore(query, spec);
            if (score >= 0) {
                items.add(commandItem(spec, commandBase + score));
            }
        }
        return items;
    }

    private static List<PaletteItem> feedbackItems(String rawBody, @Nullable CompiledRecipeGraph graph) {
        if (rawBody.isEmpty()) {
            return List.of();
        }
        String[] tokens = rawBody.split("\\s+", 2);
        String verb = lower(tokens[0]);
        String argument = tokens.length > 1 ? tokens[1].trim() : "";
        if (verb.isEmpty() || argument.isEmpty()) {
            return List.of();
        }
        return switch (verb) {
            case "own", "拥有", "声明拥有" -> List.of(ownMachineItem(argument)); // NOPMD - Chinese verb alias
            case "free", "免费", "设为免费" -> List.of(freeResourceItem(argument, graph)); // NOPMD - Chinese verb alias
            default -> List.of();
        };
    }

    private static PaletteItem ownMachineItem(String argument) {
        String machineId = argument.trim();
        DeclareOwnedMachineFeedback feedback = new DeclareOwnedMachineFeedback(machineId, 1, false);
        return new PaletteItem(
            feedbackItemPrefix + "own:" + machineId,
            categoryCommand,
            "own 1× " + machineId,
            null,
            null,
            commandBase + scoreExact,
            new PaletteAction.ApplyFeedback(feedback),
            null
        );
    }

    private static PaletteItem freeResourceItem(String argument, @Nullable CompiledRecipeGraph graph) {
        ResourceKey resource = resolveResource(argument, graph);
        GuidanceRule rule = new ResourceRule(
            "session.free:" + resource.shortString(),
            GuidanceScope.session,
            RuleStrength.hardPin,
            resource,
            true
        );
        return new PaletteItem(
            feedbackItemPrefix + "free:" + resource.shortString(),
            categoryCommand,
            "free " + resource.shortString(),
            null,
            resource,
            commandBase + scoreExact,
            new PaletteAction.ApplyFeedback(new ApplyRulesFeedback(List.of(rule), false)),
            null
        );
    }

    private static PaletteItem targetItem(ResourceKey resource, int score, String categoryKey) {
        return new PaletteItem(
            resourceItemPrefix + resource.shortString(),
            categoryKey,
            resource.shortString(),
            null,
            resource,
            score,
            new PaletteAction.SetTarget(resource),
            new PaletteAction.AddToBasket(resource)
        );
    }

    private static PaletteItem commandItem(CommandSpec spec, int score) {
        return new PaletteItem(
            commandItemPrefix + spec.id(),
            categoryCommand,
            spec.label(),
            null,
            null,
            score,
            new PaletteAction.RunCommand(spec.id()),
            null
        );
    }

    private static int commandScore(String query, CommandSpec spec) {
        if (query.isEmpty()) {
            return 0;
        }
        int best = match(query, spec.id());
        best = Math.max(best, match(query, spec.label()));
        for (String keyword : spec.keywords()) {
            best = Math.max(best, match(query, keyword));
        }
        return best;
    }

    private static int match(String query, String haystack) {
        if (query.isEmpty()) {
            return 0;
        }
        String lowered = lower(haystack);
        if (lowered.equals(query)) {
            return scoreExact;
        }
        if (lowered.startsWith(query)) {
            return scorePrefix;
        }
        int index = lowered.indexOf(query);
        if (index < 0) {
            return -1;
        }
        return scoreSubstring - Math.min(index, scoreSubstringDecayCap);
    }

    private static String stepSearchText(PlanStep step) {
        StringBuilder text = new StringBuilder(step.stepId());
        switch (step) {
            case AcquireStep acquire -> text.append(' ').append(acquire.resource().resourceId());
            case ProvisionMachineStep provision -> text.append(' ').append(provision.machineId());
            case SetupLineStep setup -> text.append(' ').append(setup.systemId()).append(' ')
                    .append(setup.primaryMachineId()).append(' ').append(setup.targetOutput().resourceId());
            case CraftStep craft -> {
                text.append(' ').append(craft.recipeKey().shortString());
                craft.recipeKey().explicitRecipeId().ifPresent(id -> text.append(' ').append(id));
                craft.chosenMachineId().ifPresent(machine -> text.append(' ').append(machine));
            }
            case ConvertStep convert -> text.append(' ').append(convert.conversionRecipeKey().shortString());
            case LinePullStep pull ->
                text.append(' ').append(pull.resource().resourceId()).append(' ').append(pull.lineId());
            case CycleLoopStep loop -> {
                text.append(' ').append(loop.macroKey().macroId());
                for (CraftStep inner : loop.singleIterationTemplate()) {
                    text.append(' ').append(inner.recipeKey().shortString());
                    inner.chosenMachineId().ifPresent(machine -> text.append(' ').append(machine));
                }
            }
        }
        return text.toString();
    }

    private static @Nullable ResourceKey stepIconResource(PlanStep step) {
        return switch (step) {
            case AcquireStep acquire -> acquire.resource();
            case LinePullStep pull -> pull.resource();
            case SetupLineStep setup -> setup.targetOutput();
            case ProvisionMachineStep provision -> null;
            case CraftStep craft -> null;
            case ConvertStep convert -> null;
            case CycleLoopStep loop -> null;
        };
    }

    private static ResourceKey resolveResource(String token, @Nullable CompiledRecipeGraph graph) {
        String raw = token.trim();
        if (raw.indexOf(':') >= 0) {
            return ResourceKey.item(raw);
        }
        if (graph != null) {
            String suffix = ":" + lower(raw);
            ResourceKey best = null;
            for (ResourceKey resource : graph.allProducibleResources()) {
                if (lower(resource.resourceId()).endsWith(suffix) && (best == null || resource.compareTo(best) < 0)) {
                    best = resource;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return ResourceKey.item("minecraft:" + raw);
    }

    private static String namespaceOf(ResourceKey resource) {
        String id = resource.resourceId();
        int separator = id.indexOf(':');
        return separator <= 0 ? "" : lower(id.substring(0, separator));
    }

    private static List<PaletteItem> finalizeItems(List<PaletteItem> items, int limit) {
        Map<String, PaletteItem> unique = new LinkedHashMap<>();
        for (PaletteItem item : items) {
            unique.putIfAbsent(item.id(), item);
        }
        List<PaletteItem> ordered = new ArrayList<>(unique.values());
        ordered.sort(resultOrder);
        if (ordered.size() > limit) {
            ordered = ordered.subList(0, limit);
        }
        return List.copyOf(ordered);
    }

    private static String lower(@Nullable String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static Map<String, CommandSpec> indexCommands() {
        Map<String, CommandSpec> index = new LinkedHashMap<>();
        for (CommandSpec spec : commands) {
            index.put(spec.id(), spec);
        }
        return Map.copyOf(index);
    }

    private static CommandSpec command(String id, String label, String... keywords) {
        return new CommandSpec(id, label, List.of(keywords));
    }

    private record CommandSpec(String id, String label, List<String> keywords) {}
}
