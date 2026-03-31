package dev.vfyjxf.cirrus.ui.execution.page;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionTelemetrySnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayTargetProgress;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayUi;
import dev.vfyjxf.cirrus.ui.execution.widget.ExecutionTargetOutputsWidget;
import dev.vfyjxf.cirrus.ui.execution.widget.ExecutionWidgetGroup;
import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusFittedTextWidget;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipStack;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.JustifyContent;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItems;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.columnGap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayBlock;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayNone;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.justifyContent;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.rowGap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.widthOf;

final class RecipeStatusCardWidget extends CompositeWidget<Widget> {
	//region constants

	private static final int targetOutputVisibleCount = 3;
	private static final int statusCardPadding = 4;
	private static final int statusCardPaddingCompact = 3;
	private static final int statusCardPaddingDense = 3;
	private static final int mainGap = 4;
	private static final int mainGapCompact = 3;
	private static final int mainGapDense = 2;
	private static final int infoRowGap = 3;
	private static final int infoRowGapCompact = 2;
	private static final int infoRowGapDense = 2;
	private static final int targetPaneWidth = 62;
	private static final int targetPaneWidthCompact = 50;
	private static final int targetPaneWidthDense = 42;
	private static final int targetPaneMinWidth = 42;
	private static final int targetPaneMinWidthCompact = 38;
	private static final int targetPaneMinWidthDense = 30;
	private static final int infoIconCellWidth = 0;
	private static final int infoIconCellWidthCompact = 0;
	private static final int infoIconCellWidthDense = 0;
	private static final int infoTextMinWidth = 42;
	private static final int infoTextMinWidthCompact = 34;
	private static final int infoTextMinWidthDense = 28;
	private static final int bottomStripHeight = 15;
	private static final int bottomStripHeightCompact = 14;
	private static final int bottomStripHeightDense = 13;
	private static final int quantityInputWidth = 38;
	private static final int quantityInputWidthCompact = 34;
	private static final int quantityInputWidthDense = 30;
	private static final int quantityInputHeight = 13;
	private static final int quantityInputHeightCompact = 12;
	private static final int quantityInputHeightDense = 12;
	private static final int textChromeInset = 7;
	private static final int metricMarkerWidth = 1;
	private static final int metricTextGap = 2;
	//endregion

	//region state

	private final RecipePage owner;
	private final ExecutionWidgetGroup mainRow;
	private final TargetPaneWidget targetPane;
	private final ExecutionWidgetGroup infoColumn;
	private final StatusInfoRowWidget requiredRow;
	private final StatusInfoRowWidget progressRow;
	private final TargetStripWidget targetStrip;
	private final ExecutionTargetOutputsWidget targetOutputs;
	private final NumberInputWidget targetTimesInput;

	private boolean syncingTargetTimesInput;
	private RecipePage.LayoutDensity layoutDensity = RecipePage.LayoutDensity.normal;
	//endregion

	RecipeStatusCardWidget(RecipePage owner) {
		this.owner = owner;
		useStyle(UIStyle.of(
				flexColumn(),
				rowGap(mainGap),
				padding(statusCardPadding),
				minWidth(0),
				minHeight(0),
				boxSizingBorderBox()
		));
		setTickable(true);
		onHoverTooltip((mouseX, mouseY) -> owner.statusTooltip());

		mainRow = addWidget(new ExecutionWidgetGroup());
		mainRow.useStyle(UIStyle.of(
				flexRow(),
				alignItems(AlignItems.STRETCH),
				columnGap(mainGap),
				flexGrow(1),
				minWidth(0),
				minHeight(0)
		));

		targetPane = mainRow.append(new TargetPaneWidget());
		targetOutputs = targetPane.outputs();
		targetOutputs.setTooltipProvider(owner::statusTooltip);

		infoColumn = mainRow.append(new ExecutionWidgetGroup());
		infoColumn.useStyle(UIStyle.of(
				flexColumn(),
				rowGap(infoRowGap),
				flexGrow(1),
				minWidth(0),
				minHeight(0)
		));

		requiredRow = infoColumn.append(new StatusInfoRowWidget(
				CirrusTextures.Icon.Sign.Amount.missing,
				ExecutionOverlayUi.warning,
				this::requiredTooltip
		));
		progressRow = infoColumn.append(new StatusInfoRowWidget(
				CirrusTextures.Icon.Sign.Amount.available,
				ExecutionOverlayUi.success,
				this::progressTooltip
		));

		targetStrip = addWidget(new TargetStripWidget(this::handleTargetTimesChanged));
		targetTimesInput = targetStrip.targetTimesInput();
		targetTimesInput.tooltipWithCalculatorPopup(this::targetTimesTooltip);
	}

