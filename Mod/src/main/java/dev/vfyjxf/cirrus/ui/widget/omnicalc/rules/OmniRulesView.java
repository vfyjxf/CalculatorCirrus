package dev.vfyjxf.cirrus.ui.widget.omnicalc.rules;

import dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import dev.vfyjxf.cloudlib.ui.widget.SpacerWidget;
import net.minecraft.network.chat.Component;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.*;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;

/**
 * Rules Engine page with visual node editor.
 * Features rounded nodes with colored headers, bezier curve connections,
 * grid-pattern canvas, and properties inspector.
 */
public class OmniRulesView extends CompositeWidget<Widget> {

    // ── Node definitions for the visual editor ──
    private static final int[][] NODE_POSITIONS = {
            // {x, y, width, height}
            {20, 25, 60, 26},   // Trigger
            {100, 15, 65, 26},  // Condition 1
            {100, 55, 65, 26},  // Condition 2
            {190, 35, 60, 26},  // Action
            {190, 75, 55, 26},  // Logic
    };
    private static final String[] NODE_TYPES = {"Trigger", "Condition", "Condition", "Action", "Logic"};
    private static final String[] NODE_DESCS = {
            "When recipe check", "Input contains", "Check mach. tier", "Block recipe", "AND gate"
    };
    private static final int[] NODE_COLORS = {
            NODE_TRIGGER, NODE_CONDITION, NODE_CONDITION, NODE_ACTION, NODE_LOGIC
    };

    private boolean showVisual = true;

