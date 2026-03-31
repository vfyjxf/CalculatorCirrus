package dev.vfyjxf.cirrus.ui.process;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents a single process step (recipe) in the flow chart.
 *
 * @param id              unique identifier
 * @param name            display name (e.g. "Blast Furnace Smelting")
 * @param machine         machine/device name (e.g. "Electric Blast Furnace")
 * @param inputs          input material slots
 * @param outputs         output material slots
 * @param durationTicks   processing time in game ticks
 * @param euPerTick       energy consumption per tick (EU/t)
 * @param tier            processing tier (0=ULV, 1=LV, 2=MV, ...)
 * @param machineOptions  all machines that can execute this recipe (empty = no icon data)
 * @param recipe          source recipe entry used for native preview/rendering when available
 */
public record ProcessNode(
        String id,
        String name,
        String machine,
        List<MaterialSlot> inputs,
        List<MaterialSlot> outputs,
        int durationTicks,
        int euPerTick,
        int tier,
        List<MachineInfo> machineOptions,
        @Nullable RecipeEntry<?> recipe
) {

    /**
     * Info about one possible machine that can run this recipe.
     *
     * @param name      display name
     * @param icon      optional ItemStack for rendering (may be null or empty)
     */
    public record MachineInfo(String name, @Nullable ItemStack icon) {
        public MachineInfo(String name) {
            this(name, null);
        }
    }

    /**
     * A single material input/output slot.
     *
     * @param materialName display name
     * @param amount       quantity
     * @param type         material type for colour coding
     * @param chance       output chance (1.0 = 100%, only meaningful for outputs)
     * @param isCatalyst   true if this material is not consumed
     * @param content      structured recipe content payload for exact / tag / alternative inputs
     * @param itemStack    optional ItemStack for rendering the actual item icon
     * @param fluidStack   optional FluidStack for rendering the actual fluid icon
     */
    public record MaterialSlot(
            String materialName,
            int amount,
            MaterialType type,
            double chance,
            boolean isCatalyst,
            RecipeContent content,
            @Nullable ItemStack itemStack,
            @Nullable FluidStack fluidStack
    ) {
        public MaterialSlot(
            String materialName,
            int amount,
            MaterialType type,
            double chance,
            boolean isCatalyst,
            @Nullable ItemStack itemStack,
            @Nullable FluidStack fluidStack
        ) {
            this(
                materialName,
                amount,
                type,
                chance,
                isCatalyst,
                defaultContent(amount, chance, itemStack, fluidStack),
                itemStack,
                fluidStack
            );
        }

        public MaterialSlot(String materialName, int amount, MaterialType type) {
            this(materialName, amount, type, 1.0, false, RecipeContent.empty(), null, null);
        }

        public MaterialSlot(String materialName, int amount, MaterialType type, boolean isCatalyst) {
            this(materialName, amount, type, 1.0, isCatalyst, RecipeContent.empty(), null, null);
        }

        /** Convenience constructor with ItemStack. */
        public static MaterialSlot item(ItemStack stack, int amount) {
            return new MaterialSlot(stack.getHoverName().getString(), amount,
                    MaterialType.ITEM, 1.0, false, OutputContent.fromItemStack(stack, Math.max(1, amount)), stack.copyWithCount(amount), null);
        }

        /** Convenience constructor with ItemStack + chance. */
        public static MaterialSlot item(ItemStack stack, int amount, double chance) {
            return new MaterialSlot(stack.getHoverName().getString(), amount,
                    MaterialType.ITEM, chance, false, OutputContent.fromItemStack(stack, Math.max(1, amount)), stack.copyWithCount(amount), null);
        }

        /** Convenience constructor for catalyst items. */
        public static MaterialSlot catalyst(ItemStack stack, int amount) {
            return new MaterialSlot(stack.getHoverName().getString(), amount,
                    MaterialType.CATALYST, 1.0, true, OutputContent.fromItemStack(stack, Math.max(1, amount)), stack.copyWithCount(amount), null);
        }

        /** Convenience constructor with FluidStack. */
        public static MaterialSlot fluid(FluidStack stack, int amount) {
            return new MaterialSlot(stack.getHoverName().getString(), amount,
                    MaterialType.FLUID, 1.0, false, OutputContent.fromFluidStack(stack, Math.max(1, amount)), null, stack.copyWithAmount(amount));
        }

        /** Convenience constructor for energy. */
        public static MaterialSlot energy(String name, int amount) {
            return new MaterialSlot(name, amount, MaterialType.ENERGY, 1.0, false, RecipeContent.empty(), null, null);
        }

        /** Whether this slot has a renderable visual (item or fluid). */
        public boolean hasVisual() {
            return (itemStack != null && !itemStack.isEmpty()) || (fluidStack != null && !fluidStack.isEmpty());
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
     * Material classification for colour coding edges and slots.
     */
    public enum MaterialType {
        ITEM(0xFFE0A030),       // amber/orange for solid items
        FLUID(0xFF3080E0),      // blue for fluids
        ENERGY(0xFFE03030),     // red for energy
        GAS(0xFF30E0E0),        // cyan for gases
        CATALYST(0xFFA030E0);   // purple for catalysts

        public final int color;

        MaterialType(int color) {
            this.color = color;
        }
    }

    /**
     * Tier name lookup.
     */
    public String tierName() {
        return switch (tier) {
            case 0 -> "ULV";
            case 1 -> "LV";
            case 2 -> "MV";
            case 3 -> "HV";
            case 4 -> "EV";
            case 5 -> "IV";
            case 6 -> "LuV";
            case 7 -> "ZPM";
            case 8 -> "UV";
            case 9 -> "UHV";
            default -> "T" + tier;
        };
    }

    /**
     * Colour associated with this tier.
     */
    public int tierColor() {
        return switch (tier) {
            case 0 -> 0xFF888888;
            case 1 -> 0xFFDCDCDC;
            case 2 -> 0xFFFF6D00;
            case 3 -> 0xFFFFFF00;
            case 4 -> 0xFF00C6FF;
            case 5 -> 0xFF8B00FF;
            case 6 -> 0xFFFF69B4;
            case 7 -> 0xFF00FFCC;
            case 8 -> 0xFF33FF33;
            case 9 -> 0xFFFF3333;
            default -> 0xFFFFFFFF;
        };
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

    /**
     * EU/t formatted with SI suffix.
     */
    public String euFormatted() {
        if (euPerTick < 1000) return euPerTick + " EU/t";
        if (euPerTick < 1_000_000) return String.format("%.1fK EU/t", euPerTick / 1000.0);
        return String.format("%.1fM EU/t", euPerTick / 1_000_000.0);
    }
}
