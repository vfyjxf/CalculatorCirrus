package dev.vfyjxf.cirrus.ui.execution.page;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayUi;
import dev.vfyjxf.cirrus.ui.execution.widget.ExecutionSlotViewportWidget;
import dev.vfyjxf.cirrus.ui.execution.widget.ExecutionSlotWidget;
import dev.vfyjxf.cirrus.ui.execution.widget.ExecutionWidgetGroup;
import dev.vfyjxf.cirrus.ui.execution.widget.ExecutionWrappedTextWidget;
import dev.vfyjxf.cirrus.ui.execution.widget.JeiRecipePreviewWidget;
import dev.vfyjxf.cirrus.ui.process.ProcessNode;
import dev.vfyjxf.cirrus.ui.widget.toggle.sw.Rock;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.JustifyContent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItems;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.columnGap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayBlock;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayFlex;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayNone;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.icon;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetRight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetTop;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.justifyContent;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionRelative;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

final class RecipeCurrentRecipeCardWidget extends CompositeWidget<Widget> {
	//region constants

	private static final int contentTopPadding = 18;
	private static final int contentTopPaddingCompact = 17;
	private static final int contentTopPaddingDense = 16;
	private static final int contentPadding = 5;
	private static final int contentPaddingCompact = 4;
	private static final int contentPaddingDense = 3;
	private static final int innerPanelPadding = 5;
	private static final int innerPanelPaddingCompact = 4;
	private static final int innerPanelPaddingDense = 3;
	private static final int traceTopInset = 3;
	private static final int traceWidth = 20;
	private static final int traceHeight = 13;
	private static final int visibleRows = 3;
	private static final int inputColumns = 3;
	private static final int viewportGap = 5;
	private static final int viewportGapCompact = 4;
	private static final int viewportGapDense = 3;
	private static final int emptyTextPadding = 8;
	private static final int emptyTextPaddingCompact = 6;
	private static final int emptyTextPaddingDense = 4;
	private static final int emptyTextLineGap = 2;
	private static final int traceRightInset = 8;
	private static final int traceRightInsetCompact = 6;
	private static final int traceRightInsetDense = 5;

	//endregion

	//region state

	private final RecipePage owner;
	private final CurrentRecipeBodyWidget body;
	private final CurrentRecipeEmptyStateWidget emptyState;
	private final Rock traceToggle;

	private @Nullable RecipeActiveContext boundContext;
	private RecipePage.LayoutDensity layoutDensity = RecipePage.LayoutDensity.normal;

	//endregion

	RecipeCurrentRecipeCardWidget(RecipePage owner) {
		this.owner = owner;
		useStyle(UIStyle.of(
				flexColumn(),
				background(Background.Outlined.flat),
				padding(contentTopPadding, contentPadding, contentPadding, contentPadding),
				positionRelative(),
				minWidth(0),
				minHeight(0),
				boxSizingBorderBox()
		));
		setTickable(true);

		body = addWidget(new CurrentRecipeBodyWidget());
		body.useStyle(UIStyle.of(
				flexGrow(1),
				minWidth(0),
				minHeight(0),
				displayNone()
		));
		body.setVisible(false);

		emptyState = addWidget(new CurrentRecipeEmptyStateWidget());
		emptyState.useStyle(UIStyle.of(
				flexGrow(1),
				minWidth(0),
				minHeight(0),
				displayBlock()
		));

		traceToggle = addWidget(new Rock(
				owner.viewState()::traceEnabled,
				owner.viewState()::setTraceEnabled,
				owner::traceTooltip
		));
		traceToggle.useStyle(UIStyle.of(
				positionAbsolute(),
				insetRight(traceRightInset),
				insetTop(traceTopInset),
				sizeOf(traceWidth, traceHeight)
		));

		onHoverTooltip((mouseX, mouseY) -> owner.currentRecipeTooltip());
		syncContext(owner.activeRecipeContext());
		syncResponsiveLayout();
	}

	//region lifecycle

	@Override
	public void tick() {
		super.tick();
		syncResponsiveLayout();
		RecipeActiveContext current = owner.viewState().calculating() ? null : owner.activeRecipeContext();
		if (!sameContext(boundContext, current)) {
			syncContext(current);
		} else if (current != null) {
			body.syncResponsiveLayout(current);
		}
	}

