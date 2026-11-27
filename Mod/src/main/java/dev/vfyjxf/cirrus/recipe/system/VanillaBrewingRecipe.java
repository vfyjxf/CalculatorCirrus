package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * Native Cirrus representation of one brewing step discovered directly from Minecraft's
 * {@link net.minecraft.world.item.alchemy.PotionBrewing} (vanilla potion mixes, container mixes,
 * and NeoForge {@link net.neoforged.neoforge.common.brewing.BrewingRecipe} registrations).
 */
public record VanillaBrewingRecipe(
    ResourceLocation id,
    List<ItemStack> potionInputs,
    Ingredient reagent,
    ItemStack output,
    int brewingTicks,
    boolean vanillaMix
) {
    public static final int DEFAULT_BREWING_TICKS = 400;

    public VanillaBrewingRecipe {
        Checks.checkNotNull(id, "id");
        Checks.checkNotNull(potionInputs, "potionInputs");
        Checks.checkNotNull(reagent, "reagent");
        Checks.checkNotNull(output, "output");
        potionInputs = potionInputs.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
        output = output.copy();
        brewingTicks = Math.max(1, brewingTicks);
    }

    public ItemStack primaryInput() {
        return potionInputs.isEmpty() ? ItemStack.EMPTY : potionInputs.getFirst();
    }
}
