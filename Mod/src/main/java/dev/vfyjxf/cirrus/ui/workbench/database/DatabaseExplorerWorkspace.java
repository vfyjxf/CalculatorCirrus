package dev.vfyjxf.cirrus.ui.workbench.database;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.DatabasePage;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeButtons;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.texture.ColorTexture;
import dev.vfyjxf.cloudlib.ui.widget.TextFieldWidget;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignSelfStretch;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayFlex;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayNone;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexBasisPx;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetLeft;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetRight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetTop;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.widthOf;

/**
 * Master-detail Recipe Database explorer: collapsible System/Mod category rail, quick trait filter strip, and DatabasePage results.
 */
public final class DatabaseExplorerWorkspace extends WidgetGroup<Widget> {

    public static final int sidebarExpandedWidth = 192;
    public static final int sidebarCollapsedWidth = 22;
    public static final int filterStripHeight = 24;

    public enum GroupBy {
        system, mod
    }

    private final WorkbenchShellContext shellContext;
    private final DatabasePage databasePage;
    private final CategorySidebar sidebar;
    private final FilterStrip filterStrip;

    private final WidgetGroup<Widget> mainColumn;

    private GroupBy groupBy = GroupBy.system;
    private boolean sidebarCollapsed;
    private boolean lastInSubPage;
    private int sidebarScroll;

    public DatabaseExplorerWorkspace(WorkbenchShellContext shellContext, DatabasePage databasePage) {
        this.shellContext = Objects.requireNonNull(shellContext, "shellContext");
        this.databasePage = Objects.requireNonNull(databasePage, "databasePage");
        addStyleClass("workbench-database-explorer");
        useStyle(sizeFull(), flexRow(), gap(4), padding(4), minWidth(0), minHeight(0));

        this.sidebar = new CategorySidebar();
        sidebar.useStyle(widthOf(sidebarExpandedWidth), flexShrink(0), alignSelfStretch(), minHeight(0));
        addWidget(sidebar);

        this.mainColumn = new WidgetGroup<>();
        mainColumn.useStyle(
            flexColumn(),
            flexGrow(1),
            flexShrink(1),
            flexBasisPx(0f),
            alignSelfStretch(),
            gap(4),
            minWidth(0),
            minHeight(0)
        );

        this.filterStrip = new FilterStrip();
        filterStrip.useStyle(heightOf(filterStripHeight), flexShrink(0), alignSelfStretch(), minWidth(0));
        mainColumn.addWidget(filterStrip);

        WidgetGroup<Widget> pageHost = new WidgetGroup<>();
        pageHost.useStyle(
            flexColumn(),
            flexGrow(1),
            flexShrink(1),
            flexBasisPx(0f),
            alignSelfStretch(),
            minWidth(0),
            minHeight(0)
        );
        pageHost.addWidget(databasePage);
        mainColumn.addWidget(pageHost);
        addWidget(mainColumn);

        Runnable explorerListener = this::syncSubPageChrome;
        onMount((scene, mountCtx, handle) -> {
            databasePage.addExplorerListener(explorerListener);
            syncSubPageChrome();
        });
        onUnmount(() -> databasePage.removeExplorerListener(explorerListener));
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        syncSubPageChrome();
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
    }

    private void syncSubPageChrome() {
        boolean inSub = databasePage.inSubPage();
        if (inSub == lastInSubPage) {
            return;
        }
        lastInSubPage = inSub;
        useStyle(sizeFull(), flexRow(), gap(inSub ? 0 : 4), padding(inSub ? 0 : 4), minWidth(0), minHeight(0));
        sidebar.setVisible(!inSub);
        sidebar.useStyle(
            inSub ? displayNone() : displayFlex(),
            widthOf(inSub ? 0 : (sidebarCollapsed ? sidebarCollapsedWidth : sidebarExpandedWidth)),
            flexShrink(0),
            alignSelfStretch(),
            minHeight(0)
        );
        mainColumn.useStyle(
            flexColumn(),
            flexGrow(1),
            flexShrink(1),
            flexBasisPx(0f),
            alignSelfStretch(),
            gap(inSub ? 0 : 4),
            minWidth(0),
            minHeight(0)
        );
        filterStrip.setVisible(!inSub);
        filterStrip.useStyle(
            inSub ? displayNone() : displayFlex(),
            heightOf(inSub ? 0 : filterStripHeight),
            flexShrink(0),
            alignSelfStretch(),
            minWidth(0)
        );
    }

