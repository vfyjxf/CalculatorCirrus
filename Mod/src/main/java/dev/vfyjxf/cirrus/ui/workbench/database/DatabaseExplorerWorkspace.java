package dev.vfyjxf.cirrus.ui.workbench.database;

import dev.vfyjxf.cirrus.api.content.render.StackRenderer;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.database.DatabaseCategoryTree;
import dev.vfyjxf.cirrus.api.recipe.machine.ItemMachineEntry;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.DatabasePage;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchBoxWidget;
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
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

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
 * Master-detail Recipe Database explorer: collapsible hierarchical folder sidebar
 * ({@code Systems} folder tree, {@code Mods}, {@code Tags} technical axes), quick trait filter strip,
 * and {@link DatabasePage} results.
 */
public final class DatabaseExplorerWorkspace extends WidgetGroup<Widget> {

    public static final int sidebarExpandedWidth = 216;
    public static final int sidebarCollapsedWidth = 22;
    public static final int filterStripHeight = 24;

    public enum GroupBy {
        system, mod, tag
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
    private String sidebarSearchQuery = "";
    private final Set<String> expandedFolders = new LinkedHashSet<>();
    private final Set<String> collapsedFolders = new LinkedHashSet<>();
    private final Map<String, List<ContentStack<Item, ItemStack>>> systemMachineCache = new HashMap<>();
    private int cachedMachineRecipeCount = -1;
    private long cycleEpochSecond = System.currentTimeMillis() / 1000L;

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

    public List<DatabaseCategoryTree.CategoryNode> activeTreeRoots() {
        return switch (groupBy) {
            case system -> databasePage.systemCategoryTree();
            case mod -> databasePage.modCategoryTree();
            case tag -> databasePage.tagCategoryTree();
        };
    }

    public List<DatabaseCategoryTree.FlatRow> activeTreeRows() {
        return DatabaseCategoryTree.flatten(
            activeTreeRoots(),
            expandedFolders,
            collapsedFolders,
            sidebarSearchQuery,
            this::machineLabelsForNode
        );
    }

    public SearchBoxWidget sidebarSearchBox() {
        return sidebar.searchField;
    }

    List<ContentStack<Item, ItemStack>> machinesForNode(DatabaseCategoryTree.CategoryNode node) {
        if (node.kind() != DatabaseCategoryTree.NodeKind.system || !node.key().startsWith("sys:")) {
            return List.of();
        }
        int currentTotal = databasePage.totalIndexedRecipeCount();
        if (currentTotal != cachedMachineRecipeCount) {
            systemMachineCache.clear();
            cachedMachineRecipeCount = currentTotal;
        }
        List<ContentStack<Item, ItemStack>> cached = systemMachineCache.get(node.key());
        if (cached != null) {
            return cached;
        }
        Namespace systemId = Namespace.maybe(node.key().substring(4));
        if (systemId == null) {
            return List.of();
        }
        List<ContentStack<Item, ItemStack>> resolved = resolveMachinesForSystem(systemId);
        if (!resolved.isEmpty() || currentTotal > 0) {
            systemMachineCache.put(node.key(), resolved);
        }
        return resolved;
    }

    List<String> machineLabelsForNode(DatabaseCategoryTree.CategoryNode node) {
        List<ContentStack<Item, ItemStack>> machines = machinesForNode(node);
        if (machines.isEmpty()) {
            return List.of();
        }
        List<String> labels = new ArrayList<>(machines.size());
        for (ContentStack<Item, ItemStack> stack : machines) {
            labels.add(machineDisplayName(stack).getString());
        }
        return List.copyOf(labels);
    }

