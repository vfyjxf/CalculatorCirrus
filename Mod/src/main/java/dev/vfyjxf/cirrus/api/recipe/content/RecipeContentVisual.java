package dev.vfyjxf.cirrus.api.recipe.content;

import dev.vfyjxf.cirrus.api.content.render.BatchableStackRenderer;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cloudlib.util.Checks;

import java.util.List;

public final class RecipeContentVisual {
    private static final RecipeContentVisual empty = new RecipeContentVisual(List.of());

    private final List<ContentStack<?, ?>> variants;

    private RecipeContentVisual(List<ContentStack<?, ?>> variants) {
        this.variants = variants;
    }

    public static RecipeContentVisual empty() {
        return empty;
    }

    public static RecipeContentVisual of(List<? extends ContentStack<?, ?>> variants) {
        Checks.checkNotNull(variants, "variants");
        if (variants.isEmpty()) {
            return empty;
        }
        return new RecipeContentVisual(List.copyOf(variants));
    }

    public boolean isEmpty() {
        return variants.isEmpty();
    }

    public boolean notEmpty() {
        return !isEmpty();
    }

    public int variantCount() {
        return variants.size();
    }

    public ContentStack<?, ?> stack(int visualIndex) {
        if (variants.isEmpty()) {
            return ContentStack.empty();
        }
        int index = Math.max(0, Math.min(visualIndex, variants.size() - 1));
        ContentStack<?, ?> stack = variants.get(index);
        return stack == null ? ContentStack.empty() : stack;
    }

    public ContentStack<?, ?> currentStack() {
        return currentStack(System.currentTimeMillis());
    }

    public ContentStack<?, ?> currentStack(long nowMillis) {
        if (variants.isEmpty()) {
            return ContentStack.empty();
        }
        if (variants.size() == 1) {
            return variants.getFirst();
        }
        return variants.get(currentVisualIndex(nowMillis));
    }

    public int currentVisualIndex() {
        return currentVisualIndex(System.currentTimeMillis());
    }

    public int currentVisualIndex(long nowMillis) {
        if (variants.size() <= 1) {
            return 0;
        }
        return (int) ((nowMillis / 1000L) % variants.size());
    }

    public void submit(BatchableStackRenderer.Batch batch, int x, int y, int width, int height) {
        batch.add(currentStack(), x, y, width, height);
    }

    public void submitBase(BatchableStackRenderer.Batch batch, int x, int y, int width, int height) {
        batch.addBase(currentStack(), x, y, width, height);
    }
}