    public DatabasePage databasePage() {
        return databasePage;
    }

    public GroupBy groupBy() {
        return groupBy;
    }

    public void setGroupBy(GroupBy groupBy) {
        this.groupBy = Objects.requireNonNull(groupBy, "groupBy");
        this.sidebarScroll = 0;
    }

    public boolean sidebarCollapsed() {
        return sidebarCollapsed;
    }

    public void setSidebarCollapsed(boolean collapsed) {
        if (this.sidebarCollapsed == collapsed) {
            return;
        }
        this.sidebarCollapsed = collapsed;
        if (!lastInSubPage) {
            sidebar.useStyle(
                widthOf(collapsed ? sidebarCollapsedWidth : sidebarExpandedWidth),
                flexShrink(0),
                alignSelfStretch(),
                minHeight(0)
            );
        }
    }

    private String sidebarSearchQuery = "";

    public List<DatabasePage.ExplorerCategorySummary> activeCategories() {
        List<DatabasePage.ExplorerCategorySummary> all = groupBy == GroupBy.system
                ? databasePage.systemCategories()
                : databasePage.modCategories();
        String query = sidebarSearchQuery.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return all;
        }
        List<DatabasePage.ExplorerCategorySummary> filtered = new ArrayList<>();
        for (DatabasePage.ExplorerCategorySummary cat : all) {
            if (cat.label().toLowerCase(Locale.ROOT).contains(query)
                    || cat.namespace().toLowerCase(Locale.ROOT).contains(query)
                    || cat.key().toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(cat);
            }
        }
        return filtered;
    }

    public String sidebarSearchQuery() {
        return sidebarSearchQuery;
    }

    public void setSidebarSearchQuery(@Nullable String query) {
        this.sidebarSearchQuery = query == null ? "" : query;
        this.sidebarScroll = 0;
        sidebar.syncSearchText(this.sidebarSearchQuery);
    }

    private String activeCategoryLabel() {
        String key = databasePage.activeCategoryFilter();
        if (key == null) {
            return "All Recipes";
        }
        for (DatabasePage.ExplorerCategorySummary item : databasePage.systemCategories()) {
            if (key.equals(item.key())) {
                return "System: " + item.label();
            }
        }
        for (DatabasePage.ExplorerCategorySummary item : databasePage.modCategories()) {
            if (key.equals(item.key())) {
                return "Mod: " + item.label();
            }
        }
        return key;
    }

    private final class CategorySidebar extends WidgetGroup<Widget> implements OreUiTooltipProvider {
        private static final int pad = 5;
        private static final int headerH = 22;
        private static final int switchH = 18;
        private static final int searchH = 18;
        private static final int rowH = 20;

        private final OreUiMotion.SmoothValue switchIndicatorX = new OreUiMotion.SmoothValue();
        private final List<CategoryRowHit> rowHits = new ArrayList<>();
        private final TextFieldWidget searchField;
        private @Nullable Rect collapseRect;
        private @Nullable Rect systemTabRect;
        private @Nullable Rect modTabRect;
        private boolean syncingSearch;

        private record CategoryRowHit(Rect rect, @Nullable String key, String label, int count) {}

        private CategorySidebar() {
            setInteractive(true);
            searchField = TextFieldWidget.create().setMaxLength(48).setPlaceholder("Filter categories...")
                    .onTextChanged(text -> {
                        if (!syncingSearch) {
                            sidebarSearchQuery = text == null ? "" : text;
                            sidebarScroll = 0;
                        }
                    });
            searchField.useStyle(
                positionAbsolute(),
                insetLeft(pad),
                insetRight(pad),
                insetTop(headerH + switchH + 8),
                heightOf(searchH)
            );
            addWidget(searchField);
            syncSearchTheme();

            onMouseClick((input, clickCount, ctx) -> {
                if (!input.isLeftClick()) {
                    return EventDispatch.pass;
                }
                FloatPos local = input.mouseRelative(this);
                if (sidebarCollapsed) {
                    setSidebarCollapsed(false);
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                if (collapseRect != null && collapseRect.contains(local.x(), local.y())) {
                    setSidebarCollapsed(true);
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                if (systemTabRect != null && systemTabRect.contains(local.x(), local.y())) {
                    setGroupBy(GroupBy.system);
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                if (modTabRect != null && modTabRect.contains(local.x(), local.y())) {
                    setGroupBy(GroupBy.mod);
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                for (CategoryRowHit hit : rowHits) {
                    if (hit.rect().contains(local.x(), local.y())) {
                        databasePage.setActiveCategoryFilter(hit.key());
                        CirrusUiSound.playClickSound();
                        ctx.consume();
                        return EventDispatch.consumed;
                    }
                }
                return EventDispatch.pass;
            });
            onMouseScrolled((mx, my, scrollX, scrollY, ctx) -> {
                if (sidebarCollapsed || scrollY == 0.0) {
                    return EventDispatch.pass;
                }
                int step = scrollY > 0.0 ? -1 : 1;
                int total = activeCategories().size();
                int visible = Math.max(1, (height() - headerH - switchH - searchH - rowH - 30) / rowH);
                int maxScroll = Math.max(0, total - visible);
                sidebarScroll = Math.max(0, Math.min(maxScroll, sidebarScroll + step));
                ctx.consume();
                return EventDispatch.consumed;
            });
        }

        private void syncSearchText(String text) {
            if (!Objects.equals(searchField.text(), text)) {
                syncingSearch = true;
                try {
                    searchField.setText(text);
                } finally {
                    syncingSearch = false;
                }
            }
        }

        private void syncSearchTheme() {
            CirrusPalette p = shellContext.palette();
            searchField.setBackgroundTexture(new ColorTexture(0x00000000))
                    .setBorderTexture(new ColorTexture(0x00000000)).setTextColor(p.textPrimary())
                    .setPlaceholderColor(p.textMuted());
        }

        @Override
        public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
            if (sidebarCollapsed) {
                return OreUiRichTooltip.builder("Expand Category Sidebar")
                        .badge("EXPLORER", OreUiRichTooltip.Tone.accent)
                        .text("Browse recipes grouped by System or Mod.", OreUiRichTooltip.Tone.secondary).build();
            }
            if (collapseRect != null && collapseRect.contains(localX, localY)) {
                return OreUiRichTooltip.builder("Collapse Category Sidebar").badge("FOLD", OreUiRichTooltip.Tone.accent)
                        .text("Gives full horizontal width to the recipe grid.", OreUiRichTooltip.Tone.secondary)
                        .build();
            }
            int total = Math.max(1, databasePage.totalIndexedRecipeCount());
            for (CategoryRowHit hit : rowHits) {
                if (hit.rect().contains(localX, localY)) {
                    double share = Math.min(1.0, hit.count() / (double) total);
                    return OreUiRichTooltip.builder(hit.label())
                            .badge(hit.count() + " RECIPES", OreUiRichTooltip.Tone.accent)
                            .meter(
                                Component.literal("Catalog Share"),
                                share,
                                Component.literal(Math.round(share * 100.0) + "%"),
                                OreUiRichTooltip.Tone.positive
                            ).footer("Click to filter database results").build();
                }
            }
            return null;
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            int w = width();
            int h = height();
            if (w <= 0 || h <= 0) {
                return;
            }
            CirrusPalette p = shellContext.palette();
            syncSearchTheme();
            rowHits.clear();
            OreUiPaint.raisedCard(canvas, 0, 0, w, h, p, false, false);

            searchField.setVisible(!sidebarCollapsed);
            if (sidebarCollapsed) {
                boolean hover = mouseX >= 0 && mouseX < w && mouseY >= 0 && mouseY < h;
                OreUiPaint.raisedButton(canvas, 2, 4, Math.max(12, w - 4), 24, p, false, hover, false, false);
                ChromeButtons.doubleChevronRight(
                    canvas,
                    Math.max(4, (w - 7) / 2),
                    12,
                    hover ? p.focusRing() : p.textSecondary()
                );
                return;
            }

            // Header band
            OreUiPaint.headerBand(canvas, 0, 0, w, headerH, p, false);
            collapseRect = new Rect(w - pad - 16, 3, 16, 16);
            boolean collapseHover = collapseRect.contains(mouseX, mouseY);
            OreUiPaint.raisedButton(
                canvas,
                collapseRect.x(),
                collapseRect.y(),
                collapseRect.width(),
                collapseRect.height(),
                p,
                false,
                collapseHover,
                false,
                false
            );
            ChromeButtons.doubleChevronLeft(
                canvas,
                collapseRect.x() + 4,
                collapseRect.y() + 4,
                collapseHover ? p.focusRing() : p.textSecondary()
            );

            int total = databasePage.totalIndexedRecipeCount();
            Component countBadge = Component.literal(Integer.toString(total));
            int badgeW = ChromeText.width(canvas, countBadge) + 8;
            int badgeX = collapseRect.x() - badgeW - 4;
            OreUiPaint.tagPill(canvas, badgeX, 4, 13, countBadge, p.focusRing(), p);
            ChromeText.drawFitted(
                canvas,
                Component.literal("Categories"),
                pad + 2,
                ChromeText.centeredY(0, headerH, canvas),
                Math.max(20, badgeX - pad - 6),
                p.textPrimary()
            );

            // GroupBy segmented switch: [Systems] [Mods]
            int swY = headerH + 4;
            int innerW = Math.max(30, w - pad * 2);
            int halfW = (innerW - 2) / 2;
            systemTabRect = new Rect(pad, swY, halfW, switchH);
            modTabRect = new Rect(pad + halfW + 2, swY, innerW - halfW - 2, switchH);
            drawSwitchTab(canvas, p, systemTabRect, "Systems", groupBy == GroupBy.system, mouseX, mouseY);
            drawSwitchTab(canvas, p, modTabRect, "Mods", groupBy == GroupBy.mod, mouseX, mouseY);
            Rect activeTab = groupBy == GroupBy.system ? systemTabRect : modTabRect;
            int gx = Math.round(switchIndicatorX.update(activeTab.x() + 2, 22f));
            canvas.fill(gx, activeTab.bottom() - 2, Math.max(8, activeTab.width() - 4), 2, p.focusRing());

            // Search box chrome behind TextFieldWidget
            int searchY = swY + switchH + 4;
            OreUiPaint.searchField(canvas, pad, searchY, innerW, searchH, p, searchField.focused());

            // "All Recipes" pinned row + filtered category list
            int listTop = searchY + searchH + 4;
            int listH = Math.max(24, h - listTop - pad);
            OreUiPaint.searchField(canvas, pad, listTop, innerW, listH, p, false);

            String activeKey = databasePage.activeCategoryFilter();
            int ry = listTop + 2;
            Rect allRect = new Rect(pad + 2, ry, innerW - 4, rowH - 1);
            drawCategoryRow(
                canvas,
                p,
                allRect,
                null,
                "All Recipes",
                total,
                activeKey == null,
                p.focusRing(),
                mouseX,
                mouseY
            );
            rowHits.add(new CategoryRowHit(allRect, null, "All Recipes", total));
            ry += rowH;
            canvas.fill(pad + 4, ry, innerW - 8, 1, OreUiPaint.cardDivider(p));
            ry += 2;

            List<DatabasePage.ExplorerCategorySummary> cats = activeCategories();
            int visible = Math.max(1, (listTop + listH - 2 - ry) / rowH);
            int maxScroll = Math.max(0, cats.size() - visible);
            sidebarScroll = Math.max(0, Math.min(maxScroll, sidebarScroll));
            if (cats.isEmpty()) {
                ChromeText.drawFitted(
                    canvas,
                    Component.literal(sidebarSearchQuery.isBlank() ? "Indexing recipes..." : "No matching categories"),
                    pad + 8,
                    ry + 6,
                    innerW - 16,
                    p.textMuted()
                );
            } else {
                for (int i = 0; i < visible && sidebarScroll + i < cats.size(); i++) {
                    DatabasePage.ExplorerCategorySummary cat = cats.get(sidebarScroll + i);
                    Rect r = new Rect(pad + 2, ry, innerW - 4, rowH - 1);
                    if (r.bottom() > listTop + listH - 1) {
                        break;
                    }
                    boolean selected = cat.key().equals(activeKey);
                    int dotColor = categoryAccent(p, sidebarScroll + i);
                    drawCategoryRow(
                        canvas,
                        p,
                        r,
                        cat.key(),
                        cat.label(),
                        cat.count(),
                        selected,
                        dotColor,
                        mouseX,
                        mouseY
                    );
                    rowHits.add(new CategoryRowHit(r, cat.key(), cat.label(), cat.count()));
                    ry += rowH;
                }
            }
        }

        private void drawSwitchTab(
            SceneCanvas canvas,
            CirrusPalette p,
            Rect rect,
            String label,
            boolean selected,
            int mouseX,
            int mouseY
        ) {
            boolean hover = rect.contains(mouseX, mouseY);
            if (selected) {
                OreUiPaint.raisedCard(canvas, rect.x(), rect.y(), rect.width(), rect.height(), p, hover, true);
            } else {
                OreUiPaint.raisedButton(
                    canvas,
                    rect.x(),
                    rect.y(),
                    rect.width(),
                    rect.height(),
                    p,
                    false,
                    hover,
                    false,
                    false
                );
            }
            Component comp = ChromeText.fit(canvas, Component.literal(label), Math.max(8, rect.width() - 6));
            ChromeText.draw(
                canvas,
                comp,
                rect.x() + Math.max(3, (rect.width() - ChromeText.width(canvas, comp)) / 2),
                ChromeText.centeredY(rect.y(), rect.height() - 1, canvas),
                selected ? p.textPrimary() : p.textSecondary()
            );
        }

        private void drawCategoryRow(
            SceneCanvas canvas,
            CirrusPalette p,
            Rect rect,
            @Nullable String key,
            String label,
            int count,
            boolean selected,
            int dotColor,
            int mouseX,
            int mouseY
        ) {
            boolean hover = rect.contains(mouseX, mouseY);
            if (selected) {
                canvas.fill(rect.x(), rect.y(), rect.width(), rect.height(), p.cardSurfaceSelected());
                canvas.strokeRect(rect.x(), rect.y(), rect.width(), rect.height(), p.focusRing(), 1);
            } else if (hover) {
                canvas.fill(rect.x(), rect.y(), rect.width(), rect.height(), p.cardSurfaceHover());
            }
            canvas.fill(rect.x() + 5, rect.y() + (rect.height() - 4) / 2, 4, 4, selected ? p.focusRing() : dotColor);
            Component cntComp = Component.literal(Integer.toString(count));
            int cntW = ChromeText.width(canvas, cntComp) + 8;
            ChromeText.drawFitted(
                canvas,
                Component.literal(label),
                rect.x() + 13,
                ChromeText.centeredY(rect.y(), rect.height(), canvas),
                Math.max(16, rect.width() - 18 - cntW),
                selected || hover ? p.textPrimary() : p.textSecondary()
            );
            ChromeText.drawRight(
                canvas,
                cntComp,
                rect.right() - 5,
                ChromeText.centeredY(rect.y(), rect.height(), canvas),
                selected ? p.focusRing() : p.textMuted()
            );
        }

        private static int categoryAccent(CirrusPalette p, int index) {
            return switch (index % 5) {
                case 0 -> p.costP();
                case 1 -> p.costS();
                case 2 -> p.costH();
                case 3 -> p.costK();
                default -> p.costC();
            };
        }
    }

    private final class FilterStrip extends Widget {
        private final List<Rect> traitRects = new ArrayList<>();
        private @Nullable Rect clearCatRect;
        private @Nullable Rect planTargetRect;

        private FilterStrip() {
            setInteractive(true);
            onMouseClick((input, clickCount, ctx) -> {
                if (!input.isLeftClick()) {
                    return EventDispatch.pass;
                }
                FloatPos local = input.mouseRelative(this);
                if (clearCatRect != null && clearCatRect.contains(local.x(), local.y())) {
                    databasePage.setActiveCategoryFilter(null);
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                if (planTargetRect != null && planTargetRect.contains(local.x(), local.y())) {
                    shellContext.setMode(WorkbenchMode.planner);
                    if (shellContext instanceof WorkbenchShellWidget shell) {
                        shell.openTargetCatalogPicker();
                    }
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                DatabasePage.QuickFeatureFilter[] values = DatabasePage.QuickFeatureFilter.values();
                for (int i = 0; i < traitRects.size() && i < values.length; i++) {
                    if (traitRects.get(i).contains(local.x(), local.y())) {
                        databasePage.setQuickFeatureFilter(values[i]);
                        CirrusUiSound.playClickSound();
                        ctx.consume();
                        return EventDispatch.consumed;
                    }
                }
                return EventDispatch.pass;
            });
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            int w = width();
            int h = height();
            if (w <= 0 || h <= 0) {
                return;
            }
            CirrusPalette p = shellContext.palette();
            traitRects.clear();
            clearCatRect = null;
            planTargetRect = null;
            OreUiPaint.raisedCard(canvas, 0, 0, w, h, p, false, false);

            int cx = 6;
            int pillY = 4;
            int pillH = Math.max(14, h - 8);
            boolean filtered = databasePage.activeCategoryFilter() != null;
            Component crumb = Component.literal(activeCategoryLabel());
            int crumbMaxW = Math.min(148, Math.max(48, w / 4));
            Component fittedCrumb = ChromeText.fit(canvas, crumb, crumbMaxW);
            int crumbW = ChromeText.width(canvas, fittedCrumb) + (filtered ? 22 : 10);
            if (filtered) {
                clearCatRect = new Rect(cx, pillY, crumbW, pillH);
                boolean hov = clearCatRect.contains(mouseX, mouseY);
                OreUiPaint.selectedToggle(canvas, cx, pillY, crumbW, pillH, p, hov);
                ChromeText.draw(canvas, fittedCrumb, cx + 5, ChromeText.centeredY(pillY, pillH, canvas), p.focusRing());
                ChromeButtons.cross(
                    canvas,
                    cx + crumbW - 10,
                    pillY + (pillH - 5) / 2,
                    5,
                    hov ? p.ruleHardBan() : p.textSecondary()
                );
            } else {
                OreUiPaint.tagPill(canvas, cx, pillY, pillH, fittedCrumb, p.focusRing(), p);
            }
            cx += crumbW + 8;

            // Right-side "Pick Target in Planner" button + live count
            int rightX = w - 6;
            Component planComp = Component.literal("Plan Target");
            int planW = ChromeText.width(canvas, planComp) + 14;
            if (rightX - planW > cx + 60) {
                planTargetRect = new Rect(rightX - planW, pillY, planW, pillH);
                boolean hov = planTargetRect.contains(mouseX, mouseY);
                OreUiPaint.raisedButton(
                    canvas,
                    planTargetRect.x(),
                    planTargetRect.y(),
                    planTargetRect.width(),
                    planTargetRect.height(),
                    p,
                    false,
                    hov,
                    true,
                    false
                );
                ChromeText.draw(
                    canvas,
                    planComp,
                    planTargetRect.x() + 7,
                    ChromeText.centeredButtonY(planTargetRect.y(), planTargetRect.height(), canvas),
                    p.textOnAccent()
                );
                rightX = planTargetRect.x() - 8;
            }
            String statStr = databasePage.visibleResultCount() + " / " + databasePage.totalIndexedRecipeCount();
            Component statComp = Component.literal(statStr);
            int statW = ChromeText.width(canvas, statComp);
            if (rightX - statW > cx + 40) {
                ChromeText
                        .drawRight(canvas, statComp, rightX, ChromeText.centeredY(pillY, pillH, canvas), p.textMuted());
                rightX -= statW + 10;
            }

            // Quick trait filter pills
            DatabasePage.QuickFeatureFilter activeFilter = databasePage.quickFeatureFilter();
            for (DatabasePage.QuickFeatureFilter filter : DatabasePage.QuickFeatureFilter.values()) {
                Component lbl = Component.literal(filter.label());
                int fw = ChromeText.width(canvas, lbl) + 12;
                if (cx + fw > rightX) {
                    break;
                }
                Rect r = new Rect(cx, pillY, fw, pillH);
                traitRects.add(r);
                boolean active = filter == activeFilter;
                boolean hov = r.contains(mouseX, mouseY);
                if (active) {
                    OreUiPaint.selectedToggle(canvas, r.x(), r.y(), r.width(), r.height(), p, hov);
                } else {
                    OreUiPaint.raisedButton(canvas, r.x(), r.y(), r.width(), r.height(), p, false, hov, false, false);
                }
                ChromeText.draw(
                    canvas,
                    lbl,
                    r.x() + 6,
                    active
                            ? ChromeText.centeredY(r.y(), r.height(), canvas)
                            : ChromeText.centeredButtonY(r.y(), r.height(), canvas),
                    active ? p.focusRing() : hov ? p.textPrimary() : p.textSecondary()
                );
                cx += fw + 3;
            }
        }
    }
}
