package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.OverrideStockFeedback;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.plan.ByproductEntry;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.internal.integration.jei.CirrusPlanBookmarkStore;
import dev.vfyjxf.cirrus.internal.integration.jei.JeiPlanBookmarkBridge;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusThemeIcons;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchBreakpoint;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeButtons;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cirrus.ui.workbench.layout.PreparationLedgerModel;
import dev.vfyjxf.cirrus.ui.workbench.layout.PreparationLedgerModel.AcquireRow;
import dev.vfyjxf.cirrus.ui.workbench.layout.PreparationLedgerModel.LedgerSortKey;
import dev.vfyjxf.cirrus.ui.workbench.layout.PreparationLedgerModel.MachinePrepRow;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.event.WidgetEvent;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyles;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import dev.vfyjxf.cloudlib.ui.widget.InfoBarWidget;
import dev.vfyjxf.cloudlib.ui.widget.RingWidget;
import dev.vfyjxf.cloudlib.ui.widget.SliderWidget;
import dev.vfyjxf.cloudlib.ui.widget.TextFieldWidget;
import dev.vfyjxf.cloudlib.ui.widget.ToggleWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetLeft;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetTop;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Preparation and byproduct ledger: four section cards, a What-If inventory slider and an inventory coverage ring.
 */
public class PreparationLedgerWidget extends CompositeWidget<Widget> implements OreUiTooltipProvider {

    public static final long whatIfDebounceMs = 180L;
    public static final int headerHeight = 26;
    public static final int titleHeight = 18;
    public static final int cardGap = 6;
    public static final int acquireRowHeight = 46;
    public static final int compactRowHeight = 28;
    public static final int tabHeight = 18;
    public static final int slotSize = 20;
    public static final int microButtonWidth = 48;
    public static final int microButtonHeight = 14;
    public static final int sliderWidth = 72;
    public static final int maxVisibleRows = 8;
    public static final int sortButtonSize = 18;
    public static final int sortButtonGap = 2;
    public static final int searchMinWidth = 96;
    public static final int searchMaxWidth = 180;
    public static final int stockHeaderHeight = 12;
    public static final int gridColumns = 2;
    public static final int stockColumnWidth = 46;
    public static final int stockMeterWidth = 56;
    public static final String stockAvailableKey = "cirrus.ui.workbench.ledger.available";
    public static final String stockConsumedKey = "cirrus.ui.workbench.ledger.consumed";
    public static final String stockReservedKey = "cirrus.ui.workbench.ledger.reserved";

    private static final int sectionAcquire = 0;
    private static final int sectionStock = 1;
    private static final int sectionMachines = 2;
    private static final int sectionSurplus = 3;

    private final FlowViewHost host;
    private final TextFieldWidget searchField;
    private final ToggleWidget viewToggle;
    private final RingWidget coverageRing;
    private final List<SliderWidget> sliders = new ArrayList<>();
    private final List<InfoBarWidget> meters = new ArrayList<>();
    private final Map<ResourceKey, Long> simulated = new TreeMap<>();
    private final Map<ResourceKey, Pending> pending = new TreeMap<>();
    private final List<Hotspot> hotspots = new ArrayList<>();
    private final List<TipRegion> tipRegions = new ArrayList<>();

    private record TipRegion(int x, int y, int width, int height, Supplier<OreUiRichTooltip> tip) {
        boolean contains(double localX, double localY) {
            return localX >= x && localX <= x + width && localY >= y && localY <= y + height;
        }
    }

    private Supplier<InventorySnapshot> inventorySource = () -> InventorySnapshot.empty;
    private LongSupplier clock = System::currentTimeMillis;
    private LedgerSortKey sortKey = LedgerSortKey.missingFirst;
    private boolean ascending = true;
    private boolean gridMode = true;
    private boolean optionsMenuOpen;
    private String query = "";
    private int activeSection;
    private final float[] targetScrollPx = new float[LedgerSorting.sectionCount];
    private final OreUiMotion.SmoothValue[] smoothScrollPx = {new OreUiMotion.SmoothValue(),
            new OreUiMotion.SmoothValue(), new OreUiMotion.SmoothValue(), new OreUiMotion.SmoothValue()};
    private final List<Hotspot> popupHotspots = new ArrayList<>();
    private int popupX;
    private int popupY;
    private int popupW;
    private int popupH;
    private int dispatchCount;
    private @Nullable PreparationLedgerModel model;
    private @Nullable InventorySnapshot overlaid;
    private @Nullable InventorySnapshot lastBaseInventory;
    private @Nullable ProductionPlan cachedPlan;
    private int inventoryPollTicks;

    private record Pending(long amount, long dueAtMillis) {}

    private record Hotspot(int x, int y, int width, int height, Runnable action) {

        boolean contains(double localX, double localY) {
            return localX >= x && localX <= x + width && localY >= y && localY <= y + height;
        }
    }

    private record Card(int x, int y, int width, int height) {}