	private void syncContext(@Nullable RecipeActiveContext context) {
		boundContext = context;
		boolean hasContext = context != null;

		body.setVisible(hasContext);
		body.useStyle(hasContext ? displayFlex() : displayNone());

		emptyState.setVisible(!hasContext);
		emptyState.useStyle(!hasContext ? displayBlock() : displayNone());

		if (context != null) {
			body.bindContext(context);
		}
	}

	void setLayoutDensity(RecipePage.LayoutDensity density) {
		layoutDensity = density == null ? RecipePage.LayoutDensity.normal : density;
		syncResponsiveLayout();
		if (boundContext != null) {
			body.bindContext(boundContext);
		}
	}

	private void syncResponsiveLayout() {
		int topPadding = switch (layoutDensity) {
			case normal -> contentTopPadding;
			case compact -> contentTopPaddingCompact;
			case dense -> contentTopPaddingDense;
		};
		int sidePadding = switch (layoutDensity) {
			case normal -> contentPadding;
			case compact -> contentPaddingCompact;
			case dense -> contentPaddingDense;
		};
		int toggleRightInset = traceRightInset(layoutDensity);

		useStyle(UIStyle.of(
				flexColumn(),
				background(Background.Outlined.flat),
				padding(topPadding, sidePadding, sidePadding, sidePadding),
				positionRelative(),
				minWidth(0),
				minHeight(0),
				boxSizingBorderBox()
		));
		traceToggle.useStyle(UIStyle.of(
				positionAbsolute(),
				insetRight(toggleRightInset),
				insetTop(traceTopInset),
				sizeOf(traceWidth, traceHeight)
		));
		emptyState.syncResponsiveLayout();
		body.syncResponsiveLayout(boundContext);
	}

	static int traceRightInset(RecipePage.LayoutDensity density) {
		return switch (density == null ? RecipePage.LayoutDensity.normal : density) {
			case normal -> traceRightInset;
			case compact -> traceRightInsetCompact;
			case dense -> traceRightInsetDense;
		};
	}

	static int traceToggleWidth() {
		return traceWidth;
	}

	static int traceToggleHeight() {
		return traceHeight;
	}

	//endregion

	//region helpers

	private boolean sameContext(@Nullable RecipeActiveContext left, @Nullable RecipeActiveContext right) {
		if (left == right) {
			return true;
		}
		if (left == null || right == null) {
			return false;
		}
		return left.recipe().equals(right.recipe())
				&& left.current() == right.current()
				&& left.phase().equals(right.phase())
				&& left.inputs().equals(right.inputs())
				&& left.outputs().equals(right.outputs());
	}

