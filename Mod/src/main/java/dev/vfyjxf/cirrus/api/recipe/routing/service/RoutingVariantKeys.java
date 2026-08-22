package dev.vfyjxf.cirrus.api.recipe.routing.service;

import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.resources.ResourceLocation;
import com.google.common.collect.MapMaker;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentMap;

/**
 * Stable, amount-insensitive keys used to carry a user-selected input variant
 * from UI state into routing.
 */
public final class RoutingVariantKeys {

    private RoutingVariantKeys() {
    }

    public static String inputKey(InputContent input) {
        Checks.checkNotNull(input, "input");
        return switch (input) {
            case TagStackContent<?> tag -> "tag:" + tag.type().typeId() + ":" + tag.tagKey().location();
            case StackContent<?, ?> stack -> "stack:" + variantKey(stack.content());
            case TypedStackListContent<?, ?> list -> "typed_stack_list:" + list.type().typeId() + ":" + variantListKey(list.stacks());
            case StackListContent list -> "stack_list:" + variantListKey(list.stacks());
            case ValueContent<?> value -> "value:" + value.type().typeId();
            default -> input.isEnumerable()
                ? "variants:" + variantListKey(input.enumerate())
                : input.getClass().getName();
        };
    }

    public static @Nullable String variantKey(RecipeContent content, int visualIndex) {
        if (!(content instanceof InputContent input) || !input.isEnumerable()) {
            return null;
        }
        int index = 0;
        for (ContentVariant variant : input.enumerate()) {
            if (index == visualIndex) {
                return variantKey(variant);
            }
            index++;
        }
        return null;
    }

    public static String variantKey(ContentVariant variant) {
        Checks.checkNotNull(variant, "variant");
        return switch (variant) {
            case ContentStack<?, ?> stack -> stackKey(stack);
            case ContentKind<?> kind -> kindKey(kind);
            case ContentValue<?> value -> "value:" + value.type().typeId();
            default -> variant.getClass().getName() + ":" + variant;
        };
    }

    private static String variantListKey(Iterable<? extends ContentVariant> variants) {
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (ContentVariant variant : variants) {
            if (index++ > 0) {
                builder.append('|');
            }
            builder.append(variantKey(variant));
        }
        return builder.toString();
    }

    private static final ConcurrentMap<ContentStack<?, ?>, String> STACK_KEY_CACHE =
            new MapMaker().weakKeys().concurrencyLevel(4).makeMap();

    private static String stackKey(ContentStack<?, ?> stack) {
        // The planner computes variant keys in hot loops; hashing the components patch is expensive
        // (deep NBT/component hashes), so cache the built key per stack instance (identity-keyed —
        // lookups never invoke the stack's own hashCode; entries evaporate with the stack).
        // Contract: stacks are read-only after construction, like the canonical component pool.
        String cached = STACK_KEY_CACHE.get(stack);
        if (cached != null) return cached;
        String key = "stack:" + stack.type().typeId() + ":" + contentKey(stack.contentType(), stack.content());
        if (!stack.componentsPatch().isEmpty()) {
            key += ":components:" + Integer.toHexString(stack.componentsPatch().hashCode());
        }
        STACK_KEY_CACHE.put(stack, key);
        return key;
    }

    private static String kindKey(ContentKind<?> kind) {
        return "kind:" + kind.type().typeId() + ":" + contentKey(kind.type(), kind.value());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String contentKey(ContentType type, Object value) {
        ResourceLocation location = type.getLocation(value);
        if (location != null) {
            return location.toString();
        }
        return type.asString(value);
    }
}
