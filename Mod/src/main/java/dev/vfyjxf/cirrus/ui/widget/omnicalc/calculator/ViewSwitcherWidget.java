package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors;
import dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator.OmniCalculatorView.ProcessView;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.*;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;

/**
 * Tab switcher for Calculator process views with rounded tab pills.
 */
public class ViewSwitcherWidget extends CompositeWidget<Widget> {

    private final OmniCalculatorView parentView;
    private final List<ViewTab> tabs = new ArrayList<>();

    public ViewSwitcherWidget(OmniCalculatorView parentView) {
        this.parentView = parentView;

        useStyle(
                flexRow(),
                gap(2),
                paddingHorizontal(4),
                paddingVertical(2)
        );

        // Render background band
        onRender((canvas, mx, my, pt, self, ctx) -> {
            canvas.fill(0, 0, width(), height(), BG_PANEL);
            canvas.fill(0, height() - 1, width(), 1, BORDER_MAIN);
        });

        addViewTab(ProcessView.TREE, "\u2726 Tree");
        addViewTab(ProcessView.MATRIX, "\u2630 Matrix");
        addViewTab(ProcessView.MILESTONES, "\u2611 Milestones");

        setActiveView(parentView.currentProcessView());
    }

    private void addViewTab(ProcessView view, String label) {
        var tab = new ViewTab(view, label);
        tabs.add(tab);
        addWidget(tab);
    }

    public void setActiveView(ProcessView view) {
        for (var tab : tabs) {
            tab.updateActiveState(tab.view == view);
        }
    }

    // ═══════════════════ ViewTab ═══════════════════

    private class ViewTab extends Widget {

        final ProcessView view;
        private boolean active = false;
        private final String text;

        ViewTab(ProcessView view, String text) {
            this.view = view;
            this.text = text;

            useStyle(
                    sizeOf(text.length() * 4 + 8, 12)
            );

            onRender((canvas, mx, my, pt, self, ctx) -> {
                int w = width(), h = height();
                if (active) {
                    // Active: accent pill with subtle shadow
                    canvas.shadow(0, 1, w, h, 3f, 1f, 3f, 0x302563EB);
                    canvas.roundedRect(0, 0, w, h, 3f, ACCENT, 0, 0);
                    canvas.drawCenteredString(text, w / 2, 3, TEXT_WHITE);
                } else if (hovered()) {
                    canvas.roundedRect(0, 0, w, h, 3f, BG_HOVER, 0, 0);
                    canvas.drawCenteredString(text, w / 2, 3, TEXT_PRIMARY);
                } else {
                    canvas.drawCenteredString(text, w / 2, 3, TEXT_SECONDARY);
                }
            });

            onMouseClick((input, clickCount, context) -> {
                parentView.switchProcessView(view);
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                return EventDispatch.consumed;
            });
        }

        void updateActiveState(boolean active) {
            this.active = active;
        }
    }
}
