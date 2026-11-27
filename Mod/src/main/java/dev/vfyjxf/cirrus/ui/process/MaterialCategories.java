package dev.vfyjxf.cirrus.ui.process;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MaterialCategories {

    private static final Map<Namespace, MaterialCategory> byId = new LinkedHashMap<>();

    public static final Namespace itemId = CirrusNamespaces.ofInternal("item");
    public static final Namespace fluidId = CirrusNamespaces.ofInternal("fluid");
    public static final Namespace valueId = CirrusNamespaces.ofInternal("value");
    public static final Namespace catalystId = CirrusNamespaces.ofInternal("catalyst");
    public static final Namespace unknownId = CirrusNamespaces.ofInternal("unknown");

    public static final MaterialCategory item = register(itemId, Component.literal("Item"), 0xFFA8BAD8);
    public static final MaterialCategory fluid = register(fluidId, Component.literal("Fluid"), 0xFF8EC3DC);
    public static final MaterialCategory value = register(valueId, Component.literal("Value"), 0xFFB4A3E8);
    public static final MaterialCategory catalyst = register(catalystId, Component.literal("Catalyst"), 0xFFC1B3E8);
    public static final MaterialCategory unknown = register(unknownId, Component.literal("Unknown"), 0xFF9EA7AF);

    private MaterialCategories() {}

    public static synchronized MaterialCategory register(Namespace id, Component label, int color) {
        MaterialCategory category = new MaterialCategory(id, label, color);
        byId.put(category.id(), category);
        return category;
    }

    public static synchronized MaterialCategory get(Namespace id) {
        return byId.getOrDefault(id, unknown);
    }

    public static synchronized Collection<MaterialCategory> values() {
        return List.copyOf(byId.values());
    }

    public static MaterialCategory infer(RecipeContent content, ContentStack<?, ?> visualStack, boolean isCatalyst) {
        if (isCatalyst) {
            return catalyst;
        }
        ContentStack<?, ?> stack = visualStack != null && visualStack.notEmpty() ? visualStack : stackFrom(content);
        if (stack.notEmpty()) {
            return stack.isFluid() ? fluid : item;
        }
        return content != null && content.notEmpty() ? value : unknown;
    }

    private static ContentStack<?, ?> stackFrom(RecipeContent content) {
        if (content instanceof StackContent<?, ?> stackContent) {
            ContentStack<?, ?> stack = stackContent.content();
            return stack == null ? ContentStack.empty() : stack;
        }
        return ContentStack.empty();
    }
}