    private static List<ContentStack<Item, ItemStack>> resolveMachinesForSystem(Namespace systemId) {
        List<ItemStack> uniqueStacks = new ArrayList<>();
        try {
            RecipeApi api = RecipeApi.instance();
            RecipeSystem<?> system = api.findSystem(systemId);
            if (system != null) {
                appendMachineEntry(uniqueStacks, system.conventionMachine());
            }
            for (MachineEntry entry : api.getMachines(systemId, true)) {
                appendMachineEntry(uniqueStacks, entry);
            }
        } catch (RuntimeException | LinkageError ignored) {
            return List.of();
        }
        if (uniqueStacks.isEmpty()) {
            return List.of();
        }
        List<ContentStack<Item, ItemStack>> out = new ArrayList<>(uniqueStacks.size());
        for (ItemStack stack : uniqueStacks) {
            ContentStack<Item, ItemStack> cs = ContentStack.fromStack(stack);
            if (!cs.isEmpty()) {
                out.add(cs);
            }
        }
        return List.copyOf(out);
    }

    private static void appendMachineEntry(List<ItemStack> out, @Nullable MachineEntry entry) {
        if (entry == null || entry.isEmpty()) {
            return;
        }
        ItemStack raw = entry instanceof ItemMachineEntry itemEntry
                ? itemEntry.machineStack().value()
                : entry.toItemStack();
        if (raw == null || raw.isEmpty()) {
            return;
        }
        ItemStack copy = raw.copyWithCount(1);
        for (ItemStack existing : out) {
            if (ItemStack.isSameItemSameComponents(existing, copy)) {
                return;
            }
        }
        out.add(copy);
    }

    private static Component machineDisplayName(ContentStack<Item, ItemStack> stack) {
        try {
            Component name = stack.value().getHoverName();
            if (name != null && !name.getString().isBlank()) {
                return name;
            }
        } catch (RuntimeException ignored) {
            // Headless fallback
        }
        return Component.literal(stack.toString());
    }

    private int activeMachineIndex(int machineCount, boolean shiftHeld) {
        if (!shiftHeld) {
            cycleEpochSecond = System.currentTimeMillis() / 1000L;
        }
        return machineCount <= 1 ? 0 : (int) Math.floorMod(cycleEpochSecond, machineCount);
    }

