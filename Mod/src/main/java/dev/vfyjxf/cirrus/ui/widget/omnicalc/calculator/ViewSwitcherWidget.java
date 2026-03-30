package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Icon;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Toggle;
import dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator.OmniCalculatorView.ProcessView;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.*;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;

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
    private static final int homeIconX = 3;
    private static final int homeIconY = 3;
    private static final int homeIconSize = 10;
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

        leftTab = addViewTab(ProcessView.TREE, TabSlot.LEFT, Component.literal("配方树"),
                Icon.Calc.Tree.tabSelected, Icon.Calc.Tree.tabUnselected, Icon.Calc.Tree.tabUnselected);
        middleTab = addViewTab(ProcessView.MATRIX, TabSlot.MIDDLE, Component.literal("矩阵"),
                Icon.Calc.Summary.tabSelected, Icon.Calc.Summary.tabUnselected, Icon.Calc.Summary.tabUnselected);
        rightTab = addViewTab(ProcessView.MILESTONES, TabSlot.RIGHT, Component.literal("里程碑"),
                Icon.Calc.Task.tabSelected, Icon.Calc.Task.tabUnselected, Icon.Calc.Task.tabUnselected);

        var spacer = new WidgetGroup<>();
        spacer.useStyle(flexGrow(1), minWidth(0));
        addWidget(spacer);

        materialButton = new ActionButton(ActionButtonKind.MATERIAL, parentView::toggleTreeMaterialPanel);
        addWidget(materialButton);

        homeButton = new ActionButton(ActionButtonKind.HOME, parentView::resetTreeViewFocus);
        addWidget(homeButton);

        setActiveView(parentView.currentProcessView());
        setTreeActionsVisible(parentView.currentProcessView() == ProcessView.TREE);
    }
    //endregion

    //region Public API
    private ViewTab addViewTab(ProcessView view, TabSlot slot, Component label,
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
    }

    int debugTreeActionCount() {
        return 2;
    }

    boolean debugTreeActionsVisible() {
        return materialButton.visible() && homeButton.visible();
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

    private enum TabSlot { LEFT, MIDDLE, RIGHT }
    private enum ActionButtonKind { MATERIAL, HOME }

    private class ViewTab extends Widget {

        final ProcessView view;
        final TabSlot slot;
        private final Component label;
        private final ImageTexture activeIcon;
        private final ImageTexture hoverIcon;
        private final ImageTexture idleIcon;
        private boolean active = false;

        ViewTab(ProcessView view, TabSlot slot, Component label,
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
                canvas.drawString(label.getString(), tabLabelX, tabLabelY + contentYOffset,
                        active ? textPrimary : (hovered() ? textPrimary : textSecondary));
            });

            onMouseClick((input, clickCount, context) -> {
                if (active || parentView.currentProcessView() == view) {
                    return EventDispatch.consumed;
                }
                parentView.switchProcessView(view);
                playClickSound();
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
    private final class ActionButton extends Widget {

        private final ActionButtonKind kind;
        private final Runnable action;

        private ActionButton(ActionButtonKind kind, Runnable action) {
            this.kind = kind;
            this.action = action;
            useStyle(sizeOf(actionButtonSize, actionButtonSize), marginLeft(2));
            setTooltip(actionTooltip(kind));

            onRender((canvas, mx, my, pt, self, ctx) -> {
                boolean active = switch (kind) {
                    case MATERIAL -> parentView.isTreeMaterialPanelVisible();
                    case HOME -> parentView.isTreeFocusedSubgraph();
                };
                if (kind == ActionButtonKind.MATERIAL) {
                    canvas.texture(active ? Icon.Calc.Summary.tabSelected : Icon.Calc.Summary.tabUnselected, 0, 0, width(), height());
                } else {
                    canvas.texture(active || hovered() ? Background.Outlined.inset : Background.flat,
                            0, 0, width(), height());
                    canvas.texture(active || hovered() ? Icon.arrowUpSelected : Icon.arrowUp,
                            homeIconX, homeIconY, homeIconSize, homeIconSize);
                }
            });

            onMouseClick((input, clickCount, context) -> {
                action.run();
                playClickSound();
                return EventDispatch.consumed;
            });
        }
    }
    //endregion

    //region Utils
    private static void playClickSound() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    private static Tooltip actionTooltip(ActionButtonKind kind) {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(Component.literal(kind == ActionButtonKind.MATERIAL ? "显示/隐藏材料清单" : "返回整棵配方树"));
        return tooltip;
    }
    //endregion
}
