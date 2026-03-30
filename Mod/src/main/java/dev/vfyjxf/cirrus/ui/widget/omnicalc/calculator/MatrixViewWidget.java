package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Icon;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Pos;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.*;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;

/**
 * Cirrus-skinned matrix view that keeps the omnicalc table hierarchy:
 * header chrome, column header band, then a dense list of machine rows.
 */
public class MatrixViewWidget extends CompositeWidget<Widget> {

    //region State
    private final MatrixRowsWidget rowsWidget;
    //endregion

    //region Data
    private static final MatrixRowData[] rows = {
            new MatrixRowData("Stargate Controller", "Assembly Line", 2, 30720,
                    new String[]{"+4 Quantum Eye", "+2 Crystal Matrix", "+16 Data Loop"},
                    new String[]{"+1 Controller Core", "+1 Spare Coil"}),
            new MatrixRowData("Dimensional Latch", "Circuit Assembler", 4, 8192,
                    new String[]{"+12 SMD Capacitor", "+8 Fine Wire", "+4 Wetware Board"},
                    new String[]{"+2 Latch Block", "+1 Debug Sample"}),
            new MatrixRowData("Flux Manifold", "Fusion Crafter", 1, 65536,
                    new String[]{"+1 Void Alloy", "+24 Stabilizer Mesh", "+8 Field Ring"},
                    new String[]{"+1 Flux Manifold"}),
            new MatrixRowData("Chronal Relay", "Nano Forge", 3, 12288,
                    new String[]{"+6 Timing Crystal", "+12 Signal Fiber", "+2 Relay Frame"},
                    new String[]{"+3 Relay", "+1 Loss"}),
            new MatrixRowData("Memory Prism", "Cleanroom Cluster", 2, 20480,
                    new String[]{"+4 Optical SoC", "+10 Crystal RAM", "+2 Prism Shell"},
                    new String[]{"+2 Memory Prism"}),
            new MatrixRowData("Stellar Frame", "Mega Bender", 1, 4096,
                    new String[]{"+6 Naquadah Plate", "+2 Dense Frame", "+8 Shield Bolt"},
                    new String[]{"+1 Stellar Frame"})
    };
    //endregion

    public MatrixViewWidget() {
        useStyle(
                flexColumn(),
                sizeFull(),
                paddingHorizontal(4),
                paddingVertical(4),
                gap(3)
        );

        onRender((canvas, mx, my, pt, self, ctx) ->
                canvas.texture(Background.inset, 0, 0, width(), height()));

        addWidget(createHeader());
        rowsWidget = new MatrixRowsWidget();
        addWidget(createTableShell());
    }

    int visibleRowCount() {
        return rows.length;
    }

    void debugSetTableScroll(float scroll) {
        rowsWidget.debugSetScroll(scroll);
    }

    float debugTableScroll() {
        return rowsWidget.debugScroll();
    }

    static String trimLedgerChipLabel(String text) {
        if (text.length() <= 14) {
            return text;
        }
        return text.substring(0, 12) + "..";
    }

    static int debugRowHeight() {
        return MatrixRowsWidget.rowHeight;
    }

    private Widget createHeader() {
        var header = new WidgetGroup<>();
        header.useStyle(
                flexRow(),
                alignItemsCenter(),
                gap(4),
                paddingHorizontal(5),
                paddingVertical(4)
        );
        header.onRender((canvas, mx, my, pt, self, ctx) ->
                canvas.texture(Background.frame, 0, 0, self.width(), self.height()));

        var icon = new Widget();
        icon.useStyle(sizeOf(16, 16));
        icon.onRender((canvas, mx, my, pt, self, ctx) -> {
            canvas.texture(Background.Outlined.inset, 0, 0, 16, 16);
            canvas.texture(Icon.Calc.Summary.tabSelected, 3, 3, 10, 10);
        });
        header.addWidget(icon);

        var titleBox = new WidgetGroup<>();
        titleBox.useStyle(flexColumn(), gap(1), flexGrow(1), minWidth(0));

        var title = LabelWidget.of(Component.literal("Topology Matrix"))
                .setColor(textPrimary)
                .setShadow(false);
        titleBox.addWidget(title);

        var subtitle = LabelWidget.of(Component.literal("Aggregated machine ledger for the active plan"))
                .setColor(textSecondary)
                .setShadow(false);
        titleBox.addWidget(subtitle);

        header.addWidget(titleBox);

        header.addWidget(createModeChip("GRID", true));
        header.addWidget(createModeChip("LEDGER", false));
        return header;
    }

    private Widget createModeChip(String text, boolean active) {
        var chip = new Widget();
        chip.useStyle(sizeOf(active ? 36 : 42, 14));
        chip.onRender((canvas, mx, my, pt, self, ctx) -> {
            canvas.texture(active ? Background.Outlined.inset : Background.flat, 0, 0, self.width(), self.height());
            canvas.drawCenteredString(text, self.width() / 2, 4, active ? textPrimary : textSecondary);
        });
        return chip;
    }

    private Widget createTableShell() {
        var shell = new WidgetGroup<>();
        shell.useStyle(
                flexColumn(),
                flexGrow(1),
                minHeight(0),
                paddingHorizontal(3),
                paddingVertical(3),
                gap(2)
        );
        shell.onRender((canvas, mx, my, pt, self, ctx) ->
                canvas.texture(Background.inset, 0, 0, self.width(), self.height()));

        shell.addWidget(createColumnHeader());
        rowsWidget.useStyle(flexGrow(1), minHeight(0));
        shell.addWidget(rowsWidget);
        return shell;
    }

