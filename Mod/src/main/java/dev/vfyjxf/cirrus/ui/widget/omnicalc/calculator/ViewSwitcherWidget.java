package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.CirrusTextures.Icon;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Toggle;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusButtonWidget;
import dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator.OmniCalculatorView.ProcessView;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textPrimary;
import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textSecondary;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.marginLeft;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingHorizontal;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingVertical;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Texture-backed tab strip for calculator process views.
 */
public class ViewSwitcherWidget extends CompositeWidget<Widget> {

	public record ViewSwitcherMetrics(int height, int actionButtonSize) {}

	//region Metrics
	private static final int stripHeight = 18;
	private static final int actionButtonSize = 16;
	private static final int unselectedTabDrop = 2;
	private static final int minUnselectedTabHeight = 16;
	private static final int tabIconX = 7;
	private static final int tabIconY = 2;
	private static final int tabIconSize = 12;
	private static final int tabLabelX = 22;
	private static final int tabLabelY = 5;
	private static final int unselectedTabContentDrop = 1;
	private static final int leftTabWidth = 110;
	private static final int middleTabWidth = 96;
	private static final int rightTabWidth = 110;
	//endregion

	//region State
	private final OmniCalculatorView parentView;
	private final ViewTab leftTab;
	private final ViewTab middleTab;
	private final ViewTab rightTab;
    private final ActionButton materialButton;
    private final ActionButton homeButton;
    private final ActionButton expandButton;
    private ActionButton deepenButton;
    private boolean deepenAvailable;
    private final ActionButton executionPathButton;
	//endregion

	//region Init
	public ViewSwitcherWidget(OmniCalculatorView parentView) {
		this.parentView = parentView;

		useStyle(
				flexRow(),
				gap(0),
				heightOf(stripHeight),
				minHeight(stripHeight),
				paddingHorizontal(2),
				paddingVertical(0),
				alignItemsCenter()
		);
		onRender((canvas, mx, my, pt, self, ctx) -> renderTripleBackground(canvas, activeSlot()));

		leftTab = addViewTab(ProcessView.TREE, TabSlot.LEFT, CirrusLangs.Calc.View.tree,
				Icon.Calc.Tree.tabSelected, Icon.Calc.Tree.tabUnselected, Icon.Calc.Tree.tabUnselected);
		middleTab = addViewTab(ProcessView.MATRIX, TabSlot.MIDDLE, CirrusLangs.Calc.View.matrix,
				Icon.Calc.Summary.tabSelected, Icon.Calc.Summary.tabUnselected, Icon.Calc.Summary.tabUnselected);
		rightTab = addViewTab(ProcessView.MILESTONES, TabSlot.RIGHT, CirrusLangs.Calc.View.milestones,
				Icon.Calc.Task.tabSelected, Icon.Calc.Task.tabUnselected, Icon.Calc.Task.tabUnselected);

		var spacer = new WidgetGroup<>();
		spacer.useStyle(flexGrow(1), minWidth(0));
		addWidget(spacer);

		materialButton = new ActionButton(ActionButtonKind.MATERIAL, parentView::toggleTreeMaterialPanel);
		addWidget(materialButton);

		homeButton = new ActionButton(ActionButtonKind.HOME, parentView::resetTreeViewFocus);
		addWidget(homeButton);

		expandButton = new ActionButton(ActionButtonKind.EXPAND, parentView::expandTreeViewAll);
		addWidget(expandButton);

		deepenButton = new ActionButton(ActionButtonKind.DEEPEN, parentView::deepenCalculation);
		addWidget(deepenButton);
		deepenButton.setVisible(false);

		executionPathButton = new ActionButton(ActionButtonKind.EXECUTION_PATH, parentView::setCurrentPathAsExecutionTarget);
		addWidget(executionPathButton);

		setActiveView(parentView.currentProcessView());
		setTreeActionsVisible(parentView.currentProcessView() == ProcessView.TREE);
	}
	//endregion

