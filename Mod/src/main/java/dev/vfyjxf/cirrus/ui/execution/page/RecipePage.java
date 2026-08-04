package dev.vfyjxf.cirrus.ui.execution.page;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionTelemetrySnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriver;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayNode;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayMaterialRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayTargetProgress;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.CirrusScroll;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.execution.state.ExecutionOverlayViewState;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayUi;
import dev.vfyjxf.cirrus.ui.execution.tooltip.ExecutionRecipeTooltipPreview;
import dev.vfyjxf.cirrus.ui.execution.widget.ExecutionWidgetGroup;
import dev.vfyjxf.cirrus.ui.process.ProcessNode;
import dev.vfyjxf.cirrus.ui.tooltip.RichTooltipComponent;
import dev.vfyjxf.cirrus.ui.widget.slider.CirrusSliderSkins;
import dev.vfyjxf.cirrus.ui.widget.slider.LongSliderFieldWidget;
import dev.vfyjxf.cirrus.ui.widget.slider.LongSliderWidget;
import dev.vfyjxf.cloudlib.api.ui.InputContext;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.effect.UIEffects;
import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipStack;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.JustifyContent;
import net.minecraft.client.gui.Font;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItems;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.columnGap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.justifyContent;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.marginTop;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.rowGap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public class RecipePage extends CompositeWidget<Widget> {
	//region constants

	private static final int topRowHeight = 66;
	private static final int topRowHeightCompact = 56;
	private static final int topRowHeightDense = 52;
	private static final int controlButtonSize = 18;
	private static final int controlButtonSizeCompact = 16;
	private static final int controlButtonSizeDense = 14;
	private static final int topRowContentTopInset = 2;
	private static final int controlButtonGap = 4;
	private static final int controlButtonGapCompact = 3;
	private static final int controlButtonGapDense = 2;
	private static final int controlColumnRightInset = 4;
	private static final int controlColumnRightInsetCompact = 3;
	private static final int controlColumnRightInsetDense = 2;
	private static final int speedRowHeight = 20;
	private static final int speedRowHeightCompact = 18;
	private static final int speedRowHeightDense = 16;
	private static final long sliderSoundMinIntervalMs = 80L;
	private static final int executionCardHeight = 98;
	private static final int executionCardHeightCompact = 80;
	private static final int executionCardHeightDense = 64;
	private static final int materialPanelHeight = 70;
	private static final int materialPanelHeightCompact = 58;
	private static final int materialPanelHeightDense = 46;
	private static final int currentRecipeScrollBarWidth = 6;
	//endregion

	//region state

	private final ExecutionOverlayViewState state;
	private final ExecutionWidgetGroup topRow;
	private final ExecutionWidgetGroup controlColumn;
	private final RecipeStatusCardWidget statusCard;
	private final RecipeCurrentRecipeCardWidget currentRecipeCard;
	private final RecipeMaterialsPanelWidget materialsPanel;
	private final RecipeIconButtonWidget playbackButton;
	private final RecipeIconButtonWidget stepButton;
	private final LongSliderFieldWidget rateControl;
	private final ScrollState pageScrollState;

	private LayoutDensity layoutDensity = LayoutDensity.normal;
	private static final int pageScrollbarVerticalInset = 4;
	private static final int pageScrollbarRightInset = 1;
	//endregion

	//region setup

	public RecipePage(ExecutionOverlayViewState state) {
		this.state = state;
		this.pageScrollState = CirrusScroll.longVerticalState(10)
										  .smooth(true)
										  .smoothSpeed(0.35f)
										  .trackTexture(Background.scroll)
										  .thumbTexture(CirrusTextures.Icon.scrollBarVertical)
										  .scrollbarWidth(currentRecipeScrollBarWidth)
										  .minThumbSize(14)
										  .viewportInset(pageScrollbarVerticalInset, pageScrollbarRightInset, pageScrollbarVerticalInset, 0);
		useStyle(UIStyle.of(
				flexColumn(),
				background(Background.Outlined.flat),
				padding(ExecutionOverlayUi.bodyPadding),
				rowGap(6),
				minWidth(0),
				minHeight(0),
				boxSizingBorderBox()
		));
		useEffect(UIEffects.scrollable(pageScrollState));
		setTickable(true);

		topRow = addWidget(new ExecutionWidgetGroup());
		topRow.useStyle(UIStyle.of(
				flexRow(),
				alignItems(AlignItems.STRETCH),
				columnGap(4),
				minWidth(0),
				minHeight(topRowHeight)
		));

		statusCard = topRow.append(new RecipeStatusCardWidget(this));
		statusCard.useStyle(UIStyle.of(
				flexGrow(1),
				minWidth(0),
				minHeight(topRowHeight - topRowContentTopInset),
				marginTop(topRowContentTopInset)
		));
		statusCard.setLayoutDensity(layoutDensity);

		controlColumn = topRow.append(new ExecutionWidgetGroup());
		controlColumn.useStyle(UIStyle.of(
				flexColumn(),
				alignItems(AlignItems.FLEX_START),
				justifyContent(JustifyContent.FLEX_START),
				rowGap(controlButtonGap),
				minWidth(controlColumnWidth(LayoutDensity.normal)),
				sizeOf(controlColumnWidth(LayoutDensity.normal), topRowHeight - topRowContentTopInset),
				marginTop(topRowContentTopInset)
		));

		playbackButton = controlColumn.append(new RecipeIconButtonWidget(
				() -> playbackRunning() ? CirrusTextures.Icon.pause : CirrusTextures.Icon.start,
				this::playbackRunning,
				this::togglePlayback,
				this::playbackTooltip
		));
		playbackButton.useStyle(sizeOf(controlButtonSize, controlButtonSize));

		stepButton = controlColumn.append(new RecipeIconButtonWidget(
				() -> CirrusTextures.Icon.next,
				() -> false,
				input -> advanceOneStep(),
				this::stepTooltip
		));
		stepButton.useStyle(sizeOf(controlButtonSize, controlButtonSize));

		rateControl = addWidget(new LongSliderFieldWidget());
		rateControl
				.setOrientation(LongSliderWidget.Orientation.HORIZONTAL)
				.setRange(ExecutionOverlayUi.rateMin, ExecutionOverlayUi.rateMax)
				.setStep(1)
				.setGap(4)
				.setControlSize(speedRowHeight)
				.setInputExtent(38)
				.setSkin(CirrusSliderSkins.scroll())
				.setSoundPlayer(value -> {
					float normalized = (float) (value - ExecutionOverlayUi.rateMin)
							/ Math.max(1, ExecutionOverlayUi.rateMax - ExecutionOverlayUi.rateMin);
					CirrusUiSound.playClickSound(0.9f + normalized * 0.18f);
				})
				.setSoundMinIntervalMs(sliderSoundMinIntervalMs)
				.onValueChanged(this::updateRateControl);
		rateControl.useStyle(UIStyle.of(
				minWidth(0),
				minHeight(speedRowHeight),
				sizeOf(-1, speedRowHeight)
		));

		currentRecipeCard = addWidget(new RecipeCurrentRecipeCardWidget(this));
		currentRecipeCard.useStyle(UIStyle.of(
				minWidth(0),
				minHeight(0)
		));
		currentRecipeCard.setLayoutDensity(layoutDensity);

		materialsPanel = addWidget(new RecipeMaterialsPanelWidget(state));
		materialsPanel.useStyle(UIStyle.of(
				minWidth(0),
				minHeight(materialPanelHeight),
				sizeOf(-1, materialPanelHeight)
		));
		materialsPanel.setLayoutDensity(layoutDensity);
	}
	//endregion

	//region lifecycle

	@Override
	public void tick() {
		syncResponsiveLayout();
		super.tick();
		syncRateControls();
		updateDynamicTooltips();
	}
	//endregion

	//region component access

	ExecutionOverlayViewState viewState() {
		return state;
	}
	//endregion

	//region layout

	private void syncResponsiveLayout() {
		LayoutDensity density = resolveLayoutDensity(width(), height());
		if (density != layoutDensity) {
			layoutDensity = density;
			statusCard.setLayoutDensity(density);
			currentRecipeCard.setLayoutDensity(density);
			materialsPanel.setLayoutDensity(density);
		}

		int pagePadding = switch (density) {
			case normal -> ExecutionOverlayUi.bodyPadding;
			case compact -> 4;
			case dense -> 3;
		};
		int rowGap = switch (density) {
			case normal -> 6;
			case compact -> 4;
			case dense -> 3;
		};
		int rowHeight = topRowHeight(density);
		int buttonSize = controlButtonSize(density);
		int buttonGap = controlButtonGap(density);
		int controlColumnWidth = controlColumnWidth(density);
		int rateHeight = speedRowHeight(density);
		int inputExtent = switch (density) {
			case normal -> 38;
			case compact -> 34;
			case dense -> 30;
		};
		int cardHeight = currentRecipeCardHeight(density);
		int materialHeight = materialPanelHeight(density);

		useStyle(UIStyle.of(
				flexColumn(),
				background(Background.Outlined.flat),
				padding(pagePadding),
				rowGap(rowGap),
				minWidth(0),
				minHeight(0),
				boxSizingBorderBox()
		));
		topRow.useStyle(UIStyle.of(
				flexRow(),
				alignItems(AlignItems.STRETCH),
				columnGap(Math.max(2, rowGap)),
				minWidth(0),
				minHeight(rowHeight)
		));
		statusCard.useStyle(UIStyle.of(
				flexGrow(1),
				minWidth(0),
				minHeight(Math.max(0, rowHeight - topRowContentTopInset)),
				marginTop(topRowContentTopInset)
		));
		controlColumn.useStyle(UIStyle.of(
				flexColumn(),
				alignItems(AlignItems.FLEX_START),
				justifyContent(JustifyContent.FLEX_START),
				rowGap(buttonGap),
				minWidth(controlColumnWidth),
				sizeOf(controlColumnWidth, Math.max(0, rowHeight - topRowContentTopInset)),
				marginTop(topRowContentTopInset)
		));
		playbackButton.useStyle(sizeOf(buttonSize, buttonSize));
		stepButton.useStyle(sizeOf(buttonSize, buttonSize));

		rateControl.setControlSize(rateHeight).setInputExtent(inputExtent);
		rateControl.useStyle(UIStyle.of(
				minWidth(0),
				minHeight(rateHeight),
				sizeOf(-1, rateHeight)
		));
		currentRecipeCard.useStyle(UIStyle.of(
				minWidth(0),
				minHeight(cardHeight),
				sizeOf(-1, cardHeight)
		));
		materialsPanel.useStyle(UIStyle.of(
				minWidth(0),
				minHeight(materialHeight),
				sizeOf(-1, materialHeight)
		));
	}

	static LayoutDensity resolveLayoutDensity(int width, int height) {
		if (width < 118 || height < 156) {
			return LayoutDensity.dense;
		}
		if (width < 150 || height < 202) {
			return LayoutDensity.compact;
		}
		return LayoutDensity.normal;
	}

	private static int topRowHeight(LayoutDensity density) {
		return switch (density) {
			case normal -> topRowHeight;
			case compact -> topRowHeightCompact;
			case dense -> topRowHeightDense;
		};
	}

	private static int controlButtonSize(LayoutDensity density) {
		return switch (density) {
			case normal -> controlButtonSize;
			case compact -> controlButtonSizeCompact;
			case dense -> controlButtonSizeDense;
		};
	}

	static int topRowContentTopInset() {
		return topRowContentTopInset;
	}

	static int controlButtonGap(LayoutDensity density) {
		return switch (density) {
			case normal -> controlButtonGap;
			case compact -> controlButtonGapCompact;
			case dense -> controlButtonGapDense;
		};
	}

	static int controlColumnWidth(LayoutDensity density) {
		return controlButtonSize(density) + switch (density) {
			case normal -> controlColumnRightInset;
			case compact -> controlColumnRightInsetCompact;
			case dense -> controlColumnRightInsetDense;
		};
	}

	private static int speedRowHeight(LayoutDensity density) {
		return switch (density) {
			case normal -> speedRowHeight;
			case compact -> speedRowHeightCompact;
			case dense -> speedRowHeightDense;
		};
	}

	private static int currentRecipeCardHeight(LayoutDensity density) {
		return switch (density) {
			case normal -> executionCardHeight;
			case compact -> executionCardHeightCompact;
			case dense -> executionCardHeightDense;
		};
	}

	static int materialPanelHeight(LayoutDensity density) {
		return switch (density) {
			case normal -> materialPanelHeight;
			case compact -> materialPanelHeightCompact;
			case dense -> materialPanelHeightDense;
		};
	}
	//endregion

	//region rate

	private long rateValue() {
		ExecutionDriverSnapshot snapshot = state.snapshot();
		if (snapshot == null) {
			return ExecutionOverlayClientConfig.overlayRate();
		}
		long configured = Math.max(ExecutionOverlayUi.rateMin, Math.min(ExecutionOverlayUi.rateMax, Math.round(snapshot.ratePerSecond())));
		return configured <= 0L ? ExecutionOverlayClientConfig.overlayRate() : configured;
	}

	private void syncRateControls() {
		long rate = rateValue();
		if (!rateControl.inputFocused()) {
			if (!rateControl.dragging() && rateControl.value() != rate) {
				rateControl.setValue(rate);
			}
			return;
		}
		if (!rateControl.dragging() && rateControl.value() != rate) {
			rateControl.setSliderValue(rate);
		}
	}

	private void updateDynamicTooltips() {
		Tooltip rateTooltip = rateTooltip();
		rateControl.setSliderTooltip(rateTooltip);
		rateControl.setInputTooltip(rateInputTooltip());
	}

	private void updateRateControl(long value) {
		setDriverRate(value);
	}

	private void setDriverRate(long value) {
		ExecutionOverlayClientConfig.setOverlayRate(value);
		ExecutionDriver driver = state.driver();
		if (driver != null) {
			driver.setRatePerSecond(value);
		}
	}

	private Tooltip rateTooltip() {
		Tooltip tooltip = Tooltip.create();
		tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Execution.Overlay.Summary.rate));
		tooltip.add(ExecutionOverlayUi.tooltipAccentText(ExecutionOverlayUi.compactNumber(rateValue())));
		tooltip.add(ExecutionOverlayUi.tooltipBodyText(
				ExecutionOverlayUi.rateMin + " - " + ExecutionOverlayUi.rateMax
		));
		tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Execution.Overlay.Rate.hintDrag));
		return tooltip;
	}

	private Tooltip rateInputTooltip() {
		Tooltip tooltip = rateTooltip();
		tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Execution.Overlay.Rate.hintType));
		if (rateControl.hasRangeWarning()) {
			tooltip.add(ExecutionOverlayUi.tooltipDangerText(
					ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Tooltip.range) + ": "
							+ ExecutionOverlayUi.rateMin + " - " + ExecutionOverlayUi.rateMax
			));
		}
		return tooltip;
	}
	//endregion

	//region controls

	private boolean autoAdvanceEnabled() {
		ExecutionDriverSnapshot snapshot = state.snapshot();
		return snapshot != null && snapshot.autoAdvanceEnabled();
	}

	private boolean sessionPaused() {
		ExecutionTelemetrySnapshot telemetry = state.telemetry();
		return telemetry != null && telemetry.paused();
	}

	private boolean sessionCompleted() {
		ExecutionTelemetrySnapshot telemetry = state.telemetry();
		return telemetry != null && telemetry.completed();
	}

	private boolean playbackRunning() {
		return autoAdvanceEnabled() && !sessionCompleted();
	}

	private void togglePlayback(@Nullable InputContext input) {
		state.flushTargetTimes();
		if (state.calculating()) {
			return;
		}
		ExecutionDriver driver = state.driver();
		if (driver == null) {
			return;
		}
		if (input != null && input.isShiftDown()) {
			driver.forceReopen();
			ExecutionOverlayUi.playClickSound();
			return;
		}
		if (sessionCompleted()) {
			return;
		}
		if (playbackRunning()) {
			if (autoAdvanceEnabled()) {
				driver.toggleAutoAdvance();
			}
			driver.pause();
		} else {
			if (sessionPaused()) {
				driver.resume();
			}
			if (!autoAdvanceEnabled()) {
				driver.toggleAutoAdvance();
			}
			driver.advanceSteps(1);
		}
		ExecutionOverlayUi.playClickSound();
	}

	private void advanceOneStep() {
		state.flushTargetTimes();
		if (state.calculating()) {
			return;
		}
		ExecutionDriver driver = state.driver();
		if (driver == null || sessionCompleted()) {
			return;
		}
		if (autoAdvanceEnabled()) {
			driver.toggleAutoAdvance();
		}
		if (sessionPaused()) {
			driver.resume();
		}
		driver.advanceSteps(1);
		ExecutionOverlayUi.playClickSound();
	}

	private Tooltip playbackTooltip() {
		Tooltip tooltip = Tooltip.create();
		tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Execution.Overlay.Control.playback));
		tooltip.add(ExecutionOverlayUi.tooltipBody(
				playbackRunning()
						? CirrusLangs.Execution.Overlay.Control.playbackPause
						: CirrusLangs.Execution.Overlay.Control.playbackStart
		));
		tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Execution.Overlay.Trace.forceReopen));
		return tooltip;
	}

	private Tooltip stepTooltip() {
		Tooltip tooltip = Tooltip.create();
		tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Execution.Overlay.Control.step));
		tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Execution.Overlay.Control.stepDesc));
		return tooltip;
	}
	//endregion

	//region target status

	Tooltip statusTooltip() {
		Tooltip tooltip = Tooltip.create();
		String target = state.targetLabel();
		if (target == null || target.isBlank()) {
			target = ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Target.none);
		}
		tooltip.add(ExecutionOverlayUi.tooltipTitleText(target));
		TooltipStack<?> targetStack = ExecutionOverlayContentHelper.tooltipStack(state.targetContent());
		if (targetStack != null) {
			tooltip.setStack(targetStack);
		}
		appendStatusDetails(tooltip);
		return tooltip;
	}

	void appendStatusDetails(Tooltip tooltip) {
		ExecutionTelemetrySnapshot telemetry = state.telemetry();
		RecipeActiveContext current = activeRecipeContext();
		RichTooltipComponent.Builder builder = RichTooltipComponent.builder();

		builder.section(ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.currentRecipe), ExecutionOverlayUi.accent);
		if (state.calculating()) {
			builder.text(ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.calculating), ExecutionOverlayUi.tooltipAccent, false);
		} else if (current != null) {
			builder.contentIconText(
					current.content(),
					ExecutionOverlayUi.accent,
					current.label(),
					ExecutionOverlayUi.tooltipTitleColor,
					ExecutionOverlayUi.tr(current.phase()),
					ExecutionOverlayUi.tooltipAccent
			);
			builder.kv(
					ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Tooltip.status),
					ExecutionOverlayUi.tooltipTextSecondary,
					telemetry == null ? ExecutionOverlayUi.localizedStatus(null) : ExecutionOverlayUi.localizedStatus(telemetry.status()),
					telemetry == null ? ExecutionOverlayUi.textSecondary : ExecutionOverlayUi.statusColor(telemetry.status())
			);
		} else if (telemetry != null && telemetry.current() != null) {
			builder.contentIconText(
					overlayNodeContent(currentOverlayNode()),
					ExecutionOverlayUi.accent,
					telemetry.current().label(),
					ExecutionOverlayUi.tooltipTitleColor,
					ExecutionOverlayUi.localizedStatus(telemetry.status()),
					ExecutionOverlayUi.statusColor(telemetry.status())
			);
		} else {
			builder.text(ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.currentRecipeNone), ExecutionOverlayUi.tooltipTextSecondary, false);
		}
		appendTargetProgress(builder);

		RecipeStatusAlert alert = telemetryAlert();
		if (alert != null) {
			builder.gap(2);
			builder.section(ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Summary.alert), alert.color());
			builder.text(alert.summary(), alert.color(), false);
			if (alert.detail() != null && !alert.detail().isBlank()) {
				builder.text(alert.detail(), ExecutionOverlayUi.tooltipTextSecondary, false);
			}
		}
		if (telemetry != null && telemetry.warnings().notEmpty()) {
			builder.gap(2);
			builder.section(
					ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Summary.alert),
					alert == null ? ExecutionOverlayUi.warning : alert.color()
			);
			String alertSummary = alert == null ? null : alert.summary();
			int shownWarnings = 0;
			int skippedWarnings = 0;
			for (String warning : telemetry.warnings()) {
				if (warning == null || warning.isBlank() || warning.equals(alertSummary)) {
					continue;
				}
				if (shownWarnings >= 2) {
					skippedWarnings++;
					continue;
				}
				builder.text(warning, ExecutionOverlayUi.tooltipTextSecondary, false);
				shownWarnings++;
			}
			if (skippedWarnings > 0) {
				builder.text(
						ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.more, skippedWarnings),
						ExecutionOverlayUi.tooltipTextSecondary,
						false
				);
			}
		}

		List<OverlayMaterialRequirement> missing = missingMaterialRequirements();
		if (!missing.isEmpty()) {
			builder.gap(2);
			builder.section(ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Ingredients.groupMissing), ExecutionOverlayUi.warning);
			int limit = Math.min(3, missing.size());
			for (int index = 0; index < limit; index++) {
				OverlayMaterialRequirement requirement = missing.get(index);
				builder.contentIconText(
						requirement.material(),
						ExecutionOverlayUi.warning,
						requirementLabel(requirement),
						ExecutionOverlayUi.tooltipTextPrimary,
						missingTooltipValue(requirement),
						ExecutionOverlayUi.tooltipWarning
				);
			}
			if (missing.size() > limit) {
				builder.text(
						ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.more, missing.size() - limit),
						ExecutionOverlayUi.tooltipTextSecondary,
						false
				);
			}
		}
		tooltip.add(builder.build());
	}

	@Nullable RecipeStatusAlert telemetryAlert() {
		ExecutionTelemetrySnapshot telemetry = state.telemetry();
		if (telemetry == null) {
			return null;
		}
		if (telemetry.errorSummary() != null && !telemetry.errorSummary().isBlank()) {
			return new RecipeStatusAlert(
					telemetry.errorSummary(),
					telemetry.errorDetail(),
					"failed".equalsIgnoreCase(telemetry.status()) ? ExecutionOverlayUi.danger : ExecutionOverlayUi.warning
			);
		}
		if (!telemetry.warnings().isEmpty()) {
			return new RecipeStatusAlert(telemetry.warnings().getFirst(), null, ExecutionOverlayUi.warning);
		}
		return null;
	}

	List<OverlayMaterialRequirement> missingMaterialRequirements() {
		ExecutionDriverSnapshot snapshot = state.snapshot();
		if (snapshot == null || snapshot.materials().isEmpty()) {
			return List.of();
		}
		ArrayList<OverlayMaterialRequirement> missing = new ArrayList<>();
		for (OverlayMaterialRequirement requirement : snapshot.materials()) {
			if (requirement.missing() > 0L) {
				missing.add(requirement);
			}
		}
		return missing;
	}

	String missingMaterialInlineSummary(Font font, int maxWidth, List<OverlayMaterialRequirement> missing) {
		if (missing.isEmpty()) {
			return "-";
		}
		String summary = ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Ingredients.groupMissing) + " "
				+ missing.size() + " "
				+ ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.unique);
		if (font.width(summary) <= maxWidth) {
			return summary;
		}
		return ExecutionOverlayUi.compactNumber(missing.size()) + " "
				+ ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Ingredients.groupMissing);
	}

	List<RecipeContent> targetOutputContents() {
		RecipeActiveContext context = state.activeRecipeContext();
		if (context != null && !context.outputContents().isEmpty()) {
			return context.outputContents();
		}
		RecipeContent target = state.targetContent();
		return target.isEmpty() ? List.of() : List.of(target);
	}

	private String missingTooltipValue(OverlayMaterialRequirement requirement) {
		return ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.miss) + " "
				+ ExecutionOverlayUi.compactNumber(requirement.missing())
				+ " | " + ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.have) + " "
				+ ExecutionOverlayUi.compactNumber(requirement.available())
				+ " | " + ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.need) + " "
				+ ExecutionOverlayUi.compactNumber(requirement.required());
	}

	private void appendTargetProgress(RichTooltipComponent.Builder builder) {
		OverlayTargetProgress progress = state.targetProgress();
		if (progress == null || progress.required() <= 0) {
			return;
		}
		long missing = Math.max(0L, progress.required() - progress.current());
		builder.kv(
				ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.have),
				ExecutionOverlayUi.tooltipTextSecondary,
				ExecutionOverlayUi.compactNumber(progress.current()),
				progress.current() >= progress.required() ? ExecutionOverlayUi.tooltipSuccess : ExecutionOverlayUi.tooltipAccent
		);
		builder.kv(
				ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.need),
				ExecutionOverlayUi.tooltipTextSecondary,
				ExecutionOverlayUi.compactNumber(progress.required()),
				ExecutionOverlayUi.tooltipTextPrimary
		);
		if (missing > 0L) {
			builder.kv(
					ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.miss),
					ExecutionOverlayUi.tooltipTextSecondary,
					ExecutionOverlayUi.compactNumber(missing),
					ExecutionOverlayUi.tooltipWarning
			);
		}
	}

	//endregion

	//region active recipe

	@Nullable RecipeActiveContext activeRecipeContext() {
		return state.activeRecipeContext();
	}

	Tooltip currentRecipeTooltip() {
		Tooltip tooltip = Tooltip.create();
		RecipeActiveContext context = activeRecipeContext();
		if (context == null) {
			tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Execution.Overlay.currentRecipe));
			tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Execution.Overlay.currentRecipeNone));
			return tooltip;
		}
		TooltipStack<?> stack = ExecutionOverlayContentHelper.tooltipStack(context.content());
		if (stack != null) {
			tooltip.setStack(stack);
		}
		tooltip.add(ExecutionOverlayUi.tooltipTitleText(context.label()));
		var preview = ExecutionRecipeTooltipPreview.create(
				context.recipe(),
				context.displayInputGrid(),
				context.displayOutputs()
		);
		if (preview != null) {
			tooltip.add(preview);
		}
		return tooltip;
	}
	//endregion

	//region trace

	Tooltip traceTooltip() {
		Tooltip tooltip = Tooltip.create();
		tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Execution.Overlay.Trace.title));
		tooltip.add(ExecutionOverlayUi.tooltipAccentText(
				ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Tooltip.status)
						+ ": "
						+ ExecutionOverlayUi.tr(
						state.traceEnabled()
								? CirrusLangs.Execution.Overlay.Trace.stateEnabled
								: CirrusLangs.Execution.Overlay.Trace.stateDisabled
				)
		));
		tooltip.add(ExecutionOverlayUi.tooltipBody(
				state.traceEnabled()
						? CirrusLangs.Execution.Overlay.Trace.disable
						: CirrusLangs.Execution.Overlay.Trace.enable
		));
		if (state.trace() != null && state.trace().available()) {
			tooltip.add(ExecutionOverlayUi.tooltipAccentText(
					state.trace().recipeLabel() == null || state.trace().recipeLabel().isBlank()
							? ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Tooltip.executing)
							: state.trace().recipeLabel()
			));
			tooltip.add(ExecutionOverlayUi.tooltipBodyText(
					ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Trace.slotCount, state.trace().highlights().size(), state.trace().moves().size())
			));
		}
		return tooltip;
	}
	//endregion

	//region helpers

	String materialLabel(ProcessNode.MaterialSlot slot) {
		if (slot.content() != null && slot.content().notEmpty()) {
			String label = ExecutionOverlayContentHelper.label(slot.content());
			if (!label.isBlank() && !"-".equals(label)) {
				return label;
			}
		}
		if (slot.materialName() != null && !slot.materialName().isBlank()) {
			return slot.materialName();
		}
		return "-";
	}

	private String requirementLabel(OverlayMaterialRequirement requirement) {
		String label = ExecutionOverlayContentHelper.label(requirement.material());
		return label == null || label.isBlank() ? "-" : label;
	}

	private @Nullable ExecutionOverlayNode currentOverlayNode() {
		ExecutionDriverSnapshot snapshot = state.snapshot();
		if (snapshot == null || snapshot.viewModel() == null || snapshot.viewModel().focusVisibleIndex() < 0) {
			return null;
		}
		int index = snapshot.viewModel().focusVisibleIndex();
		if (index < 0 || index >= snapshot.viewModel().visibleNodes().size()) {
			return null;
		}
		return snapshot.viewModel().visibleNodes().get(index);
	}

	private RecipeContent overlayNodeContent(@Nullable ExecutionOverlayNode node) {
		return node == null || node.content() == null || node.content().isEmpty()
				? RecipeContent.empty()
				: node.content().copy();
	}
	//endregion

	enum LayoutDensity {
		normal,
		compact,
		dense
	}
}
