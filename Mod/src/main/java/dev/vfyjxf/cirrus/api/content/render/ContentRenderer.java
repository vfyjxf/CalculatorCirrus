package dev.vfyjxf.cirrus.api.content.render;

import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import net.minecraft.client.gui.GuiGraphics;

public interface ContentRenderer<T> {

    void render(GuiGraphics graphics, T content, int width, int height, float delta);

    default void render(GuiGraphics graphics, ContentKind<T> content, int width, int height, float delta) {
        render(graphics, content.value(), width, height, delta);
    }

}
