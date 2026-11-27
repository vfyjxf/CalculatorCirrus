package dev.vfyjxf.cirrus.ui.execution.cockpit;

import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayMaterialRequirement;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.execution.MainPanel;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.widget.calculator.calc.RecipeContentSlotWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiSlotAmount;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexBasisPx;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.maxHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;

/**
 * Live material accounting panel with an animated expanding search box, 5 sort modes, ascending/descending toggle, and 3-state stock filter.
 */
public final class ExecutionMaterialsPanelWidget extends Widget implements OreUiTooltipProvider {

    public static final int headerH = 20;
    private static final int toolbarH = 20;
    private static final int pad = 6;
    private static final int rowH = 26;
    private static final int rowGap = 2;
    private static final int slotSize = 18;
    private static final int searchCollapsedW = 16;

    public enum FilterMode {
        all, missing, ready;

        public FilterMode next() {
            return switch (this) {
                case all -> missing;
                case missing -> ready;
                case ready -> all;
            };
        }
    }

    public enum SortMode {
        shortfall, progress, required, name, stage;

        public SortMode next() {
            return switch (this) {
                case shortfall -> progress;
                case progress -> required;
                case required -> name;
                case name -> stage;
                case stage -> shortfall;
            };
        }

        public boolean defaultAscending() {
            return this == name || this == stage || this == progress;
        }
    }

    private record RowHit(Rect rect, OverlayMaterialRequirement req) {}

    private final MainPanel cockpit;
    private final OreUiMotion.SmoothValue searchExpandAnim = new OreUiMotion.SmoothValue();
    private final OreUiMotion.SmoothValue smoothHeight = new OreUiMotion.SmoothValue();
    private final List<RowHit> rowHits = new ArrayList<>();

    private boolean collapsed;
    private FilterMode filterMode = FilterMode.all;
    private SortMode sortMode = SortMode.shortfall;
    private boolean ascending;
    private boolean searchOpen;
    private String searchQuery = "";
    private int scrollY;
    private int lastAppliedMaxHeight = -1;
    private boolean animatingCollapse;

    private @Nullable Rect headerRect;
    private @Nullable Rect searchRect;
    private @Nullable Rect searchClearRect;
    private @Nullable Rect filterRect;
    private @Nullable Rect sortRect;
    private @Nullable Rect orderRect;

