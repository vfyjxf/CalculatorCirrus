package dev.vfyjxf.cirrus.ui.execution.widget;

import dev.vfyjxf.cirrus.ui.CirrusScroll;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.effect.UIEffects;
import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState;

import java.util.List;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.widthOf;

public class ExecutionSlotViewportWidget extends CompositeWidget<Widget> {
	//region constants

	private static final int scrollBarWidth = 6;

	//endregion

	//region state

	private int columns;
	private int visibleRows;
	private final ScrollState scrollState;
	private final ExecutionSlotGridWidget grid;

	private int slotCount;

	//endregion

	public ExecutionSlotViewportWidget(int columns, int visibleRows) {
		this.columns = Math.max(1, columns);
		this.visibleRows = Math.max(1, visibleRows);
		this.scrollState = CirrusScroll.longVerticalState(10)
									  .smooth(true)
									  .smoothSpeed(0.35f)
									  .trackTexture(Background.scroll)
									  .thumbTexture(CirrusTextures.Icon.scrollBarVertical)
									  .scrollbarWidth(scrollBarWidth)
									  .minThumbSize(14)
									  .viewportInset(0);

		useStyle(
				widthOf(gridWidth()),
				heightOf(viewportHeight()),
				minWidth(gridWidth()),
				minHeight(viewportHeight())
		);
		useEffect(UIEffects.scrollable(scrollState));

		grid = addWidget(new ExecutionSlotGridWidget(this.columns));
	}

	public void setViewportShape(int columns, int visibleRows) {
		int nextColumns = Math.max(1, columns);
		int nextVisibleRows = Math.max(1, visibleRows);
		if (this.columns == nextColumns && this.visibleRows == nextVisibleRows) {
			return;
		}
		this.columns = nextColumns;
		this.visibleRows = nextVisibleRows;
		grid.setColumns(this.columns);
		syncViewportWidth();
	}

	//region content

	public void setSlots(List<ExecutionSlotWidget> slots) {
		slotCount = slots.size();
		scrollState.resetScroll();
		grid.setSlots(slots);
		syncViewportWidth();
	}

	//endregion

	//region layout

	private void syncViewportWidth() {
		boolean needsScroll = totalRows(slotCount) > visibleRows;
		int width = gridWidth() + (needsScroll ? scrollBarWidth : 0);
		scrollState.viewportInset(0);
		useStyle(
				widthOf(width),
				minWidth(width),
				heightOf(viewportHeight()),
				minHeight(viewportHeight())
		);
		scrollState.showScrollbar(needsScroll);
	}

	private int gridWidth() {
		return columns * ExecutionSlotWidget.slotSize;
	}

	private int viewportHeight() {
		return visibleRows * ExecutionSlotWidget.slotSize;
	}

	private int totalRows(int slots) {
		if (slots <= 0) {
			return 0;
		}
		return (int) Math.ceil(slots / (double) columns);
	}

	int debugViewportInsetTop() {
		return scrollState.viewportInsetTop();
	}

	int debugViewportInsetRight() {
		return scrollState.viewportInsetRight();
	}

	int debugViewportInsetBottom() {
		return scrollState.viewportInsetBottom();
	}

	int debugViewportInsetLeft() {
		return scrollState.viewportInsetLeft();
	}

	boolean debugShowsScrollbar() {
		return scrollState.showScrollbar();
	}

	//endregion
}
