package dev.vfyjxf.cirrus.ui.workbench.copilot;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.CycleBreakRule;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.ModNamespaceRule;
import dev.vfyjxf.cirrus.api.planning.human.OverrideStockFeedback;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.SystemRule;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.floating.FloatingEffect;
import dev.vfyjxf.cloudlib.api.ui.floating.FloatingPlacement;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyles;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import static dev.vfyjxf.cloudlib.api.ui.floating.FloatingMiddlewares.offset;
import static dev.vfyjxf.cloudlib.api.ui.floating.FloatingMiddlewares.shift;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetZero;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeStretch;

/**
 * Nine context menus: an 18px scope quick-pick on top, grouped items, closes on a consumed outside click.
 */
public class NodeContextMenuWidget extends Widget {

    public static final int scopeRowHeight = 18;
    public static final int itemHeight = 12;
    public static final int separatorHeight = 3;
    public static final int pad = CoPilotPaint.pad;
    public static final int menuWidth = 168;
    public static final int popoverOffset = 4;
    public static final int popoverShift = 6;
    /** Relative to the parent layer (the shell mounts this at Z5): scrim 1, menu 2. */
    public static final int scrimZ = 1;
    public static final int menuZ = 2;

    public enum MenuKind {
        canvasBlank, craftStep, acquireStep, provisionMachineStep, cycleLoopStep, convertStep, setupLineStep, linePullStep, planCard, tab
    }

    public enum Action {
        addTarget, fitView, centerTarget, autoLayout, layoutLr, layoutTb, layoutSwimlane, expandLoops, collapseLoops, toggleZen, replaceRecipe, inspectStep, banRecipe, banSystem, breakCycle, declareOwned, declareOwnedMulti, modPrefer, modPenalize, modBan, openInDatabase, overrideStock, treatFree, costOverride, softPenalizeResource, banResource, openProducers, banMachine, editFacility, expandLoopTemplate, inspectLoopSeed, banConvertRecipe, locateResources, manageLine, disableLine, selectPlan, comparePlan, forkTab, viewCertificate, forkScenario, renameTab, closeOtherTabs, closeTab
    }

    public record Item(
        LangEntry label,
        @Nullable String subject,
        @Nullable String shortcut,
        Action action,
        boolean groupEnd
    ) {
        public Item {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(action, "action");
        }
    }

    private final CoPilotHost host;
    private final MenuKind kind;
    private final List<Item> items;
    private final @Nullable String stepId;
    private final @Nullable CompiledRecipeKey recipeKey;
    private final @Nullable ComponentStackKey resource;
    private final @Nullable MachineEntry machine;
    private final @Nullable Namespace systemId;
    private boolean persistToWorld;
    private @Nullable Consumer<Action> commandSink;
    private @Nullable Widget scrim;

