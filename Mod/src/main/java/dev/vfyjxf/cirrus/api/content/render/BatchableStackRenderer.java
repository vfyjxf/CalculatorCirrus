package dev.vfyjxf.cirrus.api.content.render;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface BatchableStackRenderer<T, S> extends StackRenderer<T, S> {

    default @Nullable Object createBatchContext() {
        return null;
    }

    default void renderBatch(GuiGraphics graphics, List<Entry<T, S>> entries, float delta) {
        renderBaseBatch(graphics, entries, delta);
        decorateBatch(graphics, entries, delta);
    }

    default void renderBatch(GuiGraphics graphics, List<Entry<T, S>> entries, float delta, @Nullable Object context) {
        renderBaseBatch(graphics, entries, delta, context);
        decorateBatch(graphics, entries, delta, context);
    }

    default void renderBaseBatch(GuiGraphics graphics, List<Entry<T, S>> entries, float delta) {
        for (Entry<T, S> entry : entries) {
            renderBaseAt(graphics, entry.stack(), entry.x(), entry.y(), entry.width(), entry.height(), delta);
        }
    }

    default void renderBaseBatch(GuiGraphics graphics, List<Entry<T, S>> entries, float delta, @Nullable Object context) {
        renderBaseBatch(graphics, entries, delta);
    }

    default void decorateBatch(GuiGraphics graphics, List<Entry<T, S>> entries, float delta) {
        for (Entry<T, S> entry : entries) {
            decorateAt(graphics, entry.stack(), entry.x(), entry.y(), entry.width(), entry.height(), delta);
        }
    }

    default void decorateBatch(GuiGraphics graphics, List<Entry<T, S>> entries, float delta, @Nullable Object context) {
        decorateBatch(graphics, entries, delta);
    }

    record Entry<T, S>(
        ContentStack<T, S> stack,
        int x,
        int y,
        int width,
        int height
    ) {
    }

    final class Batch {
        private final GuiGraphics graphics;
        private final float delta;
        private final @Nullable Object contextToken;
        private final Map<StackRenderer<?, ?>, RendererState> states = new LinkedHashMap<>();

        private Batch(GuiGraphics graphics, float delta) {
            this(graphics, delta, null);
        }

        private Batch(GuiGraphics graphics, float delta, @Nullable Object contextToken) {
            this.graphics = graphics;
            this.delta = delta;
            this.contextToken = contextToken;
        }

        public static Batch create(GuiGraphics graphics, float delta) {
            return new Batch(graphics, delta);
        }

        public static Batch create(GuiGraphics graphics, float delta, @Nullable Object contextToken) {
            return new Batch(graphics, delta, contextToken);
        }

        public void add(ContentStack<?, ?> stack, int x, int y, int width, int height) {
            add(stack, x, y, width, height, false);
        }

        public void addBase(ContentStack<?, ?> stack, int x, int y, int width, int height) {
            add(stack, x, y, width, height, true);
        }

        private void add(ContentStack<?, ?> stack, int x, int y, int width, int height, boolean baseOnly) {
            if (stack == null || stack.isEmpty() || width <= 0 || height <= 0) {
                return;
            }
            StackRenderer<?, ?> renderer = StackRenderer.getStackRenderer(stack);
            RendererState state = states.computeIfAbsent(renderer, this::createState);
            state.add(stack, x, y, width, height, baseOnly);
        }

        public boolean isEmpty() {
            return states.isEmpty();
        }

        public void flush() {
            flushBase();
            flushFull();
        }

        public @Nullable Object contextToken() {
            return contextToken;
        }

        public void flushBase() {
            flush(true);
        }

        public void flushFull() {
            flush(false);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void flush(boolean baseOnly) {
            if (states.isEmpty()) {
                return;
            }
            var iterator = states.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<StackRenderer<?, ?>, RendererState> entry = iterator.next();
                StackRenderer renderer = entry.getKey();
                RendererState state = entry.getValue();
                List<Entry<?, ?>> batch = baseOnly ? state.baseEntries : state.fullEntries;
                if (!batch.isEmpty()) {
                    Object context = state.context;
                    if (renderer instanceof BatchableStackRenderer<?, ?> batchable) {
                        if (baseOnly) {
                            batchable.renderBaseBatch(graphics, (List) batch, delta, context);
                        } else {
                            batchable.renderBatch(graphics, (List) batch, delta, context);
                        }
                    } else {
                        renderFallback(renderer, batch, baseOnly);
                    }
                    batch.clear();
                }
                state.markFlushed(baseOnly);
                if (state.canRemove()) {
                    iterator.remove();
                }
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void renderFallback(StackRenderer renderer, List<Entry<?, ?>> entries, boolean baseOnly) {
            for (Entry entry : entries) {
                if (baseOnly) {
                    renderer.renderBaseAt(graphics, entry.stack(), entry.x(), entry.y(), entry.width(), entry.height(), delta);
                } else {
                    renderer.renderAt(graphics, entry.stack(), entry.x(), entry.y(), entry.width(), entry.height(), delta);
                }
            }
        }

        private RendererState createState(StackRenderer<?, ?> renderer) {
            Object context = renderer instanceof BatchableStackRenderer<?, ?> batchable ? batchable.createBatchContext() : null;
            return new RendererState(context);
        }

        private static final class RendererState {
            private final @Nullable Object context;
            private final List<Entry<?, ?>> baseEntries = new ArrayList<>();
            private final List<Entry<?, ?>> fullEntries = new ArrayList<>();
            private boolean baseFlushed;
            private boolean fullFlushed;

            private RendererState(@Nullable Object context) {
                this.context = context;
            }

            private void add(ContentStack<?, ?> stack, int x, int y, int width, int height, boolean baseOnly) {
                if (baseOnly) {
                    baseFlushed = false;
                    baseEntries.add(new Entry<>(stack, x, y, width, height));
                } else {
                    fullFlushed = false;
                    fullEntries.add(new Entry<>(stack, x, y, width, height));
                }
            }

            private boolean isEmpty() {
                return baseEntries.isEmpty() && fullEntries.isEmpty();
            }

            private void markFlushed(boolean baseOnly) {
                if (baseOnly) {
                    baseFlushed = true;
                } else {
                    fullFlushed = true;
                }
            }

            private boolean canRemove() {
                return baseFlushed && fullFlushed && isEmpty();
            }
        }
    }
}
