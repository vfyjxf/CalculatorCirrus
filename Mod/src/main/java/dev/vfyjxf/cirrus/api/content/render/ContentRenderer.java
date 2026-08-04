package dev.vfyjxf.cirrus.api.content.render;

import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.content.ClientContentApiImpl;
import net.minecraft.client.gui.GuiGraphics;

public interface ContentRenderer<T> {

    static <T> ContentRenderer<T> getContentRenderer(ContentType<T> contentType) {
        return ClientContentApiImpl.instance().getContentRenderer(contentType);
    }

    static <T> ContentRenderer<T> getContentRenderer(ContentKind<T> content) {
        return getContentRenderer(content.type());
    }

    void render(GuiGraphics graphics, T content, int width, int height, float delta);

    default void render(GuiGraphics graphics, ContentKind<T> content, int width, int height, float delta) {
        render(graphics, content.value(), width, height, delta);
    }

}
