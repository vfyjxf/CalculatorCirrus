package dev.vfyjxf.calculatorcirrus.api.content;

import dev.vfyjxf.calculatorcirrus.api.ui.Tooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public interface ContentRenderer<T> {

    default void render(ContentStack<T> content, GuiGraphics graphics, int width, int height, int mouseX, int mouseY, float delta) {
        render(content.value(), graphics, width, height, mouseX, mouseY, delta);
    }

    void render(T content, GuiGraphics graphics, int width, int height, int mouseX, int mouseY, float delta);

    void render(T content, GuiGraphics graphics, int mouseX, int mouseY, float delta);

    void decorate(ContentStack<T> content, GuiGraphics graphics, int width, int height, int mouseX, int mouseY);

    @Nullable
    Tooltip getTooltip(T content, TooltipContext context);

    Component getName(T content);

}
