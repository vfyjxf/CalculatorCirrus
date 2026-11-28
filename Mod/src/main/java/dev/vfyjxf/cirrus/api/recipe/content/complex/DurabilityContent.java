package dev.vfyjxf.cirrus.api.recipe.content.complex;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.RecipeContentType;
import dev.vfyjxf.cirrus.api.recipe.content.ComplexContent;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.RichIterable;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Objects;

/**
 * Semantic input requirement expressing a damageable tool/equipment ingredient that
 * consumes a fixed amount of durability per craft rather than consuming the item stack
 * itself (e.g., {@code Iron Pickaxe with -4 durability cost}).
 *
 * <p>Unlike a zero-chance catalyst hack ({@code chance = 0%}), this requirement retains
 * normal certainty ({@link RecipeContent#MAX_CHANCE} by default), keeps the underlying
 * tool ingredient undamaged for clean presentation, enforces remaining durability during
 * {@link #satisfy(ContentVariant)}, and renders with a dedicated durability-cost style.
 */
public final class DurabilityContent implements ComplexContent {

    public static InputContent from(InputContent ingredient, int durabilityCost) {
        if (ingredient == null || ingredient.isEmpty() || durabilityCost <= 0) {
            return empty;
        }
        return from(ingredient, durabilityCost, Math.max(1L, ingredient.amount()), MAX_CHANCE);
    }

    public static InputContent from(InputContent ingredient, int durabilityCost, long amount, float chance) {
        if (ingredient == null || ingredient.isEmpty() || durabilityCost <= 0 || amount <= 0L) {
            return empty;
        }
        InputContent unwrapped = ingredient instanceof DurabilityContent existing ? existing.ingredient : ingredient;
        if (unwrapped.isEmpty()) {
            return empty;
        }
        InputContent normalized = normalizeIngredient(unwrapped);
        if (normalized.isEmpty()) {
            return empty;
        }
        return new DurabilityContent(normalized, durabilityCost, amount, chance);
    }

    public static InputContent fromItemStack(ItemStack tool, int durabilityCost) {
        if (tool == null || tool.isEmpty() || durabilityCost <= 0) {
            return empty;
        }
        return from(InputContent.fromItemStack(tool, 1L, MAX_CHANCE), durabilityCost, 1L, MAX_CHANCE);
    }

    /**
     * Resolves the maximum durability of a content stack if it represents a damageable item,
     * returning {@code 0} for non-damageable or non-item stacks.
     */
    public static int resolveMaxDurability(ContentStack<?, ?> stack) {
        if (stack == null || stack.isEmpty() || !stack.isItem()) {
            return 0;
        }
        ItemStack itemStack = stack.<Item, ItemStack>cast().value();
        int maxDamage = itemStack.getMaxDamage();
        if (maxDamage <= 0) {
            try {
                Integer maxComp = stack.getComponent(DataComponents.MAX_DAMAGE);
                maxDamage = maxComp == null ? 0 : Math.max(0, maxComp);
            } catch (RuntimeException ignored) {
                maxDamage = 0;
            }
        }
        return Math.max(0, maxDamage);
    }

