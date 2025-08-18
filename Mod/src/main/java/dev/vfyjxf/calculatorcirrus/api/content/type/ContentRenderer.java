package dev.vfyjxf.calculatorcirrus.api.content.type;

import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.content.ClientContentApiImpl;
import dev.vfyjxf.calculatorcirrus.util.Singletons;
import dev.vfyjxf.cloudlib.api.ui.text.RichTooltip;
import dev.vfyjxf.cloudlib.api.ui.widget.TooltipContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Type class which defines render related methods for content.
 *
 * @param <T> the type of content this renderer handles
 */
public interface ContentRenderer<T> {

    static <T> ContentRenderer<T> getRenderer(ContentType<T> contentType) {
        return Singletons.get(ClientContentApiImpl.class).getRenderer(contentType);
    }

    static <T> ContentRenderer<T> getRenderer(TagType<?, T> tagType) {
        return getRenderer(tagType.stackType());
    }

    default void render(ContentStack<T> content, GuiGraphics graphics, int width, int height, int mouseX, int mouseY, float delta) {
        render(content.value(), graphics, width, height, mouseX, mouseY, delta);
    }

    void render(T content, GuiGraphics graphics, int width, int height, int mouseX, int mouseY, float delta);

    void render(T content, GuiGraphics graphics, int mouseX, int mouseY, float delta);

    void decorate(ContentStack<T> content, GuiGraphics graphics, int width, int height, int mouseX, int mouseY);

    @Nullable
    RichTooltip getTooltip(T content, TooltipContext context);

    Component getName(T content);

}
