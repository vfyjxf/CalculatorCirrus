package dev.vfyjxf.cirrus.ui.widget.calculator.page;

import dev.vfyjxf.cirrus.api.recipe.search.CirrusSearchFields;
import dev.vfyjxf.cloudlib.api.search.SearchDocument;
import dev.vfyjxf.cloudlib.api.search.SearchExpression;
import dev.vfyjxf.cloudlib.api.search.SearchField;
import dev.vfyjxf.cloudlib.api.search.SearchMatcher;
import dev.vfyjxf.cirrus.internal.integration.jei.CirrusJeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

final class RuleIngredientCatalog {
    private RuleIngredientCatalog() {
    }

    static List<Entry> itemEntries() {
        LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
        IIngredientManager manager = CirrusJeiPlugin.ingredientManagerOrNull();
        if (manager != null) {
            Collection<ItemStack> allItemStacks = allItemStacksOrNull(manager);
            var helper = itemStackHelperOrNull(manager);
            if (allItemStacks != null) for (ItemStack stack : allItemStacks) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id == null || id.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                    continue;
                }
                String material = id.toString();
                entries.computeIfAbsent(material, ignored -> Entry.of(
                    material,
                    displayName(stack, helper),
                    stack.copyWithCount(1)
                ));
            }
        }
        if (entries.isEmpty()) {
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (id == null || id.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                    continue;
                }
                ItemStack stack = new ItemStack(item);
                entries.put(id.toString(), Entry.of(id.toString(), stack.getHoverName().getString(), stack));
            }
        }
        ArrayList<Entry> sorted = new ArrayList<>(entries.values());
        sorted.sort(Comparator
            .comparing((Entry entry) -> entry.label().toLowerCase(Locale.ROOT))
            .thenComparing(Entry::material));
        return List.copyOf(sorted);
    }

    private static Collection<ItemStack> allItemStacksOrNull(IIngredientManager manager) {
        try {
            return manager.getAllItemStacks();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static @org.jetbrains.annotations.Nullable mezz.jei.api.ingredients.IIngredientHelper<ItemStack> itemStackHelperOrNull(
        IIngredientManager manager
    ) {
        try {
            return manager.getIngredientHelper(VanillaTypes.ITEM_STACK);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static String displayName(
        ItemStack stack,
        @org.jetbrains.annotations.Nullable mezz.jei.api.ingredients.IIngredientHelper<ItemStack> helper
    ) {
        if (helper != null) {
            try {
                String name = helper.getDisplayName(stack);
                if (name != null && !name.isBlank()) {
                    return name;
                }
            } catch (RuntimeException | LinkageError ignored) {
                // JEI can be partially mocked in UI contract tests.
            }
        }
        return stack.getHoverName().getString();
    }

    record Entry(String material, String label, ItemStack stack, List<String> tags, SearchDocument document) {
        Entry {
            stack = normalizedStack(stack);
            tags = tags == null ? List.of() : List.copyOf(tags);
            document = document == null ? searchDocument(material, label, tags) : document;
        }

        static Entry of(String material, String label, ItemStack stack) {
            List<String> tags = tags(stack);
            return new Entry(material, label, stack, tags, searchDocument(material, label, tags));
        }

        boolean matches(String query) {
            if (query == null || query.isBlank()) {
                return true;
            }
            String clean = query.toLowerCase(Locale.ROOT);
            if (material.toLowerCase(Locale.ROOT).contains(clean)
                || label.toLowerCase(Locale.ROOT).contains(clean)) {
                return true;
            }
            for (String tag : tags) {
                if (tag.toLowerCase(Locale.ROOT).contains(clean)) {
                    return true;
                }
            }
            return false;
        }

        boolean matches(SearchExpression expression) {
            return SearchMatcher.matches(expression, document);
        }

        String namespace() {
            int separator = material.indexOf(':');
            return separator <= 0 ? "" : material.substring(0, separator);
        }

        private static SearchDocument searchDocument(String material, String label, List<String> tags) {
            SearchDocument.Builder builder = SearchDocument.builder(CirrusSearchFields.SET, CirrusSearchFields.text)
                .searchableText(CirrusSearchFields.id, material)
                .searchableText(CirrusSearchFields.name, label)
                .searchableText(CirrusSearchFields.text, material)
                .searchableText(CirrusSearchFields.text, label);
            int separator = material == null ? -1 : material.indexOf(':');
            if (separator > 0) {
                builder.searchableText(CirrusSearchFields.mod, material.substring(0, separator));
                builder.searchableText(CirrusSearchFields.text, material.substring(separator + 1));
            }
            for (String tag : tags) {
                builder.searchableText(CirrusSearchFields.tag, tag);
            }
            return builder.build();
        }

        private static List<String> tags(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return List.of();
            }
            return stack.getItem()
                .builtInRegistryHolder()
                .tags()
                .map(tag -> tag.location().toString())
                .sorted()
                .toList();
        }

        private static ItemStack normalizedStack(ItemStack stack) {
            return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        }
    }
}
