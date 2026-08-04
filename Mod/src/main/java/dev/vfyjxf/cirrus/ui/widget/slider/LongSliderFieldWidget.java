package dev.vfyjxf.cirrus.ui.widget.slider;

import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.taffy.style.AlignItems;
import org.jetbrains.annotations.Nullable;

import java.util.function.LongConsumer;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItems;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.columnGap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.rowGap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public class LongSliderFieldWidget extends CompositeWidget<Widget> {

	private final LongSliderWidget slider;
	private final NumberInputWidget input;

	private LongSliderWidget.Orientation orientation = LongSliderWidget.Orientation.HORIZONTAL;
	private int gap = 4;
	private int controlSize = 24;
	private int inputExtent = 38;
	private boolean syncing;

	@Nullable
	private LongConsumer onValueChanged;

	public LongSliderFieldWidget() {
		slider = addWidget(new LongSliderWidget());
		input = addWidget(NumberInputWidget.create(0));
		slider.onValueChanged(this::handleSliderChanged);
		input.onValueChanged(this::handleInputChanged);
		applyLayoutStyle();
	}

	public LongSliderFieldWidget setRange(long min, long max) {
		syncing = true;
		try {
			slider.setRange(min, max);
			input.setRange(min, max);
			if (input.getValue() != slider.value()) {
				input.setValue(slider.value());
			}
		} finally {
			syncing = false;
		}
		return this;
	}

	public LongSliderFieldWidget setStep(long step) {
		long normalizedStep = Math.max(1L, step);
		slider.setStep(normalizedStep);
		input.setScrollStep(normalizedStep);
		return this;
	}

	public LongSliderFieldWidget setValue(long value) {
		syncing = true;
		try {
			slider.setValue(value);
			if (input.getValue() != slider.value()) {
				input.setValue(slider.value());
			}
		} finally {
			syncing = false;
		}
		return this;
	}

	public LongSliderFieldWidget setSliderValue(long value) {
		slider.setValue(value);
		return this;
	}

	public LongSliderFieldWidget setOrientation(LongSliderWidget.Orientation orientation) {
		this.orientation = orientation;
		slider.setOrientation(orientation);
		applyLayoutStyle();
		return this;
	}

	public LongSliderFieldWidget setGap(int gap) {
		this.gap = Math.max(0, gap);
		applyLayoutStyle();
		return this;
	}

	public LongSliderFieldWidget setControlSize(int controlSize) {
		this.controlSize = Math.max(1, controlSize);
		applyLayoutStyle();
		return this;
	}

	public LongSliderFieldWidget setInputExtent(int inputExtent) {
		this.inputExtent = Math.max(1, inputExtent);
		applyLayoutStyle();
		return this;
	}

	public LongSliderFieldWidget setSkin(LongSliderWidget.Skin skin) {
		slider.setSkin(skin);
		return this;
	}

	public LongSliderFieldWidget setSoundPlayer(@Nullable LongConsumer soundPlayer) {
		slider.setSoundPlayer(soundPlayer);
		return this;
	}

	public LongSliderFieldWidget setSoundMinIntervalMs(long soundMinIntervalMs) {
		slider.setSoundMinIntervalMs(soundMinIntervalMs);
		return this;
	}

	public LongSliderFieldWidget onValueChanged(@Nullable LongConsumer onValueChanged) {
		this.onValueChanged = onValueChanged;
		return this;
	}

	public LongSliderFieldWidget setSliderTooltip(@Nullable Tooltip tooltip) {
		slider.setTooltip(tooltip);
		return this;
	}

	public LongSliderFieldWidget setInputTooltip(@Nullable Tooltip tooltip) {
		input.setTooltip(tooltip);
		return this;
	}

	public long value() {
		return slider.value();
	}

	public boolean dragging() {
		return slider.dragging();
	}

	public boolean inputFocused() {
		return input.focused();
	}

	public boolean hasRangeWarning() {
		return input.hasRangeWarning();
	}

	private void handleSliderChanged(long value) {
		syncing = true;
		try {
			if (input.getValue() != value) {
				input.setValue(value);
			}
		} finally {
			syncing = false;
		}
		notifyValueChanged(value);
	}

	private void handleInputChanged(long value) {
		if (syncing) {
			return;
		}
		slider.setValue(value);
		notifyValueChanged(slider.value());
	}

	private void notifyValueChanged(long value) {
		if (onValueChanged != null) {
			onValueChanged.accept(value);
		}
	}

	private void applyLayoutStyle() {
		if (orientation == LongSliderWidget.Orientation.HORIZONTAL) {
			useStyle(UIStyle.of(
					flexRow(),
					alignItems(AlignItems.CENTER),
					columnGap(gap),
					minWidth(0),
					minHeight(controlSize)
			));
			slider.useStyle(UIStyle.of(
					flexGrow(1),
					minWidth(0),
					sizeOf(-1, controlSize)
			));
			input.useStyle(UIStyle.of(
					sizeOf(inputExtent, controlSize),
					minWidth(inputExtent)
			));
			return;
		}

		useStyle(UIStyle.of(
				flexColumn(),
				alignItems(AlignItems.CENTER),
				rowGap(gap),
				minWidth(Math.max(controlSize, inputExtent)),
				minHeight(0)
		));
		slider.useStyle(UIStyle.of(
				sizeOf(controlSize, -1),
				minWidth(controlSize),
				minHeight(0)
		));
		input.useStyle(UIStyle.of(
				sizeOf(inputExtent, controlSize),
				minWidth(inputExtent)
		));
	}
}
