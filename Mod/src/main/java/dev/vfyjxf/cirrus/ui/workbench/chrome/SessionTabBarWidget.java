package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusThemeIcons;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellLayout;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.SessionTabManager;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyles;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetLeft;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetTop;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Z2 top project tab bar: multi-target baskets, [+] create, four context-menu actions, theme/Zen and the 8-tab cap.
 */
public class SessionTabBarWidget extends Widget implements OreUiTooltipProvider {
    public static final int maxTabs = SessionTabManager.maxTabs;
    public static final int itemIconSize = 14;
    public static final int tabHeight = 20;
    public static final int tabGap = 3;
    public static final int tabStartX = 4;
    public static final int buttonSize = 20;
    public static final int themeButtonWidth = 56;
    public static final int zenButtonWidth = 44;
    public static final int menuWidth = 128;
    public static final int menuRowHeight = 14;

    public enum TabMenu {
        fork, rename, closeOthers, closeActive
    }

    public record TabView(String tabId, String title, List<TargetDemand> targets, boolean active) {

        public int targetCount() {
            return targets.size();
        }

        public long primaryAmount() {
            long amount = 0L;
            for (TargetDemand demand : targets) {
                amount += demand.amount();
            }
            return amount;
        }

        public @Nullable ComponentStackKey primaryResource() {
            return targets.isEmpty() ? null : targets.get(0).resource();
        }
    }

    private final WorkbenchShellContext shellContext;
    private final OreUiMotion.SmoothValue activeTabX = new OreUiMotion.SmoothValue();
    private final OreUiMotion.SmoothValue activeTabW = new OreUiMotion.SmoothValue();
    private @Nullable String contextMenuTabId;
    private @Nullable TabContextMenuPopup contextMenuPopup;
    private long contextMenuOpenedAtMs;
    private @Nullable String renamingTabId;
    private String renameDraft = "";
    private int renameCursor;