	//region Public API
	private ViewTab addViewTab(
			ProcessView view, TabSlot slot, LangEntry label,
			ImageTexture activeIcon,
			ImageTexture hoverIcon,
			ImageTexture idleIcon) {
		var tab = new ViewTab(view, slot, label, activeIcon, hoverIcon, idleIcon);
		addWidget(tab);
		return tab;
	}

	public void setActiveView(ProcessView view) {
		leftTab.updateActiveState(view == ProcessView.TREE);
		middleTab.updateActiveState(view == ProcessView.MATRIX);
		rightTab.updateActiveState(view == ProcessView.MILESTONES);
	}

	public void setTreeActionsVisible(boolean visible) {
		materialButton.setVisible(visible);
		homeButton.setVisible(visible);
		expandButton.setVisible(visible);
		deepenButton.setVisible(visible && deepenAvailable);
		executionPathButton.setVisible(visible);
	}

	/** Shows the deepen action only while the current plan has continuable pending inputs. */
	public void setDeepenAvailable(boolean available) {
		deepenAvailable = available;
		deepenButton.setVisible(available);
	}

	int debugTreeActionCount() {
		return 4;
	}

	boolean debugTreeActionsVisible() {
		return materialButton.visible() && homeButton.visible() && expandButton.visible() && executionPathButton.visible();
	}

	static ViewSwitcherMetrics debugMetrics() {
		return new ViewSwitcherMetrics(stripHeight, actionButtonSize);
	}
	//endregion

	//region Render
	private TabSlot activeSlot() {
		return switch (parentView.currentProcessView()) {
			case TREE -> TabSlot.LEFT;
			case MATRIX -> TabSlot.MIDDLE;
			case MILESTONES -> TabSlot.RIGHT;
		};
	}

	private enum TabSlot {LEFT, MIDDLE, RIGHT}

	private enum ActionButtonKind {MATERIAL, HOME, EXPAND, DEEPEN, EXECUTION_PATH}

	private class ViewTab extends Widget {

		final ProcessView view;
		final TabSlot slot;
		private final LangEntry label;
		private final ImageTexture activeIcon;
		private final ImageTexture hoverIcon;
		private final ImageTexture idleIcon;
		private boolean active = false;

		ViewTab(
				ProcessView view, TabSlot slot, LangEntry label,
				ImageTexture activeIcon,
				ImageTexture hoverIcon,
				ImageTexture idleIcon) {
			this.view = view;
			this.slot = slot;
			this.label = label;
			this.activeIcon = activeIcon;
			this.hoverIcon = hoverIcon;
			this.idleIcon = idleIcon;

			useStyle(sizeOf(widthForSlot(slot), stripHeight), minHeight(stripHeight));

			onRender((canvas, mx, my, pt, self, ctx) -> {
				var icon = active ? activeIcon : (hovered() ? hoverIcon : idleIcon);
				int contentYOffset = active ? 0 : unselectedTabContentDrop;
				canvas.texture(icon, tabIconX, tabIconY + contentYOffset, tabIconSize, tabIconSize);
				canvas.drawString(label.string(), tabLabelX, tabLabelY + contentYOffset,
						active ? textPrimary : (hovered() ? textPrimary : textSecondary));
			});

			onMouseClick((input, clickCount, context) -> {
				if (active || parentView.currentProcessView() == view) {
					return EventDispatch.consumed;
				}
				parentView.switchProcessView(view);
				CirrusUiSound.playClickSound();
				return EventDispatch.consumed;
			});
		}

		void updateActiveState(boolean active) {
			this.active = active;
		}
	}

	private int widthForSlot(TabSlot slot) {
		return switch (slot) {
			case LEFT -> leftTabWidth;
			case MIDDLE -> middleTabWidth;
			case RIGHT -> rightTabWidth;
		};
	}

