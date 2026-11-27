package dev.vfyjxf.cirrus.api.content.render;

import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface BatchableContentRenderer<T> extends ContentRenderer<T> {

    default void renderAt(GuiGraphics graphics, T content, int x, int y, int width, int height, float delta) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        render(graphics, content, width, height, delta);
        graphics.pose().popPose();
    }

    default void renderAt(
        GuiGraphics graphics,
        ContentKind<T> content,
        int x,
        int y,
        int width,
        int height,
        float delta
    ) {
        renderAt(graphics, content.value(), x, y, width, height, delta);
    }

    default void renderBatch(GuiGraphics graphics, List<Entry<T>> entries, float delta) {
        for (Entry<T> entry : entries) {
            renderAt(graphics, entry.content(), entry.x(), entry.y(), entry.width(), entry.height(), delta);
        }
    }

    record Entry<T>(ContentKind<T> content, int x, int y, int width, int height) {}

    final class Batch {
        private final GuiGraphics graphics;
        private final float delta;
        private final Map<ContentRenderer<?>, List<Entry<?>>> entries = new LinkedHashMap<>();

        private Batch(GuiGraphics graphics, float delta) {
            this.graphics = graphics;
            this.delta = delta;
        }

        public static Batch create(GuiGraphics graphics, float delta) {
            return new Batch(graphics, delta);
        }

        public void add(ContentKind<?> content, int x, int y, int width, int height) {
            if (content == null || content.isEmpty() || width <= 0 || height <= 0) {
                return;
            }
            ContentRenderer<?> renderer = ContentRenderer.getContentRenderer(content);
            entries.computeIfAbsent(renderer, ignored -> new ArrayList<>())
                    .add(new Entry<>(content, x, y, width, height));
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }

        public void flush() {
            if (entries.isEmpty()) {
                return;
            }
            for (Map.Entry<ContentRenderer<?>, List<Entry<?>>> entry : entries.entrySet()) {
                render(entry.getKey(), entry.getValue());
            }
            entries.clear();
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void render(ContentRenderer renderer, List<Entry<?>> batch) {
            if (renderer instanceof BatchableContentRenderer batchable) {
                batchable.renderBatch(graphics, (List) batch, delta);
                return;
            }
            for (Entry entry : batch) {
                graphics.pose().pushPose();
                graphics.pose().translate(entry.x(), entry.y(), 0);
                renderer.render(graphics, entry.content(), entry.width(), entry.height(), delta);
                graphics.pose().popPose();
            }
        }
    }
}
