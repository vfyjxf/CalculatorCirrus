package dev.vfyjxf.cirrus.api.recipe.execution.overlay;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import net.minecraft.world.item.ItemStack;

/**
 * Snapshot of required materials for execution overlays.
 */
public record OverlayMaterialRequirement(
    RecipeContent material,
    long required,
    long available,
    long missing
) {
    public OverlayMaterialRequirement(ItemStack stack, long required, long available, long missing) {
        this(
            stack.isEmpty() ? RecipeContent.empty() : InputContent.fromItemStack(stack, Math.max(1, stack.getCount())),
            required,
            available,
            missing
        );
    }

    public OverlayMaterialRequirement {
        required = Math.max(0L, required);
        available = Math.max(0L, available);
        missing = Math.max(0L, missing);
    }
}
