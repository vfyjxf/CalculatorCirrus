package dev.vfyjxf.cirrus.api.recipe.routing.heuristic;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Read-only context used by routing heuristic matchers.
 */
public record RecipeHeuristicContext(
    RecipeEntry<?> recipe,
    @Nullable ResourceLocation recipeId,
    Namespace systemId,
    MachineEntry machineEntry,
    ImmutableList<InputContent> mergedInputs,
    ImmutableList<OutputContent> mergedOutputs
) {
    public RecipeHeuristicContext {
        Checks.checkNotNull(recipe, "recipe");
        Checks.checkNotNull(systemId, "systemId");
        Checks.checkNotNull(machineEntry, "machineEntry");
        Checks.checkNotNull(mergedInputs, "mergedInputs");
        Checks.checkNotNull(mergedOutputs, "mergedOutputs");
    }

    public boolean recipeIdContains(String fragment) {
        return recipeId != null && containsIgnoreCase(recipeId.toString(), fragment);
    }

    public boolean recipePathContains(String fragment) {
        return recipeId != null && containsIgnoreCase(recipeId.getPath(), fragment);
    }

    public boolean system(String id) {
        return systemId.toString().equals(id);
    }

    public boolean machine(String id) {
        if (id == null || id.isBlank() || machineEntry.isEmpty()) {
            return false;
        }
        ItemStack stack = machineEntry.toItemStack();
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null && itemId.toString().equals(id);
    }

    public boolean input(String id) {
        for (InputContent input : mergedInputs) {
            if (contentResourceId(input).equals(id)) {
                return true;
            }
        }
        return false;
    }

    public boolean output(String id) {
        for (OutputContent output : mergedOutputs) {
            if (contentResourceId(output).equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static String contentResourceId(Object content) {
        if (content instanceof StackContent<?, ?> stack) {
            return stack.content().normalize().toString();
        }
        if (content instanceof ContentResource resource) {
            return resource.normalize().toString();
        }
        return "";
    }

    private static boolean containsIgnoreCase(String value, String fragment) {
        return fragment != null
            && !fragment.isBlank()
            && value.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }
}
