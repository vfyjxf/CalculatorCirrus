package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.human.PinResourceRecipeFeedback;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusThemeIcons;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cirrus.ui.workbench.flow.StepNodeCardRenderer;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiRecipeGrid;
import dev.vfyjxf.cirrus.ui.workbench.state.CommandPaletteEngine;
import dev.vfyjxf.cirrus.ui.workbench.state.PaletteAction;
import dev.vfyjxf.cirrus.ui.workbench.state.PaletteItem;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.ui.widget.TextFieldWidget;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexNoWrap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetLeft;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetTop;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Z5 project basket popover: target-row count editing, x16/x64 nudges, search append and a 180ms debounced replan.
 */
public class TargetBasketPopoverWidget extends WidgetGroup<Widget> implements OreUiTooltipProvider {
    public static final float width = 300f;
    public static final float height = 280f;
    public static final int pad = 6;
    public static final int headerTop = 3;
    public static final int headerHeight = 14;
    public static final int rowTop = 20;
    public static final int rowHeight = 20;
    public static final int rowIconSize = 16;
    public static final int maxRows = 8;
    public static final int nameLeft = pad + rowIconSize + 6;
    public static final int numberWidth = 42;
    public static final int microWidth = 24;
    public static final int deleteWidth = 14;
    public static final int searchTop = 180;
    public static final int searchHeight = 16;
    public static final int resultTop = 198;
    public static final int resultRowHeight = 16;
    public static final int resultLimit = 3;
    public static final int hintTop = 247;
    public static final int hintHeight = 10;
    public static final int footerTop = 260;
    public static final int footerHeight = 16;
    public static final int footerWidth = 108;
    public static final int closeSize = 8;
    public static final long debounceMillis = 180L;
    public static final long outsideClickGraceMillis = 120L;
    public static final long microFactorSixteen = 16L;
    public static final long microFactorSixtyFour = 64L;

    private final WorkbenchShellContext shellContext;
    private final TextFieldWidget search;
    private final NumberInputWidget[] amountInputs = new NumberInputWidget[maxRows];
    private final AtomicBoolean pendingRefresh = new AtomicBoolean(false);
    private final Runnable contextListener = this::scheduleRefresh;

    private List<TargetDemand> draft = List.of();
    private List<PaletteItem> searchResults = List.of();
    private boolean open;
    private boolean dirty;
    private boolean refreshing;
    private long clockOffsetMillis;
    private long pendingReplanAtMillis = -1L;
    private long shownAtMillis;
    private @Nullable Boolean reducedMotionOverride;
    private int inspectedRow;
    private int recipeFilter;
    private int pickerScroll;
    private int searchScroll;
    private final List<Rect> wellFilterRects = new ArrayList<>();
    private final List<RecipePinHit> wellPinHits = new ArrayList<>();
    private final List<PickerCardHit> pickerCardHits = new ArrayList<>();
    private @Nullable Rect wellCompareRect;

    private record RecipePinHit(Rect cardRect, Rect pinRect, ComponentStackKey target, CompiledRecipe recipe) {}

    private record PickerCardHit(Rect cardRect, ComponentStackKey resource) {}