    /**
     * Returns whether an {@link InputContent} represents at least one damageable tool candidate.
     */
    public static boolean supportsDurability(InputContent input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        if (input instanceof DurabilityContent existing) {
            return supportsDurability(existing.ingredient);
        }
        if (input instanceof StackContent<?, ?> stackContent) {
            return resolveMaxDurability(stackContent.content()) > 0;
        }
        if (input instanceof StackListContent list) {
            for (ContentStack<?, ?> stack : list.stacks()) {
                if (resolveMaxDurability(stack) > 0) {
                    return true;
                }
            }
            return false;
        }
        if (input instanceof TypedStackListContent<?, ?> typedList) {
            for (ContentStack<?, ?> stack : typedList.stacks()) {
                if (resolveMaxDurability(stack) > 0) {
                    return true;
                }
            }
            return false;
        }
        if (input.isEnumerable()) {
            for (ContentVariant variant : input.enumerate()) {
                if (variant instanceof ContentStack<?, ?> stack && resolveMaxDurability(stack) > 0) {
                    return true;
                }
                if (variant instanceof ContentKind<?> kind && kind.type() == CirrusTypes.Contents.item && kind.notEmpty()) {
                    if (kind.<Item>cast().value().getDefaultInstance().getMaxDamage() > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static InputContent normalizeIngredient(InputContent raw) {
        if (raw instanceof StackContent<?, ?> stackContent && stackContent.isItem()) {
            ItemStack itemStack = stackContent.<Item, ItemStack>cast().content().value();
            if (itemStack.isEmpty()) {
                return empty;
            }
            ItemStack clean = itemStack.copyWithCount(1);
            if (clean.getDamageValue() > 0) {
                clean.setDamageValue(0);
            }
            return InputContent.fromStack(
                ContentStack.fromStack(CirrusTypes.Contents.itemStack, clean, 1L),
                MAX_CHANCE,
                stackContent.matchStrategy()
            );
        }
        return (InputContent) raw.copyWithAmountAndChance(1L, MAX_CHANCE);
    }

    private final InputContent ingredient;
    private final int durabilityCost;
    private final long amount;
    private final float chance;

    private DurabilityContent(InputContent ingredient, int durabilityCost, long amount, float chance) {
        Checks.checkArgument(durabilityCost > 0, "durabilityCost must be > 0");
        Checks.checkArgument(amount > 0L, "amount must be > 0");
        Checks.checkRangeClosed(chance, 0, 1);
        this.ingredient = ingredient;
        this.durabilityCost = durabilityCost;
        this.amount = amount;
        this.chance = chance;
    }

    /**
     * The underlying tool requirement (undamaged single stack, tag, or candidate list).
     */
    public InputContent ingredient() {
        return ingredient;
    }

    /**
     * Durability points consumed per craft ({@code >= 1}).
     */
    public @Range(from = 1, to = Integer.MAX_VALUE) int durabilityCost() {
        return durabilityCost;
    }

    /**
     * Best-effort maximum durability of the primary tool candidate, or {@code 0} if unknown.
     */
    public int maxDurability() {
        if (ingredient instanceof StackContent<?, ?> stackContent) {
            return resolveMaxDurability(stackContent.content());
        }
        if (ingredient.isEnumerable()) {
            for (ContentVariant variant : ingredient.enumerate()) {
                if (variant instanceof ContentStack<?, ?> stack) {
                    int max = resolveMaxDurability(stack);
                    if (max > 0) {
                        return max;
                    }
                } else if (variant instanceof ContentKind<?> kind && kind.type() == CirrusTypes.Contents.item && kind.notEmpty()) {
                    int max = kind.<Item>cast().value().getDefaultInstance().getMaxDamage();
                    if (max > 0) {
                        return max;
                    }
                }
            }
        }
        return 0;
    }

    /**
     * Number of crafts a fresh primary tool can perform before breaking, or {@code 0} if max durability is unknown.
     */
    public int usesPerTool() {
        int max = maxDurability();
        return max > 0 ? max / durabilityCost : 0;
    }

    /**
     * Returns {@code true} when a single craft consumes the entire maximum durability of the primary tool.
     */
    public boolean consumesWholeTool() {
        int max = maxDurability();
        return max > 0 && durabilityCost >= max;
    }

    @Override
    public boolean isEmpty() {
        return ingredient.isEmpty() || durabilityCost <= 0 || amount <= 0L;
    }

    @Override
    public @Range(from = 0, to = Long.MAX_VALUE) long amount() {
        return amount;
    }

    @Override
    public @Range(from = 0, to = 1) float chance() {
        return chance;
    }

    @Override
    public DurabilityContent copy() {
        return new DurabilityContent((InputContent) ingredient.copy(), durabilityCost, amount, chance);
    }

    @Override
    public DurabilityContent copyWithAmount(long amount) {
        return new DurabilityContent((InputContent) ingredient.copy(), durabilityCost, amount, chance);
    }

    @Override
    public DurabilityContent copyWithChance(float chance) {
        return new DurabilityContent((InputContent) ingredient.copy(), durabilityCost, amount, chance);
    }

    @Override
    public DurabilityContent copyWithAmountAndChance(long amount, float chance) {
        return new DurabilityContent((InputContent) ingredient.copy(), durabilityCost, amount, chance);
    }

    @Override
    public boolean mergeable(RecipeContent other) {
        return other instanceof DurabilityContent dc
                && durabilityCost == dc.durabilityCost
                && ingredient.mergeable(dc.ingredient);
    }

    @Override
    public boolean satisfy(ContentVariant actual) {
        if (actual instanceof ContentStack<?, ?> actualStack) {
            if (actualStack.isEmpty() || !actualStack.isItem()) {
                return false;
            }
            ItemStack itemStack = actualStack.<Item, ItemStack>cast().value();
            if (itemStack.isEmpty()) {
                return false;
            }
            int maxDamage = resolveMaxDurability(actualStack);
            if (maxDamage <= 0) {
                return false;
            }
            boolean unbreakable = itemStack.has(DataComponents.UNBREAKABLE);
            if (!unbreakable) {
                int remaining = maxDamage - Math.max(0, itemStack.getDamageValue());
                if (remaining < durabilityCost) {
                    return false;
                }
            }
            if (ingredient.satisfy(actualStack)) {
                return true;
            }
            // Allow worn or unbreakable instances of the required tool to satisfy the canonical undamaged ingredient.
            if (itemStack.getDamageValue() > 0 || unbreakable) {
                ItemStack normalizedActual = itemStack.copy();
                if (unbreakable && !ingredientRequiresUnbreakable()) {
                    normalizedActual.remove(DataComponents.UNBREAKABLE);
                }
                if (normalizedActual.getDamageValue() > 0) {
                    normalizedActual.setDamageValue(0);
                }
                ContentStack<Item, ItemStack> probe = ContentStack.fromStack(
                    CirrusTypes.Contents.itemStack,
                    normalizedActual,
                    actualStack.amount()
                );
                return ingredient.satisfy(probe);
            }
            return false;
        }
        if (actual instanceof ContentKind<?> kind) {
            if (kind.isEmpty() || kind.type() != CirrusTypes.Contents.item) {
                return false;
            }
            ItemStack defaultStack = kind.<Item>cast().value().getDefaultInstance();
            int maxDamage = defaultStack.getMaxDamage();
            if (maxDamage <= 0 || (!defaultStack.has(DataComponents.UNBREAKABLE) && maxDamage < durabilityCost)) {
                return false;
            }
            return ingredient.satisfy(actual) || ingredient.satisfy(ContentStack.fromStack(defaultStack));
        }
        return false;
    }

    private boolean ingredientRequiresUnbreakable() {
        return ingredient instanceof StackContent<?, ?> stackContent
                && stackContent.isItem()
                && stackContent.<Item, ItemStack>cast().content().value().has(DataComponents.UNBREAKABLE);
    }

    @Override
    public boolean isEnumerable() {
        return ingredient.isEnumerable();
    }

    @Override
    public @UnmodifiableView RichIterable<? extends ContentVariant> enumerate() {
        return ingredient.enumerate();
    }

    @Override
    public RecipeContentType<? extends RecipeContent> recipeType() {
        return RecipeContentType.COMPLEX;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DurabilityContent other)) {
            return false;
        }
        return durabilityCost == other.durabilityCost
                && amount == other.amount
                && Float.compare(chance, other.chance) == 0
                && ingredient.getClass() == other.ingredient.getClass()
                && (ingredient.equals(other.ingredient) || ingredient.mergeable(other.ingredient));
    }

    @Override
    public int hashCode() {
        return Objects.hash(durabilityCost, amount, Float.floatToIntBits(chance), ingredient.getClass());
    }

    @Override
    public String toString() {
        return "DurabilityContent{" + "ingredient=" + ingredient + ", durabilityCost=" + durabilityCost
                + ", amount=" + amount + ", chance=" + chance + '}';
    }
}
