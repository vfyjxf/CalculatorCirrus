package dev.vfyjxf.cirrus.ui.widget.omnicalc.context;

import dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import dev.vfyjxf.cloudlib.ui.widget.SpacerWidget;
import dev.vfyjxf.cloudlib.ui.widget.ToggleWidget;
import dev.vfyjxf.taffy.style.TaffyDimension;
import net.minecraft.network.chat.Component;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.*;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;

/**
 * Machine Context Manager page.
 * Three-column top layout (Global Context | Inheritance Mask | Recipe Context)
 * plus Analysis Engine bottom section with scalar cards and IO analysis.
 */
public class OmniContextView extends CompositeWidget<Widget> {

    public OmniContextView() {
        useStyle(flexColumn(), sizeFull(), paddingHorizontal(6), paddingVertical(4), gap(4));

        onRender((canvas, mx, my, pt, self, ctx) ->
                canvas.fill(0, 0, width(), height(), BG_PRIMARY));

        // ═══════════ Top: 3-column layout ═══════════
        var topRow = new WidgetGroup<>();
        topRow.useStyle(flexRow(), gap(4), flexGrow(1), minHeight(0));

        topRow.addWidget(createGlobalContextColumn());
        topRow.addWidget(createInheritanceMaskColumn());
        topRow.addWidget(createRecipeContextColumn());

        addWidget(topRow);

        // ═══════════ Bottom: Analysis Engine ═══════════
        addWidget(createAnalysisEngine());
    }

    // ═══════════════════ Global Context Column ═══════════════════

