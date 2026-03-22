package dev.vfyjxf.cirrus.ui.widget.content;

import dev.vfyjxf.cirrus.api.content.render.ContentRenderer;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;

public class ContentWidget<T> extends Widget {

    private final ContentKind<T> content;
    private final ContentRenderer<T> renderer;

    public ContentWidget(ContentKind<T> content, ContentRenderer<T> renderer) {
        this.content = content;
        this.renderer = renderer;
    }

    public ContentKind<T> content() {
        return content;
    }

    public ContentRenderer<T> renderer() {
        return renderer;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        renderer.render(canvas.graphics(), content, width(), height(), partialTicks);
    }
}
