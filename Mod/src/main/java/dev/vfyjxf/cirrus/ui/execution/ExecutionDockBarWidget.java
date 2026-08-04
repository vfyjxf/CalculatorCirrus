package dev.vfyjxf.cirrus.ui.execution;

import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayUi;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusControlChrome;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import net.minecraft.client.renderer.Rect2i;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;

final class ExecutionDockBarWidget extends Widget {
    //region constants

    private static final int dockGap = 0;
    private static final int dockButtonInsetY = 1;
    private static final int dockArrowWidth = 12;
    private static final int dockButtonMinWidth = 20;
    private static final int dockPageButtonGap = 1;
    private static final int dockTabIconOffsetY = -1;
    //endregion

    //region state

    private final MainPanel owner;
    private int page;
    //endregion

    ExecutionDockBarWidget(MainPanel owner) {
        this.owner = owner;
        useStyle(UIStyle.of(background(Background.inset)));
        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            var relative = input.mouseRelative(this);
            int mouseX = (int) Math.round(relative.x());
            int mouseY = (int) Math.round(relative.y());
            HitTarget target = hitTarget(mouseX, mouseY);
            if (target == null) {
                return EventDispatch.consumed;
            }
            if (target == HitTarget.prev) {
                if (hasPrevPage()) {
                    page--;
                    ExecutionOverlayUi.playClickSound();
                }
                return EventDispatch.consumed;
            }
            if (target == HitTarget.next) {
                if (hasNextPage()) {
                    page++;
                    ExecutionOverlayUi.playClickSound();
                }
                return EventDispatch.consumed;
            }
            ExecutionTab next = target.tab();
            if (next != null && next != owner.activeTab()) {
                owner.activateTab(next);
                ExecutionOverlayUi.playClickSound();
            }
            return EventDispatch.consumed;
        });
    }

    //region input

    private @Nullable HitTarget hitTarget(int mouseX, int mouseY) {
        DockLayout layout = dockLayout();
        if (layout.pageCount() > 1 && ExecutionOverlayUi.contains(layout.prevArea(), mouseX, mouseY)) {
            return HitTarget.prev();
        }
        if (layout.pageCount() > 1 && ExecutionOverlayUi.contains(layout.nextArea(), mouseX, mouseY)) {
            return HitTarget.next();
        }
        for (TabPlacement placement : layout.tabs()) {
            if (ExecutionOverlayUi.contains(placement.area(), mouseX, mouseY)) {
                return HitTarget.tab(placement.tab());
            }
        }
        return null;
    }
    //endregion

    //region layout

    private DockLayout dockLayout() {
        List<List<ExecutionTab>> pages = computePages();
        int safePage = pages.isEmpty() ? 0 : Math.max(0, Math.min(page, pages.size() - 1));
        if (safePage != page) {
            page = safePage;
        }
        boolean multiPage = pages.size() > 1;
        int pageButtonWidth = multiPage ? dockArrowWidth : 0;
        int pageButtonGap = multiPage ? dockPageButtonGap : 0;
        int contentX = multiPage ? pageButtonWidth + pageButtonGap : 0;
        int contentWidth = Math.max(0, width() - contentX - (multiPage ? pageButtonWidth + pageButtonGap : 0));
        List<ExecutionTab> visible = pages.isEmpty() ? List.of() : pages.get(safePage);
        int buttonWidth = visible.isEmpty()
            ? 0
            : Math.max(dockButtonMinWidth, Math.max(0, contentWidth - dockGap * (visible.size() - 1)) / visible.size());
        int usedWidth = visible.isEmpty() ? 0 : buttonWidth * visible.size() + dockGap * (visible.size() - 1);
        int startX = contentX + Math.max(0, (contentWidth - usedWidth) / 2);

        List<TabPlacement> placements = new ArrayList<>(visible.size());
        for (int index = 0; index < visible.size(); index++) {
            int x = startX + index * (buttonWidth + dockGap);
            placements.add(new TabPlacement(visible.get(index), new Rect2i(x, 0, buttonWidth, height())));
        }

        Rect2i prevArea = new Rect2i(0, 0, multiPage ? pageButtonWidth : 0, height());
        Rect2i nextArea = new Rect2i(multiPage ? width() - pageButtonWidth : width(), 0, multiPage ? pageButtonWidth : 0, height());
        Rect2i contentArea = new Rect2i(contentX, 0, Math.max(0, contentWidth), height());
        return new DockLayout(placements, prevArea, nextArea, contentArea, pages.size(), multiPage);
    }

    private List<List<ExecutionTab>> computePages() {
        int available = Math.max(0, width());
        ExecutionTab[] tabs = ExecutionTab.values();
        if (tabs.length == 0) {
            return List.of();
        }
        int singlePageWidth = tabs.length * dockButtonMinWidth + dockGap * Math.max(0, tabs.length - 1);
        if (singlePageWidth <= available) {
            return List.of(List.of(tabs));
        }

        int pagedContentWidth = Math.max(0, available - dockArrowWidth * 2 - dockPageButtonGap * 2);
        int perPage = Math.max(1, (pagedContentWidth + dockGap) / Math.max(1, dockButtonMinWidth + dockGap));
        ArrayList<List<ExecutionTab>> pages = new ArrayList<>();
        for (int start = 0; start < tabs.length; start += perPage) {
            pages.add(List.of(Arrays.copyOfRange(tabs, start, Math.min(tabs.length, start + perPage))));
        }
        return pages;
    }

    private boolean hasPrevPage() {
        return page > 0;
    }

    private boolean hasNextPage() {
        return page + 1 < computePages().size();
    }
    //endregion

    //region render

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        DockLayout layout = dockLayout();
        HitTarget hovered = hitTarget(mouseX, mouseY);
        renderPageButtons(canvas, layout, hovered);

        List<TabPlacement> placements = layout.tabs();
        for (int index = 0; index < placements.size(); index++) {
            TabPlacement placement = placements.get(index);
            ExecutionTab tab = placement.tab();
            Rect2i area = tabButtonArea(placement.area(), !layout.multiPage() && index == 0);
            boolean active = tab == owner.activeTab();
            boolean hoveredTab = hovered != null && hovered.tab() == tab;
            CirrusControlChrome.renderButtonBackground(canvas, area.getX(), area.getY(), area.getWidth(), area.getHeight(), active, hoveredTab, true);
            var icon = active || hoveredTab ? tab.selectedIcon() : tab.icon();
            int iconX = area.getX() + CirrusControlChrome.centeredIconX(area.getWidth(), icon.width());
            int iconY = Math.max(area.getY(), area.getY() + CirrusControlChrome.centeredIconY(area.getHeight(), icon.height(), dockTabIconOffsetY));
            canvas.texture(icon, iconX, iconY, icon.width(), icon.height());
        }
    }

    @Override
    public @Nullable Tooltip hoverTooltip(int mouseX, int mouseY) {
        HitTarget hovered = hitTarget(mouseX, mouseY);
        if (hovered != null && hovered.tab() != null) {
            Tooltip tooltip = Tooltip.create();
            LangEntry title = hovered.tab().title();
            tooltip.add(ExecutionOverlayUi.tooltipTitle(title));
            tooltip.add(ExecutionOverlayUi.tooltipBody(title.suffix("desc1")));
            tooltip.add(ExecutionOverlayUi.tooltipBody(title.suffix("desc2")));
            return tooltip;
        }
        if (hovered == HitTarget.prev) {
            return pagerTooltip(CirrusLangs.Execution.Overlay.Dock.pagePrev);
        }
        if (hovered == HitTarget.next) {
            return pagerTooltip(CirrusLangs.Execution.Overlay.Dock.pageNext);
        }
        return super.hoverTooltip(mouseX, mouseY);
    }

    private Tooltip pagerTooltip(LangEntry key) {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitle(key));
        return tooltip;
    }

    private void renderPageButtons(SceneCanvas canvas, DockLayout layout, @Nullable HitTarget hovered) {
        if (!layout.multiPage()) {
            return;
        }
        renderPageButton(canvas, layout.prevArea(), hovered == HitTarget.prev, hasPrevPage(), true);
        renderPageButton(canvas, layout.nextArea(), hovered == HitTarget.next, hasNextPage(), false);
    }

    private void renderPageButton(SceneCanvas canvas, Rect2i area, boolean hovered, boolean enabled, boolean left) {
        if (area.getWidth() <= 0 || area.getHeight() <= 0) {
            return;
        }
        Rect2i buttonArea = pageButtonArea(area);
        CirrusControlChrome.renderButtonBackground(canvas, buttonArea.getX(), buttonArea.getY(), buttonArea.getWidth(), buttonArea.getHeight(), false, hovered, enabled);
        var icon = left
            ? (enabled && hovered ? CirrusTextures.Icon.Small.arrowLeftSelected : CirrusTextures.Icon.Small.arrowLeft)
            : (enabled && hovered ? CirrusTextures.Icon.Small.arrowRightSelected : CirrusTextures.Icon.Small.arrowRight);
        int iconX = buttonArea.getX() + CirrusControlChrome.centeredIconX(buttonArea.getWidth(), icon.width());
        int iconY = buttonArea.getY() + CirrusControlChrome.centeredIconY(buttonArea.getHeight(), icon.height(), false);
        if (enabled) {
            canvas.texture(icon, iconX, iconY, icon.width(), icon.height());
            return;
        }
        canvas.color(0x88FFFFFF).texture(icon, iconX, iconY, icon.width(), icon.height());
        canvas.color(0xFFFFFFFF);
    }
    //endregion

    //region geometry

    private Rect2i tabButtonArea(Rect2i area, boolean leftEdge) {
        int height = Math.max(14, area.getHeight() - dockButtonInsetY * 2);
        int leftInset = leftEdge ? 1 : 0;
        return new Rect2i(area.getX() + leftInset, area.getY() + dockButtonInsetY, Math.max(0, area.getWidth() - leftInset), height);
    }

    private Rect2i pageButtonArea(Rect2i area) {
        int height = Math.max(14, area.getHeight() - dockButtonInsetY * 2);
        return new Rect2i(area.getX(), area.getY() + dockButtonInsetY, area.getWidth(), height);
    }
    //endregion

    //region records

    private record DockLayout(
        List<TabPlacement> tabs,
        Rect2i prevArea,
        Rect2i nextArea,
        Rect2i contentArea,
        int pageCount,
        boolean multiPage
    ) {
    }

    private record TabPlacement(ExecutionTab tab, Rect2i area) {
    }

	private record HitTarget(@Nullable ExecutionTab tab) {
		private static final HitTarget prev = new HitTarget(null);
		private static final HitTarget next = new HitTarget(null);

		private static HitTarget tab(ExecutionTab tab) {
			return new HitTarget(tab);
		}

		private static HitTarget prev() {
			return prev;
		}

		private static HitTarget next() {
			return next;
		}
	}
    //endregion
}
