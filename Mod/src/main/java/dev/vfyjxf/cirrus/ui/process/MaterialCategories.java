package dev.vfyjxf.cirrus.ui.process;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MaterialCategories {

    private static final Map<Namespace, MaterialCategory> byId = new LinkedHashMap<>();

    public static final Namespace ITEM_ID = Namespace.of("cirrus", "item");
    public static final Namespace FLUID_ID = Namespace.of("cirrus", "fluid");
    public static final Namespace VALUE_ID = Namespace.of("cirrus", "value");
    public static final Namespace CATALYST_ID = Namespace.of("cirrus", "catalyst");
    public static final Namespace UNKNOWN_ID = Namespace.of("cirrus", "unknown");

    public static final MaterialCategory ITEM = register(ITEM_ID, Component.literal("Item"), 0xFFA8BAD8);
    public static final MaterialCategory FLUID = register(FLUID_ID, Component.literal("Fluid"), 0xFF8EC3DC);
    public static final MaterialCategory VALUE = register(VALUE_ID, Component.literal("Value"), 0xFFB4A3E8);
    public static final MaterialCategory CATALYST = register(CATALYST_ID, Component.literal("Catalyst"), 0xFFC1B3E8);
    public static final MaterialCategory UNKNOWN = register(UNKNOWN_ID, Component.literal("Unknown"), 0xFF9EA7AF);

    private MaterialCategories() {
    }

    public static synchronized MaterialCategory register(Namespace id, Component label, int color) {
        MaterialCategory category = new MaterialCategory(id, label, color);
        byId.put(category.id(), category);
        return category;
    }

    public static synchronized MaterialCategory get(Namespace id) {
        return byId.getOrDefault(id, UNKNOWN);
    }

    public static synchronized Collection<MaterialCategory> values() {
        return List.copyOf(byId.values());
    }

    public static MaterialCategory infer(RecipeContent content, ContentStack<?, ?> visualStack, boolean catalyst) {
        if (catalyst) {
            return CATALYST;
        }
        ContentStack<?, ?> stack = visualStack != null && visualStack.notEmpty() ? visualStack : stackFrom(content);
        if (stack.notEmpty()) {
            return stack.isFluid() ? FLUID : ITEM;
        }
        return content != null && content.notEmpty() ? VALUE : UNKNOWN;
    }

    private static ContentStack<?, ?> stackFrom(RecipeContent content) {
        if (content instanceof StackContent<?, ?> stackContent) {
            ContentStack<?, ?> stack = stackContent.content();
            return stack == null ? ContentStack.empty() : stack;
        }
        return ContentStack.empty();
    }
}
