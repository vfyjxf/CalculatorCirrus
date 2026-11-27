package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClauseSet;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClausedContent;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchema;
import dev.vfyjxf.cirrus.api.recipe.screening.RecipeScreeningHints;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.crafting.BookCloningRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.DecoratedPotRecipe;
import net.minecraft.world.item.crafting.FireworkRocketRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.MapCloningRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SuspiciousStewRecipe;
import net.minecraft.world.item.crafting.TippedArrowRecipe;
import org.eclipse.collections.api.list.MutableList;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic support for crafting remaining items (e.g., milk buckets -> buckets,
 * honey bottles -> glass bottles) and well-defined vanilla {@link CustomRecipe}s.
 */
final class VanillaSpecialCraftingSupport {

    private static final RecipeScreeningHints dynamicCloningHints = RecipeScreeningHints.builder()
            .discouraged()
            .penalty(300)
            .reasonKey("cirrus.screening.special_cloning_crafting")
            .build();

    private static final RecipeScreeningHints dynamicComponentHints = RecipeScreeningHints.builder()
            .targetOnly()
            .penalty(500)
            .reasonKey("cirrus.screening.special_dynamic_crafting")
            .build();

    private VanillaSpecialCraftingSupport() {}

    /**
     * Extracts deterministic container-return outputs from crafting ingredients
     * (e.g. 3 milk buckets in cake -> 3 empty buckets; 4 honey bottles in honey block -> 4 glass bottles).
     */
    static List<OutputContent> remainingOutputs(List<Ingredient> ingredients) {
        if (ingredients.isEmpty()) {
            return List.of();
        }
        List<ItemStack> merged = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            ItemStack remainder = deterministicRemainingItem(ingredient);
            if (remainder.isEmpty()) {
                continue;
            }
            addMerged(merged, remainder);
        }
        if (merged.isEmpty()) {
            return List.of();
        }
        List<OutputContent> outputs = new ArrayList<>(merged.size());
        for (ItemStack stack : merged) {
            OutputContent content = OutputContent.fromItemStack(stack);
            if (!content.isEmpty()) {
                outputs.add(content);
            }
        }
        return outputs;
    }

    static @Nullable SpecialSpec inspect(CraftingRecipe recipe) {
        if (!(recipe instanceof CustomRecipe)) {
            return null;
        }
        if (recipe instanceof FireworkRocketRecipe) {
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET, 3);
            rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of()));
            return new SpecialSpec(
                List.of(
                    InputContent.fromItemStack(new ItemStack(Items.PAPER), 1),
                    InputContent.fromItemStack(new ItemStack(Items.GUNPOWDER), 1)
                ),
                List.of(OutputContent.fromStack(ContentStack.fromStack(rocket), MatchStrategy.identityAndComponent)),
                RecipeScreeningHints.empty()
            );
        }
        if (recipe instanceof DecoratedPotRecipe) {
            return new SpecialSpec(
                List.of(InputContent.fromItemStack(new ItemStack(Items.BRICK), 4)),
                List.of(OutputContent.fromItemStack(new ItemStack(Items.DECORATED_POT))),
                RecipeScreeningHints.empty()
            );
        }
        if (recipe instanceof SuspiciousStewRecipe) {
            return new SpecialSpec(
                List.of(
                    InputContent.fromItemStack(new ItemStack(Items.BOWL), 1),
                    InputContent.fromItemStack(new ItemStack(Items.RED_MUSHROOM), 1),
                    InputContent.fromItemStack(new ItemStack(Items.BROWN_MUSHROOM), 1),
                    InputContent.fromItemTag(ItemTags.SMALL_FLOWERS, 1)
                ),
                List.of(OutputContent.fromItemStack(new ItemStack(Items.SUSPICIOUS_STEW))),
                dynamicComponentHints
            );
        }
        if (recipe instanceof TippedArrowRecipe) {
            return new SpecialSpec(
                List.of(
                    InputContent.fromItemStack(new ItemStack(Items.ARROW), 8),
                    InputContent.fromItemStack(new ItemStack(Items.LINGERING_POTION), 1)
                ),
                List.of(OutputContent.fromItemStack(new ItemStack(Items.TIPPED_ARROW, 8))),
                dynamicComponentHints
            );
        }
        if (recipe instanceof MapCloningRecipe) {
            return new SpecialSpec(
                List.of(
                    InputContent.fromItemStack(new ItemStack(Items.FILLED_MAP), 1),
                    InputContent.fromItemStack(new ItemStack(Items.MAP), 1)
                ),
                List.of(OutputContent.fromItemStack(new ItemStack(Items.FILLED_MAP, 2))),
                dynamicCloningHints
            );
        }
        if (recipe instanceof BookCloningRecipe) {
            return new SpecialSpec(
                List.of(
                    InputContent.fromItemStack(new ItemStack(Items.WRITTEN_BOOK), 1),
                    InputContent.fromItemStack(new ItemStack(Items.WRITABLE_BOOK), 1)
                ),
                List.of(OutputContent.fromItemStack(new ItemStack(Items.WRITTEN_BOOK, 2))),
                dynamicCloningHints
            );
        }
        return null;
    }

    static ResolveResult resolveSpecial(
        RecipeSystem<RecipeHolder<CraftingRecipe>> system,
        RecipeHolder<CraftingRecipe> holder,
        SpecialSpec spec
    ) {
        MutableList<ClausedContent<InputContent>> inputs = MutableLists.empty();
        for (InputContent input : spec.inputs()) {
            if (!input.isEmpty()) {
                inputs.add(ClausedContent.from(input));
            }
        }
        MutableList<ClausedContent<OutputContent>> outputs = MutableLists.empty();
        for (OutputContent output : spec.outputs()) {
            if (!output.isEmpty()) {
                outputs.add(ClausedContent.from(output));
            }
        }
        return ResolveResult.resolved(
            new RecipeProjection(
                RecipeEntry.of(system, holder),
                inputs.toImmutable(),
                outputs.toImmutable(),
                ProjectionSchema.empty(),
                ClauseSet.create()
            )
        );
    }

    static RoutingRecipeView routingViewSpecial(SpecialSpec spec) {
        return new RoutingRecipeView(spec.inputs(), spec.outputs());
    }

    private static ItemStack deterministicRemainingItem(Ingredient ingredient) {
        ItemStack[] items;
        try {
            items = ingredient.getItems();
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
        if (items.length == 0) {
            return ItemStack.EMPTY;
        }
        ItemStack firstRemainder = items[0].getCraftingRemainingItem();
        if (firstRemainder.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (int i = 1; i < items.length; i++) {
            ItemStack nextRemainder = items[i].getCraftingRemainingItem();
            if (nextRemainder.isEmpty() || !ItemStack.isSameItemSameComponents(firstRemainder, nextRemainder)
                    || firstRemainder.getCount() != nextRemainder.getCount()) {
                return ItemStack.EMPTY;
            }
        }
        return firstRemainder.copy();
    }

    private static void addMerged(List<ItemStack> merged, ItemStack addition) {
        for (ItemStack existing : merged) {
            if (ItemStack.isSameItemSameComponents(existing, addition)) {
                existing.grow(addition.getCount());
                return;
            }
        }
        merged.add(addition.copy());
    }

    record SpecialSpec(List<InputContent> inputs, List<OutputContent> outputs, RecipeScreeningHints hints) {}
}
