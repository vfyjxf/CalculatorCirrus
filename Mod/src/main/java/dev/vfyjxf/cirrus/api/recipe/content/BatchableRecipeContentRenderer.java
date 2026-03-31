package dev.vfyjxf.cirrus.api.recipe.content;

import dev.vfyjxf.cirrus.api.content.render.BatchableStackRenderer;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import net.minecraft.client.gui.GuiGraphics;

public interface BatchableRecipeContentRenderer<C extends RecipeContent> extends RecipeContentRenderer<C> {

    default void submit(Batch batch, C content, int visualIndex, int x, int y, int width, int height) {
        batch.renderImmediate(this, content, visualIndex, x, y, width, height);
    }

    default void submitDecorated(Batch batch, C content, int visualIndex, int x, int y, int width, int height) {
        batch.renderDecoratedImmediate(this, content, visualIndex, x, y, width, height);
    }

    final class Batch {
        private final GuiGraphics graphics;
        private final float delta;
        private final BatchableStackRenderer.Batch stackBatch;
        private final long nowMillis;

        private Batch(GuiGraphics graphics, float delta) {
            this.graphics = graphics;
            this.delta = delta;
            this.stackBatch = BatchableStackRenderer.Batch.create(graphics, delta);
            this.nowMillis = System.currentTimeMillis();
        }

        public static Batch create(GuiGraphics graphics, float delta) {
            return new Batch(graphics, delta);
        }

        public GuiGraphics graphics() {
            return graphics;
        }

        public float delta() {
            return delta;
        }

        public BatchableStackRenderer.Batch stackBatch() {
            return stackBatch;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public void add(RecipeContent content, int visualIndex, int x, int y, int width, int height) {
            if (content == null || content.isEmpty() || width <= 0 || height <= 0) {
                return;
            }
            RecipeContentRenderer renderer = RecipeContentRenderer.getRenderer(content);
            if (renderer.visualCount(content) <= 0) {
                return;
            }
            if (renderer instanceof BatchableRecipeContentRenderer batchable) {
                batchable.submit(this, content, visualIndex, x, y, width, height);
            } else {
                renderImmediate(renderer, content, visualIndex, x, y, width, height);
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public void addDecorated(RecipeContent content, int visualIndex, int x, int y, int width, int height) {
            if (content == null || content.isEmpty() || width <= 0 || height <= 0) {
                return;
            }
            RecipeContentRenderer renderer = RecipeContentRenderer.getRenderer(content);
            if (renderer.visualCount(content) <= 0) {
                return;
            }
            if (renderer instanceof BatchableRecipeContentRenderer batchable) {
                batchable.submitDecorated(this, content, visualIndex, x, y, width, height);
            } else {
                renderDecoratedImmediate(renderer, content, visualIndex, x, y, width, height);
            }
        }

        public void addBaseStack(ContentStack<?, ?> stack, int x, int y, int width, int height) {
            stackBatch.addBase(stack, x, y, width, height);
        }

        public void addStack(ContentStack<?, ?> stack, int x, int y, int width, int height) {
            stackBatch.add(stack, x, y, width, height);
        }

        public void addVisual(RecipeContentVisual visual, int x, int y, int width, int height) {
            if (visual != null && visual.notEmpty()) {
                stackBatch.add(visual.currentStack(nowMillis), x, y, width, height);
            }
        }

        public void addVisual(RecipeContentVisual visual, int visualIndex, int x, int y, int width, int height) {
            if (visual != null && visual.notEmpty()) {
                stackBatch.add(visual.stack(visualIndex), x, y, width, height);
            }
        }

        public void addBaseVisual(RecipeContentVisual visual, int x, int y, int width, int height) {
            if (visual != null && visual.notEmpty()) {
                stackBatch.addBase(visual.currentStack(nowMillis), x, y, width, height);
            }
        }

        public void addBaseVisual(RecipeContentVisual visual, int visualIndex, int x, int y, int width, int height) {
            if (visual != null && visual.notEmpty()) {
                stackBatch.addBase(visual.stack(visualIndex), x, y, width, height);
            }
        }

        public <T extends RecipeContent> void renderImmediate(
            RecipeContentRenderer<T> renderer,
            T content,
            int visualIndex,
            int x,
            int y,
            int width,
            int height
        ) {
            stackBatch.flush();
            renderer.renderAt(graphics, content, visualIndex, x, y, width, height, delta);
        }

        public <T extends RecipeContent> void renderDecoratedImmediate(
            RecipeContentRenderer<T> renderer,
            T content,
            int visualIndex,
            int x,
            int y,
            int width,
            int height
        ) {
            stackBatch.flush();
            renderer.renderDecoratedAt(graphics, content, visualIndex, x, y, width, height, delta);
        }

        public void flush() {
            stackBatch.flush();
        }
    }
}
