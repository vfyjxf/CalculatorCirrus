package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.api.planning.cost.CostWeights;
import dev.vfyjxf.cirrus.api.planning.cost.ObjectivePreset;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusThemeIcons;
import dev.vfyjxf.cirrus.ui.CirrusThemeTextures;
import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchBreakpoint;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellLayout;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.state.CommandPaletteEngine;
import dev.vfyjxf.cirrus.ui.workbench.state.PaletteAction;
import dev.vfyjxf.cirrus.ui.workbench.state.PaletteItem;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHandle;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.floating.FloatingEffect;
import dev.vfyjxf.cloudlib.api.ui.floating.FloatingPlacement;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyles;
import dev.vfyjxf.cloudlib.api.ui.texture.ColorTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;
import dev.vfyjxf.cloudlib.ui.widget.TextFieldWidget;
import dev.vfyjxf.cloudlib.ui.widget.ToggleWidget;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.vfyjxf.cloudlib.api.ui.floating.FloatingMiddlewares.flip;
import static dev.vfyjxf.cloudlib.api.ui.floating.FloatingMiddlewares.offset;
import static dev.vfyjxf.cloudlib.api.ui.floating.FloatingMiddlewares.shift;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayFlex;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayNone;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexBasisPx;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexNoWrap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.widthOf;

/**
 * Z2 top command dock: target slot, search preview, count, preset, live inventory, undo/redo, refine duration and send to execution.
 */
public class TopCommandDockWidget extends WidgetGroup<Widget> implements OreUiTooltipProvider {
    public static final int chipGap = 4;
    public static final int chipPad = 4;
    public static final int presetCellWidth = 34;
    public static final int previewRow = 14;
    public static final int previewWidth = 260;
    public static final int previewLimit = 5;

    private final WorkbenchShellContext shellContext;
    private final ChromeKeyRouter router;
    private final TextFieldWidget search;
    private final NumberInputWidget count;
    private final ToggleWidget liveStock;
    private final ChromeSlot targetSlot = new ChromeSlot();
    private final ChromeSlot presetSlot = new ChromeSlot();
    private final ChromeSlot undoSlot = new ChromeSlot();
    private final ChromeSlot redoSlot = new ChromeSlot();
    private final ChromeSlot elapsedSlot = new ChromeSlot();
    private final ChromeSlot sendSlot = new ChromeSlot();
    private final ChromeSlot moreSlot = new ChromeSlot();
    private final Runnable themeListener = this::syncThemeColors;

    private @Nullable WorkbenchBreakpoint appliedBreakpoint;
    private float appliedDockWidth;
    private float appliedDockHeight;
    private boolean presetMenuOpen;
    private boolean searchExpanded;
    private List<PaletteItem> preview = List.of();
    private boolean syncingCount;
    private @Nullable SearchPreviewPopup previewPopup;
    private boolean closePreviewScheduled;