	/**
	 * Draw triple-tab background as one stitched state machine.
	 * left/middle/right refer to "which tab is selected now", not per-tab independent state.
	 */
	private void renderTripleBackground(SceneCanvas canvas, TabSlot activeSlot) {
		int leftX = leftTab.posX();
		int middleX = middleTab.posX();
		int rightX = rightTab.posX();
		int tabY = leftTab.posY();
		int leftW = leftTab.width();
		int middleW = middleTab.width();
		int rightW = rightTab.width();
		int tabH = leftTab.height();
		int baselineY = tabY + tabH;
		int selectedTabH = tabH;
		int selectedTabY = baselineY - selectedTabH;
		int unselectedTabH = Math.max(minUnselectedTabHeight, selectedTabH - unselectedTabDrop);
		int unselectedTabY = baselineY - unselectedTabH;

		switch (activeSlot) {
			case LEFT -> {
				// Remaining two tabs share the LEFT-unselected state set.
				canvas.texture(Toggle.Triple.leftUnselected, middleX, unselectedTabY, middleW + rightW, unselectedTabH);
				canvas.texture(Toggle.Triple.leftSelected, leftX, selectedTabY, leftW, selectedTabH);
			}
			case MIDDLE -> {
				canvas.texture(Toggle.Triple.middleLeft, leftX, unselectedTabY, leftW, unselectedTabH);
				canvas.texture(Toggle.Triple.middleRight, rightX, unselectedTabY, rightW, unselectedTabH);
				canvas.texture(Toggle.Triple.middleMiddle, middleX, selectedTabY, middleW, selectedTabH);
			}
			case RIGHT -> {
				// Remaining two tabs share the RIGHT-unselected state set.
				canvas.texture(Toggle.Triple.rightUnselected, leftX, unselectedTabY, leftW + middleW, unselectedTabH);
				canvas.texture(Toggle.Triple.rightSelected, rightX, selectedTabY, rightW, selectedTabH);
			}
		}
	}
	//endregion

	//region ActionButton
	private final class ActionButton extends CirrusButtonWidget {

		private final ActionButtonKind kind;

		private ActionButton(ActionButtonKind kind, Runnable action) {
			super(actionButtonSize, actionButtonSize);
			this.kind = kind;
			useStyle(sizeOf(actionButtonSize, actionButtonSize), marginLeft(2));
			iconSupplier(this::icon)
					.selected(this::buttonSelected)
					.onPress(action)
					.clickSound(true)
					.tooltip(() -> actionTooltip(kind));
		}

		private boolean buttonSelected() {
			return switch (kind) {
				case MATERIAL -> parentView.isTreeMaterialPanelVisible();
				case HOME -> parentView.isTreeFocusedSubgraph();
				case EXPAND -> parentView.isTreeForceExpanded();
				case DEEPEN -> false;
				case EXECUTION_PATH -> false;
			};
		}

		private ImageTexture icon() {
			return switch (kind) {
				case MATERIAL -> buttonSelected() ? Icon.Calc.Summary.tabSelected : Icon.Calc.Summary.tabUnselected;
				case HOME -> buttonSelected() || hovered() ? Icon.arrowUpSelected : Icon.arrowUp;
				case EXPAND -> hovered() ? Icon.arrowDownSelected : Icon.arrowDown;
				case DEEPEN -> hovered() ? Icon.Calc.Tree.tabSelected : Icon.Calc.Tree.tabUnselected;
				case EXECUTION_PATH -> hovered() ? Icon.startPressed : Icon.start;
			};
		}
	}
	//endregion

	private static Tooltip actionTooltip(ActionButtonKind kind) {
		Tooltip tooltip = Tooltip.create();
		tooltip.add(switch (kind) {
			case MATERIAL -> CirrusLangs.Calc.actionMaterials.component();
			case HOME -> CirrusLangs.Calc.actionHome.component();
			case EXPAND -> CirrusLangs.Calc.actionTreeExpand.component();
			case DEEPEN -> CirrusLangs.Calc.actionDeepen.component();
			case EXECUTION_PATH -> CirrusLangs.Calc.actionExecutionPath.component();
		});
		if (kind == ActionButtonKind.EXECUTION_PATH) {
			tooltip.add(CirrusLangs.Calc.actionExecutionPathDesc.component());
		}
		if (kind == ActionButtonKind.DEEPEN) {
			tooltip.add(CirrusLangs.Calc.actionDeepenDesc.component());
		}
		return tooltip;
	}
}