    private static boolean isShiftHeld() {
        try {
            return Screen.hasShiftDown();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Builds the rich OreUI tooltip for a sidebar system node. When a system defines multiple
     * {@link MachineEntry} items, embeds an interactive {@link OreUiRichTooltip.Block.ItemGrid}
     * directly inside the tooltip (matching JEI's {@code TagContentTooltipComponent} +
     * {@code InteractiveIngredientTooltip}: the exact same tooltip pins in-place on {@code Shift}).
     */
    public static OreUiRichTooltip buildSystemNodeTooltip(
        DatabaseCategoryTree.CategoryNode node,
        @Nullable String breadcrumb,
        List<ContentStack<Item, ItemStack>> machines,
        int activeIndex,
        boolean shiftHeld,
        int totalRecipes
    ) {
        return buildSystemNodeTooltip(node, breadcrumb, machines, activeIndex, shiftHeld, totalRecipes, null);
    }

    public static OreUiRichTooltip buildSystemNodeTooltip(
        DatabaseCategoryTree.CategoryNode node,
        @Nullable String breadcrumb,
        List<ContentStack<Item, ItemStack>> machines,
        int activeIndex,
        boolean shiftHeld,
        int totalRecipes,
        @Nullable Consumer<ContentStack<Item, ItemStack>> onPickMachine
    ) {
        int safeTotal = Math.max(1, totalRecipes);
        double share = Math.min(1.0, node.count() / (double) safeTotal);
        OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder(node.label())
                .badge(node.count() + " RECIPES", OreUiRichTooltip.Tone.accent);
        if (breadcrumb != null && !breadcrumb.isBlank()) {
            builder.subtitle(breadcrumb);
        }
        if (!machines.isEmpty()) {
            int clampedIdx = Math.floorMod(activeIndex, machines.size());
            ContentStack<Item, ItemStack> active = machines.get(clampedIdx);
            builder.icon(active);
            builder.meter(
                Component.literal("Catalog Share"),
                share,
                Component.literal(Math.round(share * 100.0) + "%"),
                OreUiRichTooltip.Tone.positive
            );
            if (machines.size() == 1) {
                builder.itemList(
                    Component.literal("Machine"),
                    List.of(
                        new OreUiRichTooltip.ItemEntry(
                            active,
                            machineDisplayName(active),
                            null,
                            OreUiRichTooltip.Tone.muted,
                            null
                        )
                    ),
                    1
                );
                builder.footer("Click to filter database results");
            } else {
                List<ContentStack<Item, ItemStack>> snapshot = List.copyOf(machines);
                OreUiRichTooltip.GridInteraction interaction = new OreUiRichTooltip.GridInteraction() {
                    @Override
                    public @Nullable OreUiRichTooltip tooltipForItem(int index, ContentStack<?, ?> item) {
                        if (index < 0 || index >= snapshot.size()) {
                            return null;
                        }
                        return buildMachineSlotTooltip(
                            snapshot.get(index),
                            index,
                            snapshot.size(),
                            index == clampedIdx,
                            node.label()
                        );
                    }

                    @Override
                    public void onItemClick(int index, ContentStack<?, ?> item) {
                        if (onPickMachine != null && index >= 0 && index < snapshot.size()) {
                            onPickMachine.accept(snapshot.get(index));
                        }
                    }
                };
                builder.itemGrid(
                    Component.literal("Machines (" + snapshot.size() + ")"),
                    snapshot,
                    10,
                    4,
                    clampedIdx,
                    interaction
                );
                builder.footer("Hold [Shift] to pin tooltip · Click a machine to filter");
            }
            return builder.build();
        }
        builder.meter(
            Component.literal("Catalog Share"),
            share,
            Component.literal(Math.round(share * 100.0) + "%"),
            OreUiRichTooltip.Tone.positive
        );
        builder.footer("Click to filter database results");
        return builder.build();
    }

    /**
     * Builds the per-slot rich tooltip shown when hovering an individual machine inside a
     * {@code Shift}-pinned JEI-style interactive tooltip grid.
     */
    public static OreUiRichTooltip buildMachineSlotTooltip(
        ContentStack<Item, ItemStack> machine,
        int index,
        int totalMachines,
        boolean activeInCycle,
        String systemLabel
    ) {
        ItemStack stack = machine.value();
        ResourceLocation itemId = null;
        try {
            itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        } catch (RuntimeException ignored) {
            // Headless fallback
        }
        String badge = activeInCycle
                ? "ACTIVE · #" + (index + 1) + "/" + Math.max(1, totalMachines)
                : "#" + (index + 1) + "/" + Math.max(1, totalMachines);
        OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder(machineDisplayName(machine))
                .icon(machine)
                .badge(badge, activeInCycle ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.accent);
        if (itemId != null) {
            builder.subtitle(itemId.toString());
            builder.keyValue("Mod", itemId.getNamespace(), OreUiRichTooltip.Tone.secondary);
        }
        builder.keyValue("System", systemLabel, OreUiRichTooltip.Tone.accent);
        builder.footer("Click to filter systems using this machine");
        return builder.build();
    }

    public void toggleFolder(DatabaseCategoryTree.CategoryNode folder) {
        boolean currentlyExpanded = DatabaseCategoryTree.isFolderExpanded(folder, expandedFolders, collapsedFolders);
        setFolderExpanded(folder.key(), !currentlyExpanded);
    }

    public void setFolderExpanded(String folderKey, boolean expanded) {
        if (expanded) {
            collapsedFolders.remove(folderKey);
            expandedFolders.add(folderKey);
        } else {
            expandedFolders.remove(folderKey);
            collapsedFolders.add(folderKey);
        }
    }

    public List<DatabasePage.ExplorerCategorySummary> activeCategories() {
        List<DatabasePage.ExplorerCategorySummary> all = groupBy == GroupBy.mod
                ? databasePage.modCategories()
                : databasePage.systemCategories();
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
        return databasePage.categoryBreadcrumb(databasePage.activeCategoryFilter());
    }

    private final class CategorySidebar extends WidgetGroup<Widget> implements OreUiTooltipProvider {
        private static final int pad = 5;
        private static final int headerH = 22;
        private static final int switchH = 18;
        private static final int searchH = 18;
        private static final int rowH = 20;
        private static final int indentStep = 11;

        private final OreUiMotion.SmoothValue switchIndicatorX = new OreUiMotion.SmoothValue();
        private final OreUiMotion.SmoothValue switchIndicatorW = new OreUiMotion.SmoothValue();
        private final List<CategoryRowHit> rowHits = new ArrayList<>();
        private final SearchBoxWidget searchField;
        private @Nullable Rect collapseRect;
        private @Nullable Rect systemTabRect;
        private @Nullable Rect modTabRect;
        private @Nullable Rect tagTabRect;
        private boolean syncingSearch;

        private record MachineIconDraw(ContentStack<Item, ItemStack> stack, int x, int y, int size) {}

        private record CategoryRowHit(
            Rect rect,
            @Nullable Rect toggleRect,
            DatabaseCategoryTree.@Nullable CategoryNode node,
            @Nullable String key,
            String label,
            int count
        ) {}

        private CategorySidebar() {
            setInteractive(true);
            DatabaseCategorySuggestionSource suggestionSource = new DatabaseCategorySuggestionSource(
                DatabaseExplorerWorkspace.this::activeTreeRoots,
                DatabaseExplorerWorkspace.this::machineLabelsForNode
            );
            searchField = new SearchBoxWidget()
                    .setPlaceholder("Filter systems / machines...")
                    .highlighter(DatabaseCategorySuggestionSource::highlight)
                    .suggestionSource(suggestionSource)
                    .suggestionViewport(() -> {
                        Rect bounds = DatabaseExplorerWorkspace.this.absoluteBounds();
                        if (bounds.width() > 0 && bounds.height() > 0) {
                            return bounds;
                        }
                        return new Rect(
                            0,
                            0,
                            Math.max(sidebarExpandedWidth + 80, DatabaseExplorerWorkspace.this.width()),
                            Math.max(200, DatabaseExplorerWorkspace.this.height())
                        );
                    })
                    .onQueryChanged(text -> {
                        if (!syncingSearch) {
                            sidebarSearchQuery = text == null ? "" : text;
                            sidebarScroll = 0;
                        }
                    })
                    .onEnterPressed(this::applyTopSearchMatch);
            searchField.useStyle(
                positionAbsolute(),
                insetLeft(pad),
                insetRight(pad),
                insetTop(headerH + switchH + 8),
                heightOf(searchH)
            );
            addWidget(searchField);

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
                if (tagTabRect != null && tagTabRect.contains(local.x(), local.y())) {
                    setGroupBy(GroupBy.tag);
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                for (CategoryRowHit hit : rowHits) {
                    if (hit.toggleRect() != null
                            && hit.node() != null
                            && hit.toggleRect().contains(local.x(), local.y())) {
                        toggleFolder(hit.node());
                        CirrusUiSound.playClickSound();
                        ctx.consume();
                        return EventDispatch.consumed;
                    }
                    if (hit.rect().contains(local.x(), local.y())) {
                        if (hit.node() != null && hit.node().isFolder() && clickCount >= 2) {
                            toggleFolder(hit.node());
                        }
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
                int total = activeTreeRows().size();
                int visible = Math.max(1, (height() - headerH - switchH - searchH - rowH - 30) / rowH);
                int maxScroll = Math.max(0, total - visible);
                sidebarScroll = Math.max(0, Math.min(maxScroll, sidebarScroll + step));
                ctx.consume();
                return EventDispatch.consumed;
            });
        }

        private void applyTopSearchMatch(String rawQuery) {
            String trimmed = rawQuery == null ? "" : rawQuery.trim();
            if (trimmed.isEmpty()) {
                databasePage.setActiveCategoryFilter(null);
                return;
            }
            List<DatabaseCategoryTree.FlatRow> rows = activeTreeRows();
            if (rows.isEmpty()) {
                return;
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            DatabaseCategoryTree.CategoryNode best = null;
            for (DatabaseCategoryTree.FlatRow row : rows) {
                DatabaseCategoryTree.CategoryNode n = row.node();
                if (n.label().equalsIgnoreCase(trimmed)
                        || n.key().equalsIgnoreCase(trimmed)
                        || n.key().toLowerCase(Locale.ROOT).endsWith(":" + lower)) {
                    best = n;
                    break;
                }
            }
            if (best == null) {
                for (DatabaseCategoryTree.FlatRow row : rows) {
                    if (!row.node().isFolder()) {
                        best = row.node();
                        break;
                    }
                }
            }
            if (best == null) {
                best = rows.getFirst().node();
            }
            databasePage.setActiveCategoryFilter(best.key());
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

        private boolean isInteractiveTooltipPinned() {
            return shellContext instanceof WorkbenchShellWidget shell
                    && shell.tooltipOverlay().isInteractiveTooltipPinned();
        }

        @Override
        public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
            if (sidebarCollapsed) {
                return OreUiRichTooltip.builder("Expand Category Sidebar")
                        .badge("EXPLORER", OreUiRichTooltip.Tone.accent)
                        .text("Browse recipes grouped by System folders, Mods, or Technical Tags.", OreUiRichTooltip.Tone.secondary)
                        .build();
            }
            if (collapseRect != null && collapseRect.contains(localX, localY)) {
                return OreUiRichTooltip.builder("Collapse Category Sidebar").badge("FOLD", OreUiRichTooltip.Tone.accent)
                        .text("Gives full horizontal width to the recipe grid.", OreUiRichTooltip.Tone.secondary)
                        .build();
            }
            int total = Math.max(1, databasePage.totalIndexedRecipeCount());
            boolean shiftHeld = isShiftHeld();
            for (CategoryRowHit hit : rowHits) {
                if (hit.rect().contains(localX, localY)) {
                    DatabaseCategoryTree.CategoryNode node = hit.node();
                    if (node != null && node.kind() == DatabaseCategoryTree.NodeKind.system) {
                        List<ContentStack<Item, ItemStack>> machines = machinesForNode(node);
                        int activeIdx = activeMachineIndex(machines.size(), shiftHeld || isInteractiveTooltipPinned());
                        String breadcrumb = hit.key() == null ? null : databasePage.categoryBreadcrumb(hit.key());
                        return buildSystemNodeTooltip(
                            node,
                            breadcrumb,
                            machines,
                            activeIdx,
                            shiftHeld,
                            total,
                            machine -> setSidebarSearchQuery("machine:" + machineDisplayName(machine).getString())
                        );
                    }
                    double share = Math.min(1.0, hit.count() / (double) total);
                    boolean isFolder = node != null && node.isFolder();
                    OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder(hit.label())
                            .badge(
                                isFolder ? "FOLDER · " + hit.count() : hit.count() + " RECIPES",
                                OreUiRichTooltip.Tone.accent
                            );
                    if (hit.key() != null) {
                        builder.subtitle(databasePage.categoryBreadcrumb(hit.key()));
                    }
                    builder.meter(
                        Component.literal("Catalog Share"),
                        share,
                        Component.literal(Math.round(share * 100.0) + "%"),
                        OreUiRichTooltip.Tone.positive
                    );
                    if (isFolder && node != null) {
                        builder.keyValue("Sub-categories", String.valueOf(node.children().size()), OreUiRichTooltip.Tone.secondary);
                        builder.footer("Click to filter folder · Click arrow or double-click to fold");
                    } else {
                        builder.footer("Click to filter database results");
                    }
                    return builder.build();
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
            boolean tooltipPinned = isInteractiveTooltipPinned();
            int effMouseX = tooltipPinned ? Integer.MIN_VALUE : mouseX;
            int effMouseY = tooltipPinned ? Integer.MIN_VALUE : mouseY;
            rowHits.clear();
            OreUiPaint.raisedCard(canvas, 0, 0, w, h, p, false, false);

            searchField.setVisible(!sidebarCollapsed);
            if (sidebarCollapsed) {
                boolean hover = effMouseX >= 0 && effMouseX < w && effMouseY >= 0 && effMouseY < h;
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
            boolean collapseHover = collapseRect.contains(effMouseX, effMouseY);
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

            // GroupBy 3-way segmented switch: [Systems] [Mods] [Tags]
            int swY = headerH + 4;
            int innerW = Math.max(48, w - pad * 2);
            int gapPx = 2;
            int tabW = (innerW - gapPx * 2) / 3;
            int lastW = innerW - tabW * 2 - gapPx * 2;
            systemTabRect = new Rect(pad, swY, tabW, switchH);
            modTabRect = new Rect(pad + tabW + gapPx, swY, tabW, switchH);
            tagTabRect = new Rect(pad + (tabW + gapPx) * 2, swY, lastW, switchH);
            drawSwitchTab(canvas, p, systemTabRect, "Systems", groupBy == GroupBy.system, effMouseX, effMouseY);
            drawSwitchTab(canvas, p, modTabRect, "Mods", groupBy == GroupBy.mod, effMouseX, effMouseY);
            drawSwitchTab(canvas, p, tagTabRect, "Tags", groupBy == GroupBy.tag, effMouseX, effMouseY);
            Rect activeTab = switch (groupBy) {
                case system -> systemTabRect;
                case mod -> modTabRect;
                case tag -> tagTabRect;
            };
            int gx = Math.round(switchIndicatorX.update(activeTab.x() + 2, 22f));
            int gw = Math.round(switchIndicatorW.update(Math.max(8, activeTab.width() - 4), 22f));
            canvas.fill(gx, activeTab.bottom() - 2, gw, 2, p.focusRing());

            // Search box spacing (SearchBoxWidget renders its own OreUI recessed chrome & popup)
            int searchY = swY + switchH + 4;

            // "All Recipes" pinned row + hierarchical folder tree
            int listTop = searchY + searchH + 4;
            int listH = Math.max(24, h - listTop - pad);
            OreUiPaint.searchField(canvas, pad, listTop, innerW, listH, p, false);

            String activeKey = databasePage.activeCategoryFilter();
            int ry = listTop + 2;
            Rect allRect = new Rect(pad + 2, ry, innerW - 4, rowH - 1);
            drawAllRecipesRow(canvas, p, allRect, total, activeKey == null, effMouseX, effMouseY);
            rowHits.add(new CategoryRowHit(allRect, null, null, null, "All Recipes", total));
            ry += rowH;
            canvas.fill(pad + 4, ry, innerW - 8, 1, OreUiPaint.cardDivider(p));
            ry += 2;

            List<DatabaseCategoryTree.FlatRow> rows = activeTreeRows();
            int listBottom = listTop + listH - 2;
            int visible = Math.max(1, (listBottom - ry) / rowH);
            int maxScroll = Math.max(0, rows.size() - visible);
            sidebarScroll = Math.max(0, Math.min(maxScroll, sidebarScroll));
            boolean showScrollbar = rows.size() > visible;
            int rowRightInset = showScrollbar ? 6 : 2;
            boolean shiftHeld = isShiftHeld();
            List<MachineIconDraw> pendingMachineIcons = new ArrayList<>();

            if (rows.isEmpty()) {
                ChromeText.drawFitted(
                    canvas,
                    Component.literal(sidebarSearchQuery.isBlank() ? "Indexing recipes..." : "No matching categories"),
                    pad + 8,
                    ry + 6,
                    innerW - 16,
                    p.textMuted()
                );
            } else {
                int listStartY = ry;
                for (int i = 0; i < visible && sidebarScroll + i < rows.size(); i++) {
                    DatabaseCategoryTree.FlatRow flat = rows.get(sidebarScroll + i);
                    DatabaseCategoryTree.CategoryNode node = flat.node();
                    Rect r = new Rect(pad + 2, ry, innerW - 2 - rowRightInset, rowH - 1);
                    if (r.bottom() > listBottom + 1) {
                        break;
                    }
                    boolean selected = node.key().equals(activeKey);
                    Rect toggleRect = drawTreeRow(
                        canvas,
                        p,
                        r,
                        flat,
                        selected,
                        sidebarScroll + i,
                        effMouseX,
                        effMouseY,
                        shiftHeld || tooltipPinned,
                        pendingMachineIcons
                    );
                    CategoryRowHit hit = new CategoryRowHit(r, toggleRect, node, node.key(), node.label(), node.count());
                    rowHits.add(hit);
                    ry += rowH;
                }
                if (showScrollbar) {
                    int trackX = pad + innerW - 5;
                    int trackH = Math.max(12, listBottom - listStartY);
                    int thumbH = Math.max(14, Math.round(trackH * (visible / (float) rows.size())));
                    int thumbY = listStartY + Math.round((trackH - thumbH) * (sidebarScroll / (float) Math.max(1, maxScroll)));
                    canvas.fill(trackX, listStartY, 3, trackH, OreUiPaint.withAlpha(p.hairlineBorder(), 0.35f));
                    canvas.fill(trackX, thumbY, 3, thumbH, OreUiPaint.withAlpha(p.focusRing(), 0.72f));
                }
            }
            renderPendingMachineIcons(canvas, pendingMachineIcons, partialTicks);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static void renderPendingMachineIcons(
            SceneCanvas canvas,
            List<MachineIconDraw> draws,
            float partialTicks
        ) {
            if (draws.isEmpty()) {
                return;
            }
            GuiGraphics graphics = canvas.layeredGraphics();
            for (MachineIconDraw draw : draws) {
                try {
                    StackRenderer renderer = StackRenderer.getStackRenderer(draw.stack().type());
                    renderer.renderBaseAt(
                        graphics,
                        draw.stack(),
                        draw.x(),
                        draw.y(),
                        draw.size(),
                        draw.size(),
                        partialTicks
                    );
                } catch (RuntimeException ignored) {
                    // Skip icon rendering in headless unit tests.
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
            Component comp = ChromeText.fit(canvas, Component.literal(label), Math.max(8, rect.width() - 4));
            ChromeText.draw(
                canvas,
                comp,
                rect.x() + Math.max(2, (rect.width() - ChromeText.width(canvas, comp)) / 2),
                ChromeText.centeredY(rect.y(), rect.height() - 1, canvas),
                selected ? p.textPrimary() : p.textSecondary()
            );
        }

        private void drawAllRecipesRow(
            SceneCanvas canvas,
            CirrusPalette p,
            Rect rect,
            int count,
            boolean selected,
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
            canvas.fill(rect.x() + 5, rect.y() + (rect.height() - 4) / 2, 4, 4, p.focusRing());
            Component cntComp = Component.literal(Integer.toString(count));
            int cntW = ChromeText.width(canvas, cntComp) + 8;
            ChromeText.drawFitted(
                canvas,
                Component.literal("All Recipes"),
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

        private @Nullable Rect drawTreeRow(
            SceneCanvas canvas,
            CirrusPalette p,
            Rect rect,
            DatabaseCategoryTree.FlatRow flat,
            boolean selected,
            int visualIndex,
            int mouseX,
            int mouseY,
            boolean shiftHeld,
            List<MachineIconDraw> pendingMachineIcons
        ) {
            DatabaseCategoryTree.CategoryNode node = flat.node();
            boolean hover = rect.contains(mouseX, mouseY);
            if (selected) {
                canvas.fill(rect.x(), rect.y(), rect.width(), rect.height(), p.cardSurfaceSelected());
                canvas.strokeRect(rect.x(), rect.y(), rect.width(), rect.height(), p.focusRing(), 1);
            } else if (hover) {
                canvas.fill(rect.x(), rect.y(), rect.width(), rect.height(), p.cardSurfaceHover());
            }

            int indentX = rect.x() + 3 + flat.depth() * indentStep;
            int accent = nodeAccent(p, node, visualIndex);
            Rect toggleRect = null;
            int labelX;

            if (flat.expandable()) {
                toggleRect = new Rect(indentX, rect.y(), 13, rect.height());
                boolean toggleHover = toggleRect.contains(mouseX, mouseY);
                int arrowColor = toggleHover ? p.focusRing() : p.textSecondary();
                if (flat.expanded()) {
                    ChromeButtons.caretDown(canvas, indentX + 2, rect.y() + (rect.height() - 4) / 2, arrowColor);
                } else {
                    ChromeButtons.chevronRight(canvas, indentX + 4, rect.y() + (rect.height() - 7) / 2, arrowColor);
                }
                drawFolderIcon(canvas, indentX + 13, rect.y() + (rect.height() - 7) / 2, flat.expanded(), selected ? p.focusRing() : accent);
                labelX = indentX + 24;
            } else if (node.kind() == DatabaseCategoryTree.NodeKind.system) {
                List<ContentStack<Item, ItemStack>> machines = machinesForNode(node);
                if (!machines.isEmpty()) {
                    int iconSize = 14;
                    int iconX = indentX + (flat.depth() > 0 ? 2 : 0);
                    int iconY = rect.y() + (rect.height() - iconSize) / 2;
                    int idx = activeMachineIndex(machines.size(), shiftHeld);
                    pendingMachineIcons.add(new MachineIconDraw(machines.get(idx), iconX, iconY, iconSize));
                    labelX = iconX + iconSize + 4;
                } else {
                    int dotX = indentX + (flat.depth() > 0 ? 5 : 2);
                    canvas.fill(dotX, rect.y() + (rect.height() - 4) / 2, 4, 4, selected ? p.focusRing() : accent);
                    labelX = dotX + 8;
                }
            } else {
                int dotX = indentX + (flat.depth() > 0 ? 5 : 2);
                canvas.fill(dotX, rect.y() + (rect.height() - 4) / 2, 4, 4, selected ? p.focusRing() : accent);
                labelX = dotX + 8;
            }

            Component cntComp = Component.literal(Integer.toString(node.count()));
            int cntW = ChromeText.width(canvas, cntComp) + 7;
            int maxLabelW = Math.max(14, rect.right() - cntW - labelX - 2);
            int textColor = selected
                    ? p.textPrimary()
                    : flat.expandable()
                            ? (hover ? p.textPrimary() : p.textPrimary())
                            : (hover ? p.textPrimary() : p.textSecondary());
            ChromeText.drawFitted(
                canvas,
                Component.literal(node.label()),
                labelX,
                ChromeText.centeredY(rect.y(), rect.height(), canvas),
                maxLabelW,
                textColor
            );
            ChromeText.drawRight(
                canvas,
                cntComp,
                rect.right() - 4,
                ChromeText.centeredY(rect.y(), rect.height(), canvas),
                selected ? p.focusRing() : p.textMuted()
            );
            return toggleRect;
        }

        private static void drawFolderIcon(SceneCanvas canvas, int x, int y, boolean open, int color) {
            // Crisp 8x7 folder glyph (tab on top-left + folder body, subtle hollow notch when open)
            canvas.fill(x, y, 3, 1, color);
            canvas.fill(x, y + 1, 8, 1, color);
            if (open) {
                canvas.fill(x, y + 2, 1, 5, color);
                canvas.fill(x + 7, y + 2, 1, 5, color);
                canvas.fill(x + 1, y + 6, 6, 1, color);
                canvas.fill(x + 1, y + 3, 6, 3, OreUiPaint.withAlpha(color, 0.38f));
            } else {
                canvas.fill(x, y + 2, 8, 5, color);
            }
        }

        private static int nodeAccent(CirrusPalette p, DatabaseCategoryTree.CategoryNode node, int index) {
            String key = node.key();
            if (key.startsWith("dir:minecraft") || key.startsWith("sys:minecraft:")) {
                return p.costS();
            }
            if (key.startsWith("dir:jei") || key.startsWith("sys:jei:")) {
                return p.costH();
            }
            if (key.startsWith("dir:debug") || key.contains("debug")) {
                return p.costC();
            }
            if (key.startsWith("dir:native")) {
                return p.costP();
            }
            if (key.startsWith("tagaxis:provenance")) {
                return p.focusRing();
            }
            if (key.startsWith("tagaxis:process")) {
                return p.costP();
            }
            if (key.startsWith("tagaxis:trait")) {
                return p.costS();
            }
            if (key.startsWith("tagaxis:screening")) {
                return p.costH();
            }
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
            int crumbMaxW = Math.min(200, Math.max(56, w / 3));
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
