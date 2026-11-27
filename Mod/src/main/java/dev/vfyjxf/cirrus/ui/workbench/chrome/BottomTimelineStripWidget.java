package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.CostOverrideRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.TagDefaultRule;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.service.PlanningDiagnostics;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellLayout;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.state.HistoryEntry;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Z4 bottom decision breadcrumb timeline: history jumps, single-step undo/promote and planning telemetry (UI-HITL-09/10).
 */
public class BottomTimelineStripWidget extends Widget implements OreUiTooltipProvider {
    public static final int nodeHeight = 15;
    public static final int nodeGap = 9;
    public static final int badgeWidth = 9;
    public static final int telemetryWidth = 104;
    public static final int promoteWidth = 86;
    private static final int nodeIconSize = 11;

    public record Node(
        int slot,
        int jumpTarget,
        @Nullable HistoryEntry entry,
        boolean redo,
        boolean placeholder,
        List<GuidanceRule> sessionRules
    ) {}

    public record Telemetry(int nodes, int recipes, long elapsedMillis, boolean truncated) {}

    public record NodeRect(Node node, int x, int y, int width) {}

    private static final int nodeStartX = ChromeText.pad;
    private static final int nodePad = 4;

    private final WorkbenchShellContext shellContext;

    public BottomTimelineStripWidget(WorkbenchShellContext context) {
        this.shellContext = Objects.requireNonNull(context, "context");
        WorkbenchShellLayout.Rect region = shellLayout().bottomStrip();
        addStyleClass("workbench-timeline");
        defaultStyle(UIStyle.of(background(CirrusTextures.Workbench.dockIsland(context.theme()))));
        useStyle(positionAbsolute(), sizeOf(region.width(), region.height()));
        setInteractive(true);
        onMouseClick((input, clickCount, context1) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            var local = input.mouseRelative(this);
            NodeRect rect = nodeRectAt(local.x(), local.y());
            if (rect != null) {
                if (hitsRemoveBadge(rect, local.x())) {
                    if (removeRuleAt(rect.node().slot())) {
                        context1.consume();
                        return EventDispatch.consumed;
                    }
                    return EventDispatch.pass;
                }
                if (hitsPromoteBadge(rect, local.x())) {
                    if (promoteRuleAt(rect.node().slot())) {
                        context1.consume();
                        return EventDispatch.consumed;
                    }
                    return EventDispatch.pass;
                }
                if (jumpToNode(rect.node().slot())) {
                    context1.consume();
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            }
            var promoteAll = promoteAllRect();
            if (ChromeText
                    .hit(local.x(), local.y(), promoteAll.x(), promoteAll.y(), promoteAll.width(), promoteAll.height())
                    && promoteAllSessionRules()) {
                context1.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
    }

    public List<Node> nodes() {
        WorkbenchUiSnapshot snapshot = shellContext.activeSnapshot();
        List<HistoryEntry> undo = snapshot.undoHistory();
        List<HistoryEntry> redo = snapshot.redoHistory();
        List<Node> nodes = new ArrayList<>(undo.size() + redo.size() + 1);
        int slot = 0;
        nodes.add(new Node(slot++, 0, null, false, true, List.of()));
        for (int index = 0; index < undo.size(); index++) {
            HistoryEntry entry = undo.get(index);
            nodes.add(new Node(slot++, index + 1, entry, false, false, sessionRulesOf(entry)));
        }
        int currentPointer = undo.size();
        for (int index = 0; index < redo.size(); index++) {
            HistoryEntry entry = redo.get(index);
            nodes.add(new Node(slot++, currentPointer + index + 1, entry, true, false, sessionRulesOf(entry)));
        }
        return List.copyOf(nodes);
    }

    public int nodeCount() {
        return nodes().size();
    }

    public @Nullable Node nodeAt(int slot) {
        List<Node> nodes = nodes();
        if (slot < 0 || slot >= nodes.size()) {
            return null;
        }
        return nodes.get(slot);
    }

    public boolean jumpToNode(int slot) {
        Node node = nodeAt(slot);
        PlannerWorkbenchController controller = shellContext.activeController();
        if (node == null || controller == null) {
            return false;
        }
        int target = node.jumpTarget();
        int current = shellContext.activeSnapshot().undoHistory().size();
        if (target == current) {
            return false;
        }
        controller.jumpToHistoryIndex(target);
        return true;
    }

    public boolean removeRuleAt(int slot) {
        Node node = nodeAt(slot);
        PlannerWorkbenchController controller = shellContext.activeController();
        if (node == null || controller == null || node.sessionRules().isEmpty()) {
            return false;
        }
        List<String> ruleIds = new ArrayList<>(node.sessionRules().size());
        for (GuidanceRule rule : node.sessionRules()) {
            ruleIds.add(rule.ruleId());
        }
        controller.relaxConflictRules(ruleIds);
        return true;
    }

    public boolean promoteRuleAt(int slot) {
        Node node = nodeAt(slot);
        PlannerWorkbenchController controller = shellContext.activeController();
        if (node == null || controller == null || node.sessionRules().isEmpty()) {
            return false;
        }
        controller.promoteSessionRulesToWorld(node.sessionRules().get(0).ruleId());
        return true;
    }

    public boolean promoteAllSessionRules() {
        PlannerWorkbenchController controller = shellContext.activeController();
        if (controller == null || shellContext.activeSnapshot().activeSessionRules().isEmpty()) {
            return false;
        }
        controller.promoteSessionRulesToWorld(null);
        return true;
    }

    public @Nullable PlanningDiagnostics diagnostics() {
        var result = shellContext.activeSnapshot().result();
        return result == null ? null : result.diagnostics();
    }

    public Telemetry telemetry() {
        PlanningDiagnostics diagnostics = diagnostics();
        if (diagnostics == null) {
            return new Telemetry(0, 0, 0L, false);
        }
        return new Telemetry(
            diagnostics.subgraphNodes(),
            diagnostics.subgraphRecipes(),
            diagnostics.elapsedMillis(),
            diagnostics.truncatedByNodeBudget()
        );
    }

    public Component telemetryLabel() {
        Telemetry telemetry = telemetry();
        return Component.literal(
            telemetry.nodes() + " \u00b7 " + WorkbenchFormatters.formatAmount(telemetry.elapsedMillis()) + "ms \u00b7 "
                    + (telemetry.truncated() ? 1 : 0)
        );
    }

    public List<NodeRect> nodeRects() {
        int promoteReserve = shellContext.activeSnapshot().activeSessionRules().isEmpty()
                ? 0
                : promoteButtonWidth() + 6;
        int available = Math.max(0, width() - telemetryWidth - promoteReserve - nodeStartX);
        List<NodeRect> rects = new ArrayList<>();
        int cursor = nodeStartX;
        for (Node node : nodes()) {
            int nodeWidth = nodeWidth(node);
            if (cursor + nodeWidth > nodeStartX + available) {
                break;
            }
            rects.add(new NodeRect(node, cursor, Math.max(0, (height() - nodeHeight) / 2), nodeWidth));
            cursor += nodeWidth + nodeGap;
        }
        return rects;
    }

    public @Nullable NodeRect nodeRectAt(double localX, double localY) {
        for (NodeRect rect : nodeRects()) {
            if (ChromeText.hit(localX, localY, rect.x(), rect.y(), rect.width(), nodeHeight)) {
                return rect;
            }
        }
        return null;
    }

    public int nodeWidth(Node node) {
        Component label = labelOf(node);
        int textWidth = label.getString().length() * 6 + 6;
        int iconReserve = subjectResourceOf(node) != null ? nodeIconSize + 3 : 0;
        int badges = (node.entry() != null && node.entry().persistedToWorld() ? badgeWidth : 0)
                + (node.sessionRules().isEmpty() ? 0 : badgeWidth * 2)
                + (node.placeholder() ? 4 : 0);
        return Math.max(36, Math.min(168, textWidth + iconReserve + badges + nodePad * 2));
    }

    public Component labelOf(Node node) {
        if (node.placeholder() || node.entry() == null) {
            return CirrusLangs.Workbench.Timeline.initial.get();
        }
        HistoryEntry entry = node.entry();
        if (!entry.rules().isEmpty()) {
            Component primary = summarizeRule(entry.rules().get(0));
            if (entry.rules().size() > 1) {
                return primary.copy().append(Component.literal(" +" + (entry.rules().size() - 1)));
            }
            return primary;
        }
        return Component.translatable(entry.labelKey());
    }

    private @Nullable ResourceKey subjectResourceOf(Node node) {
        HistoryEntry entry = node.entry();
        if (entry == null || entry.rules().isEmpty()) {
            return null;
        }
        return subjectResourceOfRule(entry.rules().get(0));
    }

    private @Nullable ResourceKey subjectResourceOfRule(GuidanceRule rule) {
        if (rule instanceof TagDefaultRule tr) {
            return tr.preferredMember();
        }
        if (rule instanceof ResourceRule rr) {
            return rr.resource();
        }
        if (rule instanceof CostOverrideRule cor) {
            return cor.resource();
        }
        if (rule instanceof MachineRule mr) {
            return ResourceKey.item(mr.machineId());
        }
        if (rule instanceof RecipeRule rec) {
            CompiledRecipeGraph graph = CirrusPlanningRuntime.shared().flatMap(CirrusPlanningRuntime::currentGraph)
                    .orElse(null);
            if (graph != null) {
                CompiledRecipe compiled = graph.recipeByKey(rec.recipeKey()).orElse(null);
                if (compiled != null && !compiled.outputs().isEmpty()) {
                    return compiled.outputs().get(0).resource();
                }
            }
        }
        return null;
    }

    private Component summarizeRule(GuidanceRule rule) {
        if (rule instanceof TagDefaultRule tr) {
            return CoPilotPaint.resourceName(tr.preferredMember());
        }
        if (rule instanceof ResourceRule rr) {
            String prefix = rr.strength() == RuleStrength.hardBan
                    ? "\u00d7 "
                    : rr.treatAsFreeBaseSource() ? "\u2713 " : "";
            return Component.literal(prefix).append(CoPilotPaint.resourceName(rr.resource()));
        }
        if (rule instanceof MachineRule mr) {
            String prefix = mr.strength() == RuleStrength.hardBan ? "\u00d7 " : "\u2713 ";
            return Component.literal(prefix)
                    .append(CoPilotPaint.machineName(ResourceKey.item(mr.machineId()), mr.machineId()));
        }
        if (rule instanceof RecipeRule rec) {
            ResourceKey out = subjectResourceOfRule(rec);
            String prefix = rec.strength() == RuleStrength.hardBan ? "\u00d7 " : "\u2605 ";
            Component name = out != null
                    ? CoPilotPaint.resourceName(out)
                    : Component.literal(CoPilotPaint.humanizeId(rec.recipeKey().shortString()));
            return Component.literal(prefix).append(name);
        }
        if (rule instanceof CostOverrideRule cor) {
            return CoPilotPaint.resourceName(cor.resource());
        }
        return Component.literal(CoPilotPaint.humanizeId(rule.ruleId()));
    }

    private String ruleActionLabel(GuidanceRule rule) {
        if (rule instanceof TagDefaultRule tr) {
            return "#" + tr.tagId();
        }
        if (rule instanceof ResourceRule rr) {
            return rr.strength() == RuleStrength.hardBan
                    ? "Hard Ban"
                    : rr.treatAsFreeBaseSource() ? "Treat as Free" : "Resource Rule";
        }
        if (rule instanceof MachineRule mr) {
            return mr.strength() == RuleStrength.hardBan ? "Ban Machine" : "Owned Machine";
        }
        if (rule instanceof RecipeRule rec) {
            return rec.strength() == RuleStrength.hardBan ? "Ban Recipe" : "Pin Recipe";
        }
        if (rule instanceof CostOverrideRule) {
            return "Cost Override";
        }
        return "Rule";
    }

    private int promoteButtonWidth() {
        int count = shellContext.activeSnapshot().activeSessionRules().size();
        Component label = CirrusLangs.Workbench.Timeline.saveCountWorld.get(count);
        return Math.max(promoteWidth, label.getString().length() * 6 + 22);
    }

    public boolean hitsRemoveBadge(NodeRect rect, double localX) {
        if (rect.node().sessionRules().isEmpty()) {
            return false;
        }
        int start = removeBadgeX(rect);
        return localX >= start && localX < start + badgeWidth;
    }

    public boolean hitsPromoteBadge(NodeRect rect, double localX) {
        if (rect.node().sessionRules().isEmpty()
                || rect.node().entry() == null
                || rect.node().entry().persistedToWorld()) {
            return false;
        }
        int start = promoteBadgeX(rect);
        return localX >= start && localX < start + badgeWidth;
    }

    public int removeBadgeX(NodeRect rect) {
        return rect.x() + rect.width() - nodePad - badgeWidth;
    }

    public int promoteBadgeX(NodeRect rect) {
        return removeBadgeX(rect) - badgeWidth - 1;
    }

    public Rect promoteAllRect() {
        int width = Math.max(0, width());
        int height = Math.max(0, height());
        int pw = promoteButtonWidth();
        return new Rect(
            Math.max(0, width - pw - telemetryWidth - 4),
            Math.max(0, (height - nodeHeight) / 2),
            pw,
            Math.min(nodeHeight, height)
        );
    }

    public WorkbenchShellContext shellContext() {
        return shellContext;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        CirrusPalette palette = shellContext.palette();
        int width = width();
        int height = height();
        if (width <= 0 || height <= 0) {
            return;
        }
        WorkbenchUiSnapshot snapshot = shellContext.activeSnapshot();
        int textY = ChromeText.centeredY(0, height, canvas);
        ChromeText.drawRight(
            canvas,
            telemetryLabel(),
            width - ChromeText.pad,
            textY,
            telemetry().truncated() ? palette.costH() : palette.textSecondary()
        );
        int sessionRuleCount = snapshot.activeSessionRules().size();
        if (sessionRuleCount > 0) {
            var promoteAll = promoteAllRect();
            boolean pHov = ChromeText
                    .hit(mouseX, mouseY, promoteAll.x(), promoteAll.y(), promoteAll.width(), promoteAll.height());
            OreUiPaint.raisedButton(
                canvas,
                promoteAll.x(),
                promoteAll.y(),
                promoteAll.width(),
                promoteAll.height(),
                palette,
                false,
                pHov,
                false,
                false
            );
            ChromeButtons.arrowUp(
                canvas,
                promoteAll.x() + 4,
                promoteAll.y() + Math.max(0, (promoteAll.height() - 6) / 2),
                6,
                palette.focusRing()
            );
            Component label = CirrusLangs.Workbench.Timeline.saveCountWorld.get(sessionRuleCount);
            ChromeText.drawFitted(
                canvas,
                label,
                promoteAll.x() + 13,
                ChromeText.centeredY(promoteAll.y(), promoteAll.height(), canvas),
                Math.max(12, promoteAll.width() - 16),
                pHov ? palette.textPrimary() : palette.focusRing()
            );
        }
        int currentPointer = snapshot.undoHistory().size();
        List<NodeRect> rects = nodeRects();
        for (int i = 0; i < rects.size(); i++) {
            NodeRect rect = rects.get(i);
            if (i > 0) {
                int chevX = rect.x() - nodeGap + 2;
                ChromeText.draw(
                    canvas,
                    Component.literal("\u203a"),
                    chevX,
                    ChromeText.centeredY(rect.y(), nodeHeight, canvas),
                    rect.node().redo() ? palette.textDisabled() : palette.textMuted()
                );
            }
            boolean hovered = ChromeText.hit(mouseX, mouseY, rect.x(), rect.y(), rect.width(), nodeHeight);
            boolean isCurrent = !rect.node().redo() && rect.node().jumpTarget() == currentPointer;
            if (rect.node().redo()) {
                OreUiPaint.searchField(canvas, rect.x(), rect.y(), rect.width(), nodeHeight, palette, false);
            } else if (isCurrent) {
                OreUiPaint.selectedToggle(canvas, rect.x(), rect.y(), rect.width(), nodeHeight, palette, hovered);
            } else {
                OreUiPaint.raisedButton(
                    canvas,
                    rect.x(),
                    rect.y(),
                    rect.width(),
                    nodeHeight,
                    palette,
                    false,
                    hovered,
                    false,
                    false
                );
            }
            ResourceKey iconKey = subjectResourceOf(rect.node());
            int textX = rect.x() + nodePad;
            if (iconKey != null) {
                CoPilotPaint.icon(
                    canvas,
                    CoPilotPaint.stackOf(iconKey, 1L),
                    textX,
                    rect.y() + Math.max(1, (nodeHeight - nodeIconSize) / 2),
                    nodeIconSize,
                    palette
                );
                textX += nodeIconSize + 3;
            }
            int ink = rect.node().redo()
                    ? palette.textDisabled()
                    : isCurrent ? palette.focusRing() : palette.textPrimary();
            ChromeText.drawFitted(
                canvas,
                labelOf(rect.node()),
                textX,
                ChromeText.centeredY(rect.y(), nodeHeight, canvas),
                Math.max(12, rect.x() + rect.width() - textX - nodePad - badgeReserve(rect.node())),
                ink
            );
            renderBadges(canvas, palette, rect);
        }
    }

    private void renderBadges(SceneCanvas canvas, CirrusPalette palette, NodeRect rect) {
        HistoryEntry entry = rect.node().entry();
        if (entry == null) {
            return;
        }
        if (entry.persistedToWorld()) {
            ChromeButtons.lock(
                canvas,
                rect.x() + rect.width() - nodePad - badgeWidth + 1,
                rect.y() + Math.max(0, (nodeHeight - 8) / 2),
                8,
                palette.ruleHardPin()
            );
        }
        if (!rect.node().sessionRules().isEmpty()) {
            int badgeY = rect.y() + Math.max(0, (nodeHeight - 7) / 2);
            ChromeButtons.cross(canvas, removeBadgeX(rect) + 1, badgeY, 7, palette.ruleHardBan());
            if (!entry.persistedToWorld()) {
                ChromeButtons.arrowUp(canvas, promoteBadgeX(rect) + 1, badgeY, 7, palette.ruleSoftPrefer());
            }
        }
    }

    private int badgeReserve(Node node) {
        int reserve = 0;
        if (node.entry() != null && node.entry().persistedToWorld()) {
            reserve += badgeWidth;
        }
        if (!node.sessionRules().isEmpty()) {
            reserve += badgeWidth * 2;
        }
        return reserve;
    }

    private List<GuidanceRule> sessionRulesOf(HistoryEntry entry) {
        List<GuidanceRule> out = new ArrayList<>();
        for (GuidanceRule rule : entry.rules()) {
            if (rule.scope() != GuidanceScope.worldSave) {
                out.add(rule);
            }
        }
        return List.copyOf(out);
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        NodeRect hit = nodeRectAt(localX, localY);
        if (hit != null) {
            Node node = hit.node();
            int currentPointer = shellContext.activeSnapshot().undoHistory().size();
            boolean isCurrent = !node.redo() && node.jumpTarget() == currentPointer;
            String badge = node.placeholder()
                    ? (isCurrent ? "CURRENT · BASE" : "BASE PLAN")
                    : node.redo() ? "REDO" : isCurrent ? "CURRENT" : "REV #" + node.jumpTarget();
            OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder(labelOf(node)).badge(
                badge,
                node.redo()
                        ? OreUiRichTooltip.Tone.muted
                        : isCurrent ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.accent
            );
            ResourceKey iconKey = subjectResourceOf(node);
            if (iconKey != null) {
                builder.icon(iconKey);
            }
            HistoryEntry entry = node.entry();
            if (entry != null && !entry.rules().isEmpty()) {
                for (GuidanceRule rule : entry.rules()) {
                    builder.keyValue(
                        Component.literal(ruleActionLabel(rule)),
                        summarizeRule(rule),
                        rule.scope() == GuidanceScope.worldSave
                                ? OreUiRichTooltip.Tone.positive
                                : OreUiRichTooltip.Tone.primary
                    );
                }
            }
            builder.footer(
                isCurrent
                        ? "Current active revision \u00b7 Click \u00d7 to remove rule or \u2191 to save"
                        : node.redo() ? "Click to redo forward to this revision" : "Click to restore this revision"
            );
            return builder.build();
        }
        if (!shellContext.activeSnapshot().activeSessionRules().isEmpty()) {
            var promoteAll = promoteAllRect();
            if (ChromeText
                    .hit(localX, localY, promoteAll.x(), promoteAll.y(), promoteAll.width(), promoteAll.height())) {
                int count = shellContext.activeSnapshot().activeSessionRules().size();
                return OreUiRichTooltip.builder(CirrusLangs.Workbench.Timeline.promoteAllWorld.get())
                        .badge(count + " RULES", OreUiRichTooltip.Tone.positive)
                        .text(
                            "Persist all temporary session rules into your world save so future plans keep them.",
                            OreUiRichTooltip.Tone.secondary
                        ).footer("Click to save all session rules to World Save").build();
            }
        }
        Telemetry t = telemetry();
        return OreUiRichTooltip.builder("Planner Diagnostics")
                .badge(
                    t.truncated() ? "BUDGET HIT" : "READY",
                    t.truncated() ? OreUiRichTooltip.Tone.warning : OreUiRichTooltip.Tone.positive
                )
                .keyValue(
                    Component.literal("Subgraph Nodes"),
                    Component.literal(String.valueOf(t.nodes())),
                    OreUiRichTooltip.Tone.primary
                )
                .keyValue(
                    Component.literal("Recipes Evaluated"),
                    Component.literal(String.valueOf(t.recipes())),
                    OreUiRichTooltip.Tone.primary
                )
                .keyValue(
                    Component.literal("Solve Time"),
                    Component.literal(t.elapsedMillis() + " ms"),
                    OreUiRichTooltip.Tone.costP
                ).build();
    }

    private WorkbenchShellLayout shellLayout() {
        return WorkbenchShellLayout.compute(
            shellContext.shellWidth(),
            shellContext.shellHeight(),
            shellContext.breakpoint(),
            shellContext.zenMode(),
            shellContext.leftDockWidth(),
            shellContext.rightDockWidth()
        );
    }
}
