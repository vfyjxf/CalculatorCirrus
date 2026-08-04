package dev.vfyjxf.cirrus.ui.execution.widget;

import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.ui.widget.ImageWidget;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.columnGap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.justifyCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public class ExecutionMetricChipWidget extends CompositeWidget<Widget> {
	//region constants

	private static final int iconCellSize = 10;
	private static final int iconSize = 6;

	//endregion

	//region state

	private final ImageWidget iconWidget;
	private final LabelWidget valueLabel;

	private @Nullable Supplier<@Nullable Tooltip> tooltipProvider;

	//endregion

	public ExecutionMetricChipWidget(ImageTexture icon) {
		useStyle(UIStyle.of(
				flexRow(),
				alignItemsCenter(),
				columnGap(3),
				background(Background.inset),
				minWidth(0),
				minHeight(iconCellSize),
				boxSizingBorderBox()
		));

		ExecutionWidgetGroup iconFrame = addWidget(new ExecutionWidgetGroup());
		iconFrame.useStyle(UIStyle.of(
				flexRow(),
				justifyCenter(),
				alignItemsCenter(),
				background(Background.inset),
				sizeOf(iconCellSize, iconCellSize),
				minWidth(iconCellSize),
				minHeight(iconCellSize)
		));
		iconFrame.setInteractive(false);
		iconFrame.setActive(false);

		iconWidget = iconFrame.append(ImageWidget.of(icon));
		iconWidget.useStyle(UIStyle.of(
				sizeOf(iconSize, iconSize),
				minWidth(iconSize),
				minHeight(iconSize)
		));
		iconWidget.setInteractive(false);
		iconWidget.setActive(false);

		valueLabel = addWidget(LabelWidget.of("-"));
		valueLabel.setShadow(false);
		valueLabel.setAlign(LabelWidget.TextAlign.CENTER);
		valueLabel.useStyle(UIStyle.of(
				flexGrow(1),
				minWidth(0)
		));

		onHoverTooltip((mouseX, mouseY) -> tooltipProvider == null ? null : tooltipProvider.get());
	}

	//region data

	public void setIcon(ImageTexture icon) {
		iconWidget.setTexture(icon);
	}

	public void setValueText(String text) {
		valueLabel.setText(text);
	}

	public void setValueColor(int color) {
		valueLabel.setColor(color);
	}

	public void setTooltipProvider(@Nullable Supplier<@Nullable Tooltip> provider) {
		this.tooltipProvider = provider;
	}

	//endregion
}
