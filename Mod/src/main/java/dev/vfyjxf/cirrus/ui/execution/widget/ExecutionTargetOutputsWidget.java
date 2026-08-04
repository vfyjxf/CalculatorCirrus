package dev.vfyjxf.cirrus.ui.execution.widget;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayUi;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusButtonWidget;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.ui.widget.RowWidget;
import org.jetbrains.annotations.Nullable;
import dev.vfyjxf.taffy.style.JustifyContent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.columnGap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayBlock;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayFlex;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayNone;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.justifyContent;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public class ExecutionTargetOutputsWidget extends CompositeWidget<Widget> {
	//region constants

	private static final int slotGap = 4;
	private static final int arrowGap = 2;
	private static final int arrowButtonWidth = 8;

	//endregion

	//region state

	private final int maxVisibleSlots;
	private final ArrowButtonWidget leftButton;
	private final ArrowButtonWidget rightButton;
	private final RowWidget slotRow;
	private final List<ExecutionSlotWidget> slotWidgets;

	private List<RecipeContent> contents = List.of();
	private int offset;
	private int lastWidth = -1;
	private @Nullable Supplier<@Nullable Tooltip> tooltipProvider;
	private boolean alignStart;

	//endregion

	public ExecutionTargetOutputsWidget(int maxVisibleSlots) {
		this.maxVisibleSlots = Math.max(1, maxVisibleSlots);
		this.slotWidgets = new ArrayList<>(this.maxVisibleSlots);

		useStyle(UIStyle.of(
				flexRow(),
				justifyContent(JustifyContent.CENTER),
				alignItemsCenter(),
				columnGap(arrowGap),
				minWidth(0),
				minHeight(ExecutionSlotWidget.slotSize)
		));
		setTickable(true);

		leftButton = addWidget(new ArrowButtonWidget(true, () -> shift(-1)));
		leftButton.useStyle(displayNone());
		leftButton.setVisible(false);

		slotRow = addWidget(RowWidget.create(slotGap));
		slotRow.useStyle(UIStyle.of(
				alignItemsCenter(),
				minWidth(0),
				minHeight(ExecutionSlotWidget.slotSize)
		));

		for (int index = 0; index < this.maxVisibleSlots; index++) {
			ExecutionSlotWidget slot = slotRow.addWidget(new ExecutionSlotWidget());
			slotWidgets.add(slot);
		}

		rightButton = addWidget(new ArrowButtonWidget(false, () -> shift(1)));
		rightButton.useStyle(displayNone());
		rightButton.setVisible(false);

		onHoverTooltip((mouseX, mouseY) -> tooltipProvider == null ? null : tooltipProvider.get());
		onMouseScrolled((mouseX, mouseY, scrollX, scrollY, context) -> {
			if (scrollY == 0) {
				return EventDispatch.pass;
			}
			return shift(scrollY > 0 ? -1 : 1)
					? EventDispatch.consumed
					: EventDispatch.pass;
		});

		syncPresentation();
	}

	//region lifecycle

	@Override
	public void tick() {
		super.tick();
		if (lastWidth != width()) {
			lastWidth = width();
			syncPresentation();
		}
	}

	//endregion

	//region data

	public void setContents(List<RecipeContent> contents) {
		this.contents = List.copyOf(contents);
		clampOffset(visibleSlotCount());
		syncPresentation();
	}

	public void setTooltipProvider(@Nullable Supplier<@Nullable Tooltip> provider) {
		this.tooltipProvider = provider;
	}

	public void setAlignStart(boolean alignStart) {
		if (this.alignStart == alignStart) {
			return;
		}
		this.alignStart = alignStart;
		syncContainerStyle();
	}

	//endregion

	//region sync

	private void syncPresentation() {
		syncContainerStyle();
		int visibleSlots = visibleSlotCount();
		boolean empty = contents.isEmpty();
		boolean showArrows = !empty && contents.size() > visibleSlots;
		int renderedCount = empty ? 1 : Math.min(visibleSlots, Math.max(0, contents.size() - offset));

		clampOffset(visibleSlots);

		syncArrow(leftButton, showArrows, offset > 0);
		syncArrow(rightButton, showArrows, offset + visibleSlots < contents.size());

		for (int index = 0; index < slotWidgets.size(); index++) {
			ExecutionSlotWidget slot = slotWidgets.get(index);
			boolean visible = index < renderedCount;
			slot.setVisible(visible);
			slot.useStyle(visible ? displayBlock() : displayNone());
			slot.clearVisual();
			slot.setTooltipProvider(null);
			if (!visible || empty) {
				continue;
			}
			RecipeContent content = contents.get(offset + index);
			slot.setRecipeContent(
				content,
				ExecutionOverlayContentHelper.currentVisualIndex(content),
				ExecutionOverlayContentHelper.fallbackToken(content),
				ExecutionOverlayUi.textSecondary
			);
		}
	}

	private void syncArrow(ArrowButtonWidget button, boolean visible, boolean enabled) {
		button.setVisible(visible);
		button.useStyle(visible ? displayFlex() : displayNone());
		button.setEnabled(enabled);
	}

	private boolean shift(int delta) {
		if (contents.isEmpty()) {
			return false;
		}
		int visibleSlots = visibleSlotCount();
		int maxOffset = Math.max(0, contents.size() - visibleSlots);
		int next = Math.max(0, Math.min(maxOffset, offset + delta));
		if (next == offset) {
			return false;
		}
		offset = next;
		syncPresentation();
		ExecutionOverlayUi.playClickSound();
		return true;
	}

	private void clampOffset(int visibleSlots) {
		int maxOffset = Math.max(0, contents.size() - Math.max(1, visibleSlots));
		offset = Math.max(0, Math.min(maxOffset, offset));
	}

	//endregion

	private void syncContainerStyle() {
		useStyle(UIStyle.of(
				flexRow(),
				justifyContent(alignStart ? JustifyContent.FLEX_START : JustifyContent.CENTER),
				alignItemsCenter(),
				columnGap(arrowGap),
				minWidth(0),
				minHeight(ExecutionSlotWidget.slotSize)
		));
	}

	//region layout

	private int visibleSlotCount() {
		int contentSlots = contents.isEmpty() ? 1 : Math.min(maxVisibleSlots, contents.size());
		if (width() <= 0) {
			return contentSlots;
		}
		for (int visible = contentSlots; visible >= 1; visible--) {
			boolean showArrows = !contents.isEmpty() && contents.size() > visible;
			int totalWidth = visible * ExecutionSlotWidget.slotSize
					+ Math.max(0, visible - 1) * slotGap
					+ (showArrows ? arrowButtonWidth * 2 + arrowGap * 2 : 0);
			if (totalWidth <= Math.max(ExecutionSlotWidget.slotSize, width()) || visible == 1) {
				return visible;
			}
		}
		return 1;
	}

	//endregion

	//region widgets

	private static final class ArrowButtonWidget extends CirrusButtonWidget {
		private final boolean left;

		private ArrowButtonWidget(boolean left, Runnable action) {
			super(arrowButtonWidth, ExecutionSlotWidget.slotSize);
			this.left = left;
			clickSound(false).onPress(action);

			useStyle(UIStyle.of(
					flexRow(),
					justifyContent(JustifyContent.CENTER),
					alignItemsCenter(),
					sizeOf(arrowButtonWidth, ExecutionSlotWidget.slotSize),
					minWidth(arrowButtonWidth),
					minHeight(ExecutionSlotWidget.slotSize)
			));

			setEnabled(false);
		}

		private void setEnabled(boolean enabled) {
			setActive(enabled);
			setInteractive(enabled);
			enabled(enabled);
			icon(left
					? CirrusTextures.Icon.Small.arrowLeft : CirrusTextures.Icon.Small.arrowRight,
				left
					? CirrusTextures.Icon.Small.arrowLeftSelected : CirrusTextures.Icon.Small.arrowRightSelected);
		}
	}

	//endregion
}