    public SessionTabBarWidget(WorkbenchShellContext context) {
        this.shellContext = Objects.requireNonNull(context, "context");
        WorkbenchShellLayout.Rect region = shellLayout().sessionTabBar();
        addStyleClass("workbench-tabbar");
        defaultStyle(UIStyle.of(background(CirrusTextures.Workbench.dockIsland(context.theme()))));
        useStyle(positionAbsolute(), sizeOf(region.width(), region.height()));
        setInteractive(true);
        setFocusable(true);
        setClickGroup(this);
        onClickOutside(ignored -> closeContextMenu());
        onUnmount(this::removeContextMenuPopup);
        onMouseClick((input, clickCount, context1) -> {
            var local = input.mouseRelative(this);
            if (contextMenuTabId != null) {
                int row = contextMenuRowAt(local.x(), local.y());
                if (row >= 0) {
                    boolean handled = contextMenuAction(TabMenu.values()[row]);
                    context1.consume();
                    return handled ? EventDispatch.consumed : EventDispatch.pass;
                }
                closeContextMenu();
            }
            if (input.isRightClick()) {
                String tabId = tabIdAt(local.x(), local.y());
                if (tabId != null && openContextMenu(tabId)) {
                    context1.consume();
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            }
            if (input.isMiddleClick()) {
                String tabId = tabIdAt(local.x(), local.y());
                if (tabId != null && tabCount() > 1 && closeTab(tabId)) {
                    context1.consume();
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            }
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            if (ChromeText.hit(
                local.x(),
                local.y(),
                plusRect().x(),
                plusRect().y(),
                plusRect().width(),
                plusRect().height()
            )) {
                openNewTab();
                context1.consume();
                return EventDispatch.consumed;
            }
            if (hits(themeRect(), local.x(), local.y())) {
                toggleTheme();
                context1.consume();
                return EventDispatch.consumed;
            }
            if (hits(zenRect(), local.x(), local.y())) {
                toggleZen();
                context1.consume();
                return EventDispatch.consumed;
            }
            String closeTabId = closeButtonTabAt(local.x(), local.y());
            if (closeTabId != null) {
                closeTab(closeTabId);
                context1.consume();
                return EventDispatch.consumed;
            }
            String tabId = tabIdAt(local.x(), local.y());
            if (tabId != null) {
                if (renamingTabId != null && !renamingTabId.equals(tabId)) {
                    commitRename();
                }
                activateTab(tabId);
                context1.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
        onCharTyped((codePoint, modifiers, context1) -> {
            if (renamingTabId == null || Character.isISOControl(codePoint)) {
                return EventDispatch.pass;
            }
            appendRenameChar(codePoint);
            context1.consume();
            return EventDispatch.consumed;
        });
        onKeyPressed((input, context1) -> {
            if (renamingTabId == null) {
                return EventDispatch.pass;
            }
            int key = input.key().getValue();
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                backspaceRename();
                context1.consume();
                return EventDispatch.consumed;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                commitRename();
                context1.consume();
                return EventDispatch.consumed;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                cancelRename();
                context1.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
    }

    public List<TabView> tabs() {
        List<TabView> views = new ArrayList<>();
        SessionTabManager manager = shellContext.tabManager();
        String activeId = manager.activeTabId();
        for (PlannerWorkbenchController controller : manager.tabs()) {
            String tabId = controller.tabId();
            views.add(
                new TabView(
                    tabId,
                    controller.state().tabTitle(),
                    controller.state().targets(),
                    Objects.equals(activeId, tabId)
                )
            );
        }
        return List.copyOf(views);
    }

    public int tabCount() {
        return shellContext.tabManager().tabs().size();
    }

    public int basketBadge(String tabId) {
        for (TabView view : tabs()) {
            if (view.tabId().equals(tabId)) {
                return Math.max(0, view.targetCount() - 1);
            }
        }
        return 0;
    }

    public boolean canOpenMore() {
        return tabCount() < maxTabs;
    }

    public boolean openNewTab() {
        if (!canOpenMore()) {
            shellContext.toast(CirrusLangs.Workbench.Tab.maxTabsHint.get());
            return false;
        }
        shellContext.openTab(List.of(), null);
        if (shellContext.mode() != WorkbenchMode.planner) {
            shellContext.setMode(WorkbenchMode.planner);
        }
        return true;
    }

    public boolean activateTab(@Nullable String tabId) {
        if (tabId == null) {
            return false;
        }
        if (Objects.equals(shellContext.tabManager().activeTabId(), tabId)) {
            return true;
        }
        shellContext.tabManager().activate(tabId);
        return true;
    }

    public boolean forkTab(String tabId) {
        if (tabId == null) {
            return false;
        }
        if (!canOpenMore()) {
            shellContext.toast(CirrusLangs.Workbench.Tab.maxTabsHint.get());
            return false;
        }
        activateTab(tabId);
        try {
            shellContext.tabManager().forkActiveTab();
        } catch (IllegalStateException full) {
            shellContext.toast(CirrusLangs.Workbench.Tab.maxTabsHint.get());
            return false;
        }
        return true;
    }

    public boolean closeTab(String tabId) {
        if (tabId == null) {
            return false;
        }
        if (Objects.equals(shellContext.tabManager().activeTabId(), tabId)) {
            shellContext.closeActiveTab();
        } else {
            shellContext.tabManager().closeTab(tabId);
        }
        return true;
    }

    public boolean closeOtherTabs(String tabId) {
        if (tabId == null) {
            return false;
        }
        shellContext.tabManager().closeOthers(tabId);
        return true;
    }

    public boolean beginRename(String tabId) {
        if (tabId == null) {
            return false;
        }
        renamingTabId = tabId;
        renameDraft = titleOf(tabId);
        renameCursor = renameDraft.length();
        contextMenuTabId = null;
        return true;
    }

    public @Nullable String renamingTabId() {
        return renamingTabId;
    }

    public String renameDraft() {
        return renameDraft;
    }

    public int renameCursor() {
        return renameCursor;
    }

    public void setRenameDraft(String draft) {
        renameDraft = draft == null ? "" : draft;
        renameCursor = renameDraft.length();
    }

    public void appendRenameChar(char codePoint) {
        renameDraft = renameDraft.substring(0, renameCursor) + codePoint + renameDraft.substring(renameCursor);
        renameCursor++;
    }

    public void backspaceRename() {
        if (renameCursor <= 0 || renameDraft.isEmpty()) {
            return;
        }
        renameDraft = renameDraft.substring(0, renameCursor - 1) + renameDraft.substring(renameCursor);
        renameCursor--;
    }

    public boolean commitRename() {
        String tabId = renamingTabId;
        if (tabId == null) {
            return false;
        }
        String title = renameDraft.trim();
        renamingTabId = null;
        if (title.isEmpty()) {
            return false;
        }
        shellContext.tabManager().rename(tabId, title);
        return true;
    }

    public void cancelRename() {
        renamingTabId = null;
        renameDraft = "";
        renameCursor = 0;
    }

    public boolean toggleTheme() {
        shellContext.setTheme(
            shellContext.theme() == WorkbenchThemeMode.obsidianBlueprint
                    ? WorkbenchThemeMode.atelierStone
                    : WorkbenchThemeMode.obsidianBlueprint
        );
        return true;
    }

    public boolean toggleZen() {
        shellContext.setZenMode(!shellContext.zenMode());
        return true;
    }

    public boolean openContextMenu(String tabId) {
        if (tabId == null) {
            return false;
        }
        if (shellContext instanceof WorkbenchShellWidget shell) {
            shell.closeTransientOverlays(this);
        }
        contextMenuTabId = tabId;
        contextMenuOpenedAtMs = OreUiMotion.nowMs();
        renamingTabId = null;
        mountContextMenuPopup();
        return true;
    }

    public void closeContextMenu() {
        contextMenuTabId = null;
        contextMenuOpenedAtMs = 0L;
        removeContextMenuPopup();
    }

    public @Nullable String contextMenuTabId() {
        return contextMenuTabId;
    }

    private void mountContextMenuPopup() {
        removeContextMenuPopup();
        if (contextMenuTabId == null || !lifecycle().mounted() || scene() == null) {
            return;
        }
        int anchorLocalX = contextMenuAnchorX();
        int totalH = TabMenu.values().length * menuRowHeight + 4;
        FloatPos sceneOrigin = localToScene(anchorLocalX, height());
        int sceneW = Math.max(menuWidth + 8, Math.round(shellContext.shellWidth()));
        int sceneH = Math.max(totalH + 8, Math.round(shellContext.shellHeight()));
        int px = Math.max(4, Math.min(sceneW - menuWidth - 4, (int) Math.round(sceneOrigin.x)));
        int py = Math.max(4, Math.min(sceneH - totalH - 4, (int) Math.round(sceneOrigin.y) + 1));
        TabContextMenuPopup popup = new TabContextMenuPopup(px, py, menuWidth, totalH);
        popup.setClickGroup(this);
        addRootWidget(scene().root(), popup);
        contextMenuPopup = popup;
    }

    private void removeContextMenuPopup() {
        TabContextMenuPopup active = contextMenuPopup;
        contextMenuPopup = null;
        if (active != null && active.parent() instanceof WidgetGroup<?> group) {
            group.remove(active);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addRootWidget(WidgetGroup<?> root, Widget widget) {
        ((WidgetGroup) root).addWidget(widget);
    }

    private int contextMenuAnchorX() {
        List<TabView> views = tabs();
        List<Rect> rects = tabRects();
        for (int index = 0; index < rects.size() && index < views.size(); index++) {
            if (Objects.equals(views.get(index).tabId(), contextMenuTabId)) {
                return rects.get(index).x();
            }
        }
        return tabStartX;
    }

    public boolean contextMenuAction(TabMenu action) {
        String tabId = contextMenuTabId;
        closeContextMenu();
        if (action == null || tabId == null) {
            return false;
        }
        return switch (action) {
            case fork -> forkTab(tabId);
            case rename -> beginRename(tabId);
            case closeOthers -> closeOtherTabs(tabId);
            case closeActive -> closeTab(tabId);
        };
    }

    public List<Rect> tabRects() {
        List<Rect> rects = new ArrayList<>();
        int cursor = tabStartX;
        int limit = Math.max(0, width() - rightButtonsWidth() - buttonSize - tabGap * 4);
        int y = Math.max(0, (height() - tabHeight) / 2);
        for (TabView view : tabs()) {
            int tabWidth = tabWidth(view);
            if (cursor + tabWidth > tabStartX + limit) {
                break;
            }
            rects.add(new Rect(cursor, y, tabWidth, tabHeight));
            cursor += tabWidth + tabGap;
        }
        return rects;
    }

    public Rect plusRect() {
        int x = tabStartX;
        for (Rect rect : tabRects()) {
            x = rect.right() + tabGap;
        }
        int maxPlusX = Math.max(tabStartX, themeRect().x() - buttonSize - tabGap);
        return new Rect(Math.min(x, maxPlusX), Math.max(0, (height() - tabHeight) / 2), buttonSize, tabHeight);
    }

    public Rect themeRect() {
        int tw = width() < 360 ? 28 : themeButtonWidth;
        int zw = width() < 360 ? 24 : zenButtonWidth;
        return new Rect(
            Math.max(0, width() - zw - tw - tabGap * 2 - 4),
            Math.max(0, (height() - tabHeight) / 2),
            tw,
            tabHeight
        );
    }

    public Rect zenRect() {
        int zw = width() < 360 ? 24 : zenButtonWidth;
        return new Rect(Math.max(0, width() - zw - 4), Math.max(0, (height() - tabHeight) / 2), zw, tabHeight);
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        List<TabView> views = tabs();
        List<Rect> rects = tabRects();
        for (int i = 0; i < Math.min(views.size(), rects.size()); i++) {
            Rect rect = rects.get(i);
            if (ChromeText.hit(localX, localY, rect.x(), rect.y(), rect.width(), rect.height())) {
                TabView view = views.get(i);
                OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder(view.title()).badge(
                    view.active() ? "ACTIVE TAB" : "SESSION",
                    view.active() ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.accent
                );
                if (!view.targets().isEmpty()) {
                    builder.icon(view.targets().get(0).resource());
                    List<OreUiRichTooltip.ItemEntry> rows = new ArrayList<>();
                    for (TargetDemand demand : view.targets()) {
                        rows.add(
                            OreUiRichTooltip.ItemEntry.of(
                                demand.resource(),
                                CoPilotPaint.resourceName(demand.resource()),
                                Component.literal("\u00d7" + ChromeText.abbreviated(demand.amount())),
                                OreUiRichTooltip.Tone.primary
                            )
                        );
                    }
                    builder.itemList(Component.literal("Targets"), rows, 4);
                }
                builder.footer("Click to switch \u00b7 Right-click to fork, rename, or close");
                return builder.build();
            }
        }
        Rect plus = plusRect();
        if (ChromeText.hit(localX, localY, plus.x(), plus.y(), plus.width(), plus.height())) {
            return OreUiRichTooltip.builder("New Planning Tab")
                    .badge(tabCount() + "/" + maxTabs, OreUiRichTooltip.Tone.accent)
                    .text("Opens an independent target & recipe planning session.", OreUiRichTooltip.Tone.secondary)
                    .build();
        }
        Rect themeBtn = themeRect();
        if (ChromeText.hit(localX, localY, themeBtn.x(), themeBtn.y(), themeBtn.width(), themeBtn.height())) {
            boolean dark = shellContext.theme() == WorkbenchThemeMode.obsidianBlueprint;
            return OreUiRichTooltip
                    .builder(dark ? "Switch to Atelier Stone (Light)" : "Switch to Obsidian Blueprint (Dark)")
                    .badge("THEME", OreUiRichTooltip.Tone.accent)
                    .text(
                        "Toggles between dark blueprint slate and bright drafting stone.",
                        OreUiRichTooltip.Tone.secondary
                    ).build();
        }
        Rect zenBtn = zenRect();
        if (ChromeText.hit(localX, localY, zenBtn.x(), zenBtn.y(), zenBtn.width(), zenBtn.height())) {
            return OreUiRichTooltip.builder(shellContext.zenMode() ? "Exit Zen Canvas" : "Enter Zen Canvas")
                    .badge("ZEN", OreUiRichTooltip.Tone.costP)
                    .text("Hides side docks to maximize the production flow canvas.", OreUiRichTooltip.Tone.secondary)
                    .build();
        }
        return null;
    }

    public List<Rect> contextMenuRects() {
        List<Rect> rects = new ArrayList<>();
        if (contextMenuTabId == null) {
            return rects;
        }
        int x = contextMenuAnchorX();
        int y = height();
        for (int index = 0; index < TabMenu.values().length; index++) {
            rects.add(new Rect(x, y + index * menuRowHeight, menuWidth, menuRowHeight));
        }
        return rects;
    }

    public int contextMenuRowAt(double localX, double localY) {
        List<Rect> rects = contextMenuRects();
        for (int index = 0; index < rects.size(); index++) {
            Rect rect = rects.get(index);
            if (localX >= rect.x() && localX < rect.right() && localY >= rect.y() && localY < rect.bottom()) {
                return index;
            }
        }
        return -1;
    }

    public @Nullable Rect tabCloseRect(int index) {
        if (tabCount() <= 1) {
            return null;
        }
        List<Rect> rects = tabRects();
        if (index < 0 || index >= rects.size()) {
            return null;
        }
        Rect rect = rects.get(index);
        int size = 11;
        int x = rect.right() - size - 4;
        int y = rect.y() + Math.max(2, (rect.height() - 2 - size) / 2);
        return new Rect(x, y, size, size);
    }

    private @Nullable String closeButtonTabAt(double localX, double localY) {
        if (tabCount() <= 1) {
            return null;
        }
        List<TabView> views = tabs();
        List<Rect> rects = tabRects();
        for (int index = 0; index < rects.size() && index < views.size(); index++) {
            Rect close = tabCloseRect(index);
            if (close != null && hits(close, localX, localY)) {
                return views.get(index).tabId();
            }
        }
        return null;
    }

    public @Nullable String tabIdAt(double localX, double localY) {
        List<TabView> views = tabs();
        List<Rect> rects = tabRects();
        for (int index = 0; index < rects.size() && index < views.size(); index++) {
            Rect rect = rects.get(index);
            if (localX >= rect.x() && localX < rect.right() && localY >= rect.y() && localY < rect.bottom()) {
                return views.get(index).tabId();
            }
        }
        return null;
    }

    public int tabWidth(TabView view) {
        Component title = titleOf(view);
        int textWidth = measureTabTextWidth(title);
        int iconStrip = view.targetCount() > 1 ? 26 : itemIconSize;
        int badgeWidth = view.targetCount() > 1 ? 26 : view.targetCount() == 1 ? 24 : 0;
        int closeWidth = tabCount() > 1 ? 14 : 0;
        return Math.max(94, ChromeText.pad * 2 + iconStrip + ChromeText.gap + textWidth + badgeWidth + closeWidth);
    }

    private int measureTabTextWidth(Component label) {
        String text = label == null ? "" : label.getString();
        if (lifecycle().mounted()) {
            try {
                Font font = context().font();
                if (font != null) {
                    return Math.max(44, font.width(text) + 10);
                }
            } catch (RuntimeException ignored) {
                // Fall back to estimated glyph widths when no font host is attached.
            }
        }
        int estimated = 0;
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            estimated += (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) ? 10 : 6;
            index += Character.charCount(codePoint);
        }
        return Math.max(54, estimated + 14);
    }

    public Component titleOf(TabView view) {
        if (!view.title().isEmpty()) {
            return Component.literal(view.title());
        }
        if (view.targetCount() > 1) {
            return CirrusLangs.Workbench.Topbar.basket.get();
        }
        ComponentStackKey resource = view.primaryResource();
        return resource == null ? CirrusLangs.Workbench.Tab.newTab.get() : Component.literal(resource.displayId());
    }

    private Component displayTitleOf(TabView view) {
        if (!view.title().isEmpty()) {
            return Component.literal(view.title());
        }
        if (view.targetCount() > 1) {
            return CirrusLangs.Workbench.Topbar.basket.get();
        }
        ComponentStackKey resource = view.primaryResource();
        return resource == null ? CirrusLangs.Workbench.Tab.newTab.get() : CoPilotPaint.resourceName(resource);
    }

    public String titleOf(String tabId) {
        for (TabView view : tabs()) {
            if (view.tabId().equals(tabId)) {
                return view.title().isEmpty() ? titleOf(view).getString() : view.title();
            }
        }
        return "";
    }

    public WorkbenchShellContext shellContext() {
        return shellContext;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        CirrusPalette palette = shellContext.palette();
        if (width() <= 0 || height() <= 0) {
            return;
        }
        List<TabView> views = tabs();
        List<Rect> rects = tabRects();
        for (int index = 0; index < rects.size() && index < views.size(); index++) {
            TabView view = views.get(index);
            Rect rect = rects.get(index);
            boolean hover = ChromeText.hit(mouseX, mouseY, rect.x(), rect.y(), rect.width(), rect.height());
            if (view.active()) {
                ChromeButtons.selectedPlate(canvas, rect.x(), rect.y(), rect.width(), rect.height(), palette);
            } else {
                OreUiPaint.raisedButton(
                    canvas,
                    rect.x(),
                    rect.y(),
                    rect.width(),
                    rect.height(),
                    palette,
                    false,
                    hover,
                    false,
                    false
                );
            }
            int topInset = view.active() ? 3 : 2;
            int bottomInset = view.active() ? 5 : 4;
            int sideInset = view.active() ? 6 : 5;
            int innerH = Math.max(10, rect.height() - topInset - bottomInset);
            int iconDim = Math.min(10, Math.max(8, innerH - 2));
            int iconX = rect.x() + sideInset;
            int iconY = rect.y() + topInset + Math.max(0, (innerH - iconDim) / 2);
            int iconAdvance;
            if (view.targets().isEmpty()) {
                ChromeButtons.icon(
                    canvas,
                    CirrusThemeIcons.resolve(CirrusTextures.Icon.calculatorTarget, shellContext.theme()),
                    iconX,
                    iconY,
                    iconDim,
                    0xFFFFFFFF
                );
                iconAdvance = iconDim + 3;
            } else if (view.targets().size() == 1) {
                ChromeButtons.drawMiniItem(canvas, view.targets().get(0).resource(), palette, iconX, iconY, iconDim);
                iconAdvance = iconDim + 3;
            } else {
                ChromeButtons.drawMiniItem(canvas, view.targets().get(0).resource(), palette, iconX, iconY, iconDim);
                ChromeButtons.drawMiniItem(
                    canvas,
                    view.targets().get(1).resource(),
                    palette,
                    iconX + iconDim + 2,
                    iconY,
                    iconDim
                );
                iconAdvance = iconDim * 2 + 5;
            }
            Component title = Objects.equals(renamingTabId, view.tabId())
                    ? Component.literal(renameDraft)
                    : displayTitleOf(view);
            int textX = iconX + iconAdvance + 2;
            Rect closeRect = tabCloseRect(index);
            int closeReserve = closeRect != null ? closeRect.width() + 4 : 0;
            int badge = basketBadge(view.tabId());
            int badgeW = badge > 0
                    ? ChromeButtons.basketChipWidth(canvas, badge)
                    : view.targetCount() == 1 ? ChromeButtons.countChipWidth(canvas, view.primaryAmount()) : 0;
            int badgeReserve = badgeW > 0 ? badgeW + 6 : 0;
            int textWidth = Math.max(0, rect.right() - textX - sideInset - badgeReserve - closeReserve);
            ChromeText.drawFitted(
                canvas,
                title,
                textX,
                ChromeText.centeredY(rect.y() + topInset, innerH, canvas),
                textWidth,
                view.active() ? palette.textPrimary() : palette.textSecondary()
            );
            int chipRight = rect.right() - sideInset - closeReserve;
            int chipY = rect.y() + topInset + Math.max(0, (innerH - 11) / 2);
            if (badge > 0) {
                ChromeButtons.basketChip(canvas, badge, chipRight - badgeW, chipY, palette);
            } else if (view.targetCount() == 1) {
                ChromeButtons.countChip(
                    canvas,
                    view.primaryAmount(),
                    chipRight - badgeW,
                    chipY,
                    palette,
                    palette.focusRing(),
                    palette.focusRing()
                );
            }
            if (closeRect != null) {
                boolean closeHover = hits(closeRect, mouseX, mouseY);
                if (closeHover) {
                    canvas.fill(
                        closeRect.x(),
                        closeRect.y(),
                        closeRect.width(),
                        closeRect.height(),
                        palette.cardSurfaceHover()
                    );
                    canvas.strokeRect(
                        closeRect.x(),
                        closeRect.y(),
                        closeRect.width(),
                        closeRect.height(),
                        palette.hairlineBorder(),
                        1
                    );
                }
                int crossSize = 5;
                ChromeButtons.cross(
                    canvas,
                    closeRect.x() + (closeRect.width() - crossSize) / 2,
                    closeRect.y() + (closeRect.height() - crossSize) / 2,
                    crossSize,
                    closeHover ? palette.ruleHardBan() : palette.textMuted()
                );
            }
            if (Objects.equals(renamingTabId, view.tabId())) {
                int caretX = textX
                        + ChromeText.width(
                            canvas,
                            Component.literal(renameDraft.substring(0, Math.min(renameCursor, renameDraft.length())))
                        );
                float caretAlpha = 0.35f
                        + 0.65f * OreUiMotion.pulse01(OreUiMotion.nowMs(), 960L, OreUiMotion.reducedMotion());
                canvas.fill(
                    caretX,
                    rect.y() + topInset,
                    1,
                    Math.max(6, innerH),
                    OreUiPaint.withAlpha(palette.focusRing(), caretAlpha)
                );
            }
        }
        Rect activeRect = null;
        for (int index = 0; index < rects.size() && index < views.size(); index++) {
            if (views.get(index).active()) {
                activeRect = rects.get(index);
                break;
            }
        }
        if (activeRect != null) {
            int gx = Math.round(activeTabX.update(activeRect.x() + 2, 22f));
            int gw = Math.max(8, Math.round(activeTabW.update(activeRect.width() - 4, 22f)));
            canvas.fill(gx, activeRect.bottom() - 2, gw, 2, palette.focusRing());
        }
        Rect plus = plusRect();
        boolean plusHover = ChromeText.hit(mouseX, mouseY, plus.x(), plus.y(), plus.width(), plus.height());
        OreUiPaint.raisedButton(
            canvas,
            plus.x(),
            plus.y(),
            plus.width(),
            plus.height(),
            palette,
            false,
            plusHover,
            false,
            false
        );
        int plusFaceW = Math.max(8, plus.width() - 4);
        int plusFaceH = Math.max(8, plus.height() - 6);
        int plusGlyph = 8;
        ChromeButtons.plus(
            canvas,
            plus.x() + 2 + (plusFaceW - plusGlyph) / 2,
            plus.y() + 2 + (plusFaceH - plusGlyph) / 2,
            plusGlyph,
            plusHover ? palette.textPrimary() : palette.textSecondary()
        );
        Rect theme = themeRect();
        boolean themeHover = ChromeText.hit(mouseX, mouseY, theme.x(), theme.y(), theme.width(), theme.height());
        OreUiPaint.raisedButton(
            canvas,
            theme.x(),
            theme.y(),
            theme.width(),
            theme.height(),
            palette,
            false,
            themeHover,
            false,
            false
        );
        boolean dark = OreUiPaint.isDark(palette);
        ChromeButtons.themeGlyph(
            canvas,
            theme.x() + 5,
            theme.y() + Math.max(2, (theme.height() - 2 - 8) / 2),
            dark,
            dark ? palette.costH() : palette.costH()
        );
        Component themeLabel = dark ? Component.literal("Dark") : Component.literal("Light");
        ChromeText.drawFitted(
            canvas,
            themeLabel,
            theme.x() + 16,
            ChromeText.centeredButtonY(theme.y(), theme.height(), canvas),
            Math.max(8, theme.width() - 20),
            themeHover ? palette.textPrimary() : palette.textSecondary()
        );
        Rect zen = zenRect();
        boolean zenHover = ChromeText.hit(mouseX, mouseY, zen.x(), zen.y(), zen.width(), zen.height());
        if (shellContext.zenMode()) {
            OreUiPaint.raisedCard(canvas, zen.x(), zen.y(), zen.width(), zen.height(), palette, zenHover, true);
        } else {
            OreUiPaint.raisedButton(
                canvas,
                zen.x(),
                zen.y(),
                zen.width(),
                zen.height(),
                palette,
                false,
                zenHover,
                false,
                false
            );
        }
        int zenInk = shellContext.zenMode()
                ? palette.focusRing()
                : zenHover ? palette.textPrimary() : palette.textSecondary();
        ChromeButtons.zenGlyph(canvas, zen.x() + 5, zen.y() + Math.max(2, (zen.height() - 2 - 8) / 2), zenInk);
        ChromeText.drawFitted(
            canvas,
            Component.literal("Zen"),
            zen.x() + 16,
            ChromeText.centeredButtonY(zen.y(), zen.height(), canvas),
            Math.max(8, zen.width() - 20),
            zenInk
        );
        if (contextMenuPopup == null) {
            renderContextMenu(canvas, palette, mouseX, mouseY);
        }
    }

    private void renderContextMenu(SceneCanvas canvas, CirrusPalette palette, int mouseX, int mouseY) {
        List<Rect> rects = contextMenuRects();
        if (rects.isEmpty()) {
            contextMenuOpenedAtMs = 0L;
            return;
        }
        long nowMs = OreUiMotion.nowMs();
        if (contextMenuOpenedAtMs == 0L) {
            contextMenuOpenedAtMs = nowMs;
        }
        float appear = OreUiMotion
                .cubicOut(nowMs - contextMenuOpenedAtMs, OreUiMotion.overlayAppearMs, OreUiMotion.reducedMotion());
        float slideY = -(1f - appear) * 4f;
        Rect first = rects.get(0);
        Rect last = rects.get(rects.size() - 1);
        canvas.pushTransform();
        canvas.translate(0f, slideY);
        try {
            OreUiPaint.popover(canvas, first.x(), first.y(), menuWidth, last.bottom() - first.y(), palette);
            for (int index = 0; index < rects.size(); index++) {
                Rect rect = rects.get(index);
                boolean hovered = ChromeText.hit(mouseX, mouseY, rect.x(), rect.y(), rect.width(), rect.height());
                if (hovered) {
                    canvas.fill(
                        rect.x() + 1,
                        rect.y(),
                        Math.max(0, rect.width() - 2),
                        rect.height(),
                        palette.cardSurfaceHover()
                    );
                }
                Component label = menuLabel(TabMenu.values()[index]);
                ChromeText.drawFitted(
                    canvas,
                    label,
                    rect.x() + 4,
                    ChromeText.centeredY(rect.y(), rect.height(), canvas),
                    rect.width() - 8,
                    hovered ? palette.textPrimary() : palette.textSecondary()
                );
            }
        } finally {
            canvas.popTransform();
        }
    }

    private final class TabContextMenuPopup extends Widget {
        private TabContextMenuPopup(int x, int y, int w, int h) {
            setInteractive(true);
            useStyle(positionAbsolute(), insetLeft(x), insetTop(y), sizeOf(w, h), UIStyles.zIndex(2400));
            onMouseClick((input, clickCount, clickCtx) -> {
                if (!input.isLeftClick()) {
                    closeContextMenu();
                    clickCtx.consume();
                    return EventDispatch.consumed;
                }
                var local = input.mouseRelative(this);
                int row = (int) ((local.y() - 2) / menuRowHeight);
                if (row >= 0 && row < TabMenu.values().length) {
                    contextMenuAction(TabMenu.values()[row]);
                    clickCtx.consume();
                    return EventDispatch.consumed;
                }
                closeContextMenu();
                clickCtx.consume();
                return EventDispatch.consumed;
            });
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            if (contextMenuTabId == null || width() <= 0 || height() <= 0) {
                return;
            }
            CirrusPalette palette = shellContext.palette();
            long nowMs = OreUiMotion.nowMs();
            if (contextMenuOpenedAtMs == 0L) {
                contextMenuOpenedAtMs = nowMs;
            }
            float appear = OreUiMotion
                    .cubicOut(nowMs - contextMenuOpenedAtMs, OreUiMotion.overlayAppearMs, OreUiMotion.reducedMotion());
            float slideY = -(1f - appear) * 4f;
            canvas.pushTransform();
            canvas.translate(0f, slideY);
            try {
                canvas.shadow(0, 0, width(), height(), 2f, 3f, 8f, palette.dropShadow());
                OreUiPaint.popover(canvas, 0, 0, width(), height(), palette);
                TabMenu[] items = TabMenu.values();
                for (int index = 0; index < items.length; index++) {
                    int ry = 2 + index * menuRowHeight;
                    boolean hovered = mouseX >= 2
                            && mouseX < width() - 2
                            && mouseY >= ry
                            && mouseY < ry + menuRowHeight;
                    if (hovered) {
                        canvas.fill(2, ry, Math.max(0, width() - 4), menuRowHeight, palette.cardSurfaceHover());
                    }
                    if (index > 0) {
                        canvas.fill(4, ry, Math.max(0, width() - 8), 1, OreUiPaint.cardDivider(palette));
                    }
                    boolean danger = items[index] == TabMenu.closeActive;
                    int ink = hovered
                            ? (danger ? palette.ruleHardBan() : palette.textPrimary())
                            : palette.textSecondary();
                    ChromeText.drawFitted(
                        canvas,
                        menuLabel(items[index]),
                        6,
                        ChromeText.centeredY(ry, menuRowHeight, canvas),
                        Math.max(12, width() - 12),
                        ink
                    );
                }
            } finally {
                canvas.popTransform();
            }
        }
    }

    public static Component menuLabel(TabMenu action) {
        return switch (action) {
            case fork -> CirrusLangs.Workbench.Tab.fork.get();
            case rename -> CirrusLangs.Workbench.Tab.rename.get();
            case closeOthers -> CirrusLangs.Workbench.Tab.closeOthers.get();
            case closeActive -> CirrusLangs.Workbench.Tab.close.get();
        };
    }

    private int rightButtonsWidth() {
        return themeButtonWidth + zenButtonWidth + tabGap * 3 + 6;
    }

    private static boolean hits(Rect rect, double localX, double localY) {
        return localX >= rect.x() && localX < rect.right() && localY >= rect.y() && localY < rect.bottom();
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