    public TargetBasketPopoverWidget(WorkbenchShellContext context) {
        this.shellContext = Objects.requireNonNull(context, "context");
        addStyleClass("workbench-popover");
        defaultStyle(UIStyle.of(background(CirrusTextures.Workbench.cardSurface(context.theme()))));
        useStyle(positionAbsolute(), sizeOf(width, height), flexColumn(), flexNoWrap());
        setInteractive(true);
        setVisible(false);
        setTickable(true);
        setClickGroup(this);
        onTick(this::pollDebounce);
        onClickOutside(ignored -> dismissOnOutsideClick());

        search = TextFieldWidget.create().setMaxLength(64)
                .setPlaceholder(CirrusLangs.Workbench.Topbar.addTarget.string()).onTextChanged(this::setSearchQuery)
                .onEnterPressed(text -> addSearchResult(0));
        search.onMouseClicked((input, clickCtx) -> {
            if (!input.isRightClick()) {
                return EventDispatch.pass;
            }
            if (!search.text().isEmpty()) {
                search.setText("");
                setSearchQuery("");
                CirrusUiSound.playClickSound();
            }
            clickCtx.consume();
            return EventDispatch.consumed;
        });
        search.useStyle(
            positionAbsolute(),
            insetLeft(pad),
            insetTop(searchTop),
            sizeOf(width - pad * 2f, searchHeight)
        );
        addWidget(search);

        for (int row = 0; row < maxRows; row++) {
            int index = row;
            NumberInputWidget input = NumberInputWidget.create(1L).setMinValue(1L).setScrollStep(1L)
                    .onValueChanged(value -> onRowAmountChanged(index, value));
            input.useStyle(
                positionAbsolute(),
                insetLeft(numberLeft()),
                insetTop(rowTop(index) + 2),
                sizeOf(numberWidth, rowHeight - 5f)
            );
            input.setVisible(false);
            amountInputs[index] = input;
            addWidget(input);
        }

        onMount((scene, sceneContext, handle) -> {
            shellContext.addListener(contextListener);
            pendingRefresh.set(false);
            refresh();
        });
        onUnmount(() -> {
            shellContext.removeListener(contextListener);
            pendingRefresh.set(false);
        });
        onMouseClicked((input, clickContext) -> {
            if (!open) {
                return EventDispatch.pass;
            }
            clickContext.consume();
            return EventDispatch.consumed;
        });
        onMouseClick((input, clickCount, clickContext) -> {
            if (!open || !input.isLeftClick()) {
                return EventDispatch.pass;
            }
            var local = input.mouseRelative(this);
            if (handleClick(local.x(), local.y())) {
                clickContext.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
        onMouseScrolled((mouseX, mouseY, scrollX, scrollY, scrollContext) -> {
            if (!open || scrollY == 0) {
                return EventDispatch.pass;
            }
            int step = scrollY > 0 ? -1 : 1;
            var local = sceneToLocal(mouseX, mouseY);
            if (draft.isEmpty() && local.y() >= rowTop && local.y() < searchTop) {
                int total = pickerCandidates().size();
                int maxScroll = Math.max(0, (total + 1) / 2 - 4);
                pickerScroll = Math.max(0, Math.min(maxScroll, pickerScroll + step));
                scrollContext.consume();
                return EventDispatch.consumed;
            }
            if (local.y() >= resultTop && local.y() < hintTop) {
                int total = searchResults.isEmpty() ? quickSuggestions().size() : searchResults.size();
                int maxScroll = Math.max(0, total - resultLimit);
                searchScroll = Math.max(0, Math.min(maxScroll, searchScroll + step));
                scrollContext.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
        refresh();
    }

    //region state

    public void toggle() {
        if (open) {
            hide();
        } else {
            show();
        }
    }

    public void show() {
        if (open) {
            return;
        }
        if (shellContext instanceof WorkbenchShellWidget shell) {
            shell.closeTransientOverlays(this);
        }
        open = true;
        shownAtMillis = clock();
        dirty = false;
        pickerScroll = 0;
        searchScroll = 0;
        setVisible(true);
        setActive(true);
        refresh();
    }

    public void hide() {
        if (dirty) {
            commit();
        }
        open = false;
        dirty = false;
        pendingReplanAtMillis = -1L;
        setVisible(false);
        setActive(false);
    }

    public boolean isOpen() {
        return open;
    }

    public TargetBasketPopoverWidget reducedMotion(@Nullable Boolean reducedMotion) {
        this.reducedMotionOverride = reducedMotion;
        return this;
    }

    public float appearProgress(long nowMillis) {
        if (!open) {
            return 0f;
        }
        boolean reduced = reducedMotionOverride != null ? reducedMotionOverride : ChromeMotion.reducedMotion();
        return ChromeMotion.cubicOutProgress(nowMillis - shownAtMillis, ChromeMotion.overlayAppearMillis, reduced);
    }

    private void scheduleRefresh() {
        if (!lifecycle().mounted()) {
            return;
        }
        if (pendingRefresh.compareAndSet(false, true)) {
            scene().nextTick(() -> {
                pendingRefresh.set(false);
                if (!lifecycle().destroyed()) {
                    refresh();
                }
            });
        }
    }

    /**
     * On snapshot update the target rows are re-read; local unsubmitted edits are not overwritten.
     */
    public void refresh() {
        WorkbenchUiSnapshot snapshot = shellContext.activeSnapshot();
        if (!dirty) {
            draft = List.copyOf(snapshot.targets());
        }
        String query = search.text();
        searchResults = query.isBlank() ? List.of() : queryResources(query);
        syncInputs();
    }

    public List<TargetDemand> rows() {
        return draft;
    }

    public int visibleRowCount() {
        return Math.min(maxRows, draft.size());
    }

    public int overflowRowCount() {
        return Math.max(0, draft.size() - maxRows);
    }

    public long amountAt(int row) {
        return row < 0 || row >= draft.size() ? 0L : draft.get(row).amount();
    }

    public boolean setAmount(int row, long amount) {
        if (row < 0 || row >= draft.size()) {
            return false;
        }
        draft = ChromeTargets.withAmount(draft, row, amount);
        markDirty();
        return true;
    }

    public boolean multiplyAmount(int row, long factor) {
        if (row < 0 || row >= draft.size() || factor <= 0L) {
            return false;
        }
        long current = draft.get(row).amount();
        long scaled;
        try {
            scaled = Math.multiplyExact(current, factor);
        } catch (ArithmeticException overflow) {
            scaled = Long.MAX_VALUE;
        }
        return setAmount(row, scaled);
    }

    public boolean bumpSixteen(int row) {
        return multiplyAmount(row, microFactorSixteen);
    }

    public boolean bumpSixtyFour(int row) {
        return multiplyAmount(row, microFactorSixtyFour);
    }

    public boolean removeAt(int row) {
        if (row < 0 || row >= draft.size()) {
            return false;
        }
        draft = ChromeTargets.removedAt(draft, row);
        markDirty();
        return true;
    }

    public boolean addResource(@Nullable ComponentStackKey resource) {
        if (resource == null) {
            return false;
        }
        draft = ChromeTargets.merged(draft, resource, true);
        markDirty();
        return true;
    }

    public List<PaletteItem> setSearchQuery(@Nullable String query) {
        String text = query == null ? "" : query;
        searchScroll = 0;
        pickerScroll = 0;
        searchResults = text.isBlank() ? List.of() : queryResources(text);
        return searchResults;
    }

    public List<PaletteItem> searchResults() {
        return searchResults;
    }

    public @Nullable PaletteItem searchResult(int index) {
        return index < 0 || index >= searchResults.size() ? null : searchResults.get(index);
    }

    public String searchText() {
        return search.text();
    }

    public boolean addSearchResult(int index) {
        PaletteItem item = searchResult(index);
        if (item == null) {
            return false;
        }
        ComponentStackKey resource = resourceOf(item);
        if (resource == null || !addResource(resource)) {
            return false;
        }
        search.setText("");
        searchResults = List.of();
        searchScroll = 0;
        return true;
    }

    private static @Nullable ComponentStackKey resourceOf(PaletteItem item) {
        return switch (item.primaryAction()) {
            case PaletteAction.SetTarget setTarget -> setTarget.resource();
            case PaletteAction.AddToBasket addToBasket -> addToBasket.resource();
            default -> item.iconResource();
        };
    }

    public boolean planNow() {
        pendingReplanAtMillis = -1L;
        if (!dirty && shellContext.activeSnapshot().targets().equals(draft)) {
            shellContext.requestReplan();
            return true;
        }
        return commit();
    }

    public boolean pendingReplan() {
        return pendingReplanAtMillis >= 0L;
    }

    public long debounceRemainingMillis() {
        return pendingReplanAtMillis < 0L ? 0L : Math.max(0L, pendingReplanAtMillis - clock());
    }

    public boolean pollDebounce() {
        if (pendingReplanAtMillis < 0L || clock() < pendingReplanAtMillis) {
            return false;
        }
        return commit();
    }

    public boolean advance(long deltaMillis) {
        if (deltaMillis > 0L) {
            clockOffsetMillis += deltaMillis;
        }
        return pollDebounce();
    }

    public long clock() {
        return System.currentTimeMillis() + clockOffsetMillis;
    }

    public boolean commit() {
        List<TargetDemand> next = List.copyOf(draft);
        pendingReplanAtMillis = -1L;
        dirty = false;
        shellContext.setTargets(next, primaryRecipe());
        return true;
    }

    public @Nullable RecipeEntry<?> primaryRecipe() {
        PlannerWorkbenchController controller = shellContext.activeController();
        return controller == null ? null : controller.state().primaryRecipe();
    }

    public WorkbenchShellContext shellContext() {
        return shellContext;
    }

    private void markDirty() {
        dirty = true;
        pendingReplanAtMillis = clock() + debounceMillis;
        syncInputs();
    }

    private void onRowAmountChanged(int row, long value) {
        if (refreshing) {
            return;
        }
        setAmount(row, value);
    }

    private void syncInputs() {
        refreshing = true;
        try {
            for (int row = 0; row < maxRows; row++) {
                NumberInputWidget input = amountInputs[row];
                boolean visible = open && row < draft.size();
                input.setVisible(visible);
                if (visible) {
                    long amount = Math.max(1L, draft.get(row).amount());
                    if (input.getValue() != amount) {
                        input.setValue(amount);
                    }
                }
            }
        } finally {
            refreshing = false;
        }
    }

    private List<PaletteItem> queryResources(String query) {
        return CommandPaletteEngine.queryResources(query, shellContext.activeGraph(), 500);
    }

    private void dismissOnOutsideClick() {
        if (open && clock() - shownAtMillis >= outsideClickGraceMillis) {
            hide();
        }
    }

    //endregion

    //region geometry

    public static int rowTop(int row) {
        return rowTop + row * rowHeight;
    }

    public static int innerWidth() {
        return (int) width - pad * 2;
    }

    public static int numberLeft() {
        return (int) width - pad - deleteWidth - 2 - microWidth - 2 - microWidth - 2 - numberWidth;
    }

    public Rect headerRect() {
        return new Rect(pad, headerTop, innerWidth(), headerHeight);
    }

    public Rect closeRect() {
        return new Rect((int) width - pad - closeSize, headerTop, closeSize, closeSize);
    }

    public Rect rowRect(int row) {
        return new Rect(pad, rowTop(row), innerWidth(), rowHeight);
    }

    public Rect iconRect(int row) {
        return new Rect(pad, rowTop(row) + 2, rowIconSize, rowIconSize);
    }

    public Rect numberRect(int row) {
        return new Rect(numberLeft(), rowTop(row) + 2, numberWidth, rowHeight - 5);
    }

    public Rect microSixteenRect(int row) {
        return new Rect(numberLeft() + numberWidth + 2, rowTop(row) + 2, microWidth, rowHeight - 5);
    }

    public Rect microSixtyFourRect(int row) {
        return new Rect(numberLeft() + numberWidth + 2 + microWidth + 2, rowTop(row) + 2, microWidth, rowHeight - 5);
    }

    public Rect deleteRect(int row) {
        return new Rect((int) width - pad - deleteWidth, rowTop(row) + 2, deleteWidth, rowHeight - 5);
    }

    public Rect searchRect() {
        return new Rect(pad, searchTop, innerWidth(), searchHeight);
    }

    public Rect resultRect(int index) {
        return new Rect(pad, resultTop + index * resultRowHeight, innerWidth(), resultRowHeight - 1);
    }

    public Rect hintRect() {
        return new Rect(pad, hintTop, innerWidth(), hintHeight);
    }

    public Rect browseCatalogRect() {
        return new Rect(pad, footerTop, Math.max(80, (int) width - pad * 2 - footerWidth - 6), footerHeight);
    }

    public Rect planNowRect() {
        return new Rect((int) width - pad - footerWidth, footerTop, footerWidth, footerHeight);
    }

    public int rowAt(double localX, double localY) {
        if (localX < pad || localX >= (int) width - pad) {
            return -1;
        }
        for (int row = 0; row < visibleRowCount(); row++) {
            if (contains(rowRect(row), localX, localY)) {
                return row;
            }
        }
        return -1;
    }

    public int searchResultAt(double localX, double localY) {
        int visible = Math.min(resultLimit, Math.max(0, searchResults.size() - searchScroll));
        for (int index = 0; index < visible; index++) {
            if (contains(resultRect(index), localX, localY)) {
                return searchScroll + index;
            }
        }
        return -1;
    }

    private boolean handleClick(double localX, double localY) {
        Rect close = closeRect();
        if (contains(new Rect(close.x() - 3, close.y() - 2, close.width() + 6, close.height() + 5), localX, localY)) {
            hide();
            return true;
        }
        for (PickerCardHit hit : pickerCardHits) {
            if (contains(hit.cardRect(), localX, localY)) {
                boolean added = addResource(hit.resource());
                if (added) {
                    search.setText("");
                    searchResults = List.of();
                    pickerScroll = 0;
                    searchScroll = 0;
                    CirrusUiSound.playClickSound();
                }
                return added;
            }
        }
        int result = searchResultAt(localX, localY);
        if (result >= 0) {
            boolean added = addSearchResult(result);
            if (added) {
                CirrusUiSound.playClickSound();
            }
            return added;
        }
        if (searchResults.isEmpty()) {
            List<ComponentStackKey> suggestions = quickSuggestions();
            int visible = Math.min(resultLimit, Math.max(0, suggestions.size() - searchScroll));
            for (int index = 0; index < visible; index++) {
                if (contains(resultRect(index), localX, localY)) {
                    boolean added = addResource(suggestions.get(searchScroll + index));
                    if (added) {
                        CirrusUiSound.playClickSound();
                    }
                    return added;
                }
            }
        }
        if (contains(browseCatalogRect(), localX, localY)) {
            if (shellContext instanceof WorkbenchShellWidget shell) {
                hide();
                shell.openTargetCatalogPicker();
                CirrusUiSound.playClickSound();
                return true;
            }
        }
        if (contains(planNowRect(), localX, localY)) {
            CirrusUiSound.playClickSound();
            if (draft.isEmpty()) {
                if (!searchResults.isEmpty() && addSearchResult(0)) {
                    planNow();
                    hide();
                    return true;
                }
                if (shellContext instanceof WorkbenchShellWidget shell) {
                    hide();
                    shell.openTargetCatalogPicker();
                    return true;
                }
                return false;
            }
            boolean planned = planNow();
            hide();
            return planned;
        }
        for (int i = 0; i < wellFilterRects.size(); i++) {
            if (contains(wellFilterRects.get(i), localX, localY)) {
                recipeFilter = i;
                CirrusUiSound.playClickSound();
                return true;
            }
        }
        Rect cmpRect = wellCompareRect;
        if (cmpRect != null && contains(cmpRect, localX, localY)) {
            ComponentStackKey selectedRes = inspectedResource();
            if (selectedRes != null && shellContext instanceof WorkbenchShellWidget shell) {
                shell.openRecipeAssistant(selectedRes);
                CirrusUiSound.playClickSound();
                return true;
            }
        }
        for (RecipePinHit hit : wellPinHits) {
            if (contains(hit.pinRect(), localX, localY) || contains(hit.cardRect(), localX, localY)) {
                PlannerWorkbenchController controller = shellContext.activeController();
                if (controller != null) {
                    controller.refine(new PinResourceRecipeFeedback(hit.target(), hit.recipe().key(), false), null);
                    CirrusUiSound.playClickSound();
                    return true;
                }
            }
        }
        for (int row = 0; row < visibleRowCount(); row++) {
            if (contains(deleteRect(row), localX, localY)) {
                return removeAt(row);
            }
            if (contains(microSixteenRect(row), localX, localY)) {
                return bumpSixteen(row);
            }
            if (contains(microSixtyFourRect(row), localX, localY)) {
                return bumpSixtyFour(row);
            }
            if (contains(rowRect(row), localX, localY)) {
                inspectedRow = row;
                CirrusUiSound.playClickSound();
                return true;
            }
        }
        return false;
    }

    private @Nullable ComponentStackKey inspectedResource() {
        if (draft.isEmpty()) {
            return null;
        }
        int idx = Math.max(0, Math.min(draft.size() - 1, inspectedRow));
        return draft.get(idx).resource();
    }

    private List<CompiledRecipe> filteredRecipesFor(@Nullable ComponentStackKey resource) {
        CompiledRecipeGraph graph = shellContext.activeGraph();
        if (graph == null || resource == null) {
            return List.of();
        }
        List<CompiledRecipe> raw = graph.producersOf(resource);
        if (recipeFilter == 0) {
            return raw;
        }
        List<CompiledRecipe> out = new ArrayList<>(raw.size());
        for (CompiledRecipe recipe : raw) {
            boolean hand = recipe.machineRequirement().compatibleMachines().isEmpty();
            if ((recipeFilter == 1 && hand) || (recipeFilter == 2 && !hand)) {
                out.add(recipe);
            }
        }
        return out;
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        if (!open) {
            return null;
        }
        for (RecipePinHit hit : wellPinHits) {
            if (contains(hit.cardRect(), localX, localY)) {
                CompiledRecipe recipe = hit.recipe();
                String machine = recipe.machineRequirement().compatibleMachines().isEmpty()
                        ? "Hand / Crafting"
                        : StepNodeCardRenderer
                                .machineNameOf(recipe.machineRequirement().compatibleMachines().get(0), null)
                                .getString();
                List<OreUiRichTooltip.ItemBadge> ins = new ArrayList<>();
                for (InputSlot in : recipe.inputs()) {
                    if (!in.candidates().isEmpty()) {
                        ins.add(OreUiRichTooltip.ItemBadge.of(in.candidates().get(0), "\u00d7" + in.amountPerBatch()));
                    }
                }
                List<OreUiRichTooltip.ItemBadge> outs = new ArrayList<>();
                for (OutputSlot out : recipe.outputs()) {
                    outs.add(OreUiRichTooltip.ItemBadge.of(out.resource(), "\u00d7" + out.amountPerBatch()));
                }
                return OreUiRichTooltip.builder(machine).icon(hit.target())
                        .badge("RECIPE", OreUiRichTooltip.Tone.positive)
                        .itemFlow(Component.literal("Formula"), ins, outs).footer("Click to pin this recipe for target")
                        .build();
            }
        }
        for (PickerCardHit hit : pickerCardHits) {
            if (contains(hit.cardRect(), localX, localY)) {
                return resourceCandidateTooltip(hit.resource());
            }
        }
        int searchIdx = searchResultAt(localX, localY);
        if (searchIdx >= 0 && searchIdx < searchResults.size()) {
            ComponentStackKey res = resourceOf(searchResults.get(searchIdx));
            if (res != null) {
                return resourceCandidateTooltip(res);
            }
        }
        int row = rowAt(localX, localY);
        if (row >= 0 && row < draft.size()) {
            TargetDemand demand = draft.get(row);
            CompiledRecipeGraph graph = shellContext.activeGraph();
            int producers = graph == null ? 0 : graph.producersOf(demand.resource()).size();
            return OreUiRichTooltip.builder(CoPilotPaint.resourceName(demand.resource())).icon(demand.resource())
                    .badge("\u00d7" + WorkbenchFormatters.formatAmount(demand.amount()), OreUiRichTooltip.Tone.accent)
                    .keyValue("Available Recipes", String.valueOf(producers), OreUiRichTooltip.Tone.positive)
                    .footer("Click row to inspect recipes \u00b7 Use \u00d716/\u00d764 to scale").build();
        }
        return null;
    }

    private OreUiRichTooltip resourceCandidateTooltip(ComponentStackKey resource) {
        CompiledRecipeGraph graph = shellContext.activeGraph();
        List<CompiledRecipe> producers = graph == null ? List.of() : graph.producersOf(resource);
        OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder(CoPilotPaint.resourceName(resource))
                .subtitle(resource.displayId()).icon(resource)
                .badge(producers.size() + " RECIPES", OreUiRichTooltip.Tone.positive);
        if (!producers.isEmpty()) {
            CompiledRecipe first = producers.get(0);
            List<OreUiRichTooltip.ItemBadge> ins = new ArrayList<>();
            for (InputSlot in : first.inputs()) {
                if (!in.candidates().isEmpty()) {
                    ins.add(OreUiRichTooltip.ItemBadge.of(in.candidates().get(0), "\u00d7" + in.amountPerBatch()));
                }
            }
            List<OreUiRichTooltip.ItemBadge> outs = new ArrayList<>();
            for (OutputSlot out : first.outputs()) {
                outs.add(OreUiRichTooltip.ItemBadge.of(out.resource(), "\u00d7" + out.amountPerBatch()));
            }
            builder.itemFlow(Component.literal("Primary Recipe"), ins, outs);
        }
        return builder.footer("Click to add as planning target").build();
    }

    private List<ComponentStackKey> pickerCandidates() {
        if (!searchResults.isEmpty()) {
            List<ComponentStackKey> out = new ArrayList<>(searchResults.size());
            for (PaletteItem item : searchResults) {
                ComponentStackKey res = resourceOf(item);
                if (res != null && !containsDraft(res)) {
                    out.add(res);
                }
            }
            return out;
        }
        if (!search.text().isBlank()) {
            return List.of();
        }
        return quickSuggestions();
    }

    private List<ComponentStackKey> quickSuggestions() {
        List<PaletteItem> catalog = CommandPaletteEngine.queryResources("", shellContext.activeGraph(), 500);
        List<ComponentStackKey> out = new ArrayList<>(catalog.size());
        for (PaletteItem item : catalog) {
            ComponentStackKey candidate = resourceOf(item);
            if (candidate != null && !containsDraft(candidate)) {
                out.add(candidate);
            }
        }
        return out;
    }

    private boolean containsDraft(ComponentStackKey candidate) {
        for (TargetDemand demand : draft) {
            if (candidate.equals(demand.resource())) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(Rect rect, double x, double y) {
        return x >= rect.x() && y >= rect.y() && x < rect.right() && y < rect.bottom();
    }

    //endregion

    //region render

    @Override
    public void render(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        if (!open || width() <= 0 || height() <= 0) {
            return;
        }
        float progress = appearProgress(clock());
        float slideY = (1f - progress) * ChromeMotion.overlaySlidePx;
        canvas.pushTransform();
        canvas.translate(0f, slideY);
        try {
            super.render(canvas, mouseX, mouseY, partialTicks);
        } finally {
            canvas.popTransform();
        }
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        if (!open || width() <= 0 || height() <= 0) {
            return;
        }
        CirrusPalette palette = shellContext.palette();
        var textures = CirrusUiTheme.textures(this);
        search.setBackgroundTexture(textures.recessed());
        search.setTextColor(palette.textPrimary());
        search.setPlaceholderColor(palette.textMuted());
        OreUiPaint.popover(canvas, 0, 0, width(), height(), palette);
        renderHeader(canvas, palette, mouseX, mouseY);
        renderRows(canvas, palette, mouseX, mouseY);
        renderSearchResults(canvas, palette, mouseX, mouseY);
        renderHint(canvas, palette);
        renderFooter(canvas, palette, mouseX, mouseY);
    }

    private void renderHeader(SceneCanvas canvas, CirrusPalette palette, int mouseX, int mouseY) {
        OreUiPaint.headerBand(canvas, 0, 0, width(), 17, palette, false);
        Rect header = headerRect();
        int textY = ChromeText.centeredY(1, 17, canvas);
        ChromeText.drawFitted(
            canvas,
            CirrusLangs.Workbench.Topbar.basket.get(),
            header.x(),
            textY,
            Math.max(0, header.width() - 68),
            palette.textPrimary()
        );
        Component count = CirrusLangs.Workbench.Tab.basketBadge.get(draft.size());
        int pillW = canvas.font().width(count) + 10;
        Rect close = closeRect();
        OreUiPaint.tagPill(canvas, close.x() - pillW - 5, 3, 13, count, palette.focusRing(), palette);
        boolean closeHover = ChromeText.hit(mouseX, mouseY, close.x() - 1, close.y() - 1, closeSize + 2, closeSize + 2);
        if (closeHover) {
            OreUiPaint.raisedButton(
                canvas,
                close.x() - 1,
                close.y() - 1,
                closeSize + 2,
                closeSize + 2,
                palette,
                false,
                true,
                false,
                false
            );
        }
        ChromeButtons.cross(
            canvas,
            close.x(),
            close.y(),
            closeSize,
            closeHover ? palette.textPrimary() : palette.textMuted()
        );
    }

    private void renderRows(SceneCanvas canvas, CirrusPalette palette, int mouseX, int mouseY) {
        WorkbenchUiSnapshot snapshot = shellContext.activeSnapshot();
        TargetDemand primary = snapshot.targets().isEmpty() ? null : snapshot.targets().get(0);
        int visible = visibleRowCount();
        int activeInspect = draft.isEmpty() ? -1 : Math.max(0, Math.min(draft.size() - 1, inspectedRow));
        for (int row = 0; row < visible; row++) {
            TargetDemand demand = draft.get(row);
            boolean inspected = row == activeInspect;
            int ry = rowTop(row);
            int rh = rowHeight - 1;
            boolean rowHover = ChromeText.hit(mouseX, mouseY, pad, ry, innerWidth(), rh);
            int fill = inspected
                    ? palette.cardSurfaceSelected()
                    : rowHover ? palette.cardSurfaceHover() : palette.cardSurface();
            int border = inspected ? palette.focusRing() : palette.hairlineBorder();
            canvas.fill(pad, ry, innerWidth(), rh, fill);
            canvas.strokeRect(pad, ry, innerWidth(), rh, border, 1);
            int slotY = ry + 1;
            OreUiPaint.slot(canvas, pad + 2, slotY, 17, palette);
            drawResourceIcon(canvas, demand.resource(), palette, pad + 3, slotY + 1, 15);
            Component displayName = CoPilotPaint.resourceName(demand.resource());
            ChromeText.drawFitted(
                canvas,
                displayName,
                nameLeft + 2,
                ChromeText.centeredY(ry, rh, canvas),
                Math.max(0, numberLeft() - nameLeft - 6),
                palette.textPrimary()
            );
            renderMicroButton(canvas, palette, microSixteenRect(row), "\u00d716", mouseX, mouseY);
            renderMicroButton(canvas, palette, microSixtyFourRect(row), "\u00d764", mouseX, mouseY);
            Rect delete = deleteRect(row);
            boolean deleteHover = ChromeText
                    .hit(mouseX, mouseY, delete.x(), delete.y(), delete.width(), delete.height());
            OreUiPaint.raisedButton(
                canvas,
                delete.x(),
                delete.y(),
                delete.width(),
                delete.height(),
                palette,
                false,
                deleteHover,
                false,
                deleteHover
            );
            ChromeButtons.cross(
                canvas,
                delete.x() + (delete.width() - 6) / 2,
                delete.y() + (delete.height() - 2 - 6) / 2,
                6,
                deleteHover ? palette.textOnAccent() : palette.textMuted()
            );
        }

        wellFilterRects.clear();
        wellPinHits.clear();
        pickerCardHits.clear();
        wellCompareRect = null;
        int wellTop = draft.isEmpty() ? rowTop : rowTop(visible) + 4;
        int wellHeight = searchTop - wellTop - 4;
        if (draft.isEmpty() && wellHeight >= 60) {
            renderEmptyTargetPickerWell(canvas, palette, pad, wellTop, innerWidth(), wellHeight, mouseX, mouseY);
        } else if (wellHeight >= 52 && activeInspect >= 0) {
            renderRecipeAssistantWell(
                canvas,
                palette,
                pad,
                wellTop,
                innerWidth(),
                wellHeight,
                draft.get(activeInspect).resource(),
                mouseX,
                mouseY
            );
        } else if (wellHeight >= 22) {
            OreUiPaint.searchField(canvas, pad, wellTop, innerWidth(), wellHeight, palette, false);
            long totalAmount = 0L;
            for (TargetDemand demand : draft) {
                totalAmount = Math.min(Long.MAX_VALUE - Math.max(0L, demand.amount()), totalAmount)
                        + Math.max(0L, demand.amount());
            }
            Component summary = CirrusLangs.Workbench.Topbar.basketSummary
                    .get(draft.size(), WorkbenchFormatters.formatAmount(totalAmount));
            int midY = wellTop + wellHeight / 2;
            ChromeText.drawFitted(canvas, summary, pad + 8, midY - 4, innerWidth() - 16, palette.textSecondary());
        }
        if (overflowRowCount() > 0) {
            ChromeText.drawRight(
                canvas,
                Component.literal("+" + overflowRowCount()),
                (int) width - pad,
                rowTop(maxRows - 1) + 6,
                palette.textMuted()
            );
        }
    }

    private void renderEmptyTargetPickerWell(
        SceneCanvas canvas,
        CirrusPalette palette,
        int x,
        int y,
        int w,
        int h,
        int mouseX,
        int mouseY
    ) {
        OreUiPaint.searchField(canvas, x, y, w, h, palette, false);
        CompiledRecipeGraph graph = shellContext.activeGraph();
        List<ComponentStackKey> candidates = pickerCandidates();
        int totalCatalog = CommandPaletteEngine.fullCatalog(graph).size();
        int headerH = 16;
        ChromeText.drawFitted(
            canvas,
            Component.literal(search.text().isBlank() ? "Pick from Full Catalog" : "Matching Items"),
            x + 6,
            ChromeText.centeredY(y + 1, headerH, canvas),
            Math.max(20, w - 82),
            palette.textPrimary()
        );
        String countText = search.text().isBlank()
                ? (totalCatalog > 0 ? totalCatalog + " items" : "Loading...")
                : (candidates.size() + " found");
        ChromeText.drawRight(
            canvas,
            Component.literal(countText),
            x + w - 6,
            ChromeText.centeredY(y + 1, headerH, canvas),
            palette.textMuted()
        );
        canvas.fill(x + 2, y + headerH, w - 4, 1, OreUiPaint.cardDivider(palette));

        int gridTop = y + headerH + 4;
        int gridBottom = y + h - 4;
        if (candidates.isEmpty()) {
            Component emptyMsg = totalCatalog == 0
                    ? Component.literal("Recipe graph compiling — wait a moment...")
                    : Component.literal("No matching item — try another name or @mod");
            ChromeText.drawFitted(
                canvas,
                emptyMsg,
                x + 8,
                gridTop + Math.max(4, (gridBottom - gridTop - 10) / 2),
                w - 16,
                palette.textMuted()
            );
            return;
        }
        int cols = 2;
        int gapX = 4;
        int gapY = 3;
        int cellW = (w - 10 - gapX * (cols - 1)) / cols;
        int cellH = 26;
        int visibleRows = Math.max(1, (gridBottom - gridTop + gapY) / (cellH + gapY));
        int maxScroll = Math.max(0, (candidates.size() + cols - 1) / cols - visibleRows);
        pickerScroll = Math.max(0, Math.min(maxScroll, pickerScroll));
        int startIndex = pickerScroll * cols;
        for (int row = 0; row < visibleRows; row++) {
            int cy = gridTop + row * (cellH + gapY);
            if (cy + cellH > gridBottom + 1) {
                break;
            }
            for (int col = 0; col < cols; col++) {
                int idx = startIndex + row * cols + col;
                if (idx >= candidates.size()) {
                    break;
                }
                ComponentStackKey resource = candidates.get(idx);
                int cx = x + 5 + col * (cellW + gapX);
                Rect card = new Rect(cx, cy, cellW, cellH);
                boolean hovered = contains(card, mouseX, mouseY);
                OreUiPaint.raisedCard(canvas, card.x(), card.y(), card.width(), card.height(), palette, hovered, false);
                OreUiPaint.slot(canvas, cx + 4, cy + 4, 18, palette);
                drawResourceIcon(canvas, resource, palette, cx + 5, cy + 5, 16);
                int tx = cx + 25;
                int tw = Math.max(12, cellW - 38);
                ChromeText.drawFitted(
                    canvas,
                    CoPilotPaint.resourceName(resource),
                    tx,
                    cy + 4,
                    tw,
                    hovered ? palette.textPrimary() : palette.textSecondary()
                );
                int recCount = graph == null ? 0 : graph.producersOf(resource).size();
                ChromeText.drawFitted(
                    canvas,
                    Component.literal(recCount + (recCount == 1 ? " recipe" : " recipes")),
                    tx,
                    cy + 14,
                    tw,
                    palette.textMuted()
                );
                ChromeButtons.plus(
                    canvas,
                    card.right() - 10,
                    cy + (cellH - 2 - 5) / 2,
                    5,
                    hovered ? palette.costS() : palette.textMuted()
                );
                pickerCardHits.add(new PickerCardHit(card, resource));
            }
        }
    }

    private void renderRecipeAssistantWell(
        SceneCanvas canvas,
        CirrusPalette palette,
        int x,
        int y,
        int w,
        int h,
        ComponentStackKey target,
        int mouseX,
        int mouseY
    ) {
        OreUiPaint.searchField(canvas, x, y, w, h, palette, false);
        // Header row: Target recipe title + filter pills + Compare... launcher.
        int headerY = y + 3;
        String[] filterLabels = {"All", "Hand", "Mach"};
        int fx = x + 5;
        for (int i = 0; i < filterLabels.length; i++) {
            Component comp = Component.literal(filterLabels[i]);
            int fw = ChromeText.width(canvas, comp) + 10;
            Rect fr = new Rect(fx, headerY, fw, 15);
            wellFilterRects.add(fr);
            boolean active = recipeFilter == i;
            boolean hovered = contains(fr, mouseX, mouseY);
            if (active) {
                OreUiPaint.raisedCard(canvas, fr.x(), fr.y(), fr.width(), fr.height(), palette, hovered, true);
            } else {
                OreUiPaint.raisedButton(
                    canvas,
                    fr.x(),
                    fr.y(),
                    fr.width(),
                    fr.height(),
                    palette,
                    false,
                    hovered,
                    false,
                    false
                );
            }
            ChromeText.draw(
                canvas,
                comp,
                fr.x() + 5,
                active
                        ? ChromeText.centeredY(fr.y() + 2, fr.height() - 5, canvas)
                        : ChromeText.centeredButtonY(fr.y(), fr.height(), canvas),
                active ? palette.focusRing() : palette.textSecondary()
            );
            fx += fw + 3;
        }

        Component cmpComp = Component.literal("Compare...");
        int cmpW = ChromeText.width(canvas, cmpComp) + 10;
        Rect cmpRect = new Rect(x + w - cmpW - 5, headerY, cmpW, 15);
        wellCompareRect = cmpRect;
        boolean cmpHover = contains(cmpRect, mouseX, mouseY);
        OreUiPaint.raisedButton(
            canvas,
            cmpRect.x(),
            cmpRect.y(),
            cmpRect.width(),
            cmpRect.height(),
            palette,
            false,
            cmpHover,
            false,
            false
        );
        ChromeText.draw(
            canvas,
            cmpComp,
            cmpRect.x() + 5,
            ChromeText.centeredButtonY(cmpRect.y(), cmpRect.height(), canvas),
            cmpHover ? palette.focusRing() : palette.textPrimary()
        );

        List<CompiledRecipe> recipes = filteredRecipesFor(target);
        int listTop = headerY + 18;
        int listBottom = y + h - 4;
        if (recipes.isEmpty()) {
            ChromeText.drawFitted(
                canvas,
                Component.literal("No recipes match filter for " + CoPilotPaint.resourceName(target).getString()),
                x + 6,
                listTop + 6,
                w - 12,
                palette.textMuted()
            );
            return;
        }

        int maxGridRows = recipes.size() == 1 ? 2 : 1;
        int cy = listTop;
        for (int i = 0; i < recipes.size(); i++) {
            CompiledRecipe recipe = recipes.get(i);
            List<OreUiRecipeGrid.SlotEntry> inputs = OreUiRecipeGrid.inputsOf(recipe);
            List<OreUiRecipeGrid.SlotEntry> outputs = OreUiRecipeGrid.outputsOf(recipe);
            OreUiRecipeGrid.Layout grid = OreUiRecipeGrid.measure(
                inputs.size(),
                outputs.size(),
                OreUiRecipeGrid.compactSlotSize,
                Math.max(48, w - 20),
                maxGridRows
            );
            int cardH = 21 + grid.height() + 5;
            Rect card = new Rect(x + 4, cy, w - 8, cardH);
            if (card.bottom() > listBottom + 1) {
                break;
            }
            boolean cardHover = contains(card, mouseX, mouseY);
            OreUiPaint.raisedCard(canvas, card.x(), card.y(), card.width(), card.height(), palette, cardHover, false);

            String machine = recipe.machineRequirement().compatibleMachines().isEmpty()
                    ? "Hand Craft"
                    : StepNodeCardRenderer
                            .machineNameOf(recipe.machineRequirement().compatibleMachines().get(0), null).getString();
            Component pinComp = Component.literal("Pin");
            int pinW = ChromeText.width(canvas, pinComp) + 10;
            Rect pinRect = new Rect(card.right() - pinW - 4, card.y() + 3, pinW, 13);
            boolean pinHover = contains(pinRect, mouseX, mouseY);
            OreUiPaint.raisedButton(
                canvas,
                pinRect.x(),
                pinRect.y(),
                pinRect.width(),
                pinRect.height(),
                palette,
                false,
                pinHover,
                true,
                false
            );
            ChromeText.draw(
                canvas,
                pinComp,
                pinRect.x() + 5,
                ChromeText.centeredButtonY(pinRect.y(), pinRect.height(), canvas),
                palette.textOnAccent()
            );

            ChromeText.drawFitted(
                canvas,
                Component.literal(machine),
                card.x() + 5,
                card.y() + 5,
                Math.max(20, pinRect.x() - card.x() - 10),
                palette.textPrimary()
            );

            OreUiRecipeGrid.render(canvas, inputs, outputs, grid, card.x() + 6, card.y() + 19, palette);

            wellPinHits.add(new RecipePinHit(card, pinRect, target, recipe));
            cy += cardH + 3;
        }
    }

    private void renderMicroButton(
        SceneCanvas canvas,
        CirrusPalette palette,
        Rect rect,
        String label,
        int mouseX,
        int mouseY
    ) {
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
        int textW = canvas.font().width(label);
        ChromeText.draw(
            canvas,
            Component.literal(label),
            rect.x() + Math.max(1, (rect.width() - textW) / 2),
            ChromeText.centeredButtonY(rect.y(), rect.height(), canvas),
            hovered ? palette.textPrimary() : palette.textSecondary()
        );
    }

    private void renderSearchResults(SceneCanvas canvas, CirrusPalette palette, int mouseX, int mouseY) {
        if (!searchResults.isEmpty()) {
            int maxScroll = Math.max(0, searchResults.size() - resultLimit);
            searchScroll = Math.max(0, Math.min(maxScroll, searchScroll));
            int visible = Math.min(resultLimit, Math.max(0, searchResults.size() - searchScroll));
            for (int index = 0; index < visible; index++) {
                Rect rect = resultRect(index);
                PaletteItem item = searchResults.get(searchScroll + index);
                boolean hovered = ChromeText.hit(mouseX, mouseY, rect.x(), rect.y(), rect.width(), rect.height());
                canvas.fill(
                    rect.x(),
                    rect.y(),
                    rect.width(),
                    rect.height(),
                    hovered ? palette.cardSurfaceHover() : palette.cardSurface()
                );
                canvas.strokeRect(rect.x(), rect.y(), rect.width(), rect.height(), palette.hairlineBorder(), 1);
                drawResourceIcon(canvas, item.iconResource(), palette, rect.x() + 4, rect.y() + 2, 11);
                Component name = item.iconResource() != null
                        ? CoPilotPaint.resourceName(item.iconResource())
                        : Component.literal(item.displayText());
                ChromeText.drawFitted(
                    canvas,
                    name,
                    rect.x() + 20,
                    ChromeText.centeredY(rect.y(), rect.height(), canvas),
                    Math.max(0, rect.width() - 38),
                    hovered ? palette.textPrimary() : palette.textSecondary()
                );
                ChromeButtons.plus(
                    canvas,
                    rect.right() - 10,
                    rect.y() + Math.max(2, (rect.height() - 5) / 2),
                    5,
                    palette.costS()
                );
            }
            return;
        }
        List<ComponentStackKey> suggestions = quickSuggestions();
        int maxScroll = Math.max(0, suggestions.size() - resultLimit);
        searchScroll = Math.max(0, Math.min(maxScroll, searchScroll));
        int visible = Math.min(resultLimit, Math.max(0, suggestions.size() - searchScroll));
        CompiledRecipeGraph graph = shellContext.activeGraph();
        for (int index = 0; index < visible; index++) {
            Rect rect = resultRect(index);
            ComponentStackKey resource = suggestions.get(searchScroll + index);
            boolean hovered = ChromeText.hit(mouseX, mouseY, rect.x(), rect.y(), rect.width(), rect.height());
            canvas.fill(
                rect.x(),
                rect.y(),
                rect.width(),
                rect.height(),
                hovered ? palette.cardSurfaceHover() : palette.cardSurface()
            );
            canvas.strokeRect(rect.x(), rect.y(), rect.width(), rect.height(), palette.hairlineBorder(), 1);
            drawResourceIcon(canvas, resource, palette, rect.x() + 4, rect.y() + 2, 11);
            int recCount = graph == null ? 0 : graph.producersOf(resource).size();
            String meta = recCount > 0 ? recCount + " rec" : "";
            int addW = ChromeText.width(canvas, CirrusLangs.Workbench.Topbar.quickAdd.get()) + 8;
            int metaW = meta.isEmpty() ? 0 : ChromeText.width(canvas, Component.literal(meta)) + 10;
            ChromeText.drawFitted(
                canvas,
                CoPilotPaint.resourceName(resource),
                rect.x() + 20,
                ChromeText.centeredY(rect.y(), rect.height(), canvas),
                Math.max(20, rect.width() - 26 - addW - metaW),
                hovered ? palette.textPrimary() : palette.textSecondary()
            );
            if (!meta.isEmpty()) {
                ChromeText.drawRight(
                    canvas,
                    Component.literal(meta),
                    rect.right() - addW - 4,
                    ChromeText.centeredY(rect.y(), rect.height(), canvas),
                    palette.textMuted()
                );
            }
            ChromeText.drawRight(
                canvas,
                CirrusLangs.Workbench.Topbar.quickAdd.get(),
                rect.right() - 5,
                ChromeText.centeredY(rect.y(), rect.height(), canvas),
                palette.costS()
            );
        }
    }

    private void renderHint(SceneCanvas canvas, CirrusPalette palette) {
        Rect hint = hintRect();
        Component status = pendingReplan()
                ? CirrusLangs.Workbench.Topbar.replanning.get()
                : CirrusLangs.Workbench.Topbar.autoReplan.get();
        ChromeText.drawFitted(
            canvas,
            status,
            hint.x(),
            ChromeText.centeredY(hint.y(), hint.height(), canvas),
            hint.width(),
            pendingReplan() ? palette.costH() : palette.textMuted()
        );
    }

    private void renderFooter(SceneCanvas canvas, CirrusPalette palette, int mouseX, int mouseY) {
        OreUiPaint.footerBand(canvas, 0, footerTop - 3, width(), height() - footerTop + 2, palette, false);
        Rect browse = browseCatalogRect();
        boolean browseHover = ChromeText.hit(mouseX, mouseY, browse.x(), browse.y(), browse.width(), browse.height());
        OreUiPaint.raisedButton(
            canvas,
            browse.x(),
            browse.y(),
            browse.width(),
            browse.height(),
            palette,
            false,
            browseHover,
            false,
            false
        );
        ChromeButtons.searchLens(
            canvas,
            browse.x() + 5,
            browse.y() + Math.max(2, (browse.height() - 2 - 8) / 2),
            browseHover ? palette.focusRing() : palette.textSecondary()
        );
        ChromeText.drawFitted(
            canvas,
            Component.literal("Catalog & Recipes..."),
            browse.x() + 17,
            ChromeText.centeredButtonY(browse.y(), browse.height(), canvas),
            Math.max(16, browse.width() - 22),
            browseHover ? palette.focusRing() : palette.textPrimary()
        );

        Rect rect = planNowRect();
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
        var playIcon = CirrusThemeIcons.resolve(CirrusTextures.Icon.play, shellContext.theme());
        ChromeButtons.icon(
            canvas,
            playIcon,
            rect.x() + 5,
            rect.y() + Math.max(0, (rect.height() - 10) / 2),
            10,
            palette.textOnAccent()
        );
        ChromeText.drawFitted(
            canvas,
            CirrusLangs.Workbench.Topbar.planNow.get(),
            rect.x() + 19,
            ChromeText.centeredY(rect.y(), rect.height() - 1, canvas),
            rect.width() - 22,
            palette.textOnAccent()
        );
    }

    private static void drawResourceIcon(
        SceneCanvas canvas,
        @Nullable ComponentStackKey resource,
        CirrusPalette palette,
        int x,
        int y,
        int size
    ) {
        ContentStack<?, ?> stack = CoPilotPaint.stackOf(resource, 1L);
        if (stack == null || stack.isEmpty()) {
            if (resource != null) {
                canvas.fill(x, y, size, size, palette.recessedSurface());
                canvas.strokeRect(x, y, size, size, palette.hairlineBorder(), 1);
            }
            return;
        }
        int drawSize = Math.min(16, size);
        int ox = x + Math.max(0, (size - drawSize) / 2);
        int oy = y + Math.max(0, (size - drawSize) / 2);
        CoPilotPaint.renderStack(canvas, stack, ox, oy, drawSize);
    }

    //endregion
}
