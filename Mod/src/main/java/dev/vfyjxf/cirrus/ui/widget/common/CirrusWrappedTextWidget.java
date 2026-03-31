package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class CirrusWrappedTextWidget extends Widget {
    private Component text;
    private int color;
    private boolean shadow;
    private int padding;
    private int lineGap;
    private int maxLines = 2;

    public CirrusWrappedTextWidget(Component text, int color) {
        this.text = text == null ? Component.empty() : text;
        this.color = color;
    }

    public CirrusWrappedTextWidget(LangEntry text, int color, Object... args) {
        this(text == null ? Component.empty() : text.component(args), color);
    }

    public CirrusWrappedTextWidget text(Component text) {
        this.text = text == null ? Component.empty() : text;
        return this;
    }

    public Component text() {
        return text;
    }

    public CirrusWrappedTextWidget color(int color) {
        this.color = color;
        return this;
    }

    public CirrusWrappedTextWidget shadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    public CirrusWrappedTextWidget padding(int padding) {
        this.padding = Math.max(0, padding);
        return this;
    }

    public int padding() {
        return padding;
    }

    public CirrusWrappedTextWidget lineGap(int lineGap) {
        this.lineGap = Math.max(0, lineGap);
        return this;
    }

    public int lineGap() {
        return lineGap;
    }

    public CirrusWrappedTextWidget maxLines(int maxLines) {
        this.maxLines = Math.max(1, maxLines);
        return this;
    }

    public int maxLines() {
        return maxLines;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        var font = context().font();
        int textWidth = Math.max(1, width() - padding * 2);
        var lines = font.split(text, textWidth);
        if (lines.isEmpty()) {
            return;
        }
        int count = Math.min(lines.size(), availableLineCount());
        int y = padding;
        for (int i = 0; i < count; i++) {
            canvas.text(lines.get(i), padding, y, color, shadow);
            y += font.lineHeight + lineGap;
        }
    }

    @Override
    public @Nullable Tooltip hoverTooltip(int mouseX, int mouseY) {
        if (text.getString().isBlank()) {
            return super.hoverTooltip(mouseX, mouseY);
        }
        var font = context().font();
        int textWidth = Math.max(1, width() - padding * 2);
        if (font.split(text, textWidth).size() <= availableLineCount()) {
            return super.hoverTooltip(mouseX, mouseY);
        }
        Tooltip tooltip = Tooltip.create();
        tooltip.add(text);
        return tooltip;
    }

    private int availableLineCount() {
        var font = context().font();
        int contentHeight = Math.max(0, height() - padding * 2);
        int lineStep = Math.max(1, font.lineHeight + lineGap);
        int byHeight = contentHeight <= 0 ? 0 : (contentHeight + lineGap) / lineStep;
        return Math.max(1, Math.min(maxLines, byHeight));
    }
}