    public PreparationLedgerWidget(FlowViewHost host) {
        this.host = Objects.requireNonNull(host, "host");
        useStyle(UIStyles.sizeFull());
        setTickable(true);
        searchField = addWidget(
            TextFieldWidget.create().setPlaceholder(CirrusLangs.Workbench.Topbar.searchPlaceholder.get().getString())
                    .setMaxLength(64).onTextChanged(this::setQuery)
        );
        viewToggle = addWidget(
            ToggleWidget.create(true)
                    .setTextures(CirrusTextures.Toggle.Switch.Calc.listOff, CirrusTextures.Toggle.Switch.Calc.gridOn)
                    .setHoverTexture(CirrusTextures.Toggle.Switch.Calc.gridHover).onToggle(this::setGridMode)
        );
        viewToggle.setVisible(false);
        coverageRing = addWidget(RingWidget.of(() -> LedgerSorting.coverage(model)).setThickness(4));
        coverageRing.addStyleClass("ok");
        onMount((scene, context, handle) -> scene.nextTick(this::refresh, handle::onCleanup));
        onEvent(WidgetEvent.onSizeChanged, (size, context) -> {
            applySectionVisibility();
            relayout();
        });
        onMouseScrolled((mouseX, mouseY, scrollX, scrollY, context) -> {
            if (scrollY == 0.0 || model == null) {
                return EventDispatch.pass;
            }
            FloatPos local = sceneToLocal(mouseX, mouseY);
            int targetSec = sectionAtPoint(local.x(), local.y());
            if (targetSec < 0) {
                return EventDispatch.pass;
            }
            float maxScroll = maxScrollPxFor(targetSec);
            if (maxScroll <= 0.5f) {
                return EventDispatch.pass;
            }
            float next = Math.max(0f, Math.min(maxScroll, targetScrollPx[targetSec] - (float) (scrollY * 38.0)));
            if (Math.abs(next - targetScrollPx[targetSec]) < 0.01f) {
                return EventDispatch.pass;
            }
            targetScrollPx[targetSec] = next;
            context.consume();
            return EventDispatch.consumed;
        });
        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            if (optionsMenuOpen) {
                for (Hotspot hotspot : popupHotspots) {
                    if (hotspot.contains(local.x(), local.y())) {
                        hotspot.action().run();
                        CirrusUiSound.playClickSound();
                        context.consume();
                        return EventDispatch.consumed;
                    }
                }
                if (!ChromeText.hit(local.x(), local.y(), popupX, popupY, popupW, popupH)) {
                    optionsMenuOpen = false;
                }
                context.consume();
                return EventDispatch.consumed;
            }
            for (Hotspot hotspot : hotspots) {
                if (hotspot.contains(local.x(), local.y())) {
                    hotspot.action().run();
                    context.consume();
                    return EventDispatch.consumed;
                }
            }
            return EventDispatch.pass;
        });
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        for (int i = tipRegions.size() - 1; i >= 0; i--) {
            TipRegion region = tipRegions.get(i);
            if (region.contains(localX, localY)) {
                return region.tip().get();
            }
        }
        return null;
    }

    //region data

    public PreparationLedgerWidget setInventorySource(Supplier<InventorySnapshot> source) {
        this.inventorySource = source == null ? () -> InventorySnapshot.empty : source;
        refresh();
        return this;
    }

    public PreparationLedgerWidget setClock(LongSupplier clock) {
        this.clock = clock == null ? System::currentTimeMillis : clock;
        return this;
    }

    public @Nullable PreparationLedgerModel model() {
        return model;
    }

    public LedgerSortKey sortKey() {
        return sortKey;
    }

    public boolean ascending() {
        return ascending;
    }

    public boolean gridMode() {
        return gridMode;
    }

    public int activeSection() {
        return activeSection;
    }

    public int dispatchCount() {
        return dispatchCount;
    }

    public int pendingCount() {
        return pending.size();
    }

    public Map<ResourceKey, Long> simulatedStock() {
        return Map.copyOf(simulated);
    }

    public InventorySnapshot simulatedInventory() {
        InventorySnapshot current = overlaid;
        return current == null ? LedgerSorting.overlay(inventorySource.get(), simulated) : current;
    }

    public void refresh() {
        cachedPlan = host.snapshot().selectedPlan().orElse(null);
        lastBaseInventory = safeInventory();
        rebuildAll();
    }

    private InventorySnapshot safeInventory() {
        InventorySnapshot snapshot = inventorySource.get();
        return snapshot == null ? InventorySnapshot.empty : snapshot;
    }

    private void syncPlanIfChanged(boolean pollInventory) {
        ProductionPlan latestPlan = host.snapshot().selectedPlan().orElse(null);
        if (model == null || !Objects.equals(latestPlan, cachedPlan)) {
            cachedPlan = latestPlan;
            lastBaseInventory = safeInventory();
            rebuildAll();
            return;
        }
        if (pollInventory && cachedPlan != null) {
            InventorySnapshot latestInventory = safeInventory();
            if (!Objects.equals(latestInventory, lastBaseInventory)) {
                lastBaseInventory = latestInventory;
                rebuildAll();
            }
        }
    }

    private void rebuildAll() {
        rebuildModel(cachedPlan);
        rebuildRows();
        applySectionVisibility();
        relayout();
    }

    public void setQuery(@Nullable String query) {
        String next = query == null ? "" : query.trim();
        if (next.equals(this.query)) {
            return;
        }
        this.query = next;
        targetScrollPx[sectionAcquire] = 0f;
        smoothScrollPx[sectionAcquire].snap(0f, System.nanoTime());
        rebuildAll();
    }

    public void selectSortKey(LedgerSortKey key) {
        Objects.requireNonNull(key, "key");
        if (key == sortKey) {
            ascending = !ascending;
        } else {
            sortKey = key;
            ascending = true;
        }
        rebuildAll();
    }

    public void cycleSortKey() {
        selectSortKey(LedgerSorting.nextKey(sortKey));
    }

    public void toggleAscending() {
        ascending = !ascending;
        rebuildAll();
    }

    public void setGridMode(boolean gridMode) {
        if (this.gridMode == gridMode) {
            return;
        }
        this.gridMode = gridMode;
        viewToggle.setToggled(gridMode);
        targetScrollPx[sectionAcquire] = 0f;
        smoothScrollPx[sectionAcquire].snap(0f, System.nanoTime());
        applySectionVisibility();
        relayout();
    }

    public boolean optionsMenuOpen() {
        return optionsMenuOpen;
    }

    public void toggleOptionsMenu() {
        optionsMenuOpen = !optionsMenuOpen;
    }

    public void selectSection(int section) {
        if (section < 0 || section >= LedgerSorting.sectionCount) {
            return;
        }
        activeSection = section;
        targetScrollPx[section] = 0f;
        smoothScrollPx[section].snap(0f, System.nanoTime());
        applySectionVisibility();
        relayout();
    }

    public void simulateStock(ResourceKey resource, long amount) {
        if (resource == null) {
            return;
        }
        long clamped = Math.max(LedgerSorting.minSimulatedAmount, Math.min(LedgerSorting.maxSimulatedAmount, amount));
        Map<ResourceKey, Long> next = LedgerSorting.simulate(Map.copyOf(simulated), resource, clamped);
        simulated.clear();
        simulated.putAll(next);
        pending.put(resource, new Pending(clamped, clock.getAsLong() + whatIfDebounceMs));
        rebuildModel(cachedPlan);
    }

    public int flushDueOverrides() {
        return flushDueOverrides(clock.getAsLong());
    }

    public int flushDueOverrides(long nowMillis) {
        List<ResourceKey> due = new ArrayList<>();
        for (Map.Entry<ResourceKey, Pending> entry : pending.entrySet()) {
            if (entry.getValue().dueAtMillis() <= nowMillis) {
                due.add(entry.getKey());
            }
        }
        int dispatched = 0;
        for (ResourceKey resource : due) {
            Pending value = pending.remove(resource);
            if (value == null) {
                continue;
            }
            host.dispatch(new OverrideStockFeedback(resource, value.amount()));
            dispatched++;
        }
        dispatchCount += dispatched;
        return dispatched;
    }

    public void declareOwned(String machineId) {
        if (machineId == null || machineId.isBlank()) {
            return;
        }
        host.dispatch(new DeclareOwnedMachineFeedback(machineId, 1, false));
        dispatchCount++;
    }

    public void rejectMachine(String machineId) {
        if (machineId == null || machineId.isBlank()) {
            return;
        }
        MachineRule rule = new MachineRule(
            "rule:nobuild:" + machineId,
            GuidanceScope.session,
            RuleStrength.hardBan,
            machineId,
            false
        );
        host.dispatch(new ApplyRulesFeedback(List.of(rule), false));
        dispatchCount++;
    }

    public void declareFreeSource(ResourceKey resource) {
        if (resource == null) {
            return;
        }
        ResourceRule rule = new ResourceRule(
            "session.free:" + resource.shortString(),
            GuidanceScope.session,
            RuleStrength.hardPin,
            resource,
            true
        );
        host.dispatch(new ApplyRulesFeedback(List.of(rule), false));
        dispatchCount++;
    }

    public void focusStep(@Nullable String stepId) {
        if (stepId != null && !stepId.isBlank()) {
            host.onFocusStep(stepId);
        }
    }

    @Override
    public void tick() {
        super.tick();
        inventoryPollTicks++;
        boolean pollInventory = inventoryPollTicks >= 10;
        if (pollInventory) {
            inventoryPollTicks = 0;
        }
        syncPlanIfChanged(pollInventory);
        flushDueOverrides();
    }

    private void rebuildModel(@Nullable ProductionPlan plan) {
        InventorySnapshot base = lastBaseInventory == null ? safeInventory() : lastBaseInventory;
        overlaid = LedgerSorting.overlay(base, simulated);
        model = PreparationLedgerModel.build(plan, overlaid, sortKey, ascending, query.isEmpty() ? null : query);
    }

    private void rebuildRows() {
        for (SliderWidget slider : sliders) {
            remove(slider);
        }
        sliders.clear();
        for (InfoBarWidget meter : meters) {
            remove(meter);
        }
        meters.clear();
        if (model == null) {
            return;
        }
        int visible = Math.min(maxVisibleRows, model.acquireRows().size());
        for (int index = 0; index < visible; index++) {
            AcquireRow row = model.acquireRows().get(index);
            SliderWidget slider = addWidget(
                SliderWidget.create(0.0, LedgerSorting.sliderMax(row)).setStep(1.0)
                        .setValue(effectiveSimulated(row.resource()))
            );
            slider.onValueChanged(value -> simulateStock(row.resource(), Math.round(value)));
            sliders.add(slider);
        }
        int stockRows = Math.min(maxVisibleRows, model.stockConsumedRows().size());
        for (int index = 0; index < stockRows; index++) {
            AcquireRow row = model.stockConsumedRows().get(index);
            InfoBarWidget meter = addWidget(
                InfoBarWidget.segmented(InfoBarWidget.Segment.of(() -> satisfactionOf(row), InfoBarWidget.styleOk))
            );
            meter.addStyleClass("cirrus-meter");
            meters.add(meter);
        }
    }

    private double effectiveSimulated(ResourceKey resource) {
        Long value = simulated.get(resource);
        return value == null ? 0.0 : value.doubleValue();
    }

    private double satisfactionOf(AcquireRow row) {
        long needed = Math.max(1L, row.neededAmount());
        return Math
                .max(0.0, Math.min(1.0, LedgerSorting.effectiveAvailable(row, simulatedInventory()) / (double) needed));
    }

    private void applySectionVisibility() {
        boolean tabs = LedgerSorting.sectionsAsTabs(classify());
        boolean acquireVisible = (!tabs || activeSection == sectionAcquire) && !gridMode;
        boolean stockVisible = !tabs || activeSection == sectionStock;
        for (SliderWidget slider : sliders) {
            slider.setVisible(acquireVisible);
        }
        for (InfoBarWidget meter : meters) {
            meter.setVisible(stockVisible);
        }
    }

    //endregion

    //region layout

    private WorkbenchBreakpoint classify() {
        return WorkbenchBreakpoint.classify(width());
    }

    private int cardWidthFor() {
        int width = width();
        return LedgerSorting.sectionsAsTabs(classify())
                ? Math.max(0, width - cardGap * 2)
                : Math.max(0, (width - cardGap * 3) / 2);
    }

    private int sectionContentOffset() {
        return LedgerSorting.sectionsAsTabs(classify()) ? 4 : titleHeight;
    }

    private List<Card> cards() {
        int width = width();
        int height = height();
        int top = headerHeight + 4;
        if (LedgerSorting.sectionsAsTabs(classify())) {
            return List.of(
                new Card(
                    cardGap,
                    top + tabHeight + 2,
                    cardWidthFor(),
                    Math.max(0, height - top - tabHeight - 2 - cardGap)
                )
            );
        }
        int cardWidth = cardWidthFor();
        int cardHeight = Math.max(0, (height - top - cardGap * 3) / 2);
        List<Card> cards = new ArrayList<>(LedgerSorting.sectionCount);
        for (int index = 0; index < LedgerSorting.sectionCount; index++) {
            cards.add(
                new Card(
                    cardGap + (index % 2) * (cardWidth + cardGap),
                    top + (index / 2) * (cardHeight + cardGap),
                    cardWidth,
                    cardHeight
                )
            );
        }
        return cards;
    }

    private int cardIndexFor(int section) {
        return LedgerSorting.sectionsAsTabs(classify()) ? 0 : section;
    }

    private int visibleRowCount(int section, int cardHeight) {
        int header = section == sectionStock ? stockHeaderHeight : 0;
        int available = Math.max(0, cardHeight - sectionContentOffset() - 4 - header);
        int rowHeight = section == sectionAcquire ? acquireRowHeight : compactRowHeight;
        return Math.max(0, available / rowHeight);
    }

    private int searchWidth() {
        return Math.min(searchMaxWidth, Math.max(76, width() / 3));
    }

    private int sortButtonsX() {
        return 8 + searchWidth() + 6;
    }

    private int sectionAtPoint(double lx, double ly) {
        boolean asTabs = LedgerSorting.sectionsAsTabs(classify());
        if (asTabs) {
            return activeSection;
        }
        List<Card> list = cards();
        for (int sec = 0; sec < list.size() && sec < LedgerSorting.sectionCount; sec++) {
            Card c = list.get(sec);
            if (lx >= c.x() && lx <= c.x() + c.width() && ly >= c.y() && ly <= c.y() + c.height()) {
                return sec;
            }
        }
        return -1;
    }

    private float maxScrollPxFor(int section) {
        if (model == null) {
            return 0f;
        }
        Card card = cards().get(cardIndexFor(section));
        int header = section == sectionStock ? stockHeaderHeight : 0;
        int viewportH = Math.max(1, card.height() - sectionContentOffset() - 4 - header);
        int contentH;
        if (section == sectionAcquire) {
            int count = model.acquireRows().size();
            int lines = gridMode ? (count + gridColumns - 1) / gridColumns : count;
            contentH = lines * acquireRowHeight + 6;
        } else {
            int count = sectionCount(section);
            contentH = count * compactRowHeight + 4;
        }
        return Math.max(0f, contentH - viewportH);
    }

    private float currentScrollPx(int section) {
        float max = maxScrollPxFor(section);
        if (targetScrollPx[section] > max) {
            targetScrollPx[section] = max;
        }
        if (!smoothScrollPx[section].initialized()) {
            smoothScrollPx[section].snap(targetScrollPx[section], System.nanoTime());
        }
        return smoothScrollPx[section].update(targetScrollPx[section], 18.0f);
    }

    private void drawScrollbar(
        SceneCanvas canvas,
        CirrusPalette palette,
        int x,
        int y,
        int h,
        float scrollPx,
        float maxScroll
    ) {
        if (maxScroll <= 0.5f || h < 16) {
            return;
        }
        float total = h + maxScroll;
        int thumbH = Math.max(14, Math.round(h * (h / total)));
        int thumbY = y + Math.round((h - thumbH) * (scrollPx / maxScroll));
        canvas.fill(x, y, 3, h, OreUiPaint.withAlpha(palette.recessedSurface(), 0.7f));
        canvas.fill(x, thumbY, 3, thumbH, palette.hairlineHighlight());
    }

    private void relayout() {
        if (width() <= 0 || height() <= 0) {
            return;
        }
        place(searchField, 8, 4, searchWidth(), 18);
        place(viewToggle, 0, 0, 0, 0);
        place(coverageRing, width() - 8 - 22, 2, 22, 22);
        int topOffset = sectionContentOffset();
        List<Card> cardList = cards();
        if (!sliders.isEmpty()) {
            Card card = cardList.get(cardIndexFor(sectionAcquire));
            int rowRight = card.x() + card.width() - 8;
            int sliderX = rowRight - sliderWidth;
            int scrollOff = Math.round(currentScrollPx(sectionAcquire));
            for (int index = 0; index < sliders.size(); index++) {
                int rowY = card.y() + topOffset + 3 + index * acquireRowHeight - scrollOff;
                boolean inView = !gridMode
                        && rowY + 20 >= card.y() + topOffset
                        && rowY + 30 <= card.y() + card.height();
                sliders.get(index).setVisible(inView);
                place(sliders.get(index), sliderX, rowY + 20, sliderWidth, 10);
            }
        }
        if (!meters.isEmpty()) {
            Card card = cardList.get(cardIndexFor(sectionStock));
            int meterX = card.x() + card.width() - 6 - stockColumnWidth * 3 - stockMeterWidth - 6;
            for (int index = 0; index < meters.size(); index++) {
                int rowY = card.y() + topOffset + stockHeaderHeight + index * compactRowHeight;
                place(meters.get(index), meterX, rowY + 8, stockMeterWidth, 6);
            }
        }
    }

    private static void place(Widget widget, int x, int y, int width, int height) {
        if (widget == null) {
            return;
        }
        widget.useStyle(UIStyle.of(positionAbsolute(), insetLeft(x), insetTop(y), sizeOf(width, height)));
    }

    //endregion

    //region render

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        int width = width();
        int height = height();
        if (width <= 0 || height <= 0) {
            return;
        }
        syncPlanIfChanged(false);
        hotspots.clear();
        tipRegions.clear();
        CirrusPalette palette = host.palette();
        var textures = CirrusUiTheme.textures(this);
        searchField.setBackgroundTexture(textures.recessed());
        searchField.setTextColor(palette.textPrimary());
        searchField.setPlaceholderColor(palette.textMuted());
        canvas.fill(0, 0, width, height, palette.canvasVoid());
        Font font = fontOrNull(canvas);
        renderHeader(canvas, palette, font, width, mouseX, mouseY);
        WorkbenchBreakpoint breakpoint = classify();
        List<Card> cardList = cards();
        if (LedgerSorting.sectionsAsTabs(breakpoint)) {
            renderTabs(canvas, palette, font, cardList.get(0), mouseX, mouseY);
        }
        if (model == null) {
            renderCoverageLabel(canvas, palette, font, width);
            return;
        }
        for (int section = 0; section < LedgerSorting.sectionCount; section++) {
            if (LedgerSorting.sectionsAsTabs(breakpoint) && section != activeSection) {
                continue;
            }
            renderSection(canvas, palette, font, cardList.get(cardIndexFor(section)), section, mouseX, mouseY);
        }
        renderCoverageLabel(canvas, palette, font, width);
        if (optionsMenuOpen) {
            renderOptionsPopup(canvas, palette, font, width, height, mouseX, mouseY);
        }
    }

    private String sortShortLabel(LedgerSortKey key) {
        return switch (key) {
            case missingFirst -> "Missing";
            case availableFirst -> "Covered";
            case amount -> "Amount";
            case name -> "Name";
            case id -> "ID";
        };
    }

    private void renderHeader(
        SceneCanvas canvas,
        CirrusPalette palette,
        @Nullable Font font,
        int width,
        int mouseX,
        int mouseY
    ) {
        canvas.fill(0, 0, width, headerHeight, palette.dockSurface());
        canvas.fill(0, headerHeight - 1, width, 1, OreUiPaint.cardDivider(palette));
        int optX = sortButtonsX();
        int coverageReserve = 66;
        boolean hasPlan = cachedPlan != null;
        boolean bookmarked = JeiPlanBookmarkBridge.isPlanBookmarked(cachedPlan);
        int bookmarkW = hasPlan ? (width >= 420 ? 74 : 22) : 0;
        int bookmarkX = width - coverageReserve - bookmarkW - 4;

        int maxOptW = Math.max(46, (hasPlan ? bookmarkX : width - coverageReserve) - optX - 6);
        String fullLabel = (gridMode ? "Grid" : "List") + " \u00b7 " + sortShortLabel(sortKey)
                + (ascending ? " \u2191" : " \u2193");
        int optW = font == null ? Math.min(maxOptW, 108) : Math.min(maxOptW, Math.max(52, font.width(fullLabel) + 20));
        boolean optHover = ChromeText.hit(mouseX, mouseY, optX, 4, optW, sortButtonSize);
        if (optionsMenuOpen) {
            OreUiPaint.selectedToggle(canvas, optX, 4, optW, sortButtonSize, palette, optHover);
        } else {
            OreUiPaint.raisedButton(canvas, optX, 4, optW, sortButtonSize, palette, false, optHover, false, false);
        }
        int optInk = optionsMenuOpen
                ? palette.focusRing()
                : (optHover ? palette.textPrimary() : palette.textSecondary());
        if (font != null) {
            String fitted = ChromeText.fitText(fullLabel, Math.max(8, optW - 16), font::width, "...");
            canvas.text(fitted, optX + 5, ChromeText.centeredButtonY(4, sortButtonSize, canvas), optInk, false);
        }
        ChromeButtons.caretDown(canvas, optX + optW - 9, 4 + Math.max(3, (sortButtonSize - 6) / 2), optInk);
        hotspots.add(new Hotspot(optX, 4, optW, sortButtonSize, () -> {
            toggleOptionsMenu();
            CirrusUiSound.playClickSound();
        }));
        tipRegions.add(
            new TipRegion(
                optX,
                4,
                optW,
                sortButtonSize,
                () -> OreUiRichTooltip.builder("View & Sort Options")
                        .badge(gridMode ? "GRID" : "LIST", OreUiRichTooltip.Tone.accent)
                        .text(
                            "Switch between List/Grid view, sort order, and direction.",
                            OreUiRichTooltip.Tone.secondary
                        ).build()
            )
        );

        if (hasPlan && bookmarkW > 0 && bookmarkX > optX + optW + 4) {
            boolean bmHover = ChromeText.hit(mouseX, mouseY, bookmarkX, 4, bookmarkW, sortButtonSize);
            if (bookmarked) {
                OreUiPaint.selectedToggle(canvas, bookmarkX, 4, bookmarkW, sortButtonSize, palette, bmHover);
            } else {
                OreUiPaint.raisedButton(
                    canvas,
                    bookmarkX,
                    4,
                    bookmarkW,
                    sortButtonSize,
                    palette,
                    false,
                    bmHover,
                    false,
                    false
                );
            }
            int bmInk = bookmarked ? palette.focusRing() : (bmHover ? palette.textPrimary() : palette.textSecondary());
            String bmText = bookmarkW >= 54
                    ? (bookmarked ? "\u2605 Saved" : "\u2606 Bookmark")
                    : (bookmarked ? "\u2605" : "\u2606");
            if (font != null) {
                String fitted = ChromeText.fitText(bmText, Math.max(8, bookmarkW - 8), font::width, "...");
                int tx = bookmarkX + Math.max(4, (bookmarkW - font.width(fitted)) / 2);
                canvas.text(fitted, tx, ChromeText.centeredButtonY(4, sortButtonSize, canvas), bmInk, false);
            }
            hotspots.add(new Hotspot(bookmarkX, 4, bookmarkW, sortButtonSize, () -> {
                if (cachedPlan != null) {
                    String planKey = CirrusPlanBookmarkStore.planKeyOf(cachedPlan);
                    JeiPlanBookmarkBridge
                            .togglePlanBookmark(cachedPlan, model, CirrusPlanBookmarkStore.checkedSteps(planKey));
                    CirrusUiSound.playClickSound();
                }
            }));
            tipRegions.add(
                new TipRegion(
                    bookmarkX,
                    4,
                    bookmarkW,
                    sortButtonSize,
                    () -> OreUiRichTooltip.builder("Bookmark Flow to JEI")
                            .badge(
                                bookmarked ? "SAVED" : "JEI",
                                bookmarked ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.accent
                            )
                            .text(
                                "Saves this entire plan flow as a JEI bookmark with live material readiness.",
                                OreUiRichTooltip.Tone.secondary
                            ).footer("Click in JEI to execute or reopen in Calculator").build()
                )
            );
        }
    }

    private void renderOptionsPopup(
        SceneCanvas canvas,
        CirrusPalette palette,
        @Nullable Font font,
        int width,
        int height,
        int mouseX,
        int mouseY
    ) {
        popupHotspots.clear();
        popupW = Math.min(186, Math.max(132, width - 16));
        popupH = 112;
        popupX = Math.max(6, Math.min(width - popupW - 6, sortButtonsX()));
        popupY = Math.min(Math.max(4, height - popupH - 4), headerHeight + 2);

        canvas.shadow(popupX, popupY, popupW, popupH, 2f, 4f, 8f, palette.dropShadow());
        OreUiPaint.popover(canvas, popupX, popupY, popupW, popupH, palette);

        int pad = 7;
        int innerW = popupW - pad * 2;
        int y = popupY + pad;

        if (font != null) {
            canvas.text("Layout", popupX + pad, y, palette.textMuted(), false);
        }
        y += 10;
        int halfW = (innerW - 4) / 2;
        drawPopupToggle(
            canvas,
            palette,
            font,
            popupX + pad,
            y,
            halfW,
            16,
            "List",
            !gridMode,
            mouseX,
            mouseY,
            () -> setGridMode(false)
        );
        drawPopupToggle(
            canvas,
            palette,
            font,
            popupX + pad + halfW + 4,
            y,
            innerW - halfW - 4,
            16,
            "Grid",
            gridMode,
            mouseX,
            mouseY,
            () -> setGridMode(true)
        );

        y += 22;
        if (font != null) {
            canvas.text("Sort By", popupX + pad, y, palette.textMuted(), false);
        }
        y += 10;
        int colW = (innerW - 6) / 3;
        for (int i = 0; i < LedgerSorting.sortKeyCount; i++) {
            LedgerSortKey key = LedgerSorting.keyAt(i);
            int row = i / 3;
            int col = i % 3;
            int bx = popupX + pad + col * (colW + 3);
            int by = y + row * 19;
            int bw = col == 2 ? (innerW - (colW + 3) * 2) : colW;
            drawPopupToggle(
                canvas,
                palette,
                font,
                bx,
                by,
                bw,
                16,
                sortShortLabel(key),
                key == sortKey,
                mouseX,
                mouseY,
                () -> selectSortKey(key)
            );
        }

        y += 42;
        drawPopupToggle(
            canvas,
            palette,
            font,
            popupX + pad,
            y,
            halfW,
            16,
            "Asc \u2191",
            ascending,
            mouseX,
            mouseY,
            () -> {
                if (!ascending) {
                    toggleAscending();
                }
            }
        );
        drawPopupToggle(
            canvas,
            palette,
            font,
            popupX + pad + halfW + 4,
            y,
            innerW - halfW - 4,
            16,
            "Desc \u2193",
            !ascending,
            mouseX,
            mouseY,
            () -> {
                if (ascending) {
                    toggleAscending();
                }
            }
        );
    }

    private void drawPopupToggle(
        SceneCanvas canvas,
        CirrusPalette palette,
        @Nullable Font font,
        int x,
        int y,
        int w,
        int h,
        String label,
        boolean selected,
        int mouseX,
        int mouseY,
        Runnable action
    ) {
        boolean hov = ChromeText.hit(mouseX, mouseY, x, y, w, h);
        if (selected) {
            OreUiPaint.selectedToggle(canvas, x, y, w, h, palette, hov);
        } else {
            OreUiPaint.raisedButton(canvas, x, y, w, h, palette, false, hov, false, false);
        }
        if (font != null) {
            String fitted = ChromeText.fitText(label, Math.max(8, w - 6), font::width, "...");
            int tx = x + Math.max(3, (w - font.width(fitted)) / 2);
            int ty = selected ? ChromeText.centeredY(y, h, canvas) : ChromeText.centeredButtonY(y, h, canvas);
            canvas.text(
                fitted,
                tx,
                ty,
                selected ? palette.focusRing() : (hov ? palette.textPrimary() : palette.textSecondary()),
                false
            );
        }
        popupHotspots.add(new Hotspot(x, y, w, h, action));
    }

    private void renderCoverageLabel(SceneCanvas canvas, CirrusPalette palette, @Nullable Font font, int width) {
        if (font == null) {
            return;
        }
        String coverage = WorkbenchFormatters.formatPercent(LedgerSorting.coverage(model));
        int ringX = width - 8 - 22;
        canvas.text(
            coverage,
            ringX - font.width(coverage) - 4,
            ChromeText.centeredY(0, headerHeight, canvas),
            palette.textPrimary(),
            false
        );
    }

    private void renderTabs(
        SceneCanvas canvas,
        CirrusPalette palette,
        @Nullable Font font,
        Card card,
        int mouseX,
        int mouseY
    ) {
        int tabWidth = Math.max(40, card.width() / LedgerSorting.sectionCount);
        for (int section = 0; section < LedgerSorting.sectionCount; section++) {
            int x = card.x() + section * tabWidth;
            int y = card.y() - tabHeight - 2;
            boolean selected = section == activeSection;
            boolean hovered = ChromeText.hit(mouseX, mouseY, x, y, tabWidth - 2, tabHeight);
            if (selected) {
                OreUiPaint.raisedCard(canvas, x, y, tabWidth - 2, tabHeight, palette, hovered, true);
            } else {
                OreUiPaint.raisedButton(canvas, x, y, tabWidth - 2, tabHeight, palette, false, hovered, false, false);
            }
            if (font != null) {
                String baseLabel = sectionTitle(section).get().getString();
                int count = sectionCount(section);
                String label = count > 0 ? baseLabel + " (" + count + ")" : baseLabel;
                canvas.text(
                    ChromeText.fitText(label, tabWidth - 10, font::width, "..."),
                    x + 5,
                    ChromeText.centeredY(y, tabHeight - 1, canvas),
                    selected ? palette.textPrimary() : palette.textSecondary(),
                    false
                );
            }
            int targetSection = section;
            hotspots.add(new Hotspot(x, y, tabWidth - 2, tabHeight, () -> selectSection(targetSection)));
        }
    }

    private void renderSection(
        SceneCanvas canvas,
        CirrusPalette palette,
        @Nullable Font font,
        Card card,
        int section,
        int mouseX,
        int mouseY
    ) {
        if (card.width() <= 0 || card.height() <= 0) {
            return;
        }
        OreUiPaint.raisedCard(canvas, card.x(), card.y(), card.width(), card.height(), palette, false, false);
        boolean asTabs = LedgerSorting.sectionsAsTabs(classify());
        int headerOffset = sectionContentOffset();
        if (!asTabs) {
            OreUiPaint.headerBand(canvas, card.x(), card.y(), card.width(), titleHeight - 2, palette, false);
            if (font != null) {
                canvas.text(
                    sectionTitle(section).get(),
                    card.x() + 7,
                    ChromeText.centeredY(card.y() + 1, titleHeight - 2, canvas),
                    palette.textPrimary(),
                    false
                );
                int count = sectionCount(section);
                Component countBadge = Component.literal(Integer.toString(count));
                int pillW = font.width(countBadge) + 8;
                OreUiPaint.tagPill(
                    canvas,
                    card.x() + card.width() - pillW - 5,
                    card.y() + 3,
                    11,
                    countBadge,
                    sectionAccent(section, palette),
                    palette
                );
            }
        }
        int clipX = card.x() + 2;
        int clipY = card.y() + headerOffset;
        int clipH = Math.max(0, card.height() - headerOffset - 3);
        canvas.withClip(clipX, clipY, Math.max(0, card.width() - 4), clipH, () -> {
            if (cachedPlan == null) {
                renderEmptyHint(canvas, palette, card, headerOffset, CirrusLangs.Workbench.Flow.bannerNoTarget.get());
                return;
            }
            switch (section) {
                case sectionAcquire -> renderAcquireRows(canvas, palette, font, card, mouseX, mouseY);
                case sectionStock -> renderStockRows(canvas, palette, font, card);
                case sectionMachines -> renderMachineRows(canvas, palette, font, card, mouseX, mouseY);
                default -> renderSurplusRows(canvas, palette, font, card, mouseX, mouseY);
            }
        });
    }

    private int sectionCount(int section) {
        if (model == null) {
            return 0;
        }
        return switch (section) {
            case sectionAcquire -> model.acquireRows().size();
            case sectionStock -> model.stockConsumedRows().size();
            case sectionMachines -> model.machinesToProvision().size();
            default -> model.surplusByproducts().size();
        };
    }

    private void renderEmptyHint(
        SceneCanvas canvas,
        CirrusPalette palette,
        Card card,
        int headerOffset,
        Component message
    ) {
        int bodyY = card.y() + headerOffset;
        int bodyH = Math.max(1, card.height() - headerOffset - 4);
        canvas.drawCenteredString(
            message,
            card.x() + card.width() / 2,
            ChromeText.centeredY(bodyY, bodyH, canvas),
            palette.textMuted()
        );
    }

    private int sectionAccent(int section, CirrusPalette palette) {
        return switch (section) {
            case sectionAcquire -> palette.acquireStep().accent();
            case sectionStock -> palette.costP();
            case sectionMachines -> palette.provisionMachineStep().accent();
            default -> palette.costS();
        };
    }

    private void renderAcquireRows(
        SceneCanvas canvas,
        CirrusPalette palette,
        @Nullable Font font,
        Card card,
        int mouseX,
        int mouseY
    ) {
        if (model == null) {
            return;
        }
        if (gridMode) {
            renderAcquireGrid(canvas, palette, font, card, mouseX, mouseY);
            return;
        }
        List<AcquireRow> rows = model.acquireRows();
        int topOffset = sectionContentOffset();
        if (rows.isEmpty()) {
            renderEmptyHint(canvas, palette, card, topOffset, CirrusLangs.Workbench.Ledger.emptySection.get());
            return;
        }
        int clipY = card.y() + topOffset;
        int clipH = Math.max(1, card.height() - topOffset - 4);
        float maxScroll = maxScrollPxFor(sectionAcquire);
        float scrollPx = currentScrollPx(sectionAcquire);
        int scrollOff = Math.round(scrollPx);
        InventorySnapshot snapshot = simulatedInventory();
        int barReserve = maxScroll > 0.5f ? 6 : 0;
        canvas.withClip(card.x() + 2, clipY, Math.max(1, card.width() - 4), clipH, () -> {
            for (int index = 0; index < rows.size(); index++) {
                AcquireRow row = rows.get(index);
                int rx = card.x() + 5;
                int ry = clipY + 3 + index * acquireRowHeight - scrollOff;
                if (ry + acquireRowHeight < clipY || ry > clipY + clipH) {
                    continue;
                }
                int rw = Math.max(16, card.width() - 10 - barReserve);
                int barH = 7;
                int cardH = acquireRowHeight - barH - 5;
                boolean rowHover = ChromeText.hit(mouseX, mouseY, rx, ry, rw, cardH + barH)
                        && mouseY >= clipY
                        && mouseY <= clipY + clipH;
                OreUiPaint.raisedCard(canvas, rx, ry, rw, cardH, palette, rowHover, false);
                int slotX = rx + 6;
                int slotY = ry + Math.max(3, (cardH - 2 - slotSize) / 2);
                renderSlot(canvas, palette, row.resource(), slotX, slotY);
                hotspots.add(
                    new Hotspot(
                        slotX,
                        slotY,
                        slotSize,
                        slotSize,
                        () -> JeiPlanBookmarkBridge.showRecipesFor(row.resource())
                    )
                );

                long needed = Math.max(1L, row.neededAmount());
                long have = LedgerSorting.effectiveAvailable(row, snapshot);
                long missing = LedgerSorting.effectiveShortfall(row, snapshot);
                double ratio = Math.max(0.0, Math.min(1.0, (double) have / (double) needed));

                int rowRight = rx + rw - 5;
                int freeX = rowRight - microButtonWidth;
                int haveAllX = freeX - microButtonWidth - 3;
                int locateX = haveAllX - microButtonWidth - 3;
                int buttonY = ry + 4;
                renderMicroButton(
                    canvas,
                    palette,
                    font,
                    freeX,
                    buttonY,
                    CirrusLangs.Workbench.Action.treatFree.get().getString(),
                    mouseX,
                    mouseY,
                    false,
                    () -> declareFreeSource(row.resource())
                );
                renderMicroButton(
                    canvas,
                    palette,
                    font,
                    haveAllX,
                    buttonY,
                    CirrusLangs.Workbench.Ledger.haveAll.get().getString(),
                    mouseX,
                    mouseY,
                    missing > 0L,
                    () -> simulateStock(row.resource(), row.neededAmount())
                );
                if (!row.stepIds().isEmpty() && locateX > rx + slotSize + 96) {
                    String stepId = row.stepIds().get(0);
                    renderMicroButton(
                        canvas,
                        palette,
                        font,
                        locateX,
                        buttonY,
                        CirrusLangs.Workbench.Action.locateDag.get().getString(),
                        mouseX,
                        mouseY,
                        false,
                        () -> focusStep(stepId)
                    );
                }

                int textX = rx + slotSize + 12;
                int titleRight = Math.max(textX + 24, haveAllX - 6);
                if (font != null) {
                    String displayName = CoPilotPaint.resourceName(row.resource()).getString();
                    canvas.text(
                        ChromeText.fitText(displayName, Math.max(8, titleRight - textX), font::width, "..."),
                        textX,
                        ry + 5,
                        palette.textPrimary(),
                        false
                    );
                    String statusLine = formatAcquireStatus(row.neededAmount(), have, missing);
                    int statusMaxW = Math.max(8, rowRight - sliderWidth - 8 - textX);
                    canvas.text(
                        ChromeText.fitText(statusLine, statusMaxW, font::width, "..."),
                        textX,
                        ry + 17,
                        missing > 0L ? palette.costH() : palette.costS(),
                        false
                    );
                }
                drawOuterLedgerProgressBar(
                    canvas,
                    rx,
                    ry + cardH,
                    rw,
                    barH,
                    ratio,
                    missing > 0L ? palette.costH() : palette.costS(),
                    palette
                );
                tipRegions.add(
                    new TipRegion(rx, ry, rw, cardH + barH, () -> acquireRowTooltip(row, have, missing, ratio))
                );
            }
        });
        drawScrollbar(canvas, palette, card.x() + card.width() - 5, clipY + 2, clipH - 4, scrollPx, maxScroll);
    }

    private void drawOuterLedgerProgressBar(
        SceneCanvas canvas,
        int x,
        int y,
        int width,
        int height,
        double ratio,
        int fillColor,
        CirrusPalette palette
    ) {
        OreUiPaint.flatMeter(canvas, x, y, width, height, ratio, fillColor, palette);
        // Subtle quarter-step graduations so the bar always reads as a progress meter even at 100%
        int tickColor = OreUiPaint.withAlpha(OreUiPaint.outerInk(palette), 0.35f);
        for (int step = 1; step <= 3; step++) {
            int tx = x + Math.round((width - 2) * (step / 4.0f)) + 1;
            canvas.fill(tx, y + 1, 1, Math.max(1, height - 2), tickColor);
        }
    }

    private String formatAcquireStatus(long need, long have, long missing) {
        String needStr = CirrusLangs.Workbench.Ledger.need.get(WorkbenchFormatters.formatAmount(need)).getString();
        String haveStr = CirrusLangs.Workbench.Ledger.have.get(WorkbenchFormatters.formatAmount(have)).getString();
        String tail = missing > 0L
                ? CirrusLangs.Workbench.Ledger.missing.get(WorkbenchFormatters.formatAmount(missing)).getString()
                : CirrusLangs.Workbench.Ledger.done.get().getString();
        return needStr + " · " + haveStr + " · " + tail;
    }

    private void renderAcquireGrid(
        SceneCanvas canvas,
        CirrusPalette palette,
        @Nullable Font font,
        Card card,
        int mouseX,
        int mouseY
    ) {
        if (model == null) {
            return;
        }
        List<AcquireRow> rows = model.acquireRows();
        int topOffset = sectionContentOffset();
        if (rows.isEmpty()) {
            renderEmptyHint(canvas, palette, card, topOffset, CirrusLangs.Workbench.Ledger.emptySection.get());
            return;
        }
        int clipY = card.y() + topOffset;
        int clipH = Math.max(1, card.height() - topOffset - 4);
        float maxScroll = maxScrollPxFor(sectionAcquire);
        float scrollPx = currentScrollPx(sectionAcquire);
        int scrollOff = Math.round(scrollPx);
        int barReserve = maxScroll > 0.5f ? 6 : 0;
        int cellWidth = Math.max(76, (card.width() - 12 - barReserve) / gridColumns);
        InventorySnapshot snapshot = simulatedInventory();
        canvas.withClip(card.x() + 2, clipY, Math.max(1, card.width() - 4), clipH, () -> {
            for (int index = 0; index < rows.size(); index++) {
                AcquireRow row = rows.get(index);
                int column = index % gridColumns;
                int line = index / gridColumns;
                int x = card.x() + 5 + column * cellWidth;
                int y = clipY + 3 + line * acquireRowHeight - scrollOff;
                if (y + acquireRowHeight < clipY || y > clipY + clipH) {
                    continue;
                }
                int w = Math.max(20, cellWidth - 6);
                int barH = 7;
                int cardH = acquireRowHeight - barH - 5;
                boolean hovered = ChromeText.hit(mouseX, mouseY, x, y, w, cardH + barH)
                        && mouseY >= clipY
                        && mouseY <= clipY + clipH;
                OreUiPaint.raisedCard(canvas, x, y, w, cardH, palette, hovered, false);
                int slotX = x + 6;
                int slotY = y + Math.max(3, (cardH - 2 - slotSize) / 2);
                renderSlot(canvas, palette, row.resource(), slotX, slotY);
                hotspots.add(
                    new Hotspot(
                        slotX,
                        slotY,
                        slotSize,
                        slotSize,
                        () -> JeiPlanBookmarkBridge.showRecipesFor(row.resource())
                    )
                );

                long needed = Math.max(1L, row.neededAmount());
                long have = LedgerSorting.effectiveAvailable(row, snapshot);
                long missing = LedgerSorting.effectiveShortfall(row, snapshot);
                double ratio = Math.max(0.0, Math.min(1.0, (double) have / (double) needed));
                int textX = x + slotSize + 11;
                int pctPillW = 0;
                if (font != null) {
                    Component pct = Component.literal(Math.round(ratio * 100.0) + "%");
                    pctPillW = font.width(pct) + 10;
                    OreUiPaint.tagPill(
                        canvas,
                        x + w - pctPillW - 5,
                        y + 4,
                        13,
                        pct,
                        missing > 0L ? palette.costH() : palette.costS(),
                        palette
                    );
                }
                int titleW = Math.max(8, w - (slotSize + 20) - pctPillW);
                int textW = Math.max(8, w - (slotSize + 16));
                if (font != null) {
                    String displayName = CoPilotPaint.resourceName(row.resource()).getString();
                    canvas.text(
                        ChromeText.fitText(displayName, titleW, font::width, "..."),
                        textX,
                        y + 6,
                        palette.textPrimary(),
                        false
                    );
                    String status = missing > 0L
                            ? CirrusLangs.Workbench.Ledger.missing.get(WorkbenchFormatters.formatAmount(missing))
                                    .getString()
                            : CirrusLangs.Workbench.Ledger.done.get().getString();
                    String needStr = CirrusLangs.Workbench.Ledger.need.get(WorkbenchFormatters.formatAmount(needed))
                            .getString();
                    canvas.text(
                        ChromeText.fitText(needStr + " \u00b7 " + status, textW, font::width, "..."),
                        textX,
                        y + 18,
                        missing > 0L ? palette.costH() : palette.costS(),
                        false
                    );
                }
                drawOuterLedgerProgressBar(
                    canvas,
                    x,
                    y + cardH,
                    w,
                    barH,
                    ratio,
                    missing > 0L ? palette.costH() : palette.costS(),
                    palette
                );
                if (!row.stepIds().isEmpty()) {
                    String stepId = row.stepIds().get(0);
                    hotspots.add(
                        new Hotspot(
                            x + slotSize + 8,
                            y,
                            Math.max(8, w - slotSize - 8),
                            cardH + barH,
                            () -> focusStep(stepId)
                        )
                    );
                }
                tipRegions
                        .add(new TipRegion(x, y, w, cardH + barH, () -> acquireRowTooltip(row, have, missing, ratio)));
            }
        });
        drawScrollbar(canvas, palette, card.x() + card.width() - 5, clipY + 2, clipH - 4, scrollPx, maxScroll);
    }

    private OreUiRichTooltip acquireRowTooltip(AcquireRow row, long have, long missing, double ratio) {
        Component name = CoPilotPaint.resourceName(row.resource());
        return OreUiRichTooltip.builder(name).icon(row.resource())
                .badge(
                    missing > 0L ? "MISSING " + WorkbenchFormatters.formatAmount(missing) : "READY",
                    missing > 0L ? OreUiRichTooltip.Tone.costH : OreUiRichTooltip.Tone.positive
                )
                .meter(
                    "Stock Progress",
                    ratio,
                    WorkbenchFormatters.formatAmount(have) + " / "
                            + WorkbenchFormatters.formatAmount(row.neededAmount()) + " (" + Math.round(ratio * 100.0)
                            + "%)",
                    missing > 0L ? OreUiRichTooltip.Tone.costH : OreUiRichTooltip.Tone.positive
                )
                .keyValue(
                    "Used In",
                    row.stepIds().size() + (row.stepIds().size() == 1 ? " step" : " steps"),
                    OreUiRichTooltip.Tone.secondary
                ).footer("Click card to locate step on DAG canvas").build();
    }

    private void renderStockRows(SceneCanvas canvas, CirrusPalette palette, @Nullable Font font, Card card) {
        if (model == null) {
            return;
        }
        List<AcquireRow> rows = model.stockConsumedRows();
        int topOffset = sectionContentOffset();
        if (rows.isEmpty()) {
            renderEmptyHint(canvas, palette, card, topOffset, CirrusLangs.Workbench.Ledger.emptySection.get());
            return;
        }
        int visible = visibleRowCount(sectionStock, card.height());
        InventorySnapshot snapshot = simulatedInventory();
        var theme = CirrusUiTheme.mode(this);
        if (font != null) {
            int headerY = card.y() + topOffset + 1;
            drawStockColumn(canvas, palette, font, card, headerY, stockAvailableKey, palette.textMuted());
            drawStockColumn(canvas, palette, font, card, headerY, stockConsumedKey, palette.textMuted());
            drawStockColumn(canvas, palette, font, card, headerY, stockReservedKey, palette.textMuted());
        }
        for (int index = 0; index < Math.min(visible, rows.size()); index++) {
            AcquireRow row = rows.get(index);
            int y = card.y() + topOffset + stockHeaderHeight + index * compactRowHeight;
            OreUiPaint.raisedCard(
                canvas,
                card.x() + 4,
                y + 1,
                Math.max(12, card.width() - 8),
                compactRowHeight - 2,
                palette,
                false,
                false
            );
            long available = LedgerSorting.effectiveAvailable(row, snapshot);
            long missing = LedgerSorting.effectiveShortfall(row, snapshot);
            int badgeX = card.x() + 8;
            int badgeY = y + (compactRowHeight - 6) / 2;
            if (missing > 0L) {
                int missInk = palette.ruleHardBan();
                canvas.fill(badgeX, badgeY + 2, 6, 2, missInk);
            } else {
                var okIcon = CirrusThemeIcons.resolve(CirrusTextures.Icon.finished, theme);
                canvas.texture(okIcon, badgeX - 1, badgeY - 1, 8, 8);
            }
            if (font != null) {
                int textY = ChromeText.centeredY(y + 1, compactRowHeight - 2, canvas);
                String displayName = CoPilotPaint.resourceName(row.resource()).getString();
                canvas.text(
                    ChromeText.fitText(
                        displayName,
                        Math.max(0, card.width() - stockColumnWidth * 3 - stockMeterWidth - 30),
                        font::width,
                        "..."
                    ),
                    card.x() + 18,
                    textY,
                    missing > 0L ? palette.costH() : palette.textPrimary(),
                    false
                );
                drawStockValue(
                    canvas,
                    palette,
                    font,
                    card,
                    textY,
                    0,
                    WorkbenchFormatters.formatAmount(available),
                    missing > 0L ? palette.costH() : palette.costS()
                );
                drawStockValue(
                    canvas,
                    palette,
                    font,
                    card,
                    textY,
                    1,
                    WorkbenchFormatters.formatAmount(row.neededAmount()),
                    palette.textSecondary()
                );
                drawStockValue(
                    canvas,
                    palette,
                    font,
                    card,
                    textY,
                    2,
                    WorkbenchFormatters.formatAmount(row.reservedStock()),
                    palette.textMuted()
                );
            }
        }
        renderOverflow(canvas, palette, font, rows.size() - visible, card);
    }

    private void drawStockColumn(
        SceneCanvas canvas,
        CirrusPalette palette,
        Font font,
        Card card,
        int y,
        String key,
        int color
    ) {
        String label = LangEntry.of(key).get().getString();
        int index = key.equals(stockAvailableKey) ? 0 : key.equals(stockConsumedKey) ? 1 : 2;
        int right = stockColumnRight(card, index);
        canvas.text(
            ChromeText.fitText(label, stockColumnWidth, font::width, ""),
            Math.max(card.x() + 4, right - Math.min(font.width(label), stockColumnWidth)),
            y,
            color,
            false
        );
    }

    private void drawStockValue(
        SceneCanvas canvas,
        CirrusPalette palette,
        Font font,
        Card card,
        int y,
        int column,
        String value,
        int color
    ) {
        int right = stockColumnRight(card, column);
        canvas.text(value, Math.max(card.x() + 4, right - font.width(value)), y, color, false);
    }

    private int stockColumnRight(Card card, int column) {
        int base = card.x() + card.width() - 10;
        return base - column * stockColumnWidth;
    }

    private void renderMachineRows(
        SceneCanvas canvas,
        CirrusPalette palette,
        @Nullable Font font,
        Card card,
        int mouseX,
        int mouseY
    ) {
        if (model == null) {
            return;
        }
        var theme = CirrusUiTheme.mode(this);
        List<MachinePrepRow> rows = model.machinesToProvision();
        int topOffset = sectionContentOffset();
        if (rows.isEmpty() && model.automatedLinesUsed().isEmpty() && model.catalystsRequired().isEmpty()) {
            renderEmptyHint(canvas, palette, card, topOffset, CirrusLangs.Workbench.Ledger.emptySection.get());
            return;
        }
        int visible = visibleRowCount(sectionMachines, card.height());
        for (int index = 0; index < Math.min(visible, rows.size()); index++) {
            MachinePrepRow row = rows.get(index);
            int rx = card.x() + 4;
            int ry = card.y() + topOffset + 2 + index * compactRowHeight;
            int rw = Math.max(16, card.width() - 8);
            int rh = compactRowHeight - 2;
            boolean rowHover = ChromeText.hit(mouseX, mouseY, rx, ry, rw, rh);
            OreUiPaint.raisedCard(canvas, rx, ry, rw, rh, palette, rowHover, false);
            renderSlot(canvas, palette, ResourceKey.item(row.machineId()), rx + 4, ry + Math.max(1, (rh - 18) / 2), 18);
            int rowRight = rx + rw - 4;
            int ownX = rowRight - microButtonWidth;
            int rejectX = ownX - microButtonWidth - 3;
            int buttonY = ry + Math.max(2, (rh - microButtonHeight) / 2);
            renderMicroButton(
                canvas,
                palette,
                font,
                ownX,
                buttonY,
                CirrusLangs.Workbench.Action.declareOwned.get().getString(),
                mouseX,
                mouseY,
                true,
                () -> declareOwned(row.machineId())
            );
            renderMicroButton(
                canvas,
                palette,
                font,
                rejectX,
                buttonY,
                CirrusLangs.Planning.Prompt.MachineFork.optionRejectBuild.get().getString(),
                mouseX,
                mouseY,
                false,
                () -> rejectMachine(row.machineId())
            );
            if (font != null) {
                String name = StepNodeCardRenderer.machineNameOf(row.machineId(), null).getString();
                String status = CirrusLangs.Workbench.Ledger.needBuildStatus.get(row.countToBuild()).getString();
                String label = name + " · " + status;
                canvas.text(
                    ChromeText.fitText(label, Math.max(8, rejectX - (rx + 28)), font::width, "..."),
                    rx + 26,
                    ChromeText.centeredY(ry, rh, canvas),
                    palette.textPrimary(),
                    false
                );
            }
        }
        int baseY = card.y() + topOffset + 4 + Math.min(visible, rows.size()) * compactRowHeight;
        if (font != null && !model.automatedLinesUsed().isEmpty()) {
            String lines = String.join(", ", model.automatedLinesUsed());
            canvas.text(
                ChromeText.fitText(lines, Math.max(0, card.width() - 12), font::width, "..."),
                card.x() + 6,
                baseY + 2,
                palette.textMuted(),
                false
            );
            baseY += 12;
        }
        int iconX = card.x() + 6;
        int catColor = palette.edgeCatalyst();
        for (ResourceKey catalyst : model.catalystsRequired()) {
            if (iconX + 54 > card.x() + card.width()) {
                break;
            }
            float cx = iconX + 3f;
            float cy = baseY + 6f;
            canvas.coloredQuad(cx, cy - 2.6f, cx + 2.6f, cy, cx, cy + 2.6f, cx - 2.6f, cy, catColor, catColor, catColor, catColor);
            String label = CoPilotPaint.resourceName(catalyst).getString();
            if (font != null) {
                canvas.text(
                    ChromeText.fitText(label, 44, font::width, "..."),
                    iconX + 8,
                    baseY + 1,
                    palette.textSecondary(),
                    false
                );
            }
            iconX += 56;
        }
    }

    private void renderSurplusRows(
        SceneCanvas canvas,
        CirrusPalette palette,
        @Nullable Font font,
        Card card,
        int mouseX,
        int mouseY
    ) {
        if (model == null) {
            return;
        }
        List<ByproductEntry> rows = model.surplusByproducts();
        int topOffset = sectionContentOffset();
        if (rows.isEmpty()) {
            renderEmptyHint(canvas, palette, card, topOffset, CirrusLangs.Workbench.Ledger.emptySection.get());
            return;
        }
        int visible = visibleRowCount(sectionSurplus, card.height());
        for (int index = 0; index < Math.min(visible, rows.size()); index++) {
            ByproductEntry entry = rows.get(index);
            int rx = card.x() + 4;
            int ry = card.y() + topOffset + 2 + index * compactRowHeight;
            int rw = Math.max(16, card.width() - 8);
            int rh = compactRowHeight - 2;
            boolean hovered = ChromeText.hit(mouseX, mouseY, rx, ry, rw, rh);
            OreUiPaint.raisedCard(canvas, rx, ry, rw, rh, palette, hovered, false);
            renderSlot(canvas, palette, entry.resource(), rx + 4, ry + Math.max(1, (rh - 18) / 2), 18);
            if (font != null) {
                Component qty = Component.literal("+" + WorkbenchFormatters.formatAmount(entry.amount()));
                int pillW = font.width(qty) + 8;
                OreUiPaint.tagPill(
                    canvas,
                    rx + rw - pillW - 5,
                    ry + Math.max(2, (rh - 11) / 2),
                    11,
                    qty,
                    palette.costS(),
                    palette
                );
                String name = CoPilotPaint.resourceName(entry.resource()).getString();
                canvas.text(
                    ChromeText.fitText(name, Math.max(8, rw - pillW - 36), font::width, "..."),
                    rx + 26,
                    ChromeText.centeredY(ry, rh, canvas),
                    palette.textPrimary(),
                    false
                );
            }
            hotspots.add(new Hotspot(rx, ry, rw, rh, () -> focusStep(entry.producedByStepId())));
        }
        renderOverflow(canvas, palette, font, rows.size() - visible, card);
    }

    private void renderOverflow(SceneCanvas canvas, CirrusPalette palette, @Nullable Font font, int hidden, Card card) {
        if (hidden <= 0 || font == null) {
            return;
        }
        String hint = "+" + hidden;
        canvas.text(
            hint,
            card.x() + card.width() - 6 - font.width(hint),
            card.y() + card.height() - 10,
            palette.textMuted(),
            false
        );
    }

    private void renderSlot(SceneCanvas canvas, CirrusPalette palette, @Nullable ResourceKey resource, int x, int y) {
        renderSlot(canvas, palette, resource, x, y, slotSize);
    }

    private void renderSlot(
        SceneCanvas canvas,
        CirrusPalette palette,
        @Nullable ResourceKey resource,
        int x,
        int y,
        int size
    ) {
        CoPilotPaint.renderSlotItem(
            canvas,
            resource == null ? null : CoPilotPaint.stackOf(resource, 1L),
            x,
            y,
            size,
            palette
        );
    }

    private void renderMicroButton(
        SceneCanvas canvas,
        CirrusPalette palette,
        @Nullable Font font,
        int x,
        int y,
        String label,
        int mouseX,
        int mouseY,
        boolean primary,
        Runnable action
    ) {
        boolean hovered = ChromeText.hit(mouseX, mouseY, x, y, microButtonWidth, microButtonHeight);
        OreUiPaint.raisedButton(
            canvas,
            x,
            y,
            microButtonWidth,
            microButtonHeight,
            palette,
            false,
            hovered,
            primary,
            false
        );
        if (font != null) {
            String fitted = ChromeText.fitText(label, microButtonWidth - 6, font::width, "");
            int textW = font.width(fitted);
            canvas.text(
                fitted,
                x + Math.max(2, (microButtonWidth - textW) / 2),
                ChromeText.centeredY(y, microButtonHeight - 1, canvas),
                primary ? palette.textOnAccent() : palette.textPrimary(),
                false
            );
        }
        hotspots.add(new Hotspot(x, y, microButtonWidth, microButtonHeight, action));
    }

    private String amountText(long amount) {
        long groups = LedgerSorting.groupCount(amount);
        long remainder = LedgerSorting.groupRemainder(amount);
        if (groups <= 0L) {
            return WorkbenchFormatters.formatAmount(amount);
        }
        String grouped = CirrusLangs.Workbench.Ledger.stackBreakdown.get(String.valueOf(groups)).getString();
        return grouped + " " + remainder;
    }

    private ImageTexture sortIcon(LedgerSortKey key, boolean selected) {
        return switch (key) {
            case missingFirst -> selected
                    ? CirrusTextures.Icon.Calc.Sort.missingFirstPressed
                    : CirrusTextures.Icon.Calc.Sort.missingFirst;
            case availableFirst -> selected
                    ? CirrusTextures.Icon.Calc.Sort.availableFirstPressed
                    : CirrusTextures.Icon.Calc.Sort.availableFirst;
            case amount ->
                selected ? CirrusTextures.Icon.Calc.Sort.numberPressed : CirrusTextures.Icon.Calc.Sort.number;
            case name -> selected ? CirrusTextures.Icon.Calc.Sort.namePressed : CirrusTextures.Icon.Calc.Sort.name;
            case id -> selected ? CirrusTextures.Icon.Calc.Sort.idPressed : CirrusTextures.Icon.Calc.Sort.id;
        };
    }

    private LangEntry sectionTitle(int section) {
        return switch (section) {
            case sectionAcquire -> CirrusLangs.Workbench.Ledger.acquire;
            case sectionStock -> CirrusLangs.Workbench.Ledger.stockReserved;
            case sectionMachines -> CirrusLangs.Workbench.Ledger.machinesLines;
            default -> CirrusLangs.Workbench.Ledger.surplus;
        };
    }

    private static @Nullable Font fontOrNull(SceneCanvas canvas) {
        try {
            return canvas.font();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    //endregion
}