    public ExecutionMaterialsPanelWidget(MainPanel cockpit) {
        this.cockpit = cockpit;
        setInteractive(true);
        setFocusable(true);
        applyFlexStyle();

        onMouseClick((input, clickCount, ctx) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            int lx = (int) Math.round(local.x());
            int ly = (int) Math.round(local.y());

            if (searchClearRect != null && searchClearRect.contains(lx, ly)) {
                searchQuery = "";
                searchOpen = false;
                scrollY = 0;
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (searchRect != null && searchRect.contains(lx, ly)) {
                if (!searchOpen) {
                    searchOpen = true;
                }
                if (scene() != null) {
                    scene().requestFocus(this);
                }
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (filterRect != null && filterRect.contains(lx, ly)) {
                collapseEmptySearch();
                filterMode = filterMode.next();
                scrollY = 0;
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (sortRect != null && sortRect.contains(lx, ly)) {
                collapseEmptySearch();
                sortMode = sortMode.next();
                ascending = sortMode.defaultAscending();
                scrollY = 0;
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (orderRect != null && orderRect.contains(lx, ly)) {
                collapseEmptySearch();
                ascending = !ascending;
                scrollY = 0;
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (headerRect != null && headerRect.contains(lx, ly)) {
                collapseEmptySearch();
                setCollapsed(!collapsed);
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (searchOpen && searchQuery.isEmpty()) {
                searchOpen = false;
            }
            return EventDispatch.pass;
        });

        onKeyPressed((input, ctx) -> {
            if (!searchOpen) {
                return EventDispatch.pass;
            }
            int key = input.key().getValue();
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = "";
                } else {
                    searchOpen = false;
                }
                scrollY = 0;
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (input.isCtrlDown()) {
                    searchQuery = "";
                } else if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                }
                scrollY = 0;
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (searchQuery.isEmpty()) {
                    searchOpen = false;
                }
                ctx.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });

        onCharTyped((codePoint, modifiers, ctx) -> {
            if (!searchOpen || Character.isISOControl(codePoint)) {
                return EventDispatch.pass;
            }
            if (searchQuery.length() < 32) {
                searchQuery = searchQuery + new String(Character.toChars(codePoint));
                scrollY = 0;
            }
            ctx.consume();
            return EventDispatch.consumed;
        });

        onMouseScrolled((mouseX, mouseY, scrollX, scrollYDelta, ctx) -> {
            if (collapsed) {
                return EventDispatch.pass;
            }
            int listH = Math.max(0, height() - headerH - toolbarH - 4);
            int maxScroll = maxScroll(visibleMaterials().size(), listH);
            if (maxScroll <= 0) {
                return EventDispatch.pass;
            }
            scrollY = Math.max(0, Math.min(maxScroll, scrollY - (int) Math.round(scrollYDelta * 22.0)));
            ctx.consume();
            return EventDispatch.consumed;
        });
    }

    private void collapseEmptySearch() {
        if (searchOpen && searchQuery.isEmpty()) {
            searchOpen = false;
        }
    }

    private void applyFlexStyle() {
        applyFlexStyleForCount(4);
    }

    private void applyFlexStyleForCount(int visibleCount) {
        int rows = Math.max(1, Math.min(4, visibleCount));
        int neededH = headerH + toolbarH + 6 + rows * (rowH + rowGap) - rowGap;
        int budgetCap = Math.min(148, Math.max(headerH + 24, Math.round(cockpit.panelHeightBudget() * 0.34f)));
        int targetMaxH = Math.min(budgetCap, Math.max(headerH + toolbarH + 20, neededH));
        int desired = collapsed ? headerH : targetMaxH;
        if (!smoothHeight.initialized()) {
            smoothHeight.snap(desired, System.nanoTime());
        }
        float current = animatingCollapse ? smoothHeight.update(desired, 18.0f) : desired;
        int animH = Math.max(headerH, Math.min(targetMaxH, Math.round(current)));
        if (animatingCollapse && Math.abs(current - desired) <= 0.6f) {
            animatingCollapse = false;
            animH = desired;
        }
        if (collapsed || animatingCollapse) {
            if (lastAppliedMaxHeight != animH) {
                lastAppliedMaxHeight = animH;
                useStyle(heightOf(animH), flexGrow(0), flexShrink(0), minHeight(animH), maxHeight(animH), minWidth(0));
            }
        } else if (lastAppliedMaxHeight != targetMaxH) {
            lastAppliedMaxHeight = targetMaxH;
            smoothHeight.snap(targetMaxH, System.nanoTime());
            useStyle(
                flexGrow(1),
                flexShrink(1),
                flexBasisPx(0f),
                minHeight(Math.min(cockpit.isCompactHeight() ? headerH + 16 : 42, targetMaxH)),
                maxHeight(targetMaxH),
                minWidth(0)
            );
        }
    }

    public boolean collapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        if (this.collapsed != collapsed) {
            int currentH = height() > 0 ? height() : (this.collapsed ? headerH : 120);
            smoothHeight.snap(currentH, System.nanoTime());
            this.collapsed = collapsed;
            this.animatingCollapse = true;
            applyFlexStyle();
        }
    }

    public boolean missingOnly() {
        return filterMode == FilterMode.missing;
    }

    public void setMissingOnly(boolean missingOnly) {
        this.filterMode = missingOnly ? FilterMode.missing : FilterMode.all;
        this.scrollY = 0;
    }

    public FilterMode filterMode() {
        return filterMode;
    }

    public void setFilterMode(FilterMode filterMode) {
        this.filterMode = filterMode;
        this.scrollY = 0;
    }

    public SortMode sortMode() {
        return sortMode;
    }

    public void setSortMode(SortMode sortMode) {
        this.sortMode = sortMode;
        this.ascending = sortMode.defaultAscending();
        this.scrollY = 0;
    }

    public boolean ascending() {
        return ascending;
    }

    public void setAscending(boolean ascending) {
        this.ascending = ascending;
        this.scrollY = 0;
    }

    public boolean searchOpen() {
        return searchOpen;
    }

    public void setSearchOpen(boolean open) {
        this.searchOpen = open;
        if (!open) {
            this.searchQuery = "";
        }
        this.searchExpandAnim.snap(open ? 1f : 0f, System.nanoTime());
        this.scrollY = 0;
    }

    public String searchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String query) {
        this.searchQuery = query == null ? "" : query;
        if (!this.searchQuery.isEmpty()) {
            this.searchOpen = true;
            this.searchExpandAnim.snap(1f, System.nanoTime());
        }
        this.scrollY = 0;
    }

    public List<OverlayMaterialRequirement> visibleMaterials() {
        return filterAndSort(
            cockpit.sortedMaterials(),
            filterMode,
            sortMode,
            ascending,
            searchQuery,
            cockpit.stageModel()
        );
    }

    public static List<OverlayMaterialRequirement> filterAndSort(
        List<OverlayMaterialRequirement> source,
        FilterMode filter,
        SortMode sort,
        boolean ascending,
        String query,
        ExecutionStageModel stageModel
    ) {
        String norm = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<OverlayMaterialRequirement> out = new ArrayList<>(source.size());
        for (OverlayMaterialRequirement req : source) {
            if (filter == FilterMode.missing && req.missing() <= 0L) {
                continue;
            }
            if (filter == FilterMode.ready && req.missing() > 0L) {
                continue;
            }
            if (!norm.isEmpty()) {
                String label = ExecutionOverlayContentHelper.label(req.material()).toLowerCase(Locale.ROOT);
                String key = ExecutionOverlayContentHelper.recipeContentKey(req.material()).toLowerCase(Locale.ROOT);
                if (!label.contains(norm) && !key.contains(norm)) {
                    continue;
                }
            }
            out.add(req);
        }

        Comparator<OverlayMaterialRequirement> cmp = switch (sort) {
            case shortfall -> Comparator.comparingLong(OverlayMaterialRequirement::missing)
                    .thenComparingLong(OverlayMaterialRequirement::required);
            case progress -> Comparator.comparingDouble(ExecutionMaterialsPanelWidget::readinessRatio)
                    .thenComparingLong(OverlayMaterialRequirement::missing);
            case required -> Comparator.comparingLong(OverlayMaterialRequirement::required)
                    .thenComparingLong(OverlayMaterialRequirement::missing);
            case name -> Comparator
                    .comparing(req -> ExecutionOverlayContentHelper.label(req.material()).toLowerCase(Locale.ROOT));
            case stage -> Comparator.comparingInt(
                (OverlayMaterialRequirement req) -> stageModel.firstStageIndexForMaterial(req.material())
            ).thenComparingLong(req -> -req.missing());
        };
        if (!ascending) {
            cmp = cmp.reversed();
        }
        out.sort(cmp);
        return out;
    }

    private static double readinessRatio(OverlayMaterialRequirement req) {
        if (req.required() <= 0L) {
            return 1.0d;
        }
        return Math.min(1.0d, req.available() / (double) req.required());
    }

    private static int maxScroll(int count, int viewportH) {
        int contentH = count <= 0 ? 0 : count * (rowH + rowGap) - rowGap + 4;
        return Math.max(0, contentH - viewportH);
    }

    private Component filterLabel() {
        return switch (filterMode) {
            case all -> CirrusLangs.Execution.Cockpit.matFilterAll.get();
            case missing -> CirrusLangs.Execution.Cockpit.matFilterMissing.get();
            case ready -> CirrusLangs.Execution.Cockpit.matFilterReady.get();
        };
    }

    private Component sortLabel() {
        return switch (sortMode) {
            case shortfall -> CirrusLangs.Execution.Cockpit.matSortShortfall.get();
            case progress -> CirrusLangs.Execution.Cockpit.matSortProgress.get();
            case required -> CirrusLangs.Execution.Cockpit.matSortRequired.get();
            case name -> CirrusLangs.Execution.Cockpit.matSortName.get();
            case stage -> CirrusLangs.Execution.Cockpit.matSortStage.get();
        };
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        if (searchRect != null && searchRect.contains(localX, localY)) {
            return OreUiRichTooltip.builder(CirrusLangs.Execution.Cockpit.matSearchTip.get())
                    .badge(searchOpen ? "ACTIVE" : "SEARCH", OreUiRichTooltip.Tone.accent)
                    .text(CirrusLangs.Execution.Cockpit.matSearchTipDesc.get(), OreUiRichTooltip.Tone.secondary)
                    .build();
        }
        if (filterRect != null && filterRect.contains(localX, localY)) {
            Component title = switch (filterMode) {
                case all -> CirrusLangs.Execution.Cockpit.matTipShowMissing.get();
                case missing -> CirrusLangs.Execution.Cockpit.matFilterReady.get();
                case ready -> CirrusLangs.Execution.Cockpit.matTipShowAll.get();
            };
            String badge = switch (filterMode) {
                case all -> "ALL";
                case missing -> "SHORT";
                case ready -> "READY";
            };
            return OreUiRichTooltip.builder(title)
                    .badge(
                        badge,
                        filterMode == FilterMode.missing ? OreUiRichTooltip.Tone.warning : OreUiRichTooltip.Tone.accent
                    ).text(CirrusLangs.Execution.Cockpit.matTipFilterDesc.get(), OreUiRichTooltip.Tone.secondary)
                    .build();
        }
        if (sortRect != null && sortRect.contains(localX, localY)) {
            return OreUiRichTooltip.builder(CirrusLangs.Execution.Cockpit.matSortTip.get())
                    .badge(sortLabel(), OreUiRichTooltip.Tone.accent)
                    .text(CirrusLangs.Execution.Cockpit.matSortTipDesc.get(), OreUiRichTooltip.Tone.secondary).build();
        }
        if (orderRect != null && orderRect.contains(localX, localY)) {
            return OreUiRichTooltip.builder(CirrusLangs.Execution.Cockpit.matOrderTip.get())
                    .badge(ascending ? "ASC" : "DESC", OreUiRichTooltip.Tone.secondary).build();
        }
        if (headerRect != null && headerRect.contains(localX, localY)) {
            return OreUiRichTooltip
                    .builder(
                        collapsed
                                ? CirrusLangs.Execution.Cockpit.matTipExpand.get()
                                : CirrusLangs.Execution.Cockpit.matTipFold.get()
                    ).badge(collapsed ? "EXPAND" : "FOLD", OreUiRichTooltip.Tone.secondary)
                    .text(CirrusLangs.Execution.Cockpit.matTipFoldDesc.get(), OreUiRichTooltip.Tone.secondary).build();
        }
        for (RowHit hit : rowHits) {
            if (hit.rect().contains(localX, localY)) {
                return buildMaterialTooltip(hit.req());
            }
        }
        return null;
    }

    private OreUiRichTooltip buildMaterialTooltip(OverlayMaterialRequirement req) {
        String name = ExecutionOverlayContentHelper.label(req.material());
        var preview = RecipeContentSlotWidget.previewStack(req.material());
        OreUiRichTooltip.Builder b = OreUiRichTooltip.builder(name);
        if (preview != null) {
            b.icon(preview);
        }
        b.badge(
            req.missing() > 0L
                    ? CirrusLangs.Execution.Cockpit.matBadgeMissing.get(formatCount(req.missing()))
                    : CirrusLangs.Execution.Cockpit.matBadgeReady.get(),
            req.missing() > 0L ? OreUiRichTooltip.Tone.warning : OreUiRichTooltip.Tone.positive
        );
        b.keyValue(
            CirrusLangs.Execution.Cockpit.matKvRequired.get(),
            Component.literal(formatCount(req.required())),
            OreUiRichTooltip.Tone.primary
        );
        b.keyValue(
            CirrusLangs.Execution.Cockpit.matKvOwned.get(),
            Component.literal(formatCount(req.available())),
            OreUiRichTooltip.Tone.positive
        );
        if (req.missing() > 0L) {
            b.keyValue(
                CirrusLangs.Execution.Cockpit.matKvMissing.get(),
                Component.literal(formatCount(req.missing())),
                OreUiRichTooltip.Tone.warning
            );
        }
        int firstStage = cockpit.stageModel().firstStageIndexForMaterial(req.material());
        if (firstStage < Integer.MAX_VALUE) {
            b.keyValue(
                CirrusLangs.Execution.Cockpit.matSortStage.get(),
                Component.literal("#" + (firstStage + 1)),
                OreUiRichTooltip.Tone.accent
            );
        }
        double ratio = readinessRatio(req);
        b.meter(
            CirrusLangs.Execution.Cockpit.matKvProgress.get(),
            ratio,
            Component.literal(Math.round(ratio * 100.0) + "%"),
            req.missing() > 0L ? OreUiRichTooltip.Tone.warning : OreUiRichTooltip.Tone.positive
        );
        return b.build();
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        int w = width();
        int h = height();
        if (w <= 0 || h <= 0) {
            return;
        }
        CirrusPalette p = ExecutionCockpitTheme.palette(this);
        rowHits.clear();
        searchRect = null;
        searchClearRect = null;
        filterRect = null;
        sortRect = null;
        orderRect = null;

        canvas.fill(0, 0, w, h, p.recessedSurface());
        canvas.fill(0, h - 1, w, 1, OreUiPaint.cardDivider(p));

        List<OverlayMaterialRequirement> shown = visibleMaterials();
        applyFlexStyleForCount(shown.size());

        // OreUI section header with right fold chevron
        headerRect = new Rect(0, 0, w, headerH);
        boolean headerHover = headerRect.contains(mouseX, mouseY);
        OreUiPaint.headerBand(canvas, 0, 0, w, headerH, p, headerHover);
        List<OverlayMaterialRequirement> all = cockpit.sortedMaterials();
        int totalCount = all.size();
        int missingCount = cockpit.missingMaterialCount();
        int readyCount = Math.max(0, totalCount - missingCount);

        Component chevComp = Component.literal(collapsed ? "\u25b8" : "\u25be");
        int chevW = ChromeText.width(canvas, chevComp);
        int chevX = w - pad - chevW;
        ChromeText.draw(
            canvas,
            chevComp,
            chevX,
            ChromeText.centeredY(0, headerH, canvas),
            headerHover ? p.textPrimary() : p.textSecondary()
        );
        int pillRight = chevX - 6;
        if (totalCount > 0) {
            Component summaryComp = missingCount > 0
                    ? CirrusLangs.Execution.Cockpit.matSummaryMissing.get(missingCount)
                    : CirrusLangs.Execution.Cockpit.matSummaryReady.get(readyCount, totalCount);
            int sw = ChromeText.width(canvas, summaryComp) + 10;
            if (pillRight - sw > 68) {
                OreUiPaint.tagPill(
                    canvas,
                    pillRight - sw,
                    3,
                    13,
                    summaryComp,
                    missingCount > 0 ? p.ruleHardBan() : p.costS(),
                    p
                );
                pillRight -= sw + 4;
            }
        }

        ChromeText.drawFitted(
            canvas,
            CirrusLangs.Execution.Cockpit.matTitle.get(),
            pad,
            ChromeText.centeredY(0, headerH, canvas),
            Math.max(24, pillRight - pad - 2),
            p.textPrimary()
        );

        if (h <= headerH + 6) {
            return;
        }

        // Toolbar row: Animated Search button -> Search field + Filter + Sort + Asc/Desc
        drawToolbar(canvas, p, w, headerH, mouseX, mouseY);
        int listTop = headerH + toolbarH + 2;
        int listH = Math.max(0, h - listTop - 2);
        if (listH <= 4) {
            return;
        }
        int maxS = maxScroll(shown.size(), listH);
        if (scrollY > maxS) {
            scrollY = maxS;
        }
        int barReserve = maxS > 0 ? 6 : 0;
        int rowW = Math.max(40, w - pad * 2 - barReserve);

        canvas.withClip(0, listTop, w, listH, () -> {
            if (shown.isEmpty()) {
                Component msg;
                if (totalCount == 0) {
                    msg = CirrusLangs.Execution.Cockpit.matEmptyNone.get();
                } else if (!searchQuery.isBlank() || filterMode == FilterMode.ready) {
                    msg = CirrusLangs.Execution.Cockpit.matNoMatch.get();
                } else {
                    msg = CirrusLangs.Execution.Cockpit.matEmptyReady.get();
                }
                int mw = ChromeText.width(canvas, msg);
                ChromeText.drawFitted(
                    canvas,
                    msg,
                    Math.max(pad, (w - mw) / 2),
                    ChromeText.centeredY(listTop, listH, canvas),
                    Math.max(20, w - pad * 2),
                    totalCount == 0 || !searchQuery.isBlank() ? p.textMuted() : p.costS()
                );
                return;
            }
            int y = listTop + 2 - scrollY;
            for (OverlayMaterialRequirement req : shown) {
                if (y + rowH >= listTop && y <= listTop + listH) {
                    Rect rowRect = new Rect(pad, y, rowW, rowH);
                    drawMaterialRow(canvas, p, rowRect, req, mouseX, mouseY, partialTicks);
                    rowHits.add(new RowHit(rowRect, req));
                }
                y += rowH + rowGap;
            }
        });

        if (maxS > 0 && listH > 12) {
            int trackX = w - 5;
            canvas.fill(trackX, listTop + 2, 3, listH - 4, OreUiPaint.cardDivider(p));
            int thumbH = Math.max(12, Math.round((listH - 4) * (listH / (float) (listH + maxS))));
            int thumbY = listTop + 2 + Math.round(((listH - 4 - thumbH) * scrollY) / (float) maxS);
            canvas.fill(trackX, thumbY, 3, thumbH, p.focusRing());
        }
    }

    private void drawToolbar(SceneCanvas canvas, CirrusPalette p, int w, int y, int mouseX, int mouseY) {
        canvas.fill(0, y, w, toolbarH, p.dockSurface());
        canvas.fill(0, y + toolbarH - 1, w, 1, OreUiPaint.cardDivider(p));

        int btnY = y + 2;
        int btnH = 16;

        // Right-aligned controls: Asc/Desc + Sort + Filter
        int rx = w - pad;
        int ordW = 16;
        orderRect = new Rect(rx - ordW, btnY, ordW, btnH);
        rx -= ordW + 2;

        Component sortComp = sortLabel();
        int sortW = Math.min(72, Math.max(36, ChromeText.width(canvas, sortComp) + 10));
        sortRect = new Rect(rx - sortW, btnY, sortW, btnH);
        rx -= sortW + 2;

        Component filterComp = filterLabel();
        int filtW = Math.min(54, Math.max(28, ChromeText.width(canvas, filterComp) + 8));
        filterRect = new Rect(rx - filtW, btnY, filtW, btnH);

        // Animated search box expands smoothly from the left icon button across the toolbar
        float targetAnim = searchOpen || !searchQuery.isEmpty() ? 1f : 0f;
        float anim = searchExpandAnim.update(targetAnim, 18f);
        int expandedW = Math.max(searchCollapsedW, orderRect.x() - pad - 4);
        int curSearchW = Math.round(OreUiMotion.lerp(searchCollapsedW, expandedW, anim));
        searchRect = new Rect(pad, btnY, curSearchW, btnH);

        // Hide filter/sort controls while search box overlaps them
        boolean showFilter = searchRect.right() + 2 <= filterRect.x();
        boolean showSort = searchRect.right() + 2 <= sortRect.x();
        if (!showFilter) {
            filterRect = null;
        }
        if (!showSort) {
            sortRect = null;
        }

        // Render Asc/Desc button
        boolean ordHov = orderRect.contains(mouseX, mouseY);
        OreUiPaint.raisedButton(
            canvas,
            orderRect.x(),
            orderRect.y(),
            orderRect.width(),
            orderRect.height(),
            p,
            false,
            ordHov,
            false,
            false
        );
        Component arrow = Component.literal(ascending ? "\u2191" : "\u2193");
        int aw = ChromeText.width(canvas, arrow);
        ChromeText.draw(
            canvas,
            arrow,
            orderRect.x() + Math.max(1, (orderRect.width() - aw) / 2),
            ChromeText.centeredY(btnY, btnH, canvas),
            ordHov ? p.textPrimary() : p.textSecondary()
        );

        // Render Sort pill
        if (sortRect != null) {
            boolean sHov = sortRect.contains(mouseX, mouseY);
            OreUiPaint.raisedButton(
                canvas,
                sortRect.x(),
                sortRect.y(),
                sortRect.width(),
                sortRect.height(),
                p,
                sortMode != SortMode.shortfall,
                sHov,
                false,
                false
            );
            ChromeText.drawFitted(
                canvas,
                sortComp,
                sortRect.x() + 4,
                ChromeText.centeredY(btnY, btnH, canvas),
                Math.max(12, sortRect.width() - 8),
                sortMode != SortMode.shortfall ? p.focusRing() : (sHov ? p.textPrimary() : p.textSecondary())
            );
        }

        // Render Filter pill
        if (filterRect != null) {
            boolean fHov = filterRect.contains(mouseX, mouseY);
            boolean activeFilter = filterMode != FilterMode.all;
            OreUiPaint.raisedButton(
                canvas,
                filterRect.x(),
                filterRect.y(),
                filterRect.width(),
                filterRect.height(),
                p,
                activeFilter,
                fHov,
                false,
                false
            );
            int fColor = filterMode == FilterMode.missing
                    ? p.ruleHardBan()
                    : (filterMode == FilterMode.ready ? p.costS() : (fHov ? p.textPrimary() : p.textSecondary()));
            ChromeText.drawFitted(
                canvas,
                filterComp,
                filterRect.x() + 4,
                ChromeText.centeredY(btnY, btnH, canvas),
                Math.max(12, filterRect.width() - 8),
                fColor
            );
        }

        // Render animated Search button / expanded search input field
        boolean sHover = searchRect.contains(mouseX, mouseY);
        if (anim < 0.12f) {
            OreUiPaint.raisedButton(
                canvas,
                searchRect.x(),
                searchRect.y(),
                searchRect.width(),
                searchRect.height(),
                p,
                false,
                sHover,
                false,
                false
            );
            drawSearchGlyph(
                canvas,
                searchRect.x() + 4,
                searchRect.y() + 4,
                sHover ? p.textPrimary() : p.textSecondary()
            );
        } else {
            OreUiPaint.searchField(
                canvas,
                searchRect.x(),
                searchRect.y(),
                searchRect.width(),
                searchRect.height(),
                p,
                searchOpen
            );
            drawSearchGlyph(canvas, searchRect.x() + 4, searchRect.y() + 4, p.focusRing());

            boolean showFieldContents = searchOpen || !searchQuery.isEmpty() || anim >= 0.55f;
            int clearReserve = 0;
            if (showFieldContents && curSearchW >= 38) {
                searchClearRect = new Rect(searchRect.right() - 14, btnY + 1, 13, btnH - 2);
                boolean cHov = searchClearRect.contains(mouseX, mouseY);
                Component xMark = Component.literal("\u00d7");
                int xw = ChromeText.width(canvas, xMark);
                ChromeText.draw(
                    canvas,
                    xMark,
                    searchClearRect.x() + Math.max(1, (searchClearRect.width() - xw) / 2),
                    ChromeText.centeredY(btnY, btnH, canvas),
                    cHov ? p.textPrimary() : p.textMuted()
                );
                clearReserve = 15;
            }

            int textX = searchRect.x() + 15;
            int textW = Math.max(0, curSearchW - 17 - clearReserve);
            if (showFieldContents && textW > 8) {
                boolean empty = searchQuery.isEmpty();
                Component shownText = empty
                        ? CirrusLangs.Execution.Cockpit.matSearchPlaceholder.get()
                        : Component.literal(searchQuery);
                int textColor = empty ? p.textMuted() : p.textPrimary();
                ChromeText.drawFitted(
                    canvas,
                    shownText,
                    textX,
                    ChromeText.centeredY(btnY, btnH, canvas),
                    textW,
                    textColor
                );
                if (searchOpen && !OreUiMotion.reducedMotion() && ((OreUiMotion.nowMs() / 530L) % 2L == 0L)) {
                    int caretX = textX + (empty ? 0 : Math.min(textW - 1, ChromeText.width(canvas, shownText)));
                    canvas.fill(caretX, btnY + 3, 1, btnH - 6, p.focusRing());
                }
            }
        }
    }

    private static void drawSearchGlyph(SceneCanvas canvas, int x, int y, int color) {
        canvas.strokeRect(x, y, 6, 6, color, 1);
        canvas.fill(x + 5, y + 5, 2, 2, color);
        canvas.fill(x + 6, y + 6, 2, 2, color);
    }

    private void drawMaterialRow(
        SceneCanvas canvas,
        CirrusPalette p,
        Rect r,
        OverlayMaterialRequirement req,
        int mouseX,
        int mouseY,
        float partialTicks
    ) {
        boolean hov = r.contains(mouseX, mouseY);
        boolean missing = req.missing() > 0L;
        OreUiPaint.raisedCard(canvas, r.x(), r.y(), r.width(), r.height(), p, hov, false);

        int slotX = r.x() + 4;
        int slotY = r.y() + (r.height() - slotSize) / 2;
        OreUiPaint.slot(canvas, slotX, slotY, slotSize, p);
        ExecutionOverlayContentHelper
                .renderRecipeContent(canvas, req.material(), slotX + 1, slotY + 1, 16, 16, partialTicks);
        if (req.required() > 1L) {
            OreUiSlotAmount.drawInSlot(canvas, slotX, slotY, slotSize, req.required(), p);
        }
        if (missing) {
            canvas.strokeRect(
                slotX + 1,
                slotY + 1,
                slotSize - 2,
                slotSize - 2,
                OreUiPaint.withAlpha(p.ruleHardBan(), 0.55f),
                1
            );
        }

        Component badge = missing
                ? CirrusLangs.Execution.Cockpit.matBadgeMissing.get(formatCount(req.missing()))
                : Component.literal("\u2713 " + formatCount(req.available()));
        int bw = ChromeText.width(canvas, badge) + 8;
        int bx = r.right() - bw - 4;
        OreUiPaint.tagPill(canvas, bx, r.y() + 5, 14, badge, missing ? p.ruleHardBan() : p.costS(), p);

        int textX = slotX + slotSize + 5;
        int textW = Math.max(16, bx - textX - 4);
        String name = ExecutionOverlayContentHelper.label(req.material());
        ChromeText.drawFitted(canvas, Component.literal(name), textX, r.y() + 4, textW, p.textPrimary());

        int barY = r.y() + 17;
        int barH = 3;
        canvas.fill(textX, barY, textW, barH, p.recessedSurface());
        double ratio = readinessRatio(req);
        int fillW = Math.round((float) (textW * ratio));
        if (fillW > 0) {
            canvas.fill(textX, barY, fillW, barH, missing ? p.costH() : p.costS());
        }
        if (textW >= 78) {
            Component counts = Component.literal(formatCount(req.available()) + "/" + formatCount(req.required()));
            int cw = ChromeText.width(canvas, counts);
            if (cw + 12 < textW) {
                ChromeText.draw(canvas, counts, textX + textW - cw, r.y() + 4, p.textMuted());
            }
        }
    }

    private static String formatCount(long count) {
        if (count <= 0L) {
            return "0";
        }
        if (count == 1L) {
            return "1";
        }
        return OreUiSlotAmount.format(count);
    }
}
