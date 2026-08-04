package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Icon;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusButtonWidget;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipStack;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import dev.vfyjxf.cloudlib.ui.widget.SpacerWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textPrimary;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingHorizontal;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingVertical;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.widthOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.zIndexOf;

/**
 * Top control bar for the Calculator page.
 * Styled with texture-backed chrome matching Cirrus shell controls.
 */
public class ControlBarWidget extends CompositeWidget<Widget> {

	public record ControlBarMetrics(
			int height,
			int targetSlotSize,
			int amountInputWidth,
			int settingsButtonSize,
			int calculateButtonWidth
	) {}

	//region Metrics
	private static final int barHeight = 30;
	private static final int targetSlotSize = 18;
	private static final int amountInputWidth = 56;
	private static final int calcButtonWidth = 72;
	private static final int calcButtonHeight = 16;
	private static final int settingsButtonSize = 16;
	//endregion

	//region State
	private final OmniCalculatorView parentView;
	private final TargetSlotWidget targetSlot;
	private final NumberInputWidget amountInput;
	private final CirrusButtonWidget settingsButton;
	private final CirrusButtonWidget calculateButton;
	//endregion

	//region Init
	public ControlBarWidget(OmniCalculatorView parentView) {
		this.parentView = parentView;

		useStyle(
				flexRow(),
				alignItemsCenter(),
				gap(2),
				heightOf(barHeight),
				paddingHorizontal(4),
				paddingVertical(4),
				zIndexOf(20)
		);

		onRender((canvas, mx, my, pt, self, ctx) -> renderBarBackground(canvas));

		targetSlot = new TargetSlotWidget();
		addWidget(targetSlot);

		var xLabel = LabelWidget.of(Component.literal("\u00d7"))
								.setColor(textPrimary)
								.setShadow(false);
		xLabel.useStyle(widthOf(8));
		addWidget(xLabel);

		amountInput = NumberInputWidget.create(1)
									   .setMinValue(1)
									   .setScrollStep(1)
									   .enableCalculatorPopup()
									   .tooltipWithCalculatorPopup(this::amountTooltip);
		amountInput.useStyle(widthOf(amountInputWidth), heightOf(14));
		addWidget(amountInput);

		addWidget(SpacerWidget.create());
		settingsButton = createSettingsButton();
		addWidget(settingsButton);
		calculateButton = createCalculateButton();
		addWidget(calculateButton);
	}
	//endregion

	//region Public API
	static ControlBarMetrics debugMetrics() {
		return new ControlBarMetrics(barHeight, targetSlotSize, amountInputWidth, settingsButtonSize, calcButtonWidth);
	}

	static boolean debugUsesLegacyStripTexture() {
		return false;
	}

	static boolean debugUsesStandardButtonTexture() {
		return true;
	}

	public void setTargetItem(ItemStack stack) {
		targetSlot.setTargetItem(stack);
	}

	public void setAmount(long amount) {
		amountInput.setValue(amount);
	}

	public long getAmount() {
		return amountInput.getValue();
	}

	boolean debugAmountUsesCalculatorPopup() {
		return amountInput.debugCalculatorPopupEnabled();
	}

	Tooltip debugTargetTooltip() {
		return targetSlot.targetTooltip();
	}

	Tooltip debugAmountTooltip() {
		return amountInput.hoverTooltip(0, 0);
	}

	Tooltip debugSettingsTooltip() {
		return settingsButton.hoverTooltip(0, 0);
	}

	Tooltip debugCalculateTooltip() {
		return calculateButton.hoverTooltip(0, 0);
	}
	//endregion

	//region Rendering
	private void renderBarBackground(SceneCanvas canvas) {
		int w = width(), h = height();
		canvas.texture(Background.Outlined.flat, 0, 0, w, h);
		canvas.texture(Background.flat, 2, 2, w - 4, h - 4);
	}

	private CirrusButtonWidget createCalculateButton() {
		var calculateButton = new CirrusButtonWidget(calcButtonWidth, calcButtonHeight)
				.icon(Icon.Calc.calculate)
				.label(CirrusLangs.Calc.calculate)
				.onPress(parentView::onCalculate)
				.tooltip(this::calculateTooltip)
				.clickSound(true);
		calculateButton.useStyle(sizeOf(calcButtonWidth, calcButtonHeight));
		return calculateButton;
	}

	private CirrusButtonWidget createSettingsButton() {
		var settingsButton = new CirrusButtonWidget(settingsButtonSize, settingsButtonSize)
				.iconSupplier(() -> parentView.isSettingsPanelOpen() ? Icon.settingSelected : Icon.setting)
				.selected(parentView::isSettingsPanelOpen)
				.onPress(parentView::toggleSettingsPanel)
				.tooltip(() -> {
					var tooltip = Tooltip.create();
					tooltip.add(CirrusLangs.Calc.actionSettings.component(), Tooltip.title);
					tooltip.add(CirrusLangs.Calc.actionSettingsDesc.component());
					return tooltip;
				})
				.clickSound(true);
		settingsButton.useStyle(sizeOf(settingsButtonSize, settingsButtonSize));
		return settingsButton;
	}

	private Tooltip amountTooltip() {
		Tooltip tooltip = Tooltip.create();
		tooltip.add(CirrusLangs.Calc.amount.component(), Tooltip.title);
		tooltip.add(CirrusLangs.Calculator.Settings.currentValue.component(amountInput.getValue()));
		tooltip.add(CirrusLangs.Calculator.Settings.range.component(amountInput.minValue(), amountInput.maxValue()));
		tooltip.add(CirrusLangs.Calc.amountDesc.component());
		return tooltip;
	}

	private Tooltip calculateTooltip() {
		Tooltip tooltip = Tooltip.create();
		tooltip.add(CirrusLangs.Calc.calculate.component(), Tooltip.title);
		tooltip.add(CirrusLangs.Calc.calculateDesc.component());
		return tooltip;
	}
	//endregion

	//region TargetSlot
	private static final class TargetSlotWidget extends Widget {

		private ItemStack targetItem = ItemStack.EMPTY;

		private TargetSlotWidget() {
			useStyle(sizeOf(targetSlotSize, targetSlotSize));
			onHoverTooltip((mouseX, mouseY) -> targetTooltip());
		}

		@Override
		protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
			canvas.texture(Background.Slot.dark, 0, 0, width(), height());
			if (!targetItem.isEmpty()) {
				canvas.renderItem(targetItem, 1, 1);
			}
		}

		private void setTargetItem(ItemStack stack) {
			targetItem = stack == null ? ItemStack.EMPTY : stack.copy();
		}

		private Tooltip targetTooltip() {
			Tooltip tooltip = Tooltip.create();
			if (targetItem.isEmpty()) {
				tooltip.add(CirrusLangs.Calc.targetSlot.component(), Tooltip.title);
				tooltip.add(CirrusLangs.Calc.targetSlotEmpty.component());
				return tooltip;
			}
			ItemStack stack = targetItem.copy();
			tooltip.add(stack.getHoverName(), Tooltip.title);
			tooltip.add(CirrusLangs.Calc.targetSlotDesc.component());
			tooltip.setStack(TooltipStack.of(stack));
			return tooltip;
		}
	}
	//endregion
}
