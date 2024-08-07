package dev.vfyjxf.calculatorcirrus.api.content;

import dev.vfyjxf.calculatorcirrus.api.ui.ITooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipContext;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

public interface IContentRenderer<T> {

    default void render(IContentStack<T> content, GuiGraphics graphics, int width, int height, int mouseX, int mouseY, float delta) {
        render(content.value(), graphics, width, height, mouseX, mouseY, delta);
    }

    void render(T content, GuiGraphics graphics, int width, int height, int mouseX, int mouseY, float delta);

    void render(T content, GuiGraphics graphics, int mouseX, int mouseY, float delta);

    @Nullable
    ITooltip getTooltip(T content, TooltipContext context);

}
