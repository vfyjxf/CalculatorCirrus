package dev.vfyjxf.cirrus.ui.execution.widget;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayUi;
import dev.vfyjxf.cirrus.ui.widget.content.StackWidget;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayBlock;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayFlex;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayNone;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.inset;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.justifyCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionRelative;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public class ExecutionSlotWidget extends CompositeWidget<Widget> {
	//region constants

	public static final int slotSize = ExecutionOverlayUi.slotSize;
	private static final int contentInset = 1;
	private static final int contentSize = slotSize - contentInset * 2;

	//endregion

	//region state

	private final ExecutionWidgetGroup recipeContentLayer;
	private final RecipeContentWidget recipeContentWidget;
	private final ExecutionWidgetGroup stackLayer;
	private final StackWidget<?, ?> stackWidget;
	private final ExecutionWidgetGroup fallbackLayer;
	private final LabelWidget fallbackLabel;

	private @Nullable Supplier<@Nullable Tooltip> tooltipProvider;

	//endregion

	@SuppressWarnings({"rawtypes", "unchecked"})
	public ExecutionSlotWidget() {
		useStyle(UIStyle.of(
				positionRelative(),
				background(Background.Slot.dark),
				sizeOf(slotSize, slotSize),
				minWidth(slotSize),
				minHeight(slotSize),
				boxSizingBorderBox()
		));

		recipeContentLayer = addWidget(new ExecutionWidgetGroup());
		recipeContentLayer.useStyle(UIStyle.of(
				positionAbsolute(),
				inset(contentInset),
				sizeOf(contentSize, contentSize),
				minWidth(contentSize),
				minHeight(contentSize),
				displayNone()
		));
		recipeContentLayer.setInteractive(false);
		recipeContentLayer.setActive(false);

		recipeContentWidget = recipeContentLayer.append(new RecipeContentWidget());
		recipeContentWidget.useStyle(UIStyle.of(
				sizeOf(contentSize, contentSize),
				minWidth(contentSize),
				minHeight(contentSize)
		));
		recipeContentWidget.setInteractive(false);
		recipeContentWidget.setActive(false);

		stackLayer = addWidget(new ExecutionWidgetGroup());
		stackLayer.useStyle(UIStyle.of(
				positionAbsolute(),
				inset(contentInset),
				sizeOf(contentSize, contentSize),
				minWidth(contentSize),
				minHeight(contentSize),
				displayNone()
		));
		stackLayer.setInteractive(false);
		stackLayer.setActive(false);

		stackWidget = stackLayer.append(new StackWidget(ContentStack.empty()));
		stackWidget.useStyle(UIStyle.of(
				sizeOf(contentSize, contentSize),
				minWidth(contentSize),
				minHeight(contentSize)
		));
		stackWidget.setInteractive(false);
		stackWidget.setActive(false);

		fallbackLayer = addWidget(new ExecutionWidgetGroup());
		fallbackLayer.useStyle(UIStyle.of(
				positionAbsolute(),
				inset(0),
				sizeOf(-1, -1),
				minWidth(0),
				minHeight(0),
				flexColumn(),
				justifyCenter(),
				alignItemsCenter(),
				displayNone()
		));
		fallbackLayer.setInteractive(false);
		fallbackLayer.setActive(false);

		fallbackLabel = fallbackLayer.append(LabelWidget.of(""));
		fallbackLabel.setShadow(false);
		fallbackLabel.setInteractive(false);
		fallbackLabel.setActive(false);

		clearVisual();
		onHoverTooltip((mouseX, mouseY) -> tooltipProvider == null ? null : tooltipProvider.get());
	}

	//region content

	public void clearVisual() {
		recipeContentWidget.setContent(RecipeContent.empty(), 0);
		setStackInternal(ContentStack.empty());
		setFallbackToken("", ExecutionOverlayUi.textSecondary);
	}

	public void setTooltipProvider(@Nullable Supplier<@Nullable Tooltip> provider) {
		this.tooltipProvider = provider;
	}

	public void setRecipeContent(@Nullable RecipeContent content, int fallbackColor) {
		RecipeContent resolved = content == null ? RecipeContent.empty() : content;
		setRecipeContent(
			resolved,
			ExecutionOverlayContentHelper.currentVisualIndex(resolved),
			ExecutionOverlayContentHelper.fallbackToken(resolved),
			fallbackColor
		);
	}

	public void setRecipeContent(@Nullable RecipeContent content, int visualIndex, @Nullable String fallbackToken, int fallbackColor) {
		RecipeContent resolved = content == null ? RecipeContent.empty() : content;
		recipeContentWidget.setContent(resolved, visualIndex);
		boolean visible = ExecutionOverlayContentHelper.hasRenderableVisual(resolved);
		recipeContentLayer.setVisible(visible);
		recipeContentLayer.useStyle(visible ? displayBlock() : displayNone());
		if (visible) {
			stackLayer.setVisible(false);
			stackLayer.useStyle(displayNone());
			fallbackLayer.setVisible(false);
			fallbackLayer.useStyle(displayNone());
			setStackInternal(ContentStack.empty());
			return;
		}
		setFallbackToken(fallbackToken, fallbackColor);
	}

	public void setStack(@Nullable ContentStack<?, ?> stack) {
		ContentStack<?, ?> renderable = stack == null ? ContentStack.empty() : stack;
		setStackInternal(renderable);
		boolean visible = !renderable.isEmpty();
		stackLayer.setVisible(visible);
		stackLayer.useStyle(visible ? displayBlock() : displayNone());
		if (visible) {
			recipeContentLayer.setVisible(false);
			recipeContentLayer.useStyle(displayNone());
			recipeContentWidget.setContent(RecipeContent.empty(), 0);
			fallbackLayer.setVisible(false);
			fallbackLayer.useStyle(displayNone());
		}
	}

	public void setFallbackToken(@Nullable String token, int color) {
		String rendered = token == null ? "" : token;
		fallbackLabel.setText(rendered);
		fallbackLabel.setColor(color);

		boolean visible = !rendered.isBlank();
		fallbackLayer.setVisible(visible);
		fallbackLayer.useStyle(visible ? displayFlex() : displayNone());
		if (visible) {
			recipeContentLayer.setVisible(false);
			recipeContentLayer.useStyle(displayNone());
			recipeContentWidget.setContent(RecipeContent.empty(), 0);
			stackLayer.setVisible(false);
			stackLayer.useStyle(displayNone());
			setStackInternal(ContentStack.empty());
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void setStackInternal(ContentStack<?, ?> stack) {
		((StackWidget) stackWidget).setStack(stack);
	}

	//endregion
}
