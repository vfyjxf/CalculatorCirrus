package dev.vfyjxf.cirrus.ui.execution.widget;

import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import net.minecraft.network.chat.Component;

public class ExecutionWrappedTextWidget extends Widget {
    //region state

    private Component text;
    private int color = 0xFFFFFFFF;
    private boolean shadow;
    private int padding = 0;
    private int lineGap = 0;

    //endregion

    public ExecutionWrappedTextWidget(Component text) {
        this.text = text;
    }

    //region configuration

    public void setText(Component text) {
        this.text = text;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setShadow(boolean shadow) {
        this.shadow = shadow;
    }

    public void setPadding(int padding) {
        this.padding = Math.max(0, padding);
    }

    public void setLineGap(int lineGap) {
        this.lineGap = Math.max(0, lineGap);
    }

    //endregion

    //region render

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        var font = context().font();
        int textWidth = Math.max(1, width() - padding * 2);
        var lines = font.split(text, textWidth);
        if (lines.isEmpty()) {
            return;
        }

        int totalHeight = lines.size() * font.lineHeight + Math.max(0, lines.size() - 1) * lineGap;
        int textY = Math.max(padding, (height() - totalHeight) / 2);

        for (var line : lines) {
            int textX = Math.max(padding, (width() - font.width(line)) / 2);
            canvas.text(line, textX, textY, color, shadow);
            textY += font.lineHeight + lineGap;
        }
    }

    //endregion
}