	private Tooltip materialSlotTooltip(ProcessNode.MaterialSlot slot) {
		RecipeContent content = slot.content();
		Tooltip tooltip = ExecutionOverlayContentHelper.tooltip(content);
		if (tooltip == null) {
			tooltip = Tooltip.create();
			tooltip.add(ExecutionOverlayUi.tooltipTitleText(owner.materialLabel(slot)));
		}

		tooltip.add(ExecutionOverlayUi.tooltipAccentText(
				slot.amount() > 0 ? ExecutionOverlayUi.compactNumber(slot.amount()) : "-"
		));

		if (slot.isCatalyst()) {
			tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Summary.catalysts));
		} else if (slot.chance() > 0.0d && slot.chance() < 1.0d) {
			tooltip.add(ExecutionOverlayUi.tooltipBodyText(Math.round(slot.chance() * 100.0d) + "%"));
		}
		return tooltip;
	}

	//endregion

	//region body

	private final class CurrentRecipeBodyWidget extends CompositeWidget<Widget> {

		private final ExecutionWidgetGroup row;
		private final ExecutionSlotViewportWidget inputViewport;
		private final CurrentRecipeArrowWidget arrowWidget;
		private final ExecutionSlotViewportWidget outputViewport;
		private @Nullable JeiRecipePreviewWidget jeiPreview;
		private boolean usingJeiPreview;

		private CurrentRecipeBodyWidget() {
			useStyle(UIStyle.of(
					flexColumn(),
					justifyContent(JustifyContent.CENTER),
					alignItems(AlignItems.CENTER),
					background(Background.Border.light),
					padding(innerPanelPadding),
					minWidth(0),
					minHeight(0),
					boxSizingBorderBox()
			));

			row = addWidget(new ExecutionWidgetGroup());
			row.useStyle(UIStyle.of(
					flexRow(),
					justifyContent(JustifyContent.FLEX_START),
					alignItems(AlignItems.CENTER),
					columnGap(viewportGap),
					flexGrow(1),
					minWidth(0),
					minHeight(0)
			));

			inputViewport = row.append(new ExecutionSlotViewportWidget(inputColumns, visibleRows));
			arrowWidget = row.append(new CurrentRecipeArrowWidget());
			outputViewport = row.append(new ExecutionSlotViewportWidget(1, 1));
		}

		private void bindContext(RecipeActiveContext context) {
			syncResponsiveLayout(context);
			JeiRecipePreviewWidget preview = ensureJeiPreview(context.recipe());
			if (preview != null) {
				inputViewport.setSlots(List.of());
				outputViewport.setSlots(List.of());
				arrowWidget.setVisible(false);
				arrowWidget.useStyle(displayNone());
				return;
			}
			clearJeiPreview();
			List<ProcessNode.MaterialSlot> inputs = context.displayInputGrid();
			List<ProcessNode.MaterialSlot> outputs = context.displayOutputs();
			ViewportShape inputShape = inputShape(layoutDensity, inputs.size());
			ViewportShape outputShape = outputShape(layoutDensity, outputs.size());

			inputViewport.setSlots(slotWidgets(inputs, inputShape.columns(), inputShape.visibleRows()));
			outputViewport.setSlots(slotWidgets(outputs, outputShape.columns(), outputShape.visibleRows()));

			boolean showArrow = !inputs.isEmpty() && !outputs.isEmpty();
			arrowWidget.setVisible(showArrow);
			arrowWidget.useStyle(showArrow ? displayBlock() : displayNone());
		}

		private @Nullable JeiRecipePreviewWidget ensureJeiPreview(RecipeEntry<?> recipe) {
			JeiRecipePreviewWidget active = jeiPreview;
			if (active != null && active.matches(recipe)) {
				return active;
			}
			clearJeiPreview();
			JeiRecipePreviewWidget preview = JeiRecipePreviewWidget.create(recipe);
			if (preview == null) {
				return null;
			}
			setJeiPreview(preview);
			return preview;
		}

		private void setJeiPreview(JeiRecipePreviewWidget preview) {
			clearJeiPreview();
			usingJeiPreview = true;
			jeiPreview = addWidget(preview);
			jeiPreview.useStyle(UIStyle.of(
					flexShrink(1),
					sizeOf(preview.previewWidth(), preview.previewHeight()),
					minWidth(0),
					minHeight(0)
			));
			row.setVisible(false);
			row.useStyle(displayNone());
			syncResponsiveLayout(boundContext);
		}

		private void clearJeiPreview() {
			usingJeiPreview = false;
			JeiRecipePreviewWidget active = jeiPreview;
			jeiPreview = null;
			if (active != null) {
				remove(active);
			}
			row.setVisible(true);
			row.useStyle(displayFlex());
			syncResponsiveLayout(boundContext);
		}

		private void syncResponsiveLayout(@Nullable RecipeActiveContext context) {
			int panelPadding = switch (layoutDensity) {
				case normal -> innerPanelPadding;
				case compact -> innerPanelPaddingCompact;
				case dense -> innerPanelPaddingDense;
			};
			int gap = switch (layoutDensity) {
				case normal -> viewportGap;
				case compact -> viewportGapCompact;
				case dense -> viewportGapDense;
			};
			useStyle(UIStyle.of(
					flexColumn(),
					justifyContent(JustifyContent.CENTER),
					alignItems(AlignItems.CENTER),
					background(usingJeiPreview ? VisualTexture.empty : Background.Border.light),
					padding(usingJeiPreview ? 0 : panelPadding),
					minWidth(0),
					minHeight(0),
					boxSizingBorderBox()
			));
			row.useStyle(UIStyle.of(
					flexRow(),
					justifyContent(JustifyContent.FLEX_START),
					alignItems(AlignItems.CENTER),
					columnGap(gap),
					flexGrow(1),
					minWidth(0),
					minHeight(0),
					usingJeiPreview ? displayNone() : displayFlex()
			));
			int inputCount = context == null ? 0 : context.displayInputGrid().size();
			int outputCount = context == null ? 0 : context.displayOutputs().size();
			ViewportShape inputShape = inputShape(layoutDensity, inputCount);
			ViewportShape outputShape = outputShape(layoutDensity, outputCount);
			inputViewport.setViewportShape(inputShape.columns(), inputShape.visibleRows());
			outputViewport.setViewportShape(outputShape.columns(), outputShape.visibleRows());
		}
	}

	//endregion

	//region empty state

	private final class CurrentRecipeEmptyStateWidget extends ExecutionWrappedTextWidget {

		private CurrentRecipeEmptyStateWidget() {
			super(CirrusLangs.Execution.Overlay.currentRecipeNone.component());
			useStyle(UIStyle.of(
					background(Background.Border.light),
					minWidth(0),
					minHeight(0)
			));
			setColor(ExecutionOverlayUi.textSecondary);
			setShadow(false);
			setPadding(emptyTextPadding);
			setLineGap(emptyTextLineGap);
		}

		private void syncResponsiveLayout() {
			setPadding(switch (layoutDensity) {
				case normal -> emptyTextPadding;
				case compact -> emptyTextPaddingCompact;
				case dense -> emptyTextPaddingDense;
			});
			setText(owner.viewState().calculating()
					? CirrusLangs.Execution.Overlay.calculating.component()
					: CirrusLangs.Execution.Overlay.currentRecipeNone.component());
		}
	}

	//endregion

	//region arrow

	private static final class CurrentRecipeArrowWidget extends Widget {

		private CurrentRecipeArrowWidget() {
			useStyle(UIStyle.of(
					icon(CirrusTextures.Icon.arrowRight),
					sizeOf(CirrusTextures.Icon.arrowRight.width(), CirrusTextures.Icon.arrowRight.height()),
					minWidth(CirrusTextures.Icon.arrowRight.width()),
					minHeight(CirrusTextures.Icon.arrowRight.height()),
					displayBlock()
			));
			setInteractive(false);
			setActive(false);
		}
	}

	//endregion

	private List<ExecutionSlotWidget> slotWidgets(List<ProcessNode.MaterialSlot> slots, int columns, int visibleRows) {
		ArrayList<ExecutionSlotWidget> widgets = new ArrayList<>();
		int safeColumns = Math.max(1, columns);
		int safeVisibleRows = Math.max(1, visibleRows);
		int totalRows = Math.max(safeVisibleRows, (int) Math.ceil(slots.size() / (double) safeColumns));
		int totalCells = Math.max(safeColumns * safeVisibleRows, totalRows * safeColumns);

		for (int index = 0; index < totalCells; index++) {
			ProcessNode.MaterialSlot slot = index < slots.size() ? slots.get(index) : null;
			widgets.add(slotWidget(slot));
		}
		return List.copyOf(widgets);
	}

	private ExecutionSlotWidget slotWidget(@Nullable ProcessNode.MaterialSlot slot) {
		ExecutionSlotWidget widget = new ExecutionSlotWidget();
		if (slot == null) {
			return widget;
		}

		int color = slot.isCatalyst() ? ExecutionOverlayUi.warning : ExecutionOverlayUi.textSecondary;
		widget.setRecipeContent(slot.content(), color);
		widget.setTooltipProvider(() -> materialSlotTooltip(slot));
		return widget;
	}

	static ViewportShape inputShape(RecipePage.LayoutDensity density, int inputCount) {
		int columns = density == RecipePage.LayoutDensity.dense ? 2 : inputColumns;
		int rows = inputCount <= 0 ? 1 : (int) Math.ceil(inputCount / (double) columns);
		int maxRows = density == RecipePage.LayoutDensity.normal ? visibleRows : 2;
		return new ViewportShape(columns, Math.max(1, Math.min(maxRows, rows)));
	}

	static ViewportShape outputShape(RecipePage.LayoutDensity density, int outputCount) {
		if (outputCount <= 1) {
			return new ViewportShape(1, 1);
		}
		int columns = density == RecipePage.LayoutDensity.dense ? 1 : 2;
		int rows = (int) Math.ceil(outputCount / (double) columns);
		int maxRows = density == RecipePage.LayoutDensity.normal ? 2 : 2;
		return new ViewportShape(columns, Math.max(1, Math.min(maxRows, rows)));
	}

	record ViewportShape(int columns, int visibleRows) {
	}
}
