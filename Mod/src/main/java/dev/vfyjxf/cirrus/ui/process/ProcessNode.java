package dev.vfyjxf.cirrus.ui.process;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Represents a single process step (recipe) in the flow chart.
 *
 * @param id             unique identifier
 * @param inputs         input material slots
 * @param outputs        output material slots
 * @param durationTicks  processing time in game ticks
 * @param machineOptions all machines that can execute this recipe (empty = no icon data)
 * @param recipe         source recipe entry used for native preview/rendering when available
 */
public record ProcessNode(
    String id,
    Component displayName,
    Component machineName,
    List<MaterialSlot> inputs,
    List<MaterialSlot> outputs,
    int durationTicks,
    List<MachineInfo> machineOptions,
    @Nullable RecipeEntry<?> recipe
) {
    public ProcessNode(
        String id,
        String name,
        String machine,
        List<MaterialSlot> inputs,
        List<MaterialSlot> outputs,
        int durationTicks,
        List<MachineInfo> machineOptions,
        @Nullable RecipeEntry<?> recipe
    ) {
        this(
            id,
            Component.literal(Objects.requireNonNullElse(name, "")),
            Component.literal(Objects.requireNonNullElse(machine, "")),
            inputs,
            outputs,
            durationTicks,
            machineOptions,
            recipe
        );
    }

    public ProcessNode {
        displayName = displayName == null ? Component.empty() : displayName;
        machineName = machineName == null ? Component.empty() : machineName;
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        machineOptions = machineOptions == null ? List.of() : List.copyOf(machineOptions);
    }

    public String name() {
        return displayName.getString();
    }

    public String machine() {
        return machineName.getString();
    }

    /**
     * Info about one possible machine that can run this recipe.
     *
     * @param name display name
     * @param icon optional stack content for rendering (empty = no icon)
     */
    public record MachineInfo(Component displayName, ContentStack<?, ?> icon) {
        public MachineInfo {
            displayName = displayName == null ? Component.empty() : displayName;
            icon = icon == null ? ContentStack.empty() : icon;
        }

        public MachineInfo(Component displayName) {
            this(displayName, ContentStack.empty());
        }

        public MachineInfo(String name, @Nullable ItemStack icon) {
            this(Component.literal(Objects.requireNonNullElse(name, "")), contentStack(icon));
        }

        public MachineInfo(String name) {
            this(Component.literal(Objects.requireNonNullElse(name, "")));
        }

        public String name() {
            return displayName.getString();
        }

        public boolean hasIcon() {
            return icon.notEmpty();
        }
    }

    /**
     * A single material input/output slot.
     *
     * @param materialName display name
     * @param amount       quantity
     * @param category     registered material category for colour coding/grouping
     * @param chance       output chance (1.0 = 100%, only meaningful for outputs)
     * @param isCatalyst   true if this material is not consumed
     * @param content      structured recipe content payload for exact / tag / alternative inputs
     * @param visualStack  optional stack content for rendering the primary icon
     */
    public record MaterialSlot(
        Component displayName,
        int amount,
        MaterialCategory category,
        double chance,
        boolean isCatalyst,
        RecipeContent content,
        ContentStack<?, ?> visualStack
    ) {
        public MaterialSlot {
            displayName = displayName == null ? Component.empty() : displayName;
            content = content == null ? RecipeContent.empty() : content;
            visualStack = visualStack == null ? ProcessNode.visualStack(content) : visualStack;
            category = category == null ? MaterialCategories.infer(content, visualStack, isCatalyst) : category;
        }

        public MaterialSlot(
            Component displayName,
            int amount,
            MaterialCategory category,
            double chance,
            boolean isCatalyst,
            RecipeContent content
        ) {
            this(displayName, amount, category, chance, isCatalyst, content, ProcessNode.visualStack(content));
        }

        public MaterialSlot(
            String materialName,
            int amount,
            MaterialCategory category,
            double chance,
            boolean isCatalyst,
            RecipeContent content
        ) {
            this(
                Component.literal(Objects.requireNonNullElse(materialName, "")),
                amount,
                category,
                chance,
                isCatalyst,
                content
            );
        }

        public MaterialSlot(
            String materialName,
            int amount,
            MaterialCategory category,
            double chance,
            boolean isCatalyst,
            RecipeContent content,
            ContentStack<?, ?> visualStack
        ) {
            this(
                Component.literal(Objects.requireNonNullElse(materialName, "")),
                amount,
                category,
                chance,
                isCatalyst,
                content,
                visualStack
            );
        }

        public MaterialSlot(
            String materialName,
            int amount,
            MaterialCategory category,
            double chance,
            boolean isCatalyst,
            @Nullable ItemStack itemStack,
            @Nullable FluidStack fluidStack
        ) {
            this(
                materialName,
                amount,
                category,
                chance,
                isCatalyst,
                defaultContent(amount, chance, itemStack, fluidStack),
                contentStack(itemStack, fluidStack, amount)
            );
        }

        public MaterialSlot(String materialName, int amount, MaterialCategory category) {
            this(materialName, amount, category, 1.0, false, RecipeContent.empty(), ContentStack.empty());
        }

        public MaterialSlot(String materialName, int amount, MaterialCategory category, boolean isCatalyst) {
            this(materialName, amount, category, 1.0, isCatalyst, RecipeContent.empty(), ContentStack.empty());
        }

        /**
         * Convenience constructor with ItemStack.
         */
        public static MaterialSlot item(ItemStack stack, int amount) {
            return new MaterialSlot(
                stack.getHoverName(),
                amount,
                MaterialCategories.item,
                1.0,
                false,
                OutputContent.fromItemStack(stack, Math.max(1, amount)),
                contentStack(stack.copyWithCount(amount))
            );
        }

        /**
         * Convenience constructor with ItemStack + chance.
         */
        public static MaterialSlot item(ItemStack stack, int amount, double chance) {
            return new MaterialSlot(
                stack.getHoverName(),
                amount,
                MaterialCategories.item,
                chance,
                false,
                OutputContent.fromItemStack(stack, Math.max(1, amount)),
                contentStack(stack.copyWithCount(amount))
            );
        }

        /**
         * Convenience constructor for catalyst items.
         */
        public static MaterialSlot catalyst(ItemStack stack, int amount) {
            return new MaterialSlot(
                stack.getHoverName(),
                amount,
                MaterialCategories.catalyst,
                1.0,
                true,
                OutputContent.fromItemStack(stack, Math.max(1, amount)),
                contentStack(stack.copyWithCount(amount))
            );
        }

        /**
         * Convenience constructor with FluidStack.
         */
        public static MaterialSlot fluid(FluidStack stack, int amount) {
            return new MaterialSlot(
                stack.getHoverName(),
                amount,
                MaterialCategories.fluid,
                1.0,
                false,
                OutputContent.fromFluidStack(stack, Math.max(1, amount)),
                contentStack(stack.copyWithAmount(amount))
            );
        }

        /**
         * Convenience constructor for abstract value-like materials.
         */
        public static MaterialSlot value(String name, int amount) {
            return new MaterialSlot(
                name,
                amount,
                MaterialCategories.value,
                1.0,
                false,
                RecipeContent.empty(),
                ContentStack.empty()
            );
        }

        /**
         * Whether this slot has a renderable visual (item or fluid).
         */
        public boolean hasVisual() {
            return visualStack.notEmpty() || content.notEmpty();
        }

        public String materialName() {
            return displayName.getString();
        }

        public MaterialCategory type() {
            return category;
        }

        public @Nullable ItemStack itemStack() {
            if (visualStack.isEmpty() || !visualStack.isItem()) {
                return null;
            }
            ContentStack<Item, ItemStack> item = visualStack.cast();
            return item.value().copy();
        }

        public @Nullable FluidStack fluidStack() {
            if (visualStack.isEmpty() || !visualStack.isFluid()) {
                return null;
            }
            ContentStack<Fluid, FluidStack> fluid = visualStack.cast();
            return fluid.value().copy();
        }

        private static RecipeContent defaultContent(
            int amount,
            double chance,
            @Nullable ItemStack itemStack,
            @Nullable FluidStack fluidStack
        ) {
            if (itemStack != null && !itemStack.isEmpty()) {
                return OutputContent.fromItemStack(itemStack, Math.max(1, amount), (float) chance);
            }
            if (fluidStack != null && !fluidStack.isEmpty()) {
                return OutputContent.fromFluidStack(fluidStack, Math.max(1, amount), (float) chance);
            }
            return RecipeContent.empty();
        }
    }

    /**
     * Duration formatted as seconds.
     */
    public String durationFormatted() {
        double seconds = durationTicks / 20.0;
        if (seconds < 60) return String.format("%.1fs", seconds);
        double minutes = seconds / 60;
        if (minutes < 60) return String.format("%.1fm", minutes);
        return String.format("%.1fh", minutes / 60);
    }

    private static ContentStack<?, ?> contentStack(@Nullable ItemStack itemStack) {
        return itemStack == null || itemStack.isEmpty() ? ContentStack.empty() : ContentStack.fromStack(itemStack);
    }

    private static ContentStack<?, ?> contentStack(@Nullable FluidStack fluidStack) {
        return fluidStack == null || fluidStack.isEmpty() ? ContentStack.empty() : ContentStack.fromStack(fluidStack);
    }

    private static ContentStack<?, ?> contentStack(
        @Nullable ItemStack itemStack,
        @Nullable FluidStack fluidStack,
        int amount
    ) {
        ContentStack<?, ?> item = contentStack(itemStack);
        if (item.notEmpty()) {
            return item.copyWithAmount(Math.max(1, amount));
        }
        ContentStack<?, ?> fluid = contentStack(fluidStack);
        return fluid.isEmpty() ? ContentStack.empty() : fluid.copyWithAmount(Math.max(1, amount));
    }

    private static ContentStack<?, ?> visualStack(RecipeContent content) {
        if (content instanceof StackContent<?, ?> stack) {
            return stack.content();
        }
        if (content instanceof InputContent input && input.isEnumerable()) {
            Object found = input.enumerate()
                    .detect(each -> each instanceof ContentStack<?, ?> stack && stack.notEmpty());
            return found instanceof ContentStack<?, ?> stack ? stack : ContentStack.empty();
        }
        return ContentStack.empty();
    }

}
