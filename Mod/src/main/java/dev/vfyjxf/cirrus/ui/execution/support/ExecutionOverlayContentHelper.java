package dev.vfyjxf.cirrus.ui.execution.support;

import dev.vfyjxf.cirrus.api.recipe.execution.OutputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.InputBinding;
import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.render.StackRenderer;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.ui.process.MaterialCategories;
import dev.vfyjxf.cirrus.ui.process.MaterialCategory;
import dev.vfyjxf.cirrus.ui.process.ProcessNode;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class ExecutionOverlayContentHelper {

    private ExecutionOverlayContentHelper() {
    }

    public static boolean hasRenderableVisual(@Nullable RecipeContent content) {
        return content != null && content.notEmpty() && renderer(content).visualCount(content) > 0;
    }

    public static boolean hasMultipleVisuals(@Nullable RecipeContent content) {
        return content != null && content.notEmpty() && renderer(content).visualCount(content) > 1;
    }

    public static boolean hasSingleVisual(@Nullable RecipeContent content) {
        return content != null && content.notEmpty() && renderer(content).visualCount(content) == 1;
    }

    public static int currentVisualIndex(@Nullable RecipeContent content) {
        if (!hasRenderableVisual(content)) {
            return 0;
        }
        int visualCount = renderer(content).visualCount(content);
        return visualCount <= 1 ? 0 : (int) ((System.currentTimeMillis() / 1000L) % visualCount);
    }

    public static void renderRecipeContent(
        dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas canvas,
        @Nullable RecipeContent content,
        int x,
        int y,
        int width,
        int height,
        float partialTicks
    ) {
        if (!hasRenderableVisual(content)) {
            return;
        }
        int visualIndex = currentVisualIndex(content);
        renderer(content).renderAt(canvas.layeredGraphics(), content, visualIndex, x, y, width, height, partialTicks);
    }

    public static @Nullable Tooltip tooltip(@Nullable RecipeContent content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        return renderer(content).tooltip(content, currentVisualIndex(content), TooltipContext.create());
    }

    public static @Nullable Tooltip tooltip(@Nullable RecipeContent content, int visualIndex) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        return renderer(content).tooltip(content, Math.max(0, visualIndex), TooltipContext.create());
    }

    public static @Nullable TooltipStack<?> tooltipStack(@Nullable RecipeContent content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        return renderer(content).tooltipStack(content, currentVisualIndex(content));
    }

    public static @Nullable TooltipStack<?> tooltipStack(@Nullable RecipeContent content, int visualIndex) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        return renderer(content).tooltipStack(content, Math.max(0, visualIndex));
    }

    public static String fallbackToken(@Nullable RecipeContent content) {
        return fallbackToken(label(content));
    }

    public static String label(@Nullable RecipeContent content) {
        if (content == null || content.isEmpty()) {
            return "-";
        }
        Component name = renderer(content).getName(content);
        String label = name == null ? "-" : name.getString();
        return label == null || label.isBlank() ? "-" : label;
    }

    public static String fallbackToken(@Nullable String label) {
        if (label == null || label.isBlank()) {
            return "-";
        }
        if (label.startsWith("#")) {
            return "#";
        }
        if (label.contains("|")) {
            return "?";
        }
        return label.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    public static String recipeContentKey(@Nullable RecipeContent content) {
        if (content == null || content.isEmpty()) {
            return "-";
        }
        String label = label(content);
        if (!hasSingleVisual(content) && !label.isBlank() && !"-".equals(label)) {
            return label.toLowerCase(Locale.ROOT);
        }
        if (content instanceof StackContent<?, ?> stackContent) {
            return contentKey(stackContent.content()).toLowerCase(Locale.ROOT);
        }
        return label.toLowerCase(Locale.ROOT);
    }

    public static ItemStack stackFromInputContent(InputContent input) {
        if (!(input instanceof StackContent<?, ?> stackContent)) {
            return ItemStack.EMPTY;
        }
        ContentStack<?, ?> contentStack = stackContent.content();
        if (contentStack.isEmpty() || !contentStack.isItem()) {
            return ItemStack.EMPTY;
        }
        return normalizeStack(contentStack.<Item, ItemStack>cast().value());
    }

    public static ContentStack<?, ?> contentStackFromContentResource(ContentResource resource) {
        if (resource instanceof ContentStack<?, ?> contentStack && !contentStack.isEmpty()) {
            return contentStack.normalize();
        }
        return ContentStack.empty();
    }

    public static ItemStack stackFromContentResource(ContentResource resource) {
        ContentStack<?, ?> contentStack = contentStackFromContentResource(resource);
        if (contentStack.isEmpty() || !contentStack.isItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = contentStack.<Item, ItemStack>cast().value().copy();
        return normalizeStack(stack);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Component contentName(@Nullable ContentStack<?, ?> stack) {
        if (stack == null || stack.isEmpty()) {
            return Component.empty();
        }
        StackRenderer renderer = StackRenderer.getStackRenderer(stack.type());
        return renderer.getName(stack.value());
    }

    public static @Nullable TooltipStack<?> tooltipStack(@Nullable ContentStack<?, ?> stack) {
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Tooltip tooltipForContent(@Nullable ContentStack<?, ?> stack) {
        if (stack == null || stack.isEmpty()) {
            return Tooltip.create();
        }
        StackRenderer renderer = StackRenderer.getStackRenderer(stack.type());
        Tooltip tooltip = renderer.getTooltip(stack.value(), TooltipContext.create());
        TooltipStack<?> tooltipStack = tooltipStack(stack);
        if (tooltipStack != null) {
            tooltip.setStack(tooltipStack);
        }
        return tooltip;
    }

    public static String contentKey(@Nullable ContentStack<?, ?> stack) {
        if (stack == null || stack.isEmpty()) {
            return "-";
        }
        if (stack.isItem()) {
            ItemStack itemStack = stack.<Item, ItemStack>cast().value();
            return "item:" + itemStack.getDescriptionId().toLowerCase(Locale.ROOT) + ":" + stack.componentsPatch();
        }
        if (stack.isFluid()) {
            FluidStack fluidStack = stack.<Fluid, FluidStack>cast().value();
            return "fluid:" + fluidStack.getFluidHolder().getRegisteredName().toLowerCase(Locale.ROOT) + ":" + stack.componentsPatch();
        }
        var location = stack.kind().getLocation();
        return location == null ? stack.toString() : location.toString();
    }

    public static ProcessNode.MaterialSlot materialSlot(InputBinding binding, boolean catalyst) {
        return materialSlot(binding.input(), safeInt(binding.amount()), catalyst);
    }

    public static ProcessNode.MaterialSlot materialSlot(InputContent input) {
        return materialSlot(input, safeInt(input.amount()), input.notConsumed());
    }

    private static ProcessNode.MaterialSlot materialSlot(InputContent input, int amount, boolean catalyst) {
        InputContent content = input.copyWithAmount(Math.max(1, amount));
        return new ProcessNode.MaterialSlot(
            label(content),
            amount,
            catalyst ? MaterialCategories.CATALYST : materialCategory(content),
            1.0,
            catalyst,
            content,
            visualStack(content)
        );
    }

    public static ProcessNode.MaterialSlot materialSlot(OutputBinding binding) {
        return materialSlot(binding.resource(), safeInt(binding.amount()), 1.0d);
    }

    public static ProcessNode.MaterialSlot materialSlot(OutputContent output) {
        return materialSlot(output.asResource(), safeInt(output.amount()), output.chance());
    }

    private static ProcessNode.MaterialSlot materialSlot(ContentResource resource, int amount, double chance) {
        RecipeContent content = recipeContentFromResource(resource, amount, chance);
        return new ProcessNode.MaterialSlot(
            label(content),
            amount,
            materialCategory(content),
            chance,
            false,
            content,
            visualStack(content)
        );
    }

    private static MaterialCategory materialCategory(@Nullable RecipeContent content) {
        if (content == null || content.isEmpty()) {
            return MaterialCategories.UNKNOWN;
        }
        if (content instanceof StackContent<?, ?> stackContent) {
            ContentStack<?, ?> stack = stackContent.content();
            if (stack.notEmpty() && stack.isFluid()) {
                return MaterialCategories.FLUID;
            }
            return MaterialCategories.ITEM;
        }
        return MaterialCategories.VALUE;
    }

    public static boolean supportsItemMatching(@Nullable RecipeContent content) {
        if (!(content instanceof InputContent input) || input.isEmpty()) {
            return false;
        }
        if (input instanceof StackContent<?, ?> stackContent) {
            ContentStack<?, ?> stack = stackContent.content();
            return stack.notEmpty() && stack.isItem();
        }
        if (!input.isEnumerable()) {
            return false;
        }
        for (ContentVariant variant : input.enumerate()) {
            switch (variant) {
                case ContentStack<?, ?> stack when stack.isItem() -> {
                    return true;
                }
                case ContentKind<?> kind when kind.type() == CirrusTypes.Contents.ITEM -> {
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }

    public static boolean matchesItemStack(@Nullable RecipeContent content, ItemStack stack) {
        if (!(content instanceof InputContent input) || stack.isEmpty()) {
            return false;
        }
        return input.satisfy(ContentStack.fromStack(normalizeStack(stack)));
    }

    public static long countInventory(ItemStack template) {
        if (template.isEmpty()) {
            return 0L;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return 0L;
        }
        return countInventory(minecraft.player.getInventory(), template);
    }

    public static long countInventory(ContentStack<?, ?> template) {
        if (template == null || template.isEmpty() || !template.isItem()) {
            return 0L;
        }
        return countInventory(template.<Item, ItemStack>cast().value());
    }

    public static long countInventory(@Nullable RecipeContent material) {
        if (material == null || material.isEmpty()) {
            return 0L;
        }
        if (material instanceof StackContent<?, ?> stackContent) {
            ContentStack<?, ?> stack = stackContent.content();
            if (stack.notEmpty() && stack.isItem()) {
                return countInventory(stack.<Item, ItemStack>cast().value());
            }
        }
        if (!(material instanceof InputContent input)) {
            return 0L;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return 0L;
        }
        long total = 0L;
        for (ItemStack stack : minecraft.player.getInventory().items) {
            if (!stack.isEmpty() && matchesItemStack(input, stack)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : minecraft.player.getInventory().offhand) {
            if (!stack.isEmpty() && matchesItemStack(input, stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static long countInventory(Inventory inventory, ItemStack template) {
        if (template.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (ItemStack stack : inventory.items) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, template)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : inventory.offhand) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, template)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static ItemStack normalizeStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    public static RecipeContent recipeContentFromResource(ContentResource resource, long amount, double chance) {
        if (resource instanceof ContentStack<?, ?> stack && !stack.isEmpty()) {
            return OutputContent.fromStack(stack.copyWithAmount(Math.max(1L, amount)), (float) chance);
        }
        if (resource instanceof ContentValue<?> value) {
            return OutputContent.fromValue(value, (float) chance);
        }
        return RecipeContent.empty();
    }

    private static @Nullable ItemStack exactItemStack(@Nullable RecipeContent content) {
        if (!(content instanceof StackContent<?, ?> stackContent)) {
            return ItemStack.EMPTY;
        }
        ContentStack<?, ?> stack = stackContent.content();
        if (stack.isEmpty() || !stack.isItem()) {
            return ItemStack.EMPTY;
        }
        return normalizeStack(stack.<Item, ItemStack>cast().value());
    }

    private static @Nullable FluidStack exactFluidStack(@Nullable RecipeContent content) {
        if (!(content instanceof StackContent<?, ?> stackContent)) {
            return FluidStack.EMPTY;
        }
        ContentStack<?, ?> stack = stackContent.content();
        if (stack.isEmpty() || !stack.isFluid()) {
            return FluidStack.EMPTY;
        }
        FluidStack copy = stack.<Fluid, FluidStack>cast().value().copy();
        copy.setAmount(Math.max(1, copy.getAmount()));
        return copy;
    }

    private static ContentStack<?, ?> visualStack(@Nullable RecipeContent content) {
        if (!(content instanceof StackContent<?, ?> stackContent)) {
            return ContentStack.empty();
        }
        ContentStack<?, ?> stack = stackContent.content();
        return stack == null ? ContentStack.empty() : stack.copy();
    }

    @SuppressWarnings("unchecked")
    private static <C extends RecipeContent> RecipeContentRenderer<C> renderer(C content) {
        return RecipeContentRenderer.getRenderer(content);
    }

    private static int safeInt(long value) {
        return value <= 0L ? 1 : (int) Math.min(Integer.MAX_VALUE, value);
    }
}