    public NodeContextMenuWidget(
        CoPilotHost host,
        MenuKind kind,
        @Nullable String stepId,
        @Nullable CompiledRecipeKey recipeKey,
        @Nullable ComponentStackKey resource,
        @Nullable MachineEntry machine,
        @Nullable Namespace systemId
    ) {
        this.host = Objects.requireNonNull(host, "host");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.stepId = stepId;
        this.recipeKey = recipeKey;
        this.resource = resource;
        this.machine = (machine != null && !machine.isEmpty()) ? machine : null;
        this.systemId = systemId == null && recipeKey != null ? recipeKey.systemId() : systemId;
        this.items = itemsFor(kind, subjectFor());
        useStyle(sizeOf(menuWidth, preferredHeight()), positionAbsolute(), UIStyles.zIndex(menuZ));
        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            if (scopeRect(true).contains(local.x(), local.y())) {
                setPersistToWorld(false);
                return EventDispatch.consumed;
            }
            if (scopeRect(false).contains(local.x(), local.y())) {
                setPersistToWorld(true);
                return EventDispatch.consumed;
            }
            int index = itemIndexAt(local.x(), local.y());
            if (index < 0) {
                return EventDispatch.pass;
            }
            trigger(items.get(index).action());
            CirrusUiSound.playClickSound();
            context.consume();
            return EventDispatch.consumed;
        });
        onClickOutside(context -> close());
    }

    private String subjectFor() {
        if (machine != null) {
            return machine.contentKey().map(ComponentStackKey::displayId).orElse(machine.canonicalKey());
        }
        if (resource != null) {
            return resource.displayId();
        }
        if (recipeKey != null) {
            return recipeKey.shortString();
        }
        return stepId == null ? "" : stepId;
    }

    public static List<Item> itemsFor(MenuKind kind, @Nullable String subject) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case canvasBlank -> List.of(
                item(CirrusLangs.Workbench.Topbar.addTarget, null, "Ctrl+K", Action.addTarget, false),
                item(CirrusLangs.Workbench.Flow.fit, null, "F", Action.fitView, false),
                item(CirrusLangs.Workbench.Flow.home, null, "Home", Action.centerTarget, false),
                item(CirrusLangs.Workbench.Flow.autoLayout, null, "L", Action.autoLayout, true),
                item(CirrusLangs.Workbench.Flow.layoutLr, null, null, Action.layoutLr, false),
                item(CirrusLangs.Workbench.Flow.layoutTb, null, null, Action.layoutTb, false),
                item(CirrusLangs.Workbench.Flow.layoutSwimlane, null, null, Action.layoutSwimlane, true),
                item(CirrusLangs.Workbench.Flow.expandLoops, null, null, Action.expandLoops, false),
                item(CirrusLangs.Workbench.Flow.collapseLoops, null, null, Action.collapseLoops, true),
                item(CirrusLangs.Workbench.Topbar.zenEnter, null, "Tab", Action.toggleZen, false)
            );
            case craftStep -> List.of(
                item(CirrusLangs.Workbench.Action.replaceRecipe, subject, "P", Action.replaceRecipe, false),
                item(CirrusLangs.Workbench.Copilot.tabInspector, null, null, Action.inspectStep, true),
                item(CirrusLangs.Workbench.Action.banRecipe, null, "Del", Action.banRecipe, false),
                item(CirrusLangs.Workbench.Action.banSystem, subject, null, Action.banSystem, false),
                item(CirrusLangs.Workbench.Action.breakCycle, subject, null, Action.breakCycle, true),
                item(CirrusLangs.Workbench.Action.declareOwned, subject, "M", Action.declareOwned, false),
                item(CirrusLangs.Workbench.Action.modPrefer, null, null, Action.modPrefer, false),
                item(CirrusLangs.Workbench.Action.modPenalize, null, null, Action.modPenalize, false),
                item(CirrusLangs.Workbench.Action.modBan, null, null, Action.modBan, true),
                item(CirrusLangs.Workbench.Action.openInDb, null, null, Action.openInDatabase, false)
            );
            case acquireStep -> List.of(
                item(CirrusLangs.Workbench.Action.overrideStock, null, null, Action.overrideStock, false),
                item(CirrusLangs.Workbench.Action.treatFree, null, null, Action.treatFree, false),
                item(CirrusLangs.Workbench.Action.costOverride, null, null, Action.costOverride, true),
                item(CirrusLangs.Workbench.Action.modPenalize, null, null, Action.softPenalizeResource, false),
                item(CirrusLangs.Workbench.Action.banResource, null, null, Action.banResource, true),
                item(CirrusLangs.Workbench.Action.replaceRecipe, subject, "P", Action.openProducers, false)
            );
            case provisionMachineStep -> List.of(
                item(CirrusLangs.Workbench.Action.declareOwned, subject, "M", Action.declareOwned, false),
                item(CirrusLangs.Workbench.Action.declareOwnedMulti, null, null, Action.declareOwnedMulti, true),
                item(CirrusLangs.Workbench.Action.banSystem, null, null, Action.banMachine, false),
                item(CirrusLangs.Workbench.Facilities.edit, null, null, Action.editFacility, false)
            );
            case cycleLoopStep -> List.of(
                item(CirrusLangs.Workbench.Flow.expandLoops, null, null, Action.expandLoopTemplate, false),
                item(CirrusLangs.Workbench.Flow.nodeWorkingSeed, null, null, Action.inspectLoopSeed, true),
                item(CirrusLangs.Workbench.Action.breakCycle, subject, null, Action.breakCycle, false)
            );
            case convertStep -> List.of(
                item(CirrusLangs.Workbench.Action.banRecipe, subject, null, Action.banConvertRecipe, false),
                item(CirrusLangs.Workbench.Action.locateDag, null, null, Action.locateResources, false)
            );
            case setupLineStep,
                    linePullStep ->
                List.of(
                    item(CirrusLangs.Workbench.Facilities.lines, subject, null, Action.manageLine, false),
                    item(CirrusLangs.Workbench.Facilities.delete, null, null, Action.disableLine, false)
                );
            case planCard -> List.of(
                item(CirrusLangs.Workbench.Pareto.cardsMode, subject, null, Action.selectPlan, false),
                item(CirrusLangs.Workbench.Pareto.scatterMode, subject, null, Action.comparePlan, false),
                item(CirrusLangs.Workbench.Tab.fork, null, null, Action.forkTab, true),
                item(CirrusLangs.Workbench.Pareto.certValid, null, null, Action.viewCertificate, false)
            );
            case tab -> List.of(
                item(CirrusLangs.Workbench.Tab.fork, subject, null, Action.forkScenario, false),
                item(CirrusLangs.Workbench.Tab.rename, null, null, Action.renameTab, true),
                item(CirrusLangs.Workbench.Tab.closeOthers, null, null, Action.closeOtherTabs, false),
                item(CirrusLangs.Workbench.Tab.close, null, null, Action.closeTab, false)
            );
        };
    }

    private static Item item(
        LangEntry label,
        @Nullable String subject,
        @Nullable String shortcut,
        Action action,
        boolean groupEnd
    ) {
        return new Item(label, subject, shortcut, action, groupEnd);
    }

    public NodeContextMenuWidget commandSink(@Nullable Consumer<Action> sink) {
        this.commandSink = sink;
        return this;
    }

    public MenuKind menuKind() {
        return kind;
    }

    public List<Item> items() {
        return items;
    }

    public boolean persistToWorld() {
        return persistToWorld;
    }

    public NodeContextMenuWidget setPersistToWorld(boolean persistToWorld) {
        this.persistToWorld = persistToWorld;
        return this;
    }

    public @Nullable String stepId() {
        return stepId;
    }

    public int preferredHeight() {
        int height = scopeRowHeight + pad;
        for (Item item : items) {
            height += itemHeight;
            if (item.groupEnd()) {
                height += separatorHeight;
            }
        }
        return height + pad;
    }

    public Rect scopeRect(boolean session) {
        int half = Math.max(30, (menuWidth - pad * 2 - 4) / 2);
        return new Rect(session ? pad : pad + half + 4, 2, half, scopeRowHeight - 4);
    }

    public Rect itemRect(int index) {
        int y = scopeRowHeight + pad;
        for (int cursor = 0; cursor < index && cursor < items.size(); cursor++) {
            y += itemHeight + (items.get(cursor).groupEnd() ? separatorHeight : 0);
        }
        return new Rect(0, y, menuWidth, itemHeight);
    }

    public int itemIndexAt(double localX, double localY) {
        for (int index = 0; index < items.size(); index++) {
            if (itemRect(index).contains(localX, localY)) {
                return index;
            }
        }
        return -1;
    }

    public boolean trigger(Action action) {
        if (action == null) {
            return false;
        }
        GuidanceRule rule = ruleFor(action);
        if (rule != null) {
            host.refine(new ApplyRulesFeedback(List.of(rule), persistToWorld), null);
            close();
            return true;
        }
        switch (action) {
            case declareOwned -> {
                if (machine == null) {
                    return forward(action);
                }
                host.refine(new DeclareOwnedMachineFeedback(machine, 1, persistToWorld), null);
                close();
                return true;
            }
            case declareOwnedMulti -> {
                if (machine == null) {
                    return forward(action);
                }
                host.refine(new DeclareOwnedMachineFeedback(machine, 2, persistToWorld), null);
                close();
                return true;
            }
            case overrideStock -> {
                if (resource == null) {
                    return forward(action);
                }
                host.refine(new OverrideStockFeedback(resource, 0L), null);
                close();
                return true;
            }
            case selectPlan -> {
                if (stepId == null) {
                    return forward(action);
                }
                host.selectPlan(stepId);
                close();
                return true;
            }
            case comparePlan -> {
                if (stepId == null) {
                    return forward(action);
                }
                host.setComparePlan(stepId);
                close();
                return true;
            }
            case inspectStep, replaceRecipe, openProducers, locateResources -> {
                if (stepId != null) {
                    host.setSelectedSteps(Set.of(stepId));
                }
                if (action == Action.replaceRecipe && resource != null) {
                    host.openAlternativesFor(resource);
                }
                if (action == Action.openProducers && resource != null) {
                    host.openAlternativesFor(resource);
                }
                if (action == Action.locateResources && stepId != null) {
                    host.focusStepInDag(stepId);
                }
                close();
                return true;
            }
            default -> {
                return forward(action);
            }
        }
    }

    private boolean forward(Action action) {
        close();
        if (commandSink != null) {
            commandSink.accept(action);
            return true;
        }
        return false;
    }

    public @Nullable GuidanceRule ruleFor(Action action) {
        String scope = CoPilotDnd.ruleId("menu", String.valueOf(action.ordinal()));
        return switch (action) {
            case banRecipe -> recipeKey == null
                    ? null
                    : new RecipeRule(scope, CoPilotDnd.scopeFor(persistToWorld), RuleStrength.hardBan, recipeKey);
            case banConvertRecipe -> recipeKey == null
                    ? null
                    : new RecipeRule(scope, CoPilotDnd.scopeFor(persistToWorld), RuleStrength.hardBan, recipeKey);
            case banSystem -> systemId == null
                    ? null
                    : new SystemRule(scope, CoPilotDnd.scopeFor(persistToWorld), RuleStrength.hardBan, systemId, false);
            case banMachine -> machine == null
                    ? null
                    : new MachineRule(
                        scope,
                        CoPilotDnd.scopeFor(persistToWorld),
                        RuleStrength.hardBan,
                        machine,
                        false
                    );
            case breakCycle -> resource == null || recipeKey == null
                    ? null
                    : new CycleBreakRule(
                        scope,
                        CoPilotDnd.scopeFor(persistToWorld),
                        RuleStrength.hardBan,
                        resource,
                        recipeKey
                    );
            case treatFree -> resource == null
                    ? null
                    : new ResourceRule(
                        scope,
                        CoPilotDnd.scopeFor(persistToWorld),
                        RuleStrength.hardPin,
                        resource,
                        true
                    );
            case banResource -> resource == null
                    ? null
                    : new ResourceRule(
                        scope,
                        CoPilotDnd.scopeFor(persistToWorld),
                        RuleStrength.hardBan,
                        resource,
                        false
                    );
            case softPenalizeResource -> resource == null
                    ? null
                    : new ResourceRule(
                        scope,
                        CoPilotDnd.scopeFor(persistToWorld),
                        RuleStrength.softPenalize,
                        resource,
                        false
                    );
            case modPrefer, modPenalize, modBan -> {
                String namespace = namespaceOf();
                if (namespace == null) {
                    yield null;
                }
                RuleStrength strength = action == Action.modPrefer
                        ? RuleStrength.softPrefer
                        : action == Action.modPenalize ? RuleStrength.softPenalize : RuleStrength.hardBan;
                yield new ModNamespaceRule(scope, CoPilotDnd.scopeFor(persistToWorld), strength, namespace);
            }
            default -> null;
        };
    }

    private @Nullable String namespaceOf() {
        if (resource != null) {
            return resource.namespace();
        }
        if (machine != null) {
            return machine.contentKey().map(ComponentStackKey::namespace).orElse(null);
        }
        if (systemId != null) {
            return systemId.namespace();
        }
        if (recipeKey != null) {
            return recipeKey.explicitRecipeId()
                    .map(ResourceLocation::getNamespace)
                    .orElse(recipeKey.systemId().namespace());
        }
        return null;
    }

    public void showAt(Widget anchor) {
        Objects.requireNonNull(anchor, "anchor");
        if (!anchor.lifecycle().mounted()) {
            return;
        }
        WidgetGroup<?> root = anchor.scene().root();
        if (scrim == null) {
            scrim = new Scrim();
            addRootWidget(root, scrim);
        }
        useEffect(
            FloatingEffect.create(anchor, FloatingPlacement.bottomStart, offset(popoverOffset), shift(popoverShift))
        );
        addRootWidget(root, this);
    }

    public @Nullable Widget scrim() {
        return scrim;
    }

    public void close() {
        WidgetGroup<?> group = parent() instanceof WidgetGroup<?> parentGroup ? parentGroup : null;
        if (group != null) {
            group.remove(this);
        }
        Widget active = scrim;
        scrim = null;
        if (active != null && active.parent() instanceof WidgetGroup<?> scrimGroup) {
            scrimGroup.remove(active);
        }
    }

    /** Outside-click scrim: consumes the click and closes the menu. */
    public final class Scrim extends Widget {

        Scrim() {
            useStyle(positionAbsolute(), insetZero(), sizeStretch(), UIStyles.zIndex(scrimZ));
            onMouseClick((input, clickCount, context) -> {
                close();
                context.consume();
                return EventDispatch.consumed;
            });
            onMouseScrolled((mouseX, mouseY, scrollX, scrollY, context) -> {
                close();
                context.consume();
                return EventDispatch.consumed;
            });
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {}
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addRootWidget(WidgetGroup<?> root, Widget widget) {
        ((WidgetGroup) root).addWidget(widget);
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        CirrusPalette palette = host.palette();
        int width = width();
        int height = height();
        if (width <= 0 || height <= 0) {
            return;
        }
        OreUiPaint.popover(canvas, 0, 0, width, height, palette);
        renderScopeRow(canvas, palette, mouseX, mouseY);
        int hovered = itemIndexAt(mouseX, mouseY);
        for (int index = 0; index < items.size(); index++) {
            Item item = items.get(index);
            Rect rect = itemRect(index);
            if (index == hovered) {
                OreUiPaint.raisedCard(
                    canvas,
                    rect.x() + 2,
                    rect.y(),
                    Math.max(1, rect.width() - 4),
                    rect.height(),
                    palette,
                    true,
                    false
                );
            }
            Component label = item.subject() == null
                    ? item.label().get()
                    : item.label().get().copy()
                            .append(Component.literal(" " + CoPilotPaint.humanizeId(item.subject())));
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(canvas, label, width - pad * 2 - 26),
                pad,
                ChromeText.centeredY(rect.y(), rect.height(), canvas),
                index == hovered ? palette.textPrimary() : palette.textSecondary()
            );
            if (item.shortcut() != null) {
                CoPilotPaint.textRight(
                    canvas,
                    Component.literal(item.shortcut()),
                    width - pad,
                    ChromeText.centeredY(rect.y(), rect.height(), canvas),
                    palette.textDisabled()
                );
            }
            if (item.groupEnd()) {
                canvas.fill(pad, rect.y() + itemHeight, width - pad * 2, 1, OreUiPaint.cardDivider(palette));
            }
        }
    }

    private void renderScopeRow(SceneCanvas canvas, CirrusPalette palette, int mouseX, int mouseY) {
        Rect session = scopeRect(true);
        Rect world = scopeRect(false);
        renderScopeChip(
            canvas,
            palette,
            session,
            CirrusLangs.Workbench.Copilot.scopeSession.get(),
            !persistToWorld,
            session.contains(mouseX, mouseY)
        );
        renderScopeChip(
            canvas,
            palette,
            world,
            CirrusLangs.Workbench.Copilot.scopeWorld.get(),
            persistToWorld,
            world.contains(mouseX, mouseY)
        );
    }

    private void renderScopeChip(
        SceneCanvas canvas,
        CirrusPalette palette,
        Rect rect,
        Component label,
        boolean selected,
        boolean hovered
    ) {
        if (selected) {
            OreUiPaint.selectedToggle(canvas, rect.x(), rect.y(), rect.width(), rect.height(), palette, hovered);
        } else {
            OreUiPaint.raisedButton(
                canvas,
                rect.x(),
                rect.y(),
                rect.width(),
                rect.height(),
                palette,
                false,
                hovered,
                false,
                false
            );
        }
        int textWidth = canvas.font().width(label);
        canvas.drawString(
            label,
            rect.x() + Math.max(2, (rect.width() - textWidth) / 2),
            selected
                    ? ChromeText.centeredY(rect.y(), rect.height(), canvas)
                    : ChromeText.centeredButtonY(rect.y(), rect.height(), canvas),
            selected ? palette.focusRing() : (hovered ? palette.textPrimary() : palette.textSecondary()),
            false
        );
    }
}
