package dev.vfyjxf.calculatorcirrus.content.renderer;

import dev.vfyjxf.calculatorcirrus.api.content.IContentRenderer;
import dev.vfyjxf.calculatorcirrus.api.ui.ITooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipContext;
import dev.vfyjxf.calculatorcirrus.helper.LocalizationHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

public class FluidStackContentRenderer implements IContentRenderer<FluidStack> {

    public static final Logger LOGGER = LogManager.getLogger();
    private static final NumberFormat FORMAT = NumberFormat.getIntegerInstance();

    @Override
    public void render(FluidStack content, GuiGraphics graphics, int width, int height, int mouseX, int mouseY, float delta) {
        if (content.isEmpty()) return;
        RenderHelper.drawFluidForGui(graphics, content, 1000, 0, 0, width, height);
    }

    @Override
    public void render(FluidStack content, GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (content.isEmpty()) return;
        RenderHelper.drawFluidForGui(graphics, content, 1000, 0, 0, 16, 16);
    }

    @Nullable
    @Override
    public ITooltip getTooltip(FluidStack content, TooltipContext context) {
        Fluid fluid = content.getFluid();
        try {
            if (fluid.isSame(Fluids.EMPTY)) {
                return null;
            }

            List<Component> tooltip = tooltipHelper(content, context.flag());

            long milliBuckets = (content.getAmount() * 1000L) / FluidType.BUCKET_VOLUME;

            MutableComponent amountString = LocalizationHelper.translatable("tooltip.liquid.amount.with.capacity", FORMAT.format(milliBuckets), FORMAT.format(FluidType.BUCKET_VOLUME));
            tooltip.add(amountString.withStyle(ChatFormatting.GRAY));
            return ITooltip.create(tooltip);
        } catch (RuntimeException e) {
            Component displayName = content.getHoverName();
            LOGGER.error("Failed to get tooltip for fluid: " + displayName.getString(), e);
        }

        return null;
    }

    private static List<Component> tooltipHelper(FluidStack content, TooltipFlag tooltipFlag) {
        List<Component> tooltip = new ArrayList<>();
        Fluid fluid = content.getFluid();
        if (fluid.isSame(Fluids.EMPTY)) {
            return tooltip;
        }

        tooltip.add(content.getHoverName());

        if (tooltipFlag.isAdvanced()) {
            ResourceLocation resourceLocation = BuiltInRegistries.FLUID.getKey(fluid);
            ResourceLocation defaultResourceLocation = BuiltInRegistries.FLUID.getDefaultKey();
            if (resourceLocation != defaultResourceLocation) {
                MutableComponent advancedId = Component.literal(resourceLocation.toString())
                        .withStyle(ChatFormatting.DARK_GRAY);
                tooltip.add(advancedId);
            }
        }

        return tooltip;
    }

}