    public OmniRulesView() {
        useStyle(flexColumn(), sizeFull(), paddingHorizontal(6), paddingVertical(4));

        onRender((canvas, mx, my, pt, self, ctx) ->
                canvas.fill(0, 0, width(), height(), BG_PRIMARY));

        // ── Title row with tab pills ──
        var titleRow = new WidgetGroup<>();
        titleRow.useStyle(flexRow(), alignItemsCenter(), gap(4), marginBottom(1));

        var title = LabelWidget.of(Component.literal("Rules Engine"))
                .setColor(TEXT_PRIMARY).setShadow(true);
        title.useStyle(heightOf(10));
        titleRow.addWidget(title);

        titleRow.addWidget(SpacerWidget.create());

        // Library pill
        var libraryPill = new Widget();
        libraryPill.useStyle(sizeOf(32, 10));
        libraryPill.onRender((canvas, mx, my, pt, self, ctx) -> {
            int bg = self.hovered() ? BG_HOVER : BG_PANEL;
            canvas.roundedRect(0, 0, 32, 10, 3f, bg, 1f, BORDER_SEC);
            canvas.drawCenteredString("Library", 16, 2, TEXT_SECONDARY);
        });
        titleRow.addWidget(libraryPill);

        // Visual pill (active)
        var visualPill = new Widget();
        visualPill.useStyle(sizeOf(28, 10));
        visualPill.onRender((canvas, mx, my, pt, self, ctx) -> {
            canvas.shadow(0, 1, 28, 10, 3f, 1f, 3f, 0x302563EB);
            canvas.roundedRect(0, 0, 28, 10, 3f, ACCENT, 0, 0);
            canvas.drawCenteredString("Visual", 14, 2, TEXT_WHITE);
        });
        titleRow.addWidget(visualPill);
        addWidget(titleRow);

        // Subtitle
        var subtitle = LabelWidget.of(Component.literal("Define how the calculator resolves dependencies"))
                .setColor(TEXT_SECONDARY).setShadow(true);
        subtitle.useStyle(heightOf(8), marginBottom(3));
        addWidget(subtitle);

        // ── Toolbar ──
        var toolbar = new WidgetGroup<>();
        toolbar.useStyle(flexRow(), alignItemsCenter(), gap(3), marginBottom(3));

        // Add Node button
        var addNodeBtn = new Widget();
        addNodeBtn.useStyle(sizeOf(40, 10));
        addNodeBtn.onRender((canvas, mx, my, pt, self, ctx) -> {
            int bg = self.hovered() ? BG_ELEVATED : BG_HOVER;
            canvas.roundedRect(0, 0, 40, 10, 3f, bg, 1f, BORDER_SEC);
            canvas.drawCenteredString("\u2295 Add Node", 20, 2, TEXT_PRIMARY);
        });
        toolbar.addWidget(addNodeBtn);

        toolbar.addWidget(SpacerWidget.create());

        // Save button
        var saveBtn = new Widget();
        saveBtn.useStyle(sizeOf(24, 10));
        saveBtn.onRender((canvas, mx, my, pt, self, ctx) -> {
            int bg = self.hovered() ? BG_ELEVATED : BG_HOVER;
            canvas.roundedRect(0, 0, 24, 10, 3f, bg, 1f, BORDER_SEC);
            canvas.drawCenteredString("Save", 12, 2, TEXT_SECONDARY);
        });
        toolbar.addWidget(saveBtn);

        // Deploy button (accent)
        var deployBtn = new Widget();
        deployBtn.useStyle(sizeOf(36, 10));
        deployBtn.onRender((canvas, mx, my, pt, self, ctx) -> {
            int bg = self.hovered() ? ACCENT_LIGHT : ACCENT;
            canvas.shadow(0, 1, 36, 10, 3f, 1f, 3f, 0x302563EB);
            canvas.roundedRect(0, 0, 36, 10, 3f, bg, 0, 0);
            canvas.drawCenteredString("\u25B6 Deploy", 18, 2, TEXT_WHITE);
        });
        toolbar.addWidget(deployBtn);

        addWidget(toolbar);

        // ── Main area: Canvas + Inspector ──
        var mainArea = new WidgetGroup<>();
        mainArea.useStyle(flexRow(), flexGrow(1), minHeight(0));

        // ── Canvas (node editor) ──
        var canvasWidget = new Widget();
        canvasWidget.useStyle(flexGrow(1), minWidth(0));
        canvasWidget.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width(), h = self.height();

            // Canvas background
            canvas.roundedRect(0, 0, w, h, 4f, TREE_CANVAS_BG, 1f, BORDER_MAIN);

            // Grid pattern
            for (int gx = 0; gx < w; gx += 20) {
                canvas.fill(gx, 0, 1, h, 0x10FFFFFF);
            }
            for (int gy = 0; gy < h; gy += 20) {
                canvas.fill(0, gy, w, 1, 0x10FFFFFF);
            }

            // ── Bezier connections between nodes ──
            // Trigger → Condition 1
            float x0 = 80, y0 = 38, x1 = 100, y1 = 28;
            canvas.bezierCubic(x0, y0, x0 + 12, y0, x1 - 12, y1, x1, y1,
                    1.5f, ACCENT_LIGHT);

            // Trigger → Condition 2
            x0 = 80; y0 = 38; x1 = 100; y1 = 68;
            canvas.bezierCubic(x0, y0, x0 + 12, y0, x1 - 12, y1, x1, y1,
                    1.5f, ACCENT_LIGHT);

            // Condition 1 → Action
            x0 = 165; y0 = 28; x1 = 190; y1 = 48;
            canvas.bezierCubic(x0, y0, x0 + 15, y0, x1 - 15, y1, x1, y1,
                    1.5f, GREEN_TEXT);

            // Condition 2 → Logic
            x0 = 165; y0 = 68; x1 = 190; y1 = 88;
            canvas.bezierCubic(x0, y0, x0 + 15, y0, x1 - 15, y1, x1, y1,
                    1.5f, GREEN_TEXT);

            // ── Nodes ──
            for (int i = 0; i < NODE_POSITIONS.length; i++) {
                int nx = NODE_POSITIONS[i][0], ny = NODE_POSITIONS[i][1];
                int nw = NODE_POSITIONS[i][2], nh = NODE_POSITIONS[i][3];
                boolean selected = (i == 0); // first node selected for demo
                OmniCalcColors.drawRuleNode(canvas, nx, ny, nw, nh,
                        NODE_TYPES[i], NODE_DESCS[i], NODE_COLORS[i], selected);
            }
        });
        mainArea.addWidget(canvasWidget);

        // ── Properties inspector ──
        var inspector = new WidgetGroup<>();
        inspector.useStyle(flexColumn(), widthOf(65), paddingHorizontal(4), paddingVertical(4));
        inspector.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width(), h = self.height();
            canvas.fill(0, 0, w, h, BG_PANEL);
            canvas.fill(0, 0, 1, h, BORDER_MAIN);
        });

        var inspTitle = LabelWidget.of(Component.literal("Properties"))
                .setColor(TEXT_PRIMARY).setShadow(true);
        inspTitle.useStyle(heightOf(10), marginBottom(3));
        inspector.addWidget(inspTitle);

        // Divider
        var inspDiv = new Widget();
        inspDiv.useStyle(heightOf(1), marginBottom(3));
        inspDiv.onRender((canvas, mx, my, pt, self, ctx) ->
                canvas.fill(0, 0, self.width(), 1, BORDER_MAIN));
        inspector.addWidget(inspDiv);

        // Selected node info
        var nodeTypeLabel = LabelWidget.of(Component.literal("Type: Trigger"))
                .setColor(TEXT_SECONDARY).setShadow(true);
        nodeTypeLabel.useStyle(heightOf(8), marginBottom(2));
        inspector.addWidget(nodeTypeLabel);

        // Node name field placeholder
        var nameWidget = new Widget();
        nameWidget.useStyle(heightOf(10), marginBottom(3));
        nameWidget.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width();
            canvas.roundedRect(0, 0, w, 10, 2f, BG_HOVER, 1f, BORDER_SEC);
            canvas.drawString("When recipe check", 3, 2, TEXT_PRIMARY);
        });
        inspector.addWidget(nameWidget);

        // Configuration items
        String[] configLabels = {"Priority:", "Enabled:", "Scope:"};
        String[] configValues = {"100", "Yes", "Global"};
        for (int i = 0; i < configLabels.length; i++) {
            var configRow = new WidgetGroup<>();
            configRow.useStyle(flexRow(), alignItemsCenter(), gap(2), marginBottom(2));

            var lbl = LabelWidget.of(Component.literal(configLabels[i]))
                    .setColor(TEXT_WEAK).setShadow(true);
            lbl.useStyle(heightOf(7), flexGrow(1));
            configRow.addWidget(lbl);

            final int ci = i;
            var valWidget = new Widget();
            valWidget.useStyle(sizeOf(28, 8));
            valWidget.onRender((canvas, mx, my, pt, self, ctx) -> {
                canvas.roundedRect(0, 0, 28, 8, 2f, BG_HOVER, 1f, BORDER_SEC);
                canvas.drawCenteredString(configValues[ci], 14, 1, TEXT_PRIMARY);
            });
            configRow.addWidget(valWidget);
            inspector.addWidget(configRow);
        }

        mainArea.addWidget(inspector);
        addWidget(mainArea);
    }
}