    private Widget createColumnHeader() {
        var header = new Widget();
        header.useStyle(heightOf(18));
        header.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width();
            canvas.texture(Background.Outlined.flat, 0, 0, w, self.height());

            int outputX = 28;
            int processX = w / 2 - 10;
            int inputX = w - 118;

            canvas.drawString("OUT", outputX, 6, textWeak);
            canvas.drawString("PROCESS", processX, 6, textWeak);
            canvas.drawString("INPUT LEDGER", inputX, 6, textWeak);
        });
        return header;
    }

    private record MatrixRowData(
            String output,
            String machine,
            int count,
            int eut,
            String[] inputs,
            String[] outputs
    ) {}

    private static final class MatrixRowsWidget extends Widget {

        //region Metrics
        private static final int rowHeight = 30;
        private static final int rowGap = 3;
        private static final int scrollbarWidth = 8;
        private static final float scrollSmooth = 0.35f;
        //endregion

        //region State
        private float scrollY;
        private float targetScrollY;
        private boolean draggingScrollbar;
        private float dragOffsetY;
        //endregion

        private MatrixRowsWidget() {
            onMouseClicked((input, ctx) -> {
                FloatPos local = input.mouseRelative(this);
                if (isOnScrollbarThumb(local.x(), local.y())) {
                    draggingScrollbar = true;
                    dragOffsetY = (float) local.y() - thumbY();
                    ctx.cancel();
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            });

            onMouseReleased((input, ctx) -> {
                draggingScrollbar = false;
                dragOffsetY = 0;
                return EventDispatch.pass;
            });

            onMouseDragged((input, deltaX, deltaY, ctx) -> {
                if (!draggingScrollbar) return EventDispatch.pass;
                FloatPos local = input.mouseRelative(this);
                scrollY = dragTo(local.y());
                targetScrollY = scrollY;
                ctx.cancel();
                return EventDispatch.consumed;
            });

            onMouseScrolled((mouseX, mouseY, sx, sy, ctx) -> {
                targetScrollY = clamp(targetScrollY - (float) sy * 22f, 0, maxScroll());
                ctx.cancel();
                return EventDispatch.consumed;
            });
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            Font font = Minecraft.getInstance().font;
            int w = width();
            int h = height();
            int contentW = w - scrollbarWidth - 4;
            Pos abs = absolutePos();

            scrollY += (targetScrollY - scrollY) * scrollSmooth;
            if (Math.abs(scrollY - targetScrollY) < 0.5f) scrollY = targetScrollY;

            canvas.pushClip(abs.x(), abs.y(), contentW, h);
            canvas.pushTransform();
            canvas.translate(0, -scrollY);

            int rowY = 0;
            for (MatrixRowData row : rows) {
                drawRow(canvas, font, row, contentW, rowY);
                rowY += rowHeight + rowGap;
            }

            canvas.popTransform();
            canvas.popClip();

            if (maxScroll() > 0) {
                int trackX = w - scrollbarWidth;
                canvas.texture(Background.inset, trackX, 0, scrollbarWidth, h);
                canvas.texture(Icon.scrollBar, trackX + 1, thumbY(), scrollbarWidth - 2, thumbH());
            }
        }

        private void drawRow(SceneCanvas canvas, Font font, MatrixRowData row, int w, int y) {
            boolean hovered = hovered();
            canvas.texture(hovered ? Background.Outlined.inset : Background.flat, 0, y, w, rowHeight);

            canvas.texture(hovered ? Icon.arrowUpSelected : Icon.arrowDown, 6, y + 10, 10, 10);
            canvas.texture(Background.Slot.dark, 20, y + 6, 18, 18);
            canvas.texture(Icon.Calc.Summary.tabSelected, 24, y + 10, 10, 10);

            canvas.drawString(row.output, 44, y + 5, textPrimary, false);
            canvas.drawString(row.outputs[0], 44, y + 15, green, false);

            int processX = w / 2 - 14;
            canvas.drawString("x" + row.count + " " + row.machine, processX, y + 5, textSecondary, false);
            canvas.drawString(row.eut + " EU/t", processX, y + 15, orange, false);

            int chipX = Math.max(processX + 88, w - 160);
            for (int i = 0; i < Math.min(2, row.inputs.length); i++) {
                int chipY = y + 4 + i * 10;
                String label = trimLedgerChipLabel(row.inputs[i]);
                int chipW = Math.min(78, Math.max(48, label.length() * 4 + 10));
                canvas.texture(Background.Slot.dark, chipX, chipY, chipW, 10);
                canvas.drawString(label, chipX + 4, chipY + 1, textSecondary, false);
            }
        }

        private boolean isOnScrollbarThumb(double mx, double my) {
            int trackX = width() - scrollbarWidth;
            return maxScroll() > 0
                    && mx >= trackX && mx <= trackX + scrollbarWidth
                    && my >= thumbY() && my <= thumbY() + thumbH();
        }

        private int thumbY() {
            int track = Math.max(1, height() - thumbH());
            float progress = maxScroll() <= 0 ? 0 : scrollY / maxScroll();
            return Math.round(track * progress);
        }

        private int thumbH() {
            return Math.max(18, Math.min(height(), height() * height() / Math.max(contentH(), 1)));
        }

        private float dragTo(double mouseY) {
            int track = Math.max(1, height() - thumbH());
            float top = clamp((float) mouseY - dragOffsetY, 0, track);
            return maxScroll() <= 0 ? 0 : top / track * maxScroll();
        }

        private int contentH() {
            return rows.length * rowHeight + Math.max(0, rows.length - 1) * rowGap;
        }

        private int maxScroll() {
            return Math.max(0, contentH() - height());
        }

        private void debugSetScroll(float scroll) {
            scrollY = clamp(scroll, 0, maxScroll());
            targetScrollY = scrollY;
        }

        private float debugScroll() {
            return scrollY;
        }

        private static float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
