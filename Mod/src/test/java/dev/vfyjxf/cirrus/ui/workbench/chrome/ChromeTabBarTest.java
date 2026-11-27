package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.state.SessionTabManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeTestContext.coal;
import static dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeTestContext.demand;
import static dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeTestContext.goldIngot;
import static dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeTestContext.ironIngot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromeTabBarTest {

    private static ChromeTestContext contextWithOneTarget() {
        return new ChromeTestContext(1280f, 720f, List.of(demand(ironIngot, 1L)));
    }

    @Test
    void plusButtonStopsAtEightTabsAndToastsTheLimit() {
        ChromeTestContext ctx = contextWithOneTarget();
        SessionTabBarWidget bar = new SessionTabBarWidget(ctx);
        assertEquals(1, bar.tabCount());

        for (int amount = 2; amount <= SessionTabBarWidget.maxTabs; amount++) {
            ctx.openTabWith(amount);
        }

        assertEquals(SessionTabManager.maxTabs, SessionTabBarWidget.maxTabs);
        assertEquals(8, bar.tabCount());
        assertEquals(7, ctx.openTabCalls.size());
        assertFalse(bar.canOpenMore());

        assertFalse(bar.openNewTab());
        assertEquals(7, ctx.openTabCalls.size(), "the plus button must not reach the context at the cap");
        assertEquals(CirrusLangs.Workbench.Tab.maxTabsHint.key(), lastToast(ctx));

        ctx.openTab(List.of(demand(coal, 99L)), null);
        assertEquals(8, ctx.openTabCalls.size());
        assertEquals(8, bar.tabCount());
        assertEquals(CirrusLangs.Workbench.Tab.maxTabsHint.key(), lastToast(ctx));
    }

    @Test
    void forkClonesTargetsIntoANewActiveTabWithoutClosingAnything() {
        ChromeTestContext ctx = contextWithOneTarget();
        SessionTabBarWidget bar = new SessionTabBarWidget(ctx);
        String source = ctx.tabManager.activeTabId();
        assertNotNull(source);

        assertTrue(bar.forkTab(source));

        assertEquals(2, bar.tabCount());
        String fork = ctx.tabManager.activeTabId();
        assertNotEquals(source, fork);
        assertEquals(List.of(demand(ironIngot, 1L)), ctx.controller().state().targets());
        assertEquals("tab-2", fork);
        assertEquals(0, ctx.closeActiveTabCalls);
        assertEquals(0, ctx.targetCalls.size());
    }

    @Test
    void renameCommitsTrimmedDraftAndCancelKeepsTheOldTitle() {
        ChromeTestContext ctx = contextWithOneTarget();
        SessionTabBarWidget bar = new SessionTabBarWidget(ctx);
        String id = Objects.requireNonNull(ctx.tabManager.activeTabId());

        assertTrue(bar.beginRename(id));
        assertEquals("minecraft:iron_ingot", bar.renameDraft());
        bar.setRenameDraft("  钢梁线  "); // NOPMD - Chinese title fixture
        assertTrue(bar.commitRename());
        assertNull(bar.renamingTabId());
        assertEquals("钢梁线", ctx.controller().state().tabTitle()); // NOPMD - Chinese title fixture
        assertEquals("钢梁线", bar.titleOf(id)); // NOPMD - Chinese title fixture

        assertTrue(bar.beginRename(id));
        bar.setRenameDraft("   ");
        assertFalse(bar.commitRename());
        assertNull(bar.renamingTabId());
        assertEquals("钢梁线", ctx.controller().state().tabTitle()); // NOPMD - Chinese title fixture

        assertTrue(bar.beginRename(id));
        bar.setRenameDraft("discard me");
        bar.cancelRename();
        assertNull(bar.renamingTabId());
        assertEquals("钢梁线", ctx.controller().state().tabTitle()); // NOPMD - Chinese title fixture

        bar.beginRename(id);
        bar.setRenameDraft("ab");
        bar.appendRenameChar('c');
        assertEquals("abc", bar.renameDraft());
        assertEquals(3, bar.renameCursor());
        bar.backspaceRename();
        assertEquals("ab", bar.renameDraft());
        assertEquals(2, bar.renameCursor());
    }

    @Test
    void closeOthersKeepsTheRequestedTabAndRoutesCloseCurrentThroughTheContext() {
        ChromeTestContext ctx = contextWithOneTarget();
        SessionTabBarWidget bar = new SessionTabBarWidget(ctx);
        ctx.openTabWith(2L);
        ctx.openTabWith(3L);
        List<String> ids = bar.tabs().stream().map(SessionTabBarWidget.TabView::tabId).toList();
        assertEquals(3, ids.size());
        String middle = ids.get(1);

        assertTrue(bar.openContextMenu(middle));
        assertEquals(middle, bar.contextMenuTabId());
        assertTrue(bar.contextMenuAction(SessionTabBarWidget.TabMenu.closeOthers));

        assertEquals(1, bar.tabCount());
        assertNull(bar.contextMenuTabId());
        assertEquals(middle, ctx.tabManager.activeTabId());
        assertEquals(0, ctx.closeActiveTabCalls);

        ctx.openTabWith(4L);
        String active = Objects.requireNonNull(ctx.tabManager.activeTabId());
        assertTrue(bar.closeTab(active));
        assertEquals(1, ctx.closeActiveTabCalls);
        assertEquals(1, bar.tabCount());
    }

    @Test
    void contextMenuForkAndRenameTargetTheClickedTab() {
        ChromeTestContext ctx = contextWithOneTarget();
        SessionTabBarWidget bar = new SessionTabBarWidget(ctx);
        ctx.openTabWith(2L);
        String active = Objects.requireNonNull(ctx.tabManager.activeTabId());

        bar.openContextMenu(active);
        assertTrue(bar.contextMenuAction(SessionTabBarWidget.TabMenu.rename));
        assertEquals(active, bar.renamingTabId());

        bar.setRenameDraft("forked");
        bar.commitRename();

        bar.openContextMenu(active);
        assertTrue(bar.contextMenuAction(SessionTabBarWidget.TabMenu.fork));
        assertEquals(3, bar.tabCount());
        assertEquals("tab-3", ctx.tabManager.activeTabId());
    }

    @Test
    void basketBadgeCountsEveryTargetBeyondThePrimaryOne() {
        ChromeTestContext ctx = new ChromeTestContext(1280f, 720f, List.of());
        ctx.openTab(List.of(demand(ironIngot, 1L), demand(coal, 2L), demand(goldIngot, 3L)), null);
        SessionTabBarWidget bar = new SessionTabBarWidget(ctx);
        String basket = Objects.requireNonNull(ctx.tabManager.activeTabId());

        assertEquals(2, bar.basketBadge(basket));
        assertEquals(0, bar.basketBadge("missing-tab"));

        SessionTabBarWidget.TabView view = bar.tabs().stream().filter(tab -> tab.tabId().equals(basket)).findFirst()
                .orElseThrow();
        assertTrue(view.active());
        assertEquals(3, view.targetCount());
        assertEquals(6L, view.primaryAmount());
        assertEquals(ironIngot, view.primaryResource());
        assertEquals(0, bar.basketBadge(ctx.tabManager.tabs().get(0).tabId()));
    }

    @Test
    void themeAndZenButtonsDelegateToTheContext() {
        ChromeTestContext ctx = contextWithOneTarget();
        SessionTabBarWidget bar = new SessionTabBarWidget(ctx);

        assertTrue(bar.toggleTheme());
        assertEquals(List.of(WorkbenchThemeMode.atelierStone), ctx.themeCalls);
        assertEquals(WorkbenchThemeMode.atelierStone, ctx.theme());

        assertTrue(bar.toggleTheme());
        assertEquals(List.of(WorkbenchThemeMode.atelierStone, WorkbenchThemeMode.obsidianBlueprint), ctx.themeCalls);

        assertTrue(bar.toggleZen());
        assertTrue(ctx.zenMode());
        assertTrue(bar.toggleZen());
        assertFalse(ctx.zenMode());
        assertEquals(List.of(true, false), ctx.zenCalls);
    }

    @Test
    void activateTabSwitchesTheManagerFocus() {
        ChromeTestContext ctx = contextWithOneTarget();
        SessionTabBarWidget bar = new SessionTabBarWidget(ctx);
        String first = Objects.requireNonNull(ctx.tabManager.activeTabId());
        ctx.openTabWith(2L);
        String second = ctx.tabManager.activeTabId();

        assertFalse(bar.activateTab(null));
        assertTrue(bar.activateTab(first));
        assertEquals(first, ctx.tabManager.activeTabId());
        assertTrue(bar.tabs().get(0).active());
        assertEquals(second, bar.tabs().get(1).tabId());
    }

    @Test
    void openNewTabCreatesAdditionalEmptyTabsInsteadOfReusingExistingEmptyTab() {
        ChromeTestContext ctx = new ChromeTestContext(1280f, 720f, List.of());
        SessionTabBarWidget bar = new SessionTabBarWidget(ctx);
        assertEquals(1, bar.tabCount());
        String first = ctx.tabManager.activeTabId();

        int singleWidth = bar.tabWidth(bar.tabs().get(0));
        assertTrue(bar.openNewTab());
        assertEquals(2, bar.tabCount());
        assertNotEquals(first, ctx.tabManager.activeTabId());
        assertTrue(
            bar.tabWidth(bar.tabs().get(0)) >= singleWidth,
            "multi-tab layout reserves room for inline close button"
        );

        assertTrue(bar.openNewTab());
        assertEquals(3, bar.tabCount());
    }

    private static String lastToast(ChromeTestContext ctx) {
        return ctx.toasts.get(ctx.toasts.size() - 1);
    }
}