	//region lifecycle

	@Override
	public void tick() {
		super.tick();
		syncResponsiveLayout();
		syncTargetOutputs();
		syncMetricRows();
		syncTargetTimesInput();
	}

	void setLayoutDensity(RecipePage.LayoutDensity density) {
		layoutDensity = density == null ? RecipePage.LayoutDensity.normal : density;
		syncResponsiveLayout();
	}
	//endregion

	//region tooltip

	private Tooltip targetTimesTooltip() {
		Tooltip tooltip = Tooltip.create();
		tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Execution.Overlay.Summary.target));
		tooltip.add(ExecutionOverlayUi.tooltipAccentText("x" + Math.max(1, owner.viewState().targetTimes())));
		String target = owner.viewState().targetLabel();
		if (target == null || target.isBlank()) {
			tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Execution.Overlay.Target.none));
		} else {
			tooltip.add(ExecutionOverlayUi.tooltipBodyText(target));
		}
		tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Execution.Overlay.Tooltip.edit));
		TooltipStack<?> stack = ExecutionOverlayContentHelper.tooltipStack(primaryTargetContent());
		if (stack != null) {
			tooltip.setStack(stack);
		}
		return tooltip;
	}

	private Tooltip requiredTooltip() {
		OverlayTargetProgress progress = owner.viewState().targetProgress();
		Tooltip tooltip = Tooltip.create();
		tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Execution.Overlay.Summary.need));
		tooltip.add(ExecutionOverlayUi.tooltipAccentText(
				progress != null && progress.required() > 0
						? ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.need)
						+ " " + ExecutionOverlayUi.compactNumber(progress.required())
						: "-"
		));
		TooltipStack<?> stack = ExecutionOverlayContentHelper.tooltipStack(primaryTargetContent());
		if (stack != null) {
			tooltip.setStack(stack);
		}
		owner.appendStatusDetails(tooltip);
		return tooltip;
	}

	private Tooltip progressTooltip() {
		Tooltip tooltip = Tooltip.create();
		tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Execution.Overlay.Tooltip.progress));
		OverlayTargetProgress progress = owner.viewState().targetProgress();
		if (owner.viewState().targetContent().isEmpty()) {
			tooltip.add(ExecutionOverlayUi.tooltipAccentText("-"));
		} else if (progress != null && progress.required() > 0) {
			tooltip.add(ExecutionOverlayUi.tooltipAccentText(
					ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.have)
							+ " " + ExecutionOverlayUi.compactNumber(progress.current())
							+ " / " + ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.need)
							+ " " + ExecutionOverlayUi.compactNumber(progress.required())
			));
		} else {
			ExecutionTelemetrySnapshot telemetry = owner.viewState().telemetry();
			tooltip.add(ExecutionOverlayUi.tooltipAccentText(
					telemetry == null ? "-" : telemetry.completedSteps() + "/" + telemetry.totalSteps()
			));
		}
		TooltipStack<?> stack = ExecutionOverlayContentHelper.tooltipStack(primaryTargetContent());
		if (stack != null) {
			tooltip.setStack(stack);
		}
		owner.appendStatusDetails(tooltip);
		return tooltip;
	}
	//endregion

	//region state helpers

	private void syncTargetTimesInput() {
		boolean hasTarget = owner.viewState().targetLabel() != null || !owner.viewState().targetContent().isEmpty();
		targetTimesInput.setActive(hasTarget);
		targetTimesInput.setInteractive(hasTarget);

		long targetTimes = hasTarget ? Math.max(1, owner.viewState().targetTimes()) : 1L;
		if (targetTimesInput.focused() || targetTimesInput.getValue() == targetTimes) {
			return;
		}
		syncingTargetTimesInput = true;
		try {
			targetTimesInput.setValue(targetTimes);
		} finally {
			syncingTargetTimesInput = false;
		}
	}

	private void syncTargetOutputs() {
		List<RecipeContent> outputs = owner.targetOutputContents();
		targetOutputs.setContents(outputs);
		targetOutputs.setAlignStart(useAlignedOutputs(layoutDensity, targetPane.width(), outputs.size()));
	}

	private void syncMetricRows() {
		OverlayTargetProgress progress = owner.viewState().targetProgress();
		ExecutionTelemetrySnapshot telemetry = owner.viewState().telemetry();
		requiredRow.setValue(metricText(requiredDisplayValue(progress)));
		progressRow.setValue(metricText(progressDisplayValue(progress, telemetry)));
	}

	private String requiredDisplayValue(@Nullable OverlayTargetProgress progress) {
		if (owner.viewState().calculating()) {
			return ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.calculatingShort);
		}
		return progress != null && progress.required() > 0
				? ExecutionOverlayUi.compactNumber(progress.required())
				: "-";
	}

	private String progressDisplayValue(@Nullable OverlayTargetProgress progress, @Nullable ExecutionTelemetrySnapshot telemetry) {
		if (owner.viewState().calculating()) {
			return ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.calculatingShort);
		}
		if (owner.targetOutputContents().isEmpty()) {
			return "-";
		}
		if (progress != null && progress.required() > 0) {
			return ExecutionOverlayUi.compactNumber(progress.current())
					+ "/" + ExecutionOverlayUi.compactNumber(progress.required());
		}
		return telemetry == null ? "-" : telemetry.completedSteps() + "/" + telemetry.totalSteps();
	}

	private String metricText(String text) {
		return ExecutionOverlayUi.trim(context().font(), text, statusTextAvailableWidth(layoutDensity, width()));
	}

	private RecipeContent primaryTargetContent() {
		List<RecipeContent> outputs = owner.targetOutputContents();
		return outputs.isEmpty() ? owner.viewState().targetContent() : outputs.getFirst();
	}

	private void handleTargetTimesChanged(long value) {
		if (syncingTargetTimesInput) {
			return;
		}
		owner.viewState().setTargetTimes((int) Math.max(1L, Math.min(Integer.MAX_VALUE, value)));
	}

	private void syncResponsiveLayout() {
		int cardPadding = statusCardPadding(layoutDensity);
		int gap = mainGap(layoutDensity);
		int rowGap = infoRowGap(layoutDensity);
		int targetWidth = targetPaneWidth(layoutDensity, width());
		int iconCellWidth = infoIconCellWidth(layoutDensity);
		int bottomHeight = bottomStripHeight(layoutDensity);
		int inputWidth = quantityInputWidth(layoutDensity);
		int inputHeight = quantityInputHeight(layoutDensity);

		useStyle(UIStyle.of(
				flexColumn(),
				rowGap(gap),
				padding(cardPadding),
				minWidth(0),
				minHeight(0),
				boxSizingBorderBox()
		));
		mainRow.useStyle(UIStyle.of(
				flexRow(),
				alignItems(AlignItems.STRETCH),
				columnGap(gap),
				flexGrow(1),
				minWidth(0),
				minHeight(0)
		));
		targetPane.useStyle(UIStyle.of(
				widthOf(targetWidth),
				minWidth(targetWidth),
				flexShrink(0),
				minHeight(0)
		));
		infoColumn.useStyle(UIStyle.of(
				flexColumn(),
				rowGap(rowGap),
				flexGrow(1),
				minWidth(0),
				minHeight(0)
		));
		requiredRow.applyMetrics(iconCellWidth, rowGap);
		progressRow.applyMetrics(iconCellWidth, rowGap);
		targetStrip.applyMetrics(bottomHeight, inputWidth, inputHeight);
	}

	@Override
	protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
		canvas.texture(Background.Border.light, 0, 0, width(), height());
		canvas.texture(Background.flat, 1, 1, Math.max(0, width() - 2), Math.max(0, height() - 2));
	}

	static int statusCardPadding(RecipePage.LayoutDensity density) {
		return switch (density) {
			case normal -> statusCardPadding;
			case compact -> statusCardPaddingCompact;
			case dense -> statusCardPaddingDense;
		};
	}

	static int mainGap(RecipePage.LayoutDensity density) {
		return switch (density) {
			case normal -> mainGap;
			case compact -> mainGapCompact;
			case dense -> mainGapDense;
		};
	}

	static int infoRowGap(RecipePage.LayoutDensity density) {
		return switch (density) {
			case normal -> infoRowGap;
			case compact -> infoRowGapCompact;
			case dense -> infoRowGapDense;
		};
	}

	static int targetPaneWidth(RecipePage.LayoutDensity density, int cardWidth) {
		int preferred = switch (density) {
			case normal -> targetPaneWidth;
			case compact -> targetPaneWidthCompact;
			case dense -> targetPaneWidthDense;
		};
		if (cardWidth <= 0) {
			return preferred;
		}
		int min = switch (density) {
			case normal -> targetPaneMinWidth;
			case compact -> targetPaneMinWidthCompact;
			case dense -> targetPaneMinWidthDense;
		};
		int max = cardWidth
				- statusCardPadding(density) * 2
				- mainGap(density)
				- infoIconBlockWidth(density)
				- infoTextMinWidth(density);
		return Math.max(min, Math.min(preferred, max));
	}

	static int infoIconCellWidth(RecipePage.LayoutDensity density) {
		return switch (density) {
			case normal -> infoIconCellWidth;
			case compact -> infoIconCellWidthCompact;
			case dense -> infoIconCellWidthDense;
		};
	}

	static int infoIconBlockWidth(RecipePage.LayoutDensity density) {
		int iconWidth = infoIconCellWidth(density);
		return iconWidth <= 0 ? 0 : iconWidth + infoRowGap(density);
	}

	static int infoTextMinWidth(RecipePage.LayoutDensity density) {
		return switch (density) {
			case normal -> infoTextMinWidth;
			case compact -> infoTextMinWidthCompact;
			case dense -> infoTextMinWidthDense;
		};
	}

	static int bottomStripHeight(RecipePage.LayoutDensity density) {
		return switch (density) {
			case normal -> bottomStripHeight;
			case compact -> bottomStripHeightCompact;
			case dense -> bottomStripHeightDense;
		};
	}

	static int quantityInputWidth(RecipePage.LayoutDensity density) {
		return switch (density) {
			case normal -> quantityInputWidth;
			case compact -> quantityInputWidthCompact;
			case dense -> quantityInputWidthDense;
		};
	}

	static int quantityInputHeight(RecipePage.LayoutDensity density) {
		return switch (density) {
			case normal -> quantityInputHeight;
			case compact -> quantityInputHeightCompact;
			case dense -> quantityInputHeightDense;
		};
	}

	static int statusTextAvailableWidth(RecipePage.LayoutDensity density, int cardWidth) {
		if (cardWidth <= 0) {
			return Math.max(8, infoTextMinWidth(density) - textChromeInset);
		}
		int width = cardWidth
				- statusCardPadding(density) * 2
				- mainGap(density)
				- targetPaneWidth(density, cardWidth)
				- infoIconBlockWidth(density)
				- textChromeInset;
		return Math.max(8, width);
	}

	static boolean useAlignedOutputs(RecipePage.LayoutDensity density, int width, int outputCount) {
		return outputCount <= 1 || density != RecipePage.LayoutDensity.normal || width < targetPaneWidth;
	}

	static int metricTextOffset() {
		return metricMarkerWidth + metricTextGap;
	}
	//endregion

	//region widgets

	private static final class TargetPaneWidget extends CompositeWidget<Widget> {
		private final ExecutionTargetOutputsWidget outputs;

		private TargetPaneWidget() {
			useStyle(UIStyle.of(
					flexColumn(),
					justifyContent(JustifyContent.CENTER),
					padding(3),
					minWidth(0),
					minHeight(0),
					boxSizingBorderBox()
			));
			outputs = addWidget(new ExecutionTargetOutputsWidget(targetOutputVisibleCount));
			outputs.useStyle(UIStyle.of(
					flexGrow(1),
					minWidth(0),
					minHeight(ExecutionOverlayUi.slotSize)
			));
		}

		private ExecutionTargetOutputsWidget outputs() {
			return outputs;
		}
	}

	private static final class StatusInfoRowWidget extends CompositeWidget<Widget> {
		private final StatusIconCellWidget iconCell;
		private final StatusTextPanelWidget textPanel;

		private StatusInfoRowWidget(ImageTexture icon, int markerColor, Supplier<@Nullable Tooltip> tooltipProvider) {
			useStyle(UIStyle.of(
					flexRow(),
					alignItems(AlignItems.STRETCH),
					columnGap(infoRowGap),
					flexGrow(1),
					minWidth(0),
					minHeight(0)
			));
			onHoverTooltip((mouseX, mouseY) -> tooltipProvider.get());

			iconCell = addWidget(new StatusIconCellWidget(icon));
			textPanel = addWidget(new StatusTextPanelWidget(markerColor, tooltipProvider));
		}

		private void setValue(String text) {
			textPanel.setText(text);
		}

		private void applyMetrics(int iconCellWidth, int rowGap) {
			useStyle(UIStyle.of(
					flexRow(),
					alignItems(AlignItems.STRETCH),
					columnGap(iconCellWidth > 0 ? rowGap : 0),
					flexGrow(1),
					minWidth(0),
					minHeight(0)
			));
			iconCell.useStyle(UIStyle.of(
					widthOf(iconCellWidth),
					minWidth(iconCellWidth),
					flexShrink(0),
					minHeight(0),
					iconCellWidth > 0 ? displayBlock() : displayNone()
			));
			iconCell.setVisible(iconCellWidth > 0);
			textPanel.useStyle(UIStyle.of(
					flexGrow(1),
					minWidth(0),
					minHeight(0)
			));
		}
	}

	private static final class StatusIconCellWidget extends Widget {
		private final ImageTexture icon;

		private StatusIconCellWidget(ImageTexture icon) {
			this.icon = icon;
			setActive(false);
			setInteractive(false);
		}

		@Override
		protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
			if (width() <= 0 || height() <= 0) {
				return;
			}
			int iconX = Math.max(0, (width() - icon.width()) / 2);
			int iconY = Math.max(0, (height() - icon.height()) / 2);
			canvas.texture(icon, iconX, iconY, icon.width(), icon.height());
		}
	}

	private static final class StatusTextPanelWidget extends Widget {
		private final int markerColor;
		private final Supplier<@Nullable Tooltip> tooltipProvider;
		private Component text = Component.literal("-");

		private StatusTextPanelWidget(int markerColor, Supplier<@Nullable Tooltip> tooltipProvider) {
			this.markerColor = markerColor;
			this.tooltipProvider = tooltipProvider;
			onHoverTooltip((mouseX, mouseY) -> this.tooltipProvider.get());
		}

		private void setText(String text) {
			this.text = Component.literal(text == null || text.isBlank() ? "-" : text);
		}

		@Override
		protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
			Font font = context().font();
			int available = Math.max(0, width() - textChromeInset);
			String fitted = ExecutionOverlayUi.trim(font, text.getString(), available);
			int absX = absolutePos().x();
			int absY = absolutePos().y();
			int markerHeight = Math.max(1, font.lineHeight - 2);
			int markerY = Math.max(0, (height() - markerHeight) / 2);
			int textX = metricTextOffset();
			canvas.fill(0, markerY, metricMarkerWidth, markerHeight, markerColor);
			canvas.pushClip(absX + textX, absY, available, height());
			canvas.text(fitted, textX, Math.max(0, (height() - font.lineHeight) / 2 + 1),
					ExecutionOverlayUi.textPrimary, false);
			canvas.popClip();
		}
	}

	private static final class TargetStripWidget extends CompositeWidget<Widget> {
		private final CirrusFittedTextWidget timesPrefix;
		private final NumberInputWidget targetTimesInput;

		private TargetStripWidget(java.util.function.LongConsumer onTargetTimesChanged) {
			useStyle(UIStyle.of(
					flexRow(),
					alignItemsCenter(),
					justifyContent(JustifyContent.FLEX_END),
					columnGap(3),
					padding(0),
					minWidth(0),
					minHeight(bottomStripHeight),
					boxSizingBorderBox()
			));
			onHoverTooltip((mouseX, mouseY) -> null);

			timesPrefix = addWidget(new CirrusFittedTextWidget(Component.literal("x"), ExecutionOverlayUi.textSecondary));
			timesPrefix.useStyle(UIStyle.of(
					sizeOf(6, 10),
					minWidth(6),
					flexShrink(0)
			));

			targetTimesInput = addWidget(NumberInputWidget.create(1)
					.setRange(1, Integer.MAX_VALUE)
					.setScrollStep(1)
					.enableCalculatorPopup()
					.onValueChanged(onTargetTimesChanged)
			).cast();
			targetTimesInput.useStyle(UIStyle.of(
					sizeOf(quantityInputWidth, quantityInputHeight),
					minWidth(quantityInputWidth),
					minHeight(quantityInputHeight),
					flexShrink(0)
			));
		}

		private NumberInputWidget targetTimesInput() {
			return targetTimesInput;
		}

		private void applyMetrics(int height, int inputWidth, int inputHeight) {
			useStyle(UIStyle.of(
					flexRow(),
					alignItemsCenter(),
					justifyContent(JustifyContent.FLEX_END),
					columnGap(3),
					padding(0),
					heightOf(height),
					minHeight(height),
					minWidth(0),
					boxSizingBorderBox()
			));
			timesPrefix.useStyle(UIStyle.of(
					sizeOf(6, Math.max(10, inputHeight)),
					minWidth(6),
					flexShrink(0)
			));
			targetTimesInput.useStyle(UIStyle.of(
					sizeOf(inputWidth, inputHeight),
					minWidth(inputWidth),
					minHeight(inputHeight),
					flexShrink(0)
			));
		}
	}
	//endregion
}
