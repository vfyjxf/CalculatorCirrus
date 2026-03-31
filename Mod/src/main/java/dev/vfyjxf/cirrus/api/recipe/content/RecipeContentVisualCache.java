package dev.vfyjxf.cirrus.api.recipe.content;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.content.complex.FluidContainerContent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecipeContentVisualCache {
    private static final int defaultCapacity = 768;

    private final int capacity;
    private final IdentityHashMap<RecipeContent, RecipeContentVisual> identityVisuals = new IdentityHashMap<>();
    private final LinkedHashMap<VisualKey, RecipeContentVisual> keyedVisuals = new LinkedHashMap<>(16, 0.75f, true);
    private final LinkedHashMap<IdentityKey, RecipeContent> accessOrder = new LinkedHashMap<>(16, 0.75f, true);

    private RecipeContentVisualCache(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public static RecipeContentVisualCache create() {
        return new RecipeContentVisualCache(defaultCapacity);
    }

    public static RecipeContentVisualCache create(int capacity) {
        return new RecipeContentVisualCache(capacity);
    }

    public RecipeContentVisual visual(RecipeContent content) {
        if (content.isEmpty()) {
            return RecipeContentVisual.empty();
        }
        RecipeContentVisual visual = identityVisuals.get(content);
        if (visual != null) {
            touch(content);
            return visual;
        }
        VisualKey key = simpleVisualKey(content);
        if (key != null) {
            visual = keyedVisuals.get(key);
            if (visual != null) {
                identityVisuals.put(content, visual);
                touch(content);
                evictOverflow();
                return visual;
            }
        }
        ArrayList<ContentStack<?, ?>> variants = collectVisualStacks(content);
        if (key == null) {
            key = visualKey(variants);
        }
        visual = keyedVisuals.get(key);
        if (visual != null) {
            identityVisuals.put(content, visual);
            touch(content);
            evictOverflow();
            return visual;
        }
        visual = RecipeContentVisual.of(variants);
        identityVisuals.put(content, visual);
        keyedVisuals.put(key, visual);
        touch(content);
        evictOverflow();
        return visual;
    }

    public List<RecipeContentVisual> visuals(List<? extends RecipeContent> contents) {
        return visuals(contents, Integer.MAX_VALUE);
    }

    public List<RecipeContentVisual> visuals(List<? extends RecipeContent> contents, int limit) {
        if (contents.isEmpty()) {
            return List.of();
        }
        int visible = Math.min(contents.size(), Math.max(0, limit));
        if (visible <= 0) {
            return List.of();
        }
        ArrayList<RecipeContentVisual> out = new ArrayList<>(visible);
        for (int i = 0; i < visible; i++) {
            RecipeContent content = contents.get(i);
            RecipeContentVisual visual = visual(content);
            out.add(visual);
        }
        return List.copyOf(out);
    }

    public void clear() {
        identityVisuals.clear();
        keyedVisuals.clear();
        accessOrder.clear();
    }

    private void touch(RecipeContent content) {
        accessOrder.put(new IdentityKey(content), content);
    }

    private void evictOverflow() {
        Iterator<Map.Entry<IdentityKey, RecipeContent>> iterator = accessOrder.entrySet().iterator();
        while (identityVisuals.size() > capacity && iterator.hasNext()) {
            RecipeContent eldest = iterator.next().getValue();
            iterator.remove();
            identityVisuals.remove(eldest);
        }
        Iterator<VisualKey> keyed = keyedVisuals.keySet().iterator();
        while (keyedVisuals.size() > capacity && keyed.hasNext()) {
            keyed.next();
            keyed.remove();
        }
    }

    private static ArrayList<ContentStack<?, ?>> collectVisualStacks(RecipeContent content) {
        ArrayList<ContentStack<?, ?>> variants = new ArrayList<>();
        collectVisualStacks(content, variants);
        return variants;
    }

    private static void collectVisualStacks(RecipeContent content, ArrayList<ContentStack<?, ?>> out) {
        if (content.isEmpty()) {
            return;
        }
        switch (content) {
            case StackContent<?, ?> stackContent -> addDecoratedStack(out, stackContent.content(), content.amount());
            case StackListContent stackList -> addStacks(out, stackList.stacks(), content.amount());
            case TypedStackListContent<?, ?> typedList -> addStacks(out, typedList.stacks(), content.amount());
            case TagStackContent<?> tagStack -> collectEnumerable(tagStack, out, content.amount());
            case FluidContainerContent fluidContainer -> addDecoratedStack(out, fluidContainer.sampleContainer(), content.amount());
            case ComplexContent complex -> collectEnumerable(complex, out, content.amount());
            default -> collectEnumerable(content, out, content.amount());
        }
    }

    private static void collectEnumerable(RecipeContent content, ArrayList<ContentStack<?, ?>> out, long amount) {
        if (!(content instanceof InputContent input) || !input.isEnumerable()) {
            return;
        }
        for (ContentVariant variant : input.enumerate()) {
            switch (variant) {
                case ContentStack<?, ?> stack -> addDecoratedStack(out, stack, amount);
                case ContentKind<?> kind -> addDecoratedStack(out, stackFromKind(kind), amount);
                default -> {
                }
            }
        }
    }

    private static void addStacks(ArrayList<ContentStack<?, ?>> out, Iterable<? extends ContentStack<?, ?>> stacks, long amount) {
        for (ContentStack<?, ?> stack : stacks) {
            addDecoratedStack(out, stack, amount);
        }
    }

    private static void addDecoratedStack(ArrayList<ContentStack<?, ?>> out, ContentStack<?, ?> stack, long amount) {
        if (stack.isEmpty()) {
            return;
        }
        ContentStack<?, ?> visual = stack.normalize().copyWithAmount(Math.max(1L, amount));
        if (visual.notEmpty()) {
            out.add(visual);
        }
    }

    private static ContentStack<?, ?> stackFromKind(ContentKind<?> kind) {
        if (kind == ContentKind.empty()) {
            return ContentStack.empty();
        }
        if (kind.type() == CirrusTypes.Contents.ITEM) {
            return ContentStack.fromContent(kind.<Item>cast().value());
        }
        if (kind.type() == CirrusTypes.Contents.FLUID) {
            return ContentStack.fromContent(kind.<Fluid>cast().value());
        }
        return ContentStack.empty();
    }

    private static @Nullable VisualKey simpleVisualKey(RecipeContent content) {
        if (content.isEmpty()) {
            return new VisualKey(List.of());
        }
        ArrayList<StackVisualKey> keys = new ArrayList<>();
        switch (content) {
            case StackContent<?, ?> stackContent -> addStackKey(keys, stackContent.content(), content.amount());
            case StackListContent stackList -> addStackKeys(keys, stackList.stacks(), content.amount());
            case TypedStackListContent<?, ?> typedList -> addStackKeys(keys, typedList.stacks(), content.amount());
            case FluidContainerContent fluidContainer -> addStackKey(keys, fluidContainer.sampleContainer(), content.amount());
            default -> {
                return null;
            }
        }
        return new VisualKey(keys);
    }

    private static void addStackKeys(ArrayList<StackVisualKey> out, Iterable<? extends ContentStack<?, ?>> stacks, long amount) {
        for (ContentStack<?, ?> stack : stacks) {
            addStackKey(out, stack, amount);
        }
    }

    private static void addStackKey(ArrayList<StackVisualKey> out, ContentStack<?, ?> stack, long amount) {
        if (stack.isEmpty()) {
            return;
        }
        out.add(StackVisualKey.of(stack, Math.max(1L, amount)));
    }

    private static VisualKey visualKey(List<ContentStack<?, ?>> variants) {
        ArrayList<StackVisualKey> keys = new ArrayList<>(variants.size());
        for (ContentStack<?, ?> variant : variants) {
            keys.add(StackVisualKey.of(variant));
        }
        return new VisualKey(keys);
    }

    private record IdentityKey(RecipeContent content, int hash) {
        private IdentityKey(RecipeContent content) {
            this(content, System.identityHashCode(content));
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof IdentityKey other && content == other.content;
        }
    }

    private record VisualKey(List<StackVisualKey> variants) {
        private VisualKey(List<StackVisualKey> variants) {
            this.variants = List.copyOf(variants);
        }
    }

    private record StackVisualKey(Object type, Object content, long amount, Object components) {
        private static StackVisualKey of(ContentStack<?, ?> stack) {
            return of(stack, stack.amount());
        }

        private static StackVisualKey of(ContentStack<?, ?> stack, long amount) {
            return new StackVisualKey(
                stack.type(),
                stack.content(),
                amount,
                stack.componentsPatch()
            );
        }
    }
}
