package dev.vfyjxf.calculatorcirrus.content.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.vfyjxf.calculatorcirrus.api.content.render.StackRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.util.NumberUtils;
import dev.vfyjxf.cloudlib.api.ui.text.RichTooltip;
import dev.vfyjxf.cloudlib.api.ui.widget.TooltipContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;

import java.util.function.LongFunction;

public class FluidStackRenderer implements StackRenderer<Fluid, FluidStack> {

    private static final int TEXTURE_SIZE = 16;

    public static final FluidStackRenderer instance = new FluidStackRenderer();

    private FluidStackRenderer() {}

    @Override
    public void render(GuiGraphics graphics, FluidStack stack, int width, int height, float delta) {
        renderBase(graphics, stack, width, height, delta);
        decorate(graphics, stack, width, height, delta);
    }

    @Override
    public void render(GuiGraphics graphics, ContentStack<Fluid, FluidStack> stack, int width, int height, float delta) {
        renderBase(graphics, stack, width, height, delta);
        decorate(graphics, stack, width, height, delta);
    }

    @Override
    public void renderBase(GuiGraphics graphics, FluidStack stack, int width, int height, float delta) {
        if (stack.isEmpty()) return;
        //render code from jei
        var fluid = stack.getFluid();
        var extension = IClientFluidTypeExtensions.of(fluid);
        var stillTexture = extension.getStillTexture();
        var minecraft = Minecraft.getInstance();
        TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillTexture);
        if (sprite.atlasLocation() == MissingTextureAtlasSprite.getLocation()) return;

        int color = extension.getTintColor();
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        Matrix4f matrix = graphics.pose().last().pose();
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >> 24) & 0xFF) / 255F;

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        Tesselator tesselator = Tesselator.getInstance();

        float uMin = sprite.getU0();
        float uMax = sprite.getU1();
        float vMin = sprite.getV0();
        float vMax = sprite.getV1();

        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(matrix, 0, 0, 0).setColor(red, green, blue, alpha).setUv(uMin, vMin);
        buffer.addVertex(matrix, 0, height, 0).setColor(red, green, blue, alpha).setUv(uMin, vMax);
        buffer.addVertex(matrix, width, height, 0).setColor(red, green, blue, alpha).setUv(uMax, vMax);
        buffer.addVertex(matrix, width, 0, 0).setColor(red, green, blue, alpha).setUv(uMax, vMin);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    @Override
    public void renderBase(GuiGraphics graphics, ContentStack<Fluid, FluidStack> stack, int width, int height, float delta) {
        if (stack.isEmpty()) return;
        renderBase(graphics, stack.value(), width, height, delta);
    }

    private void decorate(GuiGraphics graphics, FluidStack stack, long amount, int width, int height, float delta, LongFunction<String> formatter) {}

    @Override
    public void decorate(GuiGraphics graphics, FluidStack stack, int width, int height, float delta) {
        decorate(graphics, stack, stack.getAmount(), width, height, delta, NumberUtils::toSuffix);
    }

    @Override
    public void decorate(GuiGraphics graphics, ContentStack<Fluid, FluidStack> stack, int width, int height, float delta) {
        decorate(graphics, stack.value(), stack.amount(), width, height, delta, NumberUtils::toSuffix);
    }

    @Override
    public Font getFont(FluidStack stack, Minecraft minecraft) {
        return minecraft.font;
    }

    @Override
    public RichTooltip getTooltip(FluidStack stack, TooltipContext context) {
        RichTooltip tooltip = RichTooltip.create();
        Fluid fluid = stack.getFluid();
        if (fluid.isSame(Fluids.EMPTY)) {
            return tooltip;
        }
        tooltip.add(stack.getHoverName());
//        if (context.flag().isAdvanced()) {
//            ResourceLocation resourceLocation = BuiltInRegistries.FLUID.getKey(fluid);
//            ResourceLocation defaultResourceLocation = BuiltInRegistries.FLUID.getDefaultKey();
//            if (resourceLocation != defaultResourceLocation) {
//                MutableComponent advancedId = Component.literal(resourceLocation.toString())
//                        .withStyle(ChatFormatting.DARK_GRAY);
//                tooltip.add(advancedId);
//            }
//        }
        return tooltip;
    }

    @Override
    public Component getName(FluidStack stack) {
        return stack.getHoverName();
    }

    @Override
    public String getDisplayName(FluidStack stack) {
        return stack.getHoverName().getString();
    }
}
