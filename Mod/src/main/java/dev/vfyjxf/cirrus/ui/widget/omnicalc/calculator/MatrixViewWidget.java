package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import dev.vfyjxf.cloudlib.ui.widget.SpacerWidget;
import net.minecraft.network.chat.Component;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.*;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;

/**
 * Topology Aggregation Matrix view — styled with rounded table container
 * and hoverable data rows matching omnicalc-pro's matrix view.
 */
public class MatrixViewWidget extends CompositeWidget<Widget> {

    private static final String[][] SAMPLE_DATA = {
            {"Electronic Circuit", "x1 Assembler", "Resistor(2) Copper Wire(4)"},
            {"Advanced Circuit", "x1 Assembler", "Circuit(2) IC Chip(1) Wire(8)"},
            {"Processor", "x2 Assembly Line", "Adv.Circuit(4) RAM(2) CPU(1)"},
            {"Data Module", "x1 Assembler", "Processor(2) Storage(4) SoC(1)"},
            {"Mainframe", "x1 Assembly Line", "DataMod(8) Fiber(16) Frame(1)"},
            {"Quantum Core", "x4 Cleanroom", "Mainframe(4) Crystal(16) Ctrl(2)"},
            {"Nano Circuit", "x1 Assembler", "SMD-R(8) SMD-C(4) Board(1)"},
            {"Wetware Board", "x1 Bio Reactor", "StemCell(4) Glass(2) Sub(1)"},
    };

    public MatrixViewWidget() {
        useStyle(
                flexColumn(),
                sizeFull(),
                paddingHorizontal(6),
                paddingVertical(4)
        );

        // Background
        onRender((canvas, mx, my, pt, self, ctx) ->
                canvas.fill(0, 0, width(), height(), BG_PRIMARY));

        // ── Title row ──
        var titleRow = new WidgetGroup<>();
        titleRow.useStyle(flexRow(), alignItemsCenter(), gap(4), marginBottom(1));

        var title = LabelWidget.of(Component.literal("\u2261 Topology Aggregation Matrix"))
                .setColor(TEXT_PRIMARY).setShadow(true);
        title.useStyle(heightOf(10));
        titleRow.addWidget(title);

        titleRow.addWidget(SpacerWidget.create());

        // View mode pills
        var gridPill = new Widget();
        gridPill.useStyle(sizeOf(20, 10));
        gridPill.onRender((canvas, mx, my, pt, self, ctx) -> {
            canvas.roundedRect(0, 0, 20, 10, 3f, ACCENT, 0, 0);
            canvas.drawCenteredString("Grid", 10, 2, TEXT_WHITE);
        });
        titleRow.addWidget(gridPill);

        var listPill = new Widget();
        listPill.useStyle(sizeOf(20, 10));
        listPill.onRender((canvas, mx, my, pt, self, ctx) -> {
            canvas.roundedRect(0, 0, 20, 10, 3f, BG_HOVER, 0, 0);
            canvas.drawCenteredString("List", 10, 2, TEXT_SECONDARY);
        });
        titleRow.addWidget(listPill);

        addWidget(titleRow);

        var subtitle = LabelWidget.of(Component.literal("Aggregated ledger of machine operations"))
                .setColor(TEXT_SECONDARY).setShadow(true);
        subtitle.useStyle(heightOf(8), marginBottom(4));
        addWidget(subtitle);

        // ── Table container with rounded border ──
        var tableContainer = new WidgetGroup<>();
        tableContainer.useStyle(flexColumn(), flexGrow(1));
        tableContainer.onRender((canvas, mx, my, pt, self, ctx) ->
                canvas.roundedRect(0, 0, self.width(), self.height(), 4f, BG_PANEL, 1f, BORDER_MAIN));

        // Header row (rounded top corners)
        var header = new Widget();
        header.useStyle(heightOf(12));
        header.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width();
            canvas.roundedRect(1, 1, w - 2, 11, 3f, 3f, 0f, 0f, BG_HOVER, 0, 0);
            int col1 = 18, col2 = w / 3 + 8, col3 = w * 2 / 3 - 2;
            canvas.drawString("\u2261", 6, 3, TEXT_WEAK);
            canvas.drawString("Target Output", col1, 3, TEXT_WEAK);
            canvas.drawString("Process & Scale", col2, 3, TEXT_WEAK);
            canvas.drawString("Aggregated Inputs", col3, 3, TEXT_WEAK);
        });
        tableContainer.addWidget(header);

        // Data rows
        for (int i = 0; i < SAMPLE_DATA.length; i++) {
            final int idx = i;
            var row = new Widget();
            row.useStyle(heightOf(11));
            row.onRender((canvas, mx, my, pt, self, ctx) -> {
                int w = self.width();
                int bg = self.hovered() ? BG_HOVER : (idx % 2 == 0 ? BG_PANEL : 0xFF0D1117);
                canvas.fill(1, 0, w - 2, 10, bg);
                if (self.hovered()) {
                    canvas.fill(1, 0, 1, 10, ACCENT_LIGHT); // left accent bar on hover
                }
                canvas.fill(1, 10, w - 2, 1, 0x08FFFFFF); // subtle separator
                int col1 = 18, col2 = w / 3 + 8, col3 = w * 2 / 3 - 2;
                canvas.drawString("\u25BE", 6, 2, TEXT_WEAK);
                canvas.drawString(SAMPLE_DATA[idx][0], col1, 2, TEXT_PRIMARY);
                canvas.drawString(SAMPLE_DATA[idx][1], col2, 2, TEXT_SECONDARY);
                canvas.drawString(SAMPLE_DATA[idx][2], col3, 2, TEXT_SECONDARY);
            });
            tableContainer.addWidget(row);
        }

        addWidget(tableContainer);
    }
}