    private Widget createGlobalContextColumn() {
        var col = new WidgetGroup<>();
        col.useStyle(flexColumn(), widthOf(TaffyDimension.percent(0.32f)));
        col.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width(), h = self.height();
            canvas.roundedRect(0, 0, w, h, 4f, BG_PANEL, 1f, BORDER_MAIN);
        });

        // Header
        var header = new Widget();
        header.useStyle(heightOf(12), marginBottom(2));
        header.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width();
            canvas.roundedRect(0, 0, w, 12, 4f, 4f, 0f, 0f, BG_HOVER, 0, 0);
            canvas.drawString("\u2699 GLOBAL CONTEXT", 4, 3, TEXT_PRIMARY);
        });
        col.addWidget(header);

        // Dropdowns and toggles
        col.addWidget(createDropdownRow("Voltage Tier", "HV"));
        col.addWidget(createContextToggle("Overclocked", true));
        col.addWidget(createContextToggle("Maintenance Issues", false));
        col.addWidget(createDropdownRow("Coils", "Nichrome"));

        // Plugins sub-section
        var pluginsLabel = LabelWidget.of(Component.literal("Plugins:"))
                .setColor(TEXT_WEAK).setShadow(true);
        pluginsLabel.useStyle(heightOf(7), marginTop(2), marginLeft(4));
        col.addWidget(pluginsLabel);

        col.addWidget(createContextToggle("Output Bus (Lv.2)", true));
        col.addWidget(createContextToggle("Fluid Reg. (Lv.1)", true));
        col.addWidget(createContextToggle("Parallel (Lv.4)", true));

        return col;
    }

    // ═══════════════════ Inheritance Mask Column ═══════════════════

    private Widget createInheritanceMaskColumn() {
        var col = new WidgetGroup<>();
        col.useStyle(flexColumn(), widthOf(TaffyDimension.percent(0.32f)));
        col.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width(), h = self.height();
            canvas.roundedRect(0, 0, w, h, 4f, BG_PANEL, 1f, BORDER_MAIN);
        });

        // Header
        var header = new Widget();
        header.useStyle(heightOf(12), marginBottom(2));
        header.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width();
            canvas.roundedRect(0, 0, w, 12, 4f, 4f, 0f, 0f, BG_HOVER, 0, 0);
            canvas.drawString("\u26D3 INHERITANCE MASK", 4, 3, TEXT_PRIMARY);
        });
        col.addWidget(header);

        // Mask toggles
        String[] maskItems = {"Voltage Tier", "Overclocked", "Coils", "Plugins", "Maintenance"};
        for (String item : maskItems) {
            col.addWidget(createMaskToggleRow(item, true));
        }

        col.addWidget(SpacerWidget.create());

        // Reset button
        var resetBtn = new Widget();
        resetBtn.useStyle(heightOf(10), marginHorizontal(4), marginBottom(4));
        resetBtn.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width();
            int bg = self.hovered() ? BG_ELEVATED : BG_HOVER;
            canvas.roundedRect(0, 0, w, 10, 3f, bg, 1f, BORDER_SEC);
            canvas.drawCenteredString("Reset All", w / 2, 2, TEXT_SECONDARY);
        });
        col.addWidget(resetBtn);

        return col;
    }

    // ═══════════════════ Recipe Context Column ═══════════════════

    private Widget createRecipeContextColumn() {
        var col = new WidgetGroup<>();
        col.useStyle(flexColumn(), flexGrow(1), minWidth(0));
        col.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width(), h = self.height();
            canvas.roundedRect(0, 0, w, h, 4f, BG_PANEL, 1f, BORDER_MAIN);
        });

        // Header
        var header = new Widget();
        header.useStyle(heightOf(12), marginBottom(2));
        header.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width();
            canvas.roundedRect(0, 0, w, 12, 4f, 4f, 0f, 0f, BG_HOVER, 0, 0);
            canvas.drawString("\u2709 RECIPE CONTEXT", 4, 3, TEXT_PRIMARY);
        });
        col.addWidget(header);

        // Derived values with lock icons
        String[][] recipeRows = {
                {"Voltage Tier", "HV"},
                {"Overclocked", "ON"},
                {"Coils", "Nichrome"},
                {"Parallel", "x4"},
        };
        for (String[] row : recipeRows) {
            var rowWidget = new Widget();
            rowWidget.useStyle(heightOf(9), marginHorizontal(4), marginVertical(1));
            rowWidget.onRender((canvas, mx, my, pt, self, ctx) -> {
                int w = self.width();
                canvas.roundedRect(0, 0, w, 9, 2f, BG_SURFACE, 1f, BORDER_MAIN);
                canvas.drawString(row[0], 3, 1, TEXT_SECONDARY);
                canvas.drawString(row[1], w - row[1].length() * 6 - 12, 1, TEXT_PRIMARY);
                canvas.drawString("\uD83D\uDD12", w - 8, 1, TEXT_WEAK); // lock icon
            });
            col.addWidget(rowWidget);
        }

        return col;
    }

    // ═══════════════════ Analysis Engine ═══════════════════

    private Widget createAnalysisEngine() {
        var engine = new WidgetGroup<>();
        engine.useStyle(flexColumn(), heightOf(60));
        engine.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width(), h = self.height();
            canvas.roundedRect(0, 0, w, h, 4f, BG_PANEL, 1f, BORDER_MAIN);
        });

        // Header
        var header = new Widget();
        header.useStyle(heightOf(12), marginBottom(2));
        header.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width();
            canvas.roundedRect(0, 0, w, 12, 4f, 4f, 0f, 0f, BG_HOVER, 0, 0);
            canvas.drawString("\u26A1 ANALYSIS ENGINE", 4, 3, TEXT_PRIMARY);
            canvas.drawString("Live", w - 20, 3, GREEN_TEXT);
        });
        engine.addWidget(header);

        // Scalar cards row
        var scalarRow = new WidgetGroup<>();
        scalarRow.useStyle(flexRow(), gap(3), paddingHorizontal(4), marginBottom(3));

        scalarRow.addWidget(createScalarCard("EU/t", "512", ACCENT_TEXT));
        scalarRow.addWidget(createScalarCard("Duration", "20 ticks", ORANGE_TEXT));
        scalarRow.addWidget(createScalarCard("OC Factor", "x4", GREEN_TEXT));
        scalarRow.addWidget(createScalarCard("Efficiency", "98.2%", PURPLE_TEXT));

        engine.addWidget(scalarRow);

        // IO Analysis (two columns)
        var ioRow = new WidgetGroup<>();
        ioRow.useStyle(flexRow(), gap(4), paddingHorizontal(4), flexGrow(1), minHeight(0));

        // Input analysis
        var inputCol = new WidgetGroup<>();
        inputCol.useStyle(flexColumn(), flexGrow(1), minWidth(0));
        var inputHeader = LabelWidget.of(Component.literal("INPUT ANALYSIS"))
                .setColor(TEXT_WEAK).setShadow(true);
        inputHeader.useStyle(heightOf(7), marginBottom(2));
        inputCol.addWidget(inputHeader);

        String[][] inputs = {{"Copper Wire", "4/t"}, {"Iron Plate", "2/t"}, {"Silicon", "1/t"}};
        for (String[] in : inputs) {
            inputCol.addWidget(createIORow(in[0], in[1], ACCENT_LIGHT));
        }
        ioRow.addWidget(inputCol);

        // Divider
        var ioDiv = new Widget();
        ioDiv.useStyle(widthOf(1));
        ioDiv.onRender((canvas, mx, my, pt, self, ctx) ->
                canvas.fill(0, 0, 1, self.height(), BORDER_MAIN));
        ioRow.addWidget(ioDiv);

        // Output analysis
        var outputCol = new WidgetGroup<>();
        outputCol.useStyle(flexColumn(), flexGrow(1), minWidth(0));
        var outputHeader = LabelWidget.of(Component.literal("OUTPUT ANALYSIS"))
                .setColor(TEXT_WEAK).setShadow(true);
        outputHeader.useStyle(heightOf(7), marginBottom(2));
        outputCol.addWidget(outputHeader);

        String[][] outputs = {{"Circuit", "1/t"}, {"Waste", "0.5/t"}};
        for (String[] out : outputs) {
            outputCol.addWidget(createIORow(out[0], out[1], GREEN_TEXT));
        }
        ioRow.addWidget(outputCol);

        engine.addWidget(ioRow);

        return engine;
    }

    // ═══════════════════ Helper Methods ═══════════════════

    private Widget createScalarCard(String label, String value, int valueColor) {
        var card = new Widget();
        card.useStyle(sizeOf(44, 22), flexGrow(1));
        card.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width();
            canvas.roundedRect(0, 0, w, 22, 3f, BG_HOVER, 1f, BORDER_MAIN);
            canvas.drawCenteredString(label, w / 2, 3, TEXT_WEAK);
            canvas.drawCenteredString(value, w / 2, 12, valueColor);
        });
        return card;
    }

    private Widget createIORow(String name, String rate, int dotColor) {
        var row = new Widget();
        row.useStyle(heightOf(8), marginBottom(1));
        row.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width();
            canvas.circle(4f, 4f, 1.5f, dotColor);
            canvas.drawString(name, 8, 1, TEXT_SECONDARY);
            canvas.drawString(rate, w - rate.length() * 6 - 2, 1, TEXT_PRIMARY);
        });
        return row;
    }

    private Widget createDropdownRow(String label, String value) {
        var row = new WidgetGroup<>();
        row.useStyle(flexRow(), alignItemsCenter(), gap(2), marginHorizontal(4), marginVertical(1));

        var lbl = LabelWidget.of(Component.literal(label))
                .setColor(TEXT_SECONDARY).setShadow(true);
        lbl.useStyle(heightOf(7), flexGrow(1));
        row.addWidget(lbl);

        var dropdown = new Widget();
        dropdown.useStyle(sizeOf(32, 9));
        dropdown.onRender((canvas, mx, my, pt, self, ctx) -> {
            int bg = self.hovered() ? BG_ELEVATED : BG_HOVER;
            canvas.roundedRect(0, 0, 32, 9, 2f, bg, 1f, BORDER_SEC);
            canvas.drawString(value + " \u25BE", 3, 1, TEXT_PRIMARY);
        });
        row.addWidget(dropdown);

        return row;
    }

    private Widget createContextToggle(String label, boolean defaultValue) {
        var row = new WidgetGroup<>();
        row.useStyle(flexRow(), alignItemsCenter(), gap(2), marginHorizontal(4), marginVertical(1));

        var lbl = LabelWidget.of(Component.literal(label))
                .setColor(TEXT_SECONDARY).setShadow(true);
        lbl.useStyle(heightOf(7), flexGrow(1));
        row.addWidget(lbl);

        var toggle = ToggleWidget.create()
                .setToggled(defaultValue)
                .setColors(BG_HOVER, ACCENT);
        toggle.useStyle(sizeOf(12, 7));
        row.addWidget(toggle);

        return row;
    }

    private Widget createMaskToggleRow(String label, boolean inherited) {
        var row = new WidgetGroup<>();
        row.useStyle(flexRow(), alignItemsCenter(), gap(2), marginHorizontal(4), marginVertical(1));

        // Status indicator dot
        var dot = new Widget();
        dot.useStyle(sizeOf(5, 5));
        dot.onRender((canvas, mx, my, pt, self, ctx) ->
                canvas.circle(2.5f, 2.5f, 2f, inherited ? GREEN : RED));
        row.addWidget(dot);

        var lbl = LabelWidget.of(Component.literal(label))
                .setColor(TEXT_SECONDARY).setShadow(true);
        lbl.useStyle(heightOf(7), flexGrow(1));
        row.addWidget(lbl);

        var toggle = ToggleWidget.create()
                .setToggled(inherited)
                .setColors(BG_HOVER, GREEN);
        toggle.useStyle(sizeOf(12, 7));
        row.addWidget(toggle);

        return row;
    }
}
