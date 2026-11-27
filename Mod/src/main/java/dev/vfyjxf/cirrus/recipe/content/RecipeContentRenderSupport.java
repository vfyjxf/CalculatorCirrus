package dev.vfyjxf.cirrus.recipe.content;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.render.StackRenderer;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.content.BatchableRecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Objects;

final class RecipeContentRenderSupport {

    private RecipeContentRenderSupport() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void renderBaseStack(GuiGraphics graphics, ContentStack<?, ?> stack, int width, int height, float delta) {
        renderBaseStackAt(graphics, stack, 0, 0, width, height, delta);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void renderBaseStackAt(
        GuiGraphics graphics,
        ContentStack<?, ?> stack,
        int x,
        int y,
        int width,
        int height,
        float delta
    ) {
        ContentStack<?, ?> visual = visualStack(stack);
        if (visual.isEmpty()) {
            return;
        }
        StackRenderer renderer = StackRenderer.getStackRenderer(visual.type());
        renderer.renderBaseAt(graphics, visual, x, y, width, height, delta);
    }

    static void submitBaseStack(
        BatchableRecipeContentRenderer.Batch batch,
        ContentStack<?, ?> stack,
        int x,
        int y,
        int width,
        int height
    ) {
        ContentStack<?, ?> visual = visualStack(stack);
        if (visual.isEmpty()) {
            return;
        }
        batch.addBaseStack(visual, x, y, width, height);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void renderStackAt(
        GuiGraphics graphics,
        ContentStack<?, ?> stack,
        int x,
        int y,
        int width,
        int height,
        float delta
    ) {
        ContentStack<?, ?> visual = visualStack(stack);
        if (visual.isEmpty()) {
            return;
        }
        StackRenderer renderer = StackRenderer.getStackRenderer(visual.type());
        renderer.renderAt(graphics, visual, x, y, width, height, delta);
    }

    static void submitStack(
        BatchableRecipeContentRenderer.Batch batch,
        ContentStack<?, ?> stack,
        int x,
        int y,
        int width,
        int height
    ) {
        ContentStack<?, ?> visual = visualStack(stack);
        if (visual.isEmpty()) {
            return;
        }
        batch.addStack(visual, x, y, width, height);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static @Nullable Tooltip tooltipForStack(ContentStack<?, ?> stack, TooltipContext context) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        StackRenderer renderer = StackRenderer.getStackRenderer(stack.type());
        Tooltip tooltip = renderer.getTooltip(stack.value(), context);
        TooltipStack<?> tooltipStack = tooltipStackForStack(stack);
        if (tooltipStack != null) {
            tooltip.setStack(tooltipStack);
        }
        return tooltip;
    }

    static @Nullable TooltipStack<?> tooltipStackForStack(ContentStack<?, ?> stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (stack.isItem()) {
            return TooltipStack.of(stack.<Item, ItemStack>cast().value().copy());
        }
        if (stack.isFluid()) {
            return TooltipStack.of(stack.<Fluid, FluidStack>cast().value().copy());
        }
        return TooltipStack.of(stack, ignored -> ItemStack.EMPTY);
    }

    static ImmutableList<ContentStack<?, ?>> enumeratedVisuals(InputContent input) {
        if (input == null || !input.isEnumerable()) {
            return Lists.immutable.empty();
        }
        var out = Lists.mutable.<ContentStack<?, ?>>empty();
        for (ContentVariant variant : input.enumerate()) {
            switch (variant) {
                case ContentStack<?, ?> stack when stack.notEmpty() -> out.add(stack.normalize());
                case ContentKind<?> kind when kind.notEmpty() -> {
                    ContentStack<?, ?> stack = stackFromKind(kind);
                    if (stack.notEmpty()) {
                        out.add(stack.normalize());
                    }
                }
                default -> {}
            }
        }
        return out.toImmutable();
    }

    static ContentStack<?, ?> visualAt(ImmutableList<? extends ContentStack<?, ?>> stacks, int visualIndex) {
        if (stacks == null || stacks.isEmpty()) {
            return ContentStack.empty();
        }
        int index = Math.max(0, Math.min(visualIndex, stacks.size() - 1));
        ContentStack<?, ?> stack = stacks.get(index);
        return stack == null ? ContentStack.empty() : stack.normalize();
    }

    static ContentStack<?, ?> visualStack(ContentStack<?, ?> stack) {
        if (stack == null || stack.isEmpty()) {
            return ContentStack.empty();
        }
        return stack.normalize();
    }

    static ContentStack<?, ?> stackForTooltipDisplay(RecipeContent content, ContentStack<?, ?> stack) {
        if (stack == null || stack.isEmpty() || content == null || content.isEmpty()) {
            return ContentStack.empty();
        }
        return stack.normalize().copyWithAmount(Math.max(1L, content.amount()));
    }

    static ContentStack<?, ?> stackFromKind(ContentKind<?> kind) {
        if (kind.type() == CirrusTypes.Contents.item) {
            return ContentStack.fromContent(kind.<Item>cast().value());
        }
        if (kind.type() == CirrusTypes.Contents.fluidConst) {
            return ContentStack.fromContent(kind.<Fluid>cast().value());
        }
        return ContentStack.empty();
    }

    static String labelForEnumeratedInput(InputContent input) {
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        for (ContentVariant variant : input.enumerate()) {
            switch (variant) {
                case ContentStack<?, ?> stack when stack.notEmpty() -> labels.add(describeStack(stack.normalize()));
                case ContentKind<?> kind when kind.notEmpty() -> {
                    ContentStack<?, ?> stack = stackFromKind(kind);
                    if (stack.notEmpty()) {
                        labels.add(describeStack(stack.normalize()));
                    } else {
                        labels.add(describeKind(kind));
                    }
                }
                case ContentValue<?> value -> labels.add(describeValue(value));
                default -> {}
            }
        }
        return joinedLabels(labels);
    }

    static String labelForStacks(Iterable<? extends ContentStack<?, ?>> stacks) {
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        for (ContentStack<?, ?> stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            labels.add(describeStack(stack.normalize()));
        }
        return joinedLabels(labels);
    }

    static String stackLabel(ContentStack<?, ?> stack) {
        if (stack.isItem()) {
            ItemStack itemStack = stack.<Item, ItemStack>cast().value().copy();
            if (!itemStack.isEmpty()) {
                itemStack.setCount(1);
                return itemStack.getHoverName().getString();
            }
        }
        if (stack.isFluid()) {
            FluidStack fluidStack = stack.<Fluid, FluidStack>cast().value().copy();
            if (!fluidStack.isEmpty()) {
                fluidStack.setAmount(Math.max(1, fluidStack.getAmount()));
                return fluidStack.getHoverName().getString();
            }
        }
        return describeStack(stack);
    }

    static String describeValue(ContentValue<?> value) {
        return value.type().typeId() + "=" + value.value();
    }

    private static String joinedLabels(Iterable<String> labels) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        int total = 0;
        for (String label : labels) {
            if (label == null || label.isBlank()) {
                continue;
            }
            total++;
            if (count > 0) {
                builder.append(" | ");
            }
            builder.append(label);
            count++;
            if (count >= 3) {
                break;
            }
        }
        if (count == 0) {
            return "-";
        }
        if (total > count) {
            builder.append(" | ...");
        }
        return builder.toString();
    }

    private static String describeStack(ContentStack<?, ?> stack) {
        if (stack.isItem()) {
            Item item = stack.<Item, ItemStack>cast().content();
            return Objects.toString(BuiltInRegistries.ITEM.getKey(item), "minecraft:air");
        }
        if (stack.isFluid()) {
            Fluid fluid = stack.<Fluid, FluidStack>cast().content();
            return Objects.toString(BuiltInRegistries.FLUID.getKey(fluid), "minecraft:empty");
        }
        var location = stack.kind().getLocation();
        return location == null ? Objects.toString(stack.content(), "unknown") : location.toString();
    }

    private static String describeKind(ContentKind<?> kind) {
        var location = kind.getLocation();
        return location == null
                ? kind.type().typeId() + "=" + Objects.toString(kind.value(), "unknown")
                : location.toString();
    }
}