    public TopCommandDockWidget(WorkbenchShellContext context) {
        this.shellContext = Objects.requireNonNull(context, "context");
        this.router = new ChromeKeyRouter(context);
        WorkbenchShellLayout.Rect region = shellLayout().topDock();
        addStyleClass("workbench-topbar");
        defaultStyle(UIStyle.of(background(CirrusTextures.Workbench.cardSurface(context.theme()))));
        useStyle(
            positionAbsolute(),
            sizeOf(region.width(), region.height()),
            flexRow(),
            flexNoWrap(),
            alignItemsCenter(),
            gap(chipGap),
            padding(0, chipPad)
        );
        search = TextFieldWidget.create().setMaxLength(64)
                .setPlaceholder(CirrusLangs.Workbench.Topbar.searchPlaceholder.string())
                .onTextChanged(this::setSearchQuery).onEnterPressed(text -> activatePreview(0));
        search.onFocus(ignored -> updateSearchExpanded(true));
        search.onFocusLost(ignored -> {
            updateSearchExpanded(false);
            scheduleClosePreviewPopup();
        });
        count = NumberInputWidget.create().setMinValue(1L).setScrollStep(1L).onValueChanged(this::onCountChanged);
        liveStock = ToggleWidget.create(true).onToggle(ignored -> toggleLiveStock());

        addWidget(targetSlot);
        addWidget(search);
        addWidget(count);
        addWidget(presetSlot);
        addWidget(liveStock);
        addWidget(undoSlot);
        addWidget(redoSlot);
        addWidget(elapsedSlot);
        addWidget(sendSlot);
        addWidget(moreSlot);

        applyBreakpoint(shellContext.breakpoint(), Math.max(0f, region.width()), Math.max(0f, region.height()));
        syncThemeColors();
        syncControls();

        onLayout((widget, scope) -> applyBreakpoint(shellContext.breakpoint(), width(), height()));
        onMount((Scene scene, SceneContext sceneContext, SceneHandle handle) -> {
            shellContext.addListener(themeListener);
            syncThemeColors();
        });
        onUnmount(() -> {
            shellContext.removeListener(themeListener);
            closePreviewPopupNow();
        });
        setClickGroup(this);
        onClickOutside(ignored -> {
            if (presetMenuOpen) {
                presetMenuOpen = false;
            }
            scheduleClosePreviewPopup();
        });
        onMouseClick((input, clickCount, context1) -> {
            var local = input.mouseRelative(this);
            if (presetMenuOpen) {
                int cell = presetCellAt(local.x(), local.y());
                if (cell >= 0) {
                    selectPreset(ObjectivePreset.values()[cell]);
                    context1.consume();
                    return EventDispatch.consumed;
                }
                presetMenuOpen = false;
            }
            if (!input.isLeftClick()) {
                if (hits(presetSlot, local.x(), local.y()) && openWeights()) {
                    context1.consume();
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            }
            if (hits(targetSlot, local.x(), local.y())) {
                openBasket();
                context1.consume();
                return EventDispatch.consumed;
            }
            if (hits(presetSlot, local.x(), local.y())) {
                togglePresetMenu();
                context1.consume();
                return EventDispatch.consumed;
            }
            if (hits(undoSlot, local.x(), local.y())) {
                undo();
                context1.consume();
                return EventDispatch.consumed;
            }
            if (hits(redoSlot, local.x(), local.y())) {
                redo();
                context1.consume();
                return EventDispatch.consumed;
            }
            if (hits(sendSlot, local.x(), local.y())) {
                sendToExecution();
                context1.consume();
                return EventDispatch.consumed;
            }
            if (hits(moreSlot, local.x(), local.y())) {
                more();
                context1.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
    }

    //region layout

    private void updateSearchExpanded(boolean focused) {
        boolean next = focused || !search.text().isEmpty();
        if (next == searchExpanded) {
            return;
        }
        searchExpanded = next;
        applySlotStyles(
            appliedBreakpoint != null ? appliedBreakpoint : shellContext.breakpoint(),
            appliedDockWidth > 0f ? appliedDockWidth : width(),
            appliedDockHeight > 0f ? appliedDockHeight : height()
        );
    }

    private void applyBreakpoint(WorkbenchBreakpoint breakpoint, float measuredWidth, float measuredHeight) {
        if (breakpoint == null
                || (breakpoint == appliedBreakpoint
                        && (measuredWidth <= 0f || Math.abs(measuredWidth - appliedDockWidth) < 1f)
                        && (measuredHeight <= 0f || measuredHeight == appliedDockHeight))) {
            return;
        }
        boolean same = breakpoint == appliedBreakpoint;
        appliedBreakpoint = breakpoint;
        if (measuredWidth > 0f) {
            appliedDockWidth = measuredWidth;
        }
        if (measuredHeight > 0f) {
            appliedDockHeight = measuredHeight;
        }
        applySlotStyles(breakpoint, appliedDockWidth, appliedDockHeight);
        if (!same) {
            presetMenuOpen = false;
        }
    }

    private void applySlotStyles(WorkbenchBreakpoint breakpoint, float measuredWidth, float measuredHeight) {
        boolean compact = breakpoint == WorkbenchBreakpoint.compact;
        boolean standard = breakpoint == WorkbenchBreakpoint.standard;
        float dockWidth = measuredWidth > 0f ? measuredWidth : (compact ? 380f : (standard ? 560f : 720f));
        float availW = Math.max(60f, dockWidth - chipPad * 2f);
        boolean expanded = !compact && availW >= 260f && (searchExpanded || !search.text().isEmpty());
        float dockHeight = measuredHeight > 0f ? measuredHeight : breakpoint.topDockHeight();
        int chipHeight = Math.max(14, Math.min(20, Math.round(dockHeight) - 6));

        boolean showSearch = !compact && availW >= 240f;
        boolean showPreset = !compact && availW >= 340f;
        boolean showSecondary = !compact && !(standard && expanded) && availW >= 460f;
        boolean showElapsed = !compact && !expanded && availW >= 540f;
        boolean compactSend = compact || availW < 390f;

        float moreW = 18f;
        float countW = compact || availW < 300f ? 36f : 44f;
        float presetW = showPreset ? (availW < 420f ? 54f : 68f) : 0f;
        float liveW = showSecondary ? 20f : 0f;
        float undoW = showSecondary ? 18f : 0f;
        float redoW = showSecondary ? 18f : 0f;
        float elapsedW = showElapsed ? 50f : 0f;
        float sendW = compactSend ? 24f : (availW < 500f ? 84f : (standard && expanded ? 90f : 108f));

        int visibleCount = 4
                + (showSearch ? 1 : 0)
                + (showPreset ? 1 : 0)
                + (showSecondary ? 3 : 0)
                + (showElapsed ? 1 : 0);
        float fixedNonTarget = countW
                + presetW
                + liveW
                + undoW
                + redoW
                + elapsedW
                + sendW
                + moreW
                + Math.max(0, visibleCount - 1) * chipGap;
        float remainingForTargetAndSearch = Math.max(48f, availW - fixedNonTarget);

        float preferredTarget = compact ? 84f : (standard ? (expanded ? 104f : 124f) : 144f);
        float targetW;
        if (showSearch) {
            float minSearch = expanded ? 84f : 56f;
            targetW = Math.max(54f, Math.min(preferredTarget, remainingForTargetAndSearch - minSearch));
            if (targetW + minSearch > remainingForTargetAndSearch) {
                targetW = Math.max(44f, remainingForTargetAndSearch * 0.48f);
            }
        } else {
            targetW = Math.max(48f, Math.min(preferredTarget, remainingForTargetAndSearch));
        }

        targetSlot.useStyle(displayFlex(), widthOf(targetW), heightOf(chipHeight), flexShrink(1), minWidth(0));
        if (!showSearch) {
            search.useStyle(displayNone());
        } else {
            search.useStyle(
                displayFlex(),
                heightOf(chipHeight),
                minWidth(0),
                flexGrow(1),
                flexShrink(1),
                flexBasisPx(0f)
            );
        }
        search.setVisible(showSearch);
        count.useStyle(sizeOf(countW, Math.min(chipHeight, 16f)), flexShrink(0));
        presetSlot.useStyle(
            showPreset ? displayFlex() : displayNone(),
            widthOf(presetW),
            heightOf(chipHeight),
            flexShrink(0)
        );
        presetSlot.setVisible(showPreset);
        liveStock.useStyle(
            showSecondary ? displayFlex() : displayNone(),
            sizeOf(20f, 13f),
            flexShrink(0),
            background(VisualTexture.empty)
        );
        liveStock.setVisible(showSecondary);
        undoSlot.useStyle(
            showSecondary ? displayFlex() : displayNone(),
            widthOf(18f),
            heightOf(chipHeight),
            flexShrink(0)
        );
        undoSlot.setVisible(showSecondary);
        redoSlot.useStyle(
            showSecondary ? displayFlex() : displayNone(),
            widthOf(18f),
            heightOf(chipHeight),
            flexShrink(0)
        );
        redoSlot.setVisible(showSecondary);
        elapsedSlot.useStyle(
            showElapsed ? displayFlex() : displayNone(),
            widthOf(elapsedW),
            heightOf(chipHeight),
            flexShrink(0)
        );
        elapsedSlot.setVisible(showElapsed);
        sendSlot.useStyle(widthOf(sendW), heightOf(chipHeight), flexShrink(0));
        moreSlot.useStyle(widthOf(moreW), heightOf(chipHeight), flexShrink(0));
    }

    public TextFieldWidget searchField() {
        return search;
    }

    public ToggleWidget liveStockToggle() {
        return liveStock;
    }

    public Rect slotRect(Widget slot) {
        return new Rect(slot.posX(), slot.posY(), Math.max(0, slot.width()), Math.max(0, slot.height()));
    }

    public Rect targetSlotRect() {
        return slotRect(targetSlot);
    }

    public Rect searchRect() {
        return slotRect(search);
    }

    public Rect presetSlotRect() {
        return slotRect(presetSlot);
    }

    public Rect undoSlotRect() {
        return slotRect(undoSlot);
    }

    public Rect redoSlotRect() {
        return slotRect(redoSlot);
    }

    public Rect elapsedSlotRect() {
        return slotRect(elapsedSlot);
    }

    public Rect sendSlotRect() {
        return slotRect(sendSlot);
    }

    public Rect moreSlotRect() {
        return slotRect(moreSlot);
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        if (presetMenuOpen) {
            int cell = presetCellAt(localX, localY);
            if (cell >= 0 && cell < ObjectivePreset.values().length) {
                return buildPresetTooltip(ObjectivePreset.values()[cell], true);
            }
        }
        if (hits(targetSlot, localX, localY)) {
            WorkbenchUiSnapshot snapshot = shellContext.activeSnapshot();
            List<TargetDemand> targets = snapshot.targets();
            OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder("Target Basket").badge(
                targets.size() + (targets.size() == 1 ? " TARGET" : " TARGETS"),
                OreUiRichTooltip.Tone.accent
            );
            if (!targets.isEmpty()) {
                builder.icon(targets.get(0).resource());
                List<OreUiRichTooltip.ItemEntry> rows = new ArrayList<>(targets.size());
                for (TargetDemand demand : targets) {
                    rows.add(
                        OreUiRichTooltip.ItemEntry.of(
                            demand.resource(),
                            CoPilotPaint.resourceName(demand.resource()),
                            Component.literal("\u00d7" + ChromeText.abbreviated(demand.amount())),
                            OreUiRichTooltip.Tone.primary
                        )
                    );
                }
                builder.itemList(Component.literal("Demands"), rows, 5);
            } else {
                builder.text("No planning targets configured yet.", OreUiRichTooltip.Tone.muted);
            }
            builder.footer("Click to edit basket & add targets");
            return builder.build();
        }
        if (hits(presetSlot, localX, localY)) {
            return buildPresetTooltip(currentPreset(), false);
        }
        if (hits(liveStock, localX, localY)) {
            boolean enabled = liveStock.toggled();
            return OreUiRichTooltip.builder(CirrusLangs.Workbench.Topbar.liveStock.get())
                    .badge(
                        enabled ? "ON" : "OFF",
                        enabled ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.muted
                    )
                    .text(
                        enabled
                                ? CirrusLangs.Workbench.Topbar.liveStockOnDesc.get()
                                : CirrusLangs.Workbench.Topbar.liveStockOffDesc.get(),
                        OreUiRichTooltip.Tone.secondary
                    ).footer(CirrusLangs.Workbench.Topbar.liveStockFooter.get().getString()).build();
        }
        if (hits(undoSlot, localX, localY)) {
            return OreUiRichTooltip.builder("Undo Decision")
                    .badge("CTRL+Z", canUndo() ? OreUiRichTooltip.Tone.accent : OreUiRichTooltip.Tone.muted)
                    .text(
                        canUndo() ? "Revert the latest guidance or target edit." : "Nothing to undo.",
                        OreUiRichTooltip.Tone.secondary
                    ).build();
        }
        if (hits(redoSlot, localX, localY)) {
            return OreUiRichTooltip.builder("Redo Decision")
                    .badge("CTRL+Y", canRedo() ? OreUiRichTooltip.Tone.accent : OreUiRichTooltip.Tone.muted)
                    .text(
                        canRedo() ? "Reapply the undone planning step." : "Nothing to redo.",
                        OreUiRichTooltip.Tone.secondary
                    ).build();
        }
        if (hits(sendSlot, localX, localY)) {
            return OreUiRichTooltip.builder("Send Plan to Execution HUD")
                    .badge("EXECUTE", OreUiRichTooltip.Tone.positive)
                    .text(
                        "Pins this plan into the live in-world crafting checklist overlay.",
                        OreUiRichTooltip.Tone.secondary
                    ).build();
        }
        return null;
    }

    private OreUiRichTooltip buildPresetTooltip(ObjectivePreset preset, boolean fromExpandedCell) {
        CostWeights weights = fromExpandedCell
                ? CostWeights.forPreset(preset)
                : shellContext.activeSnapshot().profile().weights();
        double sum = Math.max(
            1e-6,
            weights.humanActiveWeight()
                    + weights.passiveWaitWeight()
                    + weights.machineSetupWeight()
                    + weights.materialScarcityWeight()
                    + weights.cognitiveFrictionWeight()
        );
        String name = CoPilotPaint.humanizeId(preset.name());
        return OreUiRichTooltip.builder(fromExpandedCell ? name : "Objective Preset")
                .badge(fromExpandedCell ? "PRESET" : name, OreUiRichTooltip.Tone.costC)
                .meter(
                    "Work Weight",
                    weights.humanActiveWeight() / sum,
                    CoPilotPaint.decimal(weights.humanActiveWeight(), 2),
                    OreUiRichTooltip.Tone.costH
                )
                .meter(
                    "Wait Weight",
                    weights.passiveWaitWeight() / sum,
                    CoPilotPaint.decimal(weights.passiveWaitWeight(), 2),
                    OreUiRichTooltip.Tone.costP
                )
                .meter(
                    "Equip Weight",
                    weights.machineSetupWeight() / sum,
                    CoPilotPaint.decimal(weights.machineSetupWeight(), 2),
                    OreUiRichTooltip.Tone.costK
                )
                .meter(
                    "Stock Weight",
                    weights.materialScarcityWeight() / sum,
                    CoPilotPaint.decimal(weights.materialScarcityWeight(), 2),
                    OreUiRichTooltip.Tone.costS
                )
                .meter(
                    "Steps Weight",
                    weights.cognitiveFrictionWeight() / sum,
                    CoPilotPaint.decimal(weights.cognitiveFrictionWeight(), 2),
                    OreUiRichTooltip.Tone.costC
                )
                .footer(
                    fromExpandedCell
                            ? "Click to apply this objective preset"
                            : "Click to switch preset · Right-click to fine-tune weights"
                ).build();
    }

    public List<Rect> presetCells() {
        List<Rect> cells = new ArrayList<>(ChromeProfiles.weightAxes > 0 ? ObjectivePreset.values().length : 0);
        int y = Math.max(0, (height() - 16) / 2);
        int x = Math.max(chipPad, width() - chipPad - ObjectivePreset.values().length * presetCellWidth);
        for (int index = 0; index < ObjectivePreset.values().length; index++) {
            cells.add(new Rect(x + index * presetCellWidth, y, presetCellWidth - 1, 16));
        }
        return cells;
    }

    public int presetCellAt(double localX, double localY) {
        List<Rect> cells = presetCells();
        for (int index = 0; index < cells.size(); index++) {
            Rect rect = cells.get(index);
            if (localX >= rect.x() && localX < rect.right() && localY >= rect.y() && localY < rect.bottom()) {
                return index;
            }
        }
        return -1;
    }

    private static boolean hits(Widget slot, double localX, double localY) {
        int width = slot.width();
        int height = slot.height();
        return width > 0
                && height > 0
                && localX >= slot.posX()
                && localX < slot.posX() + width
                && localY >= slot.posY()
                && localY < slot.posY() + height;
    }

    //endregion

    //region interactions

    public boolean openBasket() {
        TargetBasketPopoverWidget basket = ChromeLookup.find(this, TargetBasketPopoverWidget.class);
        if (basket == null) {
            return false;
        }
        if (!basket.isOpen() && shellContext instanceof WorkbenchShellWidget shell) {
            shell.closeTransientOverlays(basket);
        }
        basket.toggle();
        return true;
    }

    public boolean openWeights() {
        ProfileWeightsPopoverWidget weights = ChromeLookup.find(this, ProfileWeightsPopoverWidget.class);
        if (weights == null) {
            return false;
        }
        if (!weights.isOpen() && shellContext instanceof WorkbenchShellWidget shell) {
            shell.closeTransientOverlays(weights);
        }
        weights.toggle();
        return true;
    }

    public boolean selectPreset(@Nullable ObjectivePreset preset) {
        PlannerWorkbenchController controller = shellContext.activeController();
        presetMenuOpen = false;
        if (controller == null || preset == null) {
            return false;
        }
        controller.setProfile(ChromeProfiles.forPreset(preset, shellContext.activeSnapshot().profile()));
        return true;
    }

    public void togglePresetMenu() {
        boolean opening = !presetMenuOpen;
        if (opening && shellContext instanceof WorkbenchShellWidget shell) {
            shell.closeTransientOverlays(this);
        }
        presetMenuOpen = opening;
    }

    public void closePresetMenu() {
        presetMenuOpen = false;
    }

    public boolean presetMenuOpen() {
        return presetMenuOpen;
    }

    public ObjectivePreset currentPreset() {
        return shellContext.activeSnapshot().profile().preset();
    }

    public boolean toggleLiveStock() {
        PlannerWorkbenchController controller = shellContext.activeController();
        boolean next = !shellContext.activeSnapshot().useLiveStock();
        liveStock.setToggled(next);
        if (controller == null) {
            return false;
        }
        controller.setUseLiveStock(next);
        return true;
    }

    public boolean undo() {
        return router.undo();
    }

    public boolean redo() {
        return router.redo();
    }

    public boolean canUndo() {
        return shellContext.activeSnapshot().canUndo();
    }

    public boolean canRedo() {
        return shellContext.activeSnapshot().canRedo();
    }

    public long elapsedMillis() {
        var result = shellContext.activeSnapshot().result();
        return result == null ? 0L : result.diagnostics().elapsedMillis();
    }

    public boolean sendToExecution() {
        WorkbenchUiSnapshot snapshot = shellContext.activeSnapshot();
        if (snapshot.selectedPlan().isEmpty()) {
            shellContext.toast(CirrusLangs.Workbench.Flow.bannerUnreachable.get());
            return false;
        }
        shellContext.sendActivePlanToExecution();
        return true;
    }

    public long primaryAmount() {
        List<TargetDemand> targets = shellContext.activeSnapshot().targets();
        return targets.isEmpty() ? 0L : targets.get(0).amount();
    }

    public boolean applyTargetAmount(long amount) {
        List<TargetDemand> targets = shellContext.activeSnapshot().targets();
        if (targets.isEmpty()) {
            return false;
        }
        shellContext.setTargets(ChromeTargets.withPrimaryAmount(targets, amount), null);
        return true;
    }

    private void onCountChanged(long value) {
        if (syncingCount) {
            return;
        }
        applyTargetAmount(value);
    }

    public List<PaletteItem> previewResults() {
        return preview;
    }

    public List<PaletteItem> setSearchQuery(String query) {
        String text = query == null ? "" : query;
        preview = text.isBlank()
                ? List.of()
                : CommandPaletteEngine
                        .query(text, shellContext.activeSnapshot(), shellContext.activeGraph(), previewLimit);
        updateSearchExpanded(search.focused());
        syncPreviewPopup();
        return preview;
    }

    public boolean activatePreview(int index) {
        return activatePreview(index, false);
    }

    public boolean activatePreview(int index, boolean addToBasket) {
        if (index < 0 || index >= preview.size()) {
            shellContext.openCommandPalette();
            return false;
        }
        PaletteItem item = preview.get(index);
        if (item.primaryAction() instanceof PaletteAction.SetTarget setTarget) {
            applyTarget(setTarget.resource(), addToBasket);
            clearSearchPreview();
            return true;
        }
        if (item.primaryAction() instanceof PaletteAction.AddToBasket add) {
            applyTarget(add.resource(), true);
            clearSearchPreview();
            return true;
        }
        shellContext.openCommandPalette();
        return true;
    }

    private void clearSearchPreview() {
        search.setText("");
        preview = List.of();
        closePreviewPopupNow();
        updateSearchExpanded(search.focused());
    }

    public boolean applyTarget(@Nullable ResourceKey resource, boolean addToBasket) {
        if (resource == null) {
            return false;
        }
        List<TargetDemand> current = shellContext.activeSnapshot().targets();
        List<TargetDemand> next = ChromeTargets.merged(current, resource, addToBasket);
        if (current.isEmpty() && !addToBasket && count.getValue() > 1L) {
            next = ChromeTargets.withPrimaryAmount(next, count.getValue());
        }
        shellContext.setTargets(next, null);
        return true;
    }

    public boolean more() {
        shellContext.openCommandPalette();
        return true;
    }

    public void syncControls() {
        syncCountFromSnapshot();
        liveStock.setToggled(shellContext.activeSnapshot().useLiveStock());
    }

    /** Writes the primary target amount into the count field without echoing it back as a user edit. */
    private void syncCountFromSnapshot() {
        long amount = primaryAmount();
        syncingCount = true;
        try {
            count.setValue(Math.max(1L, amount));
        } finally {
            syncingCount = false;
        }
    }

    private void syncThemeColors() {
        CirrusPalette palette = shellContext.palette();
        CirrusThemeTextures textures = CirrusThemeTextures.declared(shellContext.theme());
        search.setBackgroundTexture(textures.recessed()).setBorderTexture(new ColorTexture(0x00000000))
                .setTextColor(palette.textPrimary()).setPlaceholderColor(palette.textMuted());
        liveStock.useStyle(background(VisualTexture.empty));
        liveStock.setTextures(textures.switchOff(), textures.switchOn()).setHoverTexture(null);
        syncControls();
    }

    public WorkbenchShellContext shellContext() {
        return shellContext;
    }

    //endregion

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        CirrusPalette palette = shellContext.palette();
        WorkbenchUiSnapshot snapshot = shellContext.activeSnapshot();
        int height = height();
        if (width() <= 0 || height <= 0) {
            return;
        }
        if (presetMenuOpen) {
            renderPresetCells(canvas, palette, mouseX, mouseY);
            return;
        }
        renderTargetSlot(canvas, palette, snapshot, mouseX, mouseY);
        renderPresetChip(canvas, palette, mouseX, mouseY);
        renderUndoRedo(canvas, palette, snapshot, mouseX, mouseY);
        renderElapsed(canvas, palette);
        renderSend(canvas, palette, mouseX, mouseY);
        renderMore(canvas, palette, mouseX, mouseY);
        renderPreview(canvas, palette);
    }

    private void renderTargetSlot(
        SceneCanvas canvas,
        CirrusPalette palette,
        WorkbenchUiSnapshot snapshot,
        int mouseX,
        int mouseY
    ) {
        Rect rect = targetSlotRect();
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        boolean hovered = ChromeText.hit(mouseX, mouseY, rect.x(), rect.y(), rect.width(), rect.height());
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
        List<TargetDemand> targets = snapshot.targets();
        int innerTop = rect.y() + 2;
        int innerH = Math.max(10, rect.height() - 5);
        int iconDim = Math.min(12, Math.max(8, innerH - 2));
        int iconArea = 0;
        int iconY = innerTop + Math.max(0, (innerH - iconDim) / 2);
        if (!targets.isEmpty()) {
            ChromeButtons.drawMiniItem(canvas, targets.get(0).resource(), palette, rect.x() + 6, iconY, iconDim);
            iconArea = iconDim + 4;
            if (targets.size() > 1 && rect.width() >= 110) {
                ChromeButtons.drawMiniItem(
                    canvas,
                    targets.get(1).resource(),
                    palette,
                    rect.x() + 6 + iconDim + 2,
                    iconY,
                    iconDim
                );
                iconArea = iconDim * 2 + 6;
            }
        }
        Component label = targets.isEmpty()
                ? CirrusLangs.Workbench.Topbar.addTarget.get()
                : CoPilotPaint.resourceName(targets.get(0).resource());
        int chipW = targets.isEmpty()
                ? 0
                : targets.size() > 1
                        ? ChromeButtons.basketChipWidth(canvas, targets.size() - 1)
                        : ChromeButtons.countChipWidth(canvas, targets.get(0).amount());
        int badgeReserve = chipW > 0 ? chipW + 6 : 0;
        int textX = rect.x() + (iconArea > 0 ? iconArea + 6 : 6);
        ChromeText.drawFitted(
            canvas,
            label,
            textX,
            ChromeText.centeredY(innerTop, innerH, canvas),
            Math.max(0, rect.right() - textX - 5 - badgeReserve),
            palette.textPrimary()
        );
        if (!targets.isEmpty()) {
            int chipY = innerTop + Math.max(0, (innerH - 11) / 2);
            if (targets.size() > 1) {
                ChromeButtons.basketChip(canvas, targets.size() - 1, rect.right() - chipW - 5, chipY, palette);
            } else {
                ChromeButtons.countChip(
                    canvas,
                    targets.get(0).amount(),
                    rect.right() - chipW - 5,
                    chipY,
                    palette,
                    palette.focusRing(),
                    palette.focusRing()
                );
            }
        }
    }

    private void renderPresetChip(SceneCanvas canvas, CirrusPalette palette, int mouseX, int mouseY) {
        Rect rect = presetSlotRect();
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        boolean hovered = ChromeText.hit(mouseX, mouseY, rect.x(), rect.y(), rect.width(), rect.height());
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
        int barHeight = 2;
        int barY = rect.bottom() - barHeight - 4;
        int labelBandHeight = Math.max(8, barY - rect.y() - 2);
        ChromeText.drawFitted(
            canvas,
            CirrusLangs.Workbench.Topbar.preset.get(),
            rect.x() + 5,
            ChromeText.centeredY(rect.y() + 1, labelBandHeight, canvas),
            rect.width() - 10,
            hovered ? palette.textPrimary() : palette.textSecondary()
        );
        renderWeightBar(canvas, palette, currentPreset(), rect.x() + 5, barY, rect.width() - 10, barHeight);
    }

    private void renderPresetCells(SceneCanvas canvas, CirrusPalette palette, int mouseX, int mouseY) {
        List<Rect> cells = presetCells();
        ObjectivePreset current = currentPreset();
        for (int index = 0; index < cells.size(); index++) {
            Rect rect = cells.get(index);
            ObjectivePreset preset = ObjectivePreset.values()[index];
            boolean hovered = ChromeText.hit(mouseX, mouseY, rect.x(), rect.y(), rect.width(), rect.height());
            ChromeButtons.plate(canvas, rect.x(), rect.y(), rect.width(), rect.height(), palette);
            if (preset == current) {
                canvas.strokeRect(rect.x(), rect.y(), rect.width(), rect.height(), palette.focusRing(), 1);
            } else if (hovered) {
                canvas.fill(
                    rect.x() + 1,
                    rect.y() + 1,
                    rect.width() - 2,
                    rect.height() - 2,
                    palette.cardSurfaceHover()
                );
            }
            int barHeight = 2;
            int barY = rect.bottom() - barHeight - 1;
            ChromeText.draw(
                canvas,
                Component.literal(Integer.toString(index + 1)),
                rect.x() + 4,
                ChromeText.centeredY(rect.y() + 1, Math.max(8, barY - rect.y() - 2), canvas),
                palette.textPrimary()
            );
            renderWeightBar(canvas, palette, preset, rect.x() + 3, barY, rect.width() - 6, barHeight);
        }
    }

    private void renderWeightBar(
        SceneCanvas canvas,
        CirrusPalette palette,
        ObjectivePreset preset,
        int x,
        int y,
        int width,
        int barHeight
    ) {
        var weights = CostWeights.forPreset(preset);
        int cellWidth = Math.max(1, width / ChromeProfiles.weightAxes);
        for (int axis = 0; axis < ChromeProfiles.weightAxes; axis++) {
            double weight = ChromeProfiles.axisWeight(weights, axis);
            int lit = Math.max(1, (int) Math.round(cellWidth * Math.min(1.0, weight / 4.0)));
            canvas.fill(x + axis * cellWidth, y, lit, barHeight, palette.costColor(axis));
        }
    }

    private void renderUndoRedo(
        SceneCanvas canvas,
        CirrusPalette palette,
        WorkbenchUiSnapshot snapshot,
        int mouseX,
        int mouseY
    ) {
        renderHistorySlot(
            canvas,
            palette,
            undoSlotRect(),
            CirrusTextures.Icon.Middle.arrowLeft,
            snapshot.canUndo(),
            mouseX,
            mouseY
        );
        renderHistorySlot(
            canvas,
            palette,
            redoSlotRect(),
            CirrusTextures.Icon.Middle.arrowRight,
            snapshot.canRedo(),
            mouseX,
            mouseY
        );
    }

    private void renderHistorySlot(
        SceneCanvas canvas,
        CirrusPalette palette,
        Rect rect,
        VisualTexture icon,
        boolean enabled,
        int mouseX,
        int mouseY
    ) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        boolean hovered = enabled && ChromeText.hit(mouseX, mouseY, rect.x(), rect.y(), rect.width(), rect.height());
        OreUiPaint.raisedButton(
            canvas,
            rect.x(),
            rect.y(),
            rect.width(),
            rect.height(),
            palette,
            !enabled,
            hovered,
            false,
            false
        );
        int iconSize = 10;
        ChromeButtons.icon(
            canvas,
            icon,
            rect.x() + Math.max(0, (rect.width() - iconSize) / 2),
            rect.y() + Math.max(0, (rect.height() - (enabled ? 2 : 0) - iconSize) / 2),
            iconSize,
            enabled ? 0xFFFFFFFF : OreUiPaint.withAlpha(0xFFFFFFFF, 0.35f),
            palette
        );
    }

    private void renderElapsed(SceneCanvas canvas, CirrusPalette palette) {
        Rect rect = elapsedSlotRect();
        if (rect.width() <= 0) {
            return;
        }
        long elapsed = elapsedMillis();
        boolean planning = shellContext.activeSnapshot().planningInFlight();
        Component label = Component.literal(elapsed + "ms");
        ChromeText.drawFitted(
            canvas,
            label,
            rect.x() + 8,
            ChromeText.centeredY(rect.y(), rect.height(), canvas),
            rect.width() - 10,
            planning ? palette.costH() : palette.textSecondary()
        );
        canvas.fill(
            rect.x() + 2,
            rect.y() + (rect.height() - 3) / 2,
            3,
            3,
            planning ? palette.costH() : palette.costS()
        );
    }

    private void renderSend(SceneCanvas canvas, CirrusPalette palette, int mouseX, int mouseY) {
        Rect rect = sendSlotRect();
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        boolean hovered = ChromeText.hit(mouseX, mouseY, rect.x(), rect.y(), rect.width(), rect.height());
        OreUiPaint.raisedButton(
            canvas,
            rect.x(),
            rect.y(),
            rect.width(),
            rect.height(),
            palette,
            false,
            hovered,
            true,
            false
        );
        int iconSize = 10;
        int iconX = rect.width() <= 40 ? rect.x() + Math.max(0, (rect.width() - iconSize) / 2) : rect.x() + 4;
        ChromeButtons.icon(
            canvas,
            CirrusThemeIcons.resolve(CirrusTextures.Icon.play, shellContext.theme()),
            iconX,
            rect.y() + Math.max(0, (rect.height() - iconSize) / 2),
            iconSize,
            palette.textOnAccent()
        );
        if (rect.width() > 40) {
            ChromeText.drawFitted(
                canvas,
                CirrusLangs.Workbench.Topbar.sendExecution.get(),
                rect.x() + 16,
                ChromeText.centeredY(rect.y(), rect.height() - 1, canvas),
                rect.width() - 20,
                palette.textOnAccent()
            );
        }
    }

    private void renderMore(SceneCanvas canvas, CirrusPalette palette, int mouseX, int mouseY) {
        Rect rect = moreSlotRect();
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        boolean hovered = ChromeText.hit(mouseX, mouseY, rect.x(), rect.y(), rect.width(), rect.height());
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
        int lineX = rect.x() + 4;
        int lineWidth = Math.max(2, rect.width() - 8);
        int topLine = rect.y() + Math.max(2, (rect.height() - 7) / 2);
        for (int row = 0; row < 3; row++) {
            canvas.fill(lineX, topLine + row * 3, lineWidth, 1, palette.textSecondary());
        }
    }

    private void syncPreviewPopup() {
        if (preview.isEmpty() || appliedBreakpoint == WorkbenchBreakpoint.compact || !lifecycle().mounted()) {
            closePreviewPopupNow();
            return;
        }
        if (previewPopup == null) {
            SearchPreviewPopup next = new SearchPreviewPopup();
            next.setClickGroup(this);
            next.useEffect(FloatingEffect.create(search, FloatingPlacement.bottomStart, offset(2), flip(2), shift(4)));
            addRootWidget(scene().root(), next);
            previewPopup = next;
        }
        previewPopup.refreshBounds();
    }

    private void scheduleClosePreviewPopup() {
        if (previewPopup == null || closePreviewScheduled) {
            return;
        }
        if (!lifecycle().mounted()) {
            closePreviewPopupNow();
            return;
        }
        closePreviewScheduled = true;
        scene().nextTick(() -> {
            closePreviewScheduled = false;
            if (!search.focused()) {
                closePreviewPopupNow();
            }
        });
    }

    private void closePreviewPopupNow() {
        SearchPreviewPopup active = previewPopup;
        previewPopup = null;
        closePreviewScheduled = false;
        if (active != null && active.parent() instanceof WidgetGroup<?> group) {
            group.remove(active);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addRootWidget(WidgetGroup<?> root, Widget widget) {
        ((WidgetGroup) root).addWidget(widget);
    }

    private void renderPreview(SceneCanvas canvas, CirrusPalette palette) {
        if (previewPopup != null || preview.isEmpty() || appliedBreakpoint == WorkbenchBreakpoint.compact) {
            return;
        }
        int rowWidth = Math.min(previewWidth, Math.max(0, width() - chipPad * 2));
        int top = height() + 2;
        int totalHeight = preview.size() * previewRow + 2;
        OreUiPaint.popover(canvas, chipPad, top, rowWidth, totalHeight, palette);
        for (int index = 0; index < preview.size(); index++) {
            PaletteItem item = preview.get(index);
            int y = top + 1 + index * previewRow;
            if (index > 0) {
                canvas.fill(chipPad + 2, y, Math.max(0, rowWidth - 4), 1, OreUiPaint.cardDivider(palette));
            }
            Component label = item.iconResource() != null
                    ? CoPilotPaint.resourceName(item.iconResource())
                    : Component.literal(item.displayText());
            ChromeText.drawFitted(
                canvas,
                label,
                chipPad + 6,
                ChromeText.centeredY(y, previewRow, canvas),
                rowWidth - 12,
                palette.textPrimary()
            );
        }
    }

    private final class SearchPreviewPopup extends Widget {
        private static final int popupRowH = 18;

        private SearchPreviewPopup() {
            setInteractive(true);
            useStyle(UIStyles.zIndex(2400));
            refreshBounds();
            onMouseClick((input, clickCount, clickCtx) -> {
                if (!input.isLeftClick()) {
                    return EventDispatch.pass;
                }
                var local = input.mouseRelative(this);
                int index = (int) ((local.y() - 2) / popupRowH);
                if (index >= 0 && index < preview.size()) {
                    activatePreview(index, input.isShiftDown());
                    clickCtx.consume();
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            });
        }

        private void refreshBounds() {
            int w = Math.max(200, Math.min(previewWidth, Math.max(search.width(), 220)));
            int h = Math.max(popupRowH, preview.size() * popupRowH) + 4;
            useStyle(sizeOf(w, h), UIStyles.zIndex(2400));
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            if (preview.isEmpty() || width() <= 0 || height() <= 0) {
                return;
            }
            CirrusPalette palette = shellContext.palette();
            OreUiPaint.popover(canvas, 0, 0, width(), height(), palette);
            for (int index = 0; index < preview.size(); index++) {
                PaletteItem item = preview.get(index);
                int ry = 2 + index * popupRowH;
                boolean hovered = mouseX >= 2 && mouseX < width() - 2 && mouseY >= ry && mouseY < ry + popupRowH;
                if (hovered) {
                    canvas.fill(2, ry, width() - 4, popupRowH, palette.cardSurfaceHover());
                }
                if (index > 0) {
                    canvas.fill(4, ry, Math.max(0, width() - 8), 1, OreUiPaint.cardDivider(palette));
                }
                int tx = 6;
                if (item.iconResource() != null) {
                    ChromeButtons.drawMiniItem(canvas, item.iconResource(), palette, 6, ry + 3, 12);
                    tx = 22;
                }
                String tag = item.primaryAction() instanceof PaletteAction.SetTarget
                        ? "Target"
                        : item.primaryAction() instanceof PaletteAction.FocusStep ? "Step" : "Cmd";
                int tagW = ChromeText.width(canvas, Component.literal(tag)) + 6;
                Component label = item.iconResource() != null
                        ? CoPilotPaint.resourceName(item.iconResource())
                        : Component.literal(item.displayText());
                ChromeText.drawFitted(
                    canvas,
                    label,
                    tx,
                    ChromeText.centeredY(ry, popupRowH, canvas),
                    Math.max(12, width() - tx - tagW - 8),
                    hovered ? palette.textPrimary() : palette.textSecondary()
                );
                ChromeText.drawRight(
                    canvas,
                    Component.literal(tag),
                    width() - 6,
                    ChromeText.centeredY(ry, popupRowH, canvas),
                    item.primaryAction() instanceof PaletteAction.SetTarget ? palette.costS() : palette.textMuted()
                );
            }
        }
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
