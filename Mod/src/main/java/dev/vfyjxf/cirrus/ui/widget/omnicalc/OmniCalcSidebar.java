package dev.vfyjxf.cirrus.ui.widget.omnicalc;

import dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcScreen.ViewState;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.texture.ColorTexture;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import dev.vfyjxf.cloudlib.ui.widget.SpacerWidget;
import dev.vfyjxf.taffy.style.TaffyDimension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.*;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;

/**
 * Left sidebar navigation panel replicating omnicalc-pro's sidebar.
 * <p>
 * Features rounded icon backgrounds, active indicator bar, gradient dividers,
 * and a footer section matching the browser version's glass sidebar feel.
 */
public class OmniCalcSidebar extends CompositeWidget<Widget> {

    private static final int EXPANDED_WIDTH = 56;
    private static final int COLLAPSED_WIDTH = 20;

    private final OmniCalcScreen screen;
    private boolean expanded = true;
    private ViewState activeView = ViewState.CALCULATOR;
    private final List<NavButton> navButtons = new ArrayList<>();

    public OmniCalcSidebar(OmniCalcScreen screen) {
        this.screen = screen;

        useStyle(
                flexColumn(),
                widthOf(EXPANDED_WIDTH),
                heightOf(TaffyDimension.percent(1f)),
                paddingTop(6),
                paddingBottom(4)
        );

        // Draw sidebar background with subtle right border
        onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = width(), h = height();
            // Glass-like sidebar background
            canvas.fill(0, 0, w, h, SIDEBAR_BG);
            // Right border line
            canvas.fill(w - 1, 0, 1, h, BORDER_MAIN);
            // Top gradient accent line (very subtle)
            canvas.fillGradient(0, 0, w, 1, 0x00000000, 0x20FFFFFF);
        });

        // ── Collapse/expand toggle ──
        var toggleWidget = new Widget();
        toggleWidget.useStyle(sizeOf(14, 10), alignSelfCenter(), marginBottom(2));
        toggleWidget.onRender((canvas, mx, my, pt, self, ctx) -> {
            // Draw hamburger icon
            int cx = self.width() / 2;
            canvas.fill(cx - 4, 2, 8, 1, TEXT_WEAK);
            canvas.fill(cx - 4, 5, 8, 1, TEXT_WEAK);
            canvas.fill(cx - 4, 8, 8, 1, TEXT_WEAK);
        });
        toggleWidget.onMouseClick((input, clicks, ctx) -> {
            toggleExpanded();
            return EventDispatch.consumed;
        });
        addWidget(toggleWidget);

        // ── Gradient divider ──
        var dividerWidget = new Widget();
        dividerWidget.useStyle(heightOf(1), marginHorizontal(4), marginBottom(6));
        dividerWidget.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width();
            canvas.fillGradient(0, 0, w / 2, 1, 0x00000000, BORDER_SEC);
            canvas.fillGradient(w / 2, 0, w / 2, 1, BORDER_SEC, 0x00000000);
        });
        addWidget(dividerWidget);

        // ── Navigation buttons ──
        addNavButton(ViewState.CALCULATOR, "\u2699", "Calculator");
        addNavButton(ViewState.LIBRARY, "\u2261", "Database");
        addNavButton(ViewState.RULES, "\u2630", "Rules");
        addNavButton(ViewState.CONTEXT, "\u26A1", "Context");

        // ── Spacer ──
        addWidget(SpacerWidget.create());

        // ── Environment label ──
        var envLabel = LabelWidget.of(Component.literal("DEV"))
                .setColor(TEXT_DIM)
                .setShadow(false);
        envLabel.useStyle(heightOf(6), alignSelfCenter(), marginBottom(4));
        addWidget(envLabel);

        // ── Bottom gradient divider ──
        var bottomDiv = new Widget();
        bottomDiv.useStyle(heightOf(1), marginHorizontal(4), marginBottom(4));
        bottomDiv.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width();
            canvas.fillGradient(0, 0, w / 2, 1, 0x00000000, BORDER_SEC);
            canvas.fillGradient(w / 2, 0, w / 2, 1, BORDER_SEC, 0x00000000);
        });
        addWidget(bottomDiv);

        // ── Footer: Language + User Avatar ──
        var footerRow = new WidgetGroup<>();
        footerRow.useStyle(flexRow(), justifyCenter(), gap(3), marginHorizontal(3));

        // Language toggle
        var langWidget = new Widget();
        langWidget.useStyle(sizeOf(18, 10));
        langWidget.onRender((canvas, mx, my, pt, self, ctx) -> {
            canvas.roundedRect(0, 0, self.width(), self.height(), 3f,
                    BG_HOVER, 1f, BORDER_SEC);
            canvas.drawCenteredString("EN", self.width() / 2, 2, TEXT_WEAK);
        });
        footerRow.addWidget(langWidget);

        // Version label
        var verWidget = new Widget();
        verWidget.useStyle(sizeOf(18, 10));
        verWidget.onRender((canvas, mx, my, pt, self, ctx) -> {
            canvas.roundedRect(0, 0, self.width(), self.height(), 3f,
                    BG_HOVER, 1f, BORDER_SEC);
            canvas.drawCenteredString("v1", self.width() / 2, 2, TEXT_WEAK);
        });
        footerRow.addWidget(verWidget);
        addWidget(footerRow);

        // ── User avatar ──
        var avatarWidget = new Widget();
        avatarWidget.useStyle(sizeOf(12, 12), alignSelfCenter(), marginTop(4));
        avatarWidget.onRender((canvas, mx, my, pt, self, ctx) -> {
            // Gradient avatar circle (accent → purple)
            canvas.roundedRect(0, 0, 12, 12, 4f, ACCENT, 0, 0);
            canvas.circle(6f, 6f, 3f, PURPLE);
        });
        addWidget(avatarWidget);
    }

    private void addNavButton(ViewState view, String icon, String label) {
        var navBtn = new NavButton(view, icon, label);
        navButtons.add(navBtn);
        addWidget(navBtn);
    }

    private void toggleExpanded() {
        expanded = !expanded;
        useStyle(widthOf(expanded ? EXPANDED_WIDTH : COLLAPSED_WIDTH));
        for (var btn : navButtons) {
            btn.updateLayout(expanded);
        }
    }

    public void setActiveView(ViewState view) {
        this.activeView = view;
        for (var btn : navButtons) {
            btn.updateActive(view);
        }
    }

    // ═══════════════════ NavButton ═══════════════════

    private class NavButton extends CompositeWidget<Widget> {

        private final ViewState view;
        private final LabelWidget textLabel;
        private boolean active = false;

        NavButton(ViewState view, String icon, String label) {
            this.view = view;

            useStyle(
                    flexRow(),
                    alignItemsCenter(),
                    gap(3),
                    paddingHorizontal(3),
                    paddingVertical(3),
                    marginHorizontal(3),
                    marginVertical(1)
            );

            // Icon with rounded background (drawn in onRender)
            var iconWidget = new Widget();
            iconWidget.useStyle(sizeOf(14, 14));
            final String iconChar = icon;
            iconWidget.onRender((canvas, mx, my, pt, self, ctx) -> {
                // Rounded icon background
                int bg = active ? ACCENT_BG : (hovered() ? 0x15FFFFFF : 0x00000000);
                if (bg != 0) {
                    canvas.roundedRect(0, 0, 14, 14, 4f, bg, 0, 0);
                }
                int color = active ? ACCENT_TEXT : TEXT_SECONDARY;
                canvas.drawCenteredString(iconChar, 7, 4, color);
            });
            addWidget(iconWidget);

            // Text label
            textLabel = LabelWidget.of(Component.literal(label))
                    .setColor(TEXT_SECONDARY)
                    .setShadow(true);
            textLabel.useStyle(flexGrow(1), heightOf(10));
            addWidget(textLabel);

            updateActive(screen.currentView());

            // ── Render: active indicator bar + rounded-xl background ──
            onRender((canvas, mouseX, mouseY, partialTicks, self, context) -> {
                int w = width(), h = height();
                if (active) {
                    // Left indicator bar (rounded-r-full)
                    canvas.roundedRect(-3, (h - 8) / 2, 2, 8, 0f, 1f, 1f, 0f, ACCENT_LIGHT, 0, 0);
                    // Active background (rounded-xl)
                    canvas.roundedRect(0, 0, w, h, 5f, ACCENT_BG, 1f, ACCENT_BORDER);
                } else if (hovered()) {
                    canvas.roundedRect(0, 0, w, h, 5f, 0x15FFFFFF, 0, 0);
                }
            });

            onMouseClick((input, clickCount, context) -> {
                screen.switchView(view);
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                return EventDispatch.consumed;
            });
        }

        void updateActive(ViewState currentView) {
            this.active = (view == currentView);
            textLabel.setColor(active ? ACCENT_TEXT : TEXT_SECONDARY);
        }

        void updateLayout(boolean sidebarExpanded) {
            textLabel.setVisible(sidebarExpanded);
        }
    }
}
