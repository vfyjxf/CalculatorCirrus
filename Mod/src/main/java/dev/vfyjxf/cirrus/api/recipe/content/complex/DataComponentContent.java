package dev.vfyjxf.cirrus.api.recipe.content.complex;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.content.ComplexContent;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.RecipeContentType;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class DataComponentContent<T> implements ComplexContent {

    public static InputContent fromItemIngredient(DataComponentIngredient ingredient, long amount, float chance) {
        if (amount <= 0) return empty;
        DataComponentPatch componentPatch = ingredient.components().asPatch();
        Set<Item> items = ingredient.items()
                                    .stream()
                                    .map(Holder::value)
                                    .collect(Collectors.toSet());
        return new DataComponentContent<>(
            CirrusTypes.Contents.ITEM, items,
            ingredient.components(), componentPatch,
            ingredient.isStrict(), amount, chance
        );
    }

    public static InputContent fromFluidIngredient(DataComponentFluidIngredient ingredient, long amount, float chance) {
        if (amount <= 0) return empty;
        DataComponentPatch componentPatch = ingredient.components().asPatch();
        Set<Fluid> fluids = ingredient.fluids()
                                      .stream()
                                      .map(Holder::value)
                                      .collect(Collectors.toSet());
        return new DataComponentContent<>(
            CirrusTypes.Contents.FLUID, fluids,
            ingredient.components(), componentPatch,
            ingredient.isStrict(), amount, chance
        );
    }


    private final ContentType<T> contentType;
    private final Set<T> contentObjects;
    private final DataComponentPredicate predicate;
    private final DataComponentPatch componentPatch;
    private final boolean strict;
    private final long amount;
    private final float chance;

    private DataComponentContent(
        ContentType<T> contentType,
        Set<T> contentObjects,
        DataComponentPredicate predicate,
        DataComponentPatch componentPatch,
        boolean strict, long amount, float chance
    ) {
        Checks.checkRangeClosed(chance, 0, 1);
        this.contentType = contentType;
        this.predicate = predicate;
        this.componentPatch = componentPatch;
        this.contentObjects = contentObjects;
        this.strict = strict;
        this.amount = amount;
        this.chance = chance;
    }

    public boolean strict() {
        return strict;
    }

    @Override
    public boolean isEmpty() {
        return contentObjects.isEmpty();
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
    public DataComponentContent<T> copy() {
        return new DataComponentContent<>(contentType, contentObjects, predicate, componentPatch, strict, amount, chance);
    }

    @Override
    public DataComponentContent<T> copyWithAmount(long amount) {
        return new DataComponentContent<>(contentType, contentObjects, predicate, componentPatch, strict, amount, chance);
    }

    @Override
    public DataComponentContent<T> copyWithChance(float chance) {
        return new DataComponentContent<>(contentType, contentObjects, predicate, componentPatch, strict, amount, chance);
    }

    @Override
    public DataComponentContent<T> copyWithAmountAndChance(long amount, float chance) {
        return new DataComponentContent<>(contentType, contentObjects, predicate, componentPatch, strict, amount, chance);
    }

    @Override
    public boolean mergeable(RecipeContent other) {
        if (!(other instanceof DataComponentContent<?> o)) return false;
        return strict == o.strict && contentObjects.equals(o.contentObjects) && componentPatch.equals(o.componentPatch);
    }

    @SuppressWarnings({"SuspiciousMethodCalls"})
    @Override
    public boolean satisfy(ContentVariant actual) {
        return switch (actual) {
            case ContentStack<?, ?> stack -> {

                if (contentType != stack.contentType() || stack.isEmpty()) yield false;

                if (!stack.type().supportComponents() && !componentPatch.isEmpty()) {
                    yield contentObjects.contains(stack.content());
                }


                if (!strict) {
                    //not strict,only check content identity and stack components
                    DataComponentMap stackComponents = stack.components();
                    yield contentObjects.contains(stack.content()) && stackComponents != null && predicate.test(stackComponents);
                }

                //match stack content and content components and stack components
                //check stack component when content components is unsupported
                DataComponentPatch stackComponentPatch = stack.componentsPatch();
                if (!contentType.supportComponents()) {
                    yield contentObjects.contains(stack.content()) && componentPatch.equals(stackComponentPatch);
                }

                //check prototype
                //equals to ItemStack#isSameItemSameComponents/FluidStack#isSameFluidSameComponents
                DataComponentMap objectComponents = getObjectComponents(stack);
                for (T content : contentObjects) {
                    DataComponentMap itemComponents = contentType.getComponents(content);
                    boolean sameContent = content == stack.content();
                    boolean sameComponents = Objects.equals(itemComponents, objectComponents) &&
                                             componentPatch.equals(stackComponentPatch);
                    if (sameContent && sameComponents) yield true;
                }
                yield false;
            }
            case ContentKind<?> kind -> {
                if (contentType != kind.type()) yield false;
                if (!contentType.supportComponents() && strict) yield false;
                if (!contentObjects.contains(kind.value())) yield false;

                DataComponentMap objectComponents = kind.components();
                if (objectComponents == null) yield componentPatch.isEmpty();

                if (!strict) yield predicate.test(objectComponents);

                if (objectComponents.size() != componentPatch.size()) yield false;
                for (var entry : componentPatch.entrySet()) {
                    if (!Objects.equals(objectComponents.get(entry.getKey()), entry.getValue().orElse(null)))
                        yield false;
                }
                yield true;
            }
            default -> false;
        };
    }

    private static <T, S> @Nullable DataComponentMap getObjectComponents(ContentStack<T, S> stack) {
        ContentType<T> contentType = stack.contentType();
        return contentType.supportComponents() ? contentType.getComponents(stack.content()) : null;
    }

    @Override
    public RecipeContentType<? extends RecipeContent> recipeType() {
        return RecipeContentType.COMPLEX;
    }
}