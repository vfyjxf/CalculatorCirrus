package dev.vfyjxf.cirrus.content.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.vfyjxf.cirrus.api.content.render.BatchableStackRenderer;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.util.NumberUtils;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.LongFunction;

public class FluidStackRenderer implements BatchableStackRenderer<Fluid, FluidStack> {

    public static final FluidStackRenderer instance = new FluidStackRenderer();

    private FluidStackRenderer() {}

    @Override
    public void render(GuiGraphics graphics, FluidStack stack, int width, int height, float delta) {
        renderBase(graphics, stack, width, height, delta);
        decorate(graphics, stack, width, height, delta);
    }

    @Override
    public void render(
        GuiGraphics graphics,
        ContentStack<Fluid, FluidStack> stack,
        int width,
        int height,
        float delta
    ) {
        renderBase(graphics, stack, width, height, delta);
        decorate(graphics, stack, width, height, delta);
    }

    @Override
    public void renderBase(GuiGraphics graphics, FluidStack stack, int width, int height, float delta) {
        renderBaseAt(graphics, stack, 0, 0, width, height, delta);
    }

    @Override
    public void renderBaseAt(GuiGraphics graphics, FluidStack stack, int x, int y, int width, int height, float delta) {
        FluidVisual visual = visualFor(stack);
        if (visual == null) return;
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        addQuad(buffer, matrix, visual, x, y, width, height);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    @Override
    public void renderBase(
        GuiGraphics graphics,
        ContentStack<Fluid, FluidStack> stack,
        int width,
        int height,
        float delta
    ) {
        if (stack.isEmpty()) return;
        renderBase(graphics, stack.value(), width, height, delta);
    }

    @Override
    public void renderBaseBatch(
        GuiGraphics graphics,
        List<BatchableStackRenderer.Entry<Fluid, FluidStack>> entries,
        float delta
    ) {
        renderBaseBatch(graphics, entries, delta, null);
    }

    @Override
    public @Nullable Object createBatchContext() {
        return new BatchContext();
    }

    @Override
    public void renderBaseBatch(
        GuiGraphics graphics,
        List<BatchableStackRenderer.Entry<Fluid, FluidStack>> entries,
        float delta,
        @Nullable Object context
    ) {
        BatchContext batchContext = context instanceof BatchContext typed ? typed : new BatchContext();
        batchContext.begin(graphics);
        BufferBuilder buffer = Objects.requireNonNull(batchContext.buffer());
        Matrix4f matrix = Objects.requireNonNull(batchContext.matrix());
        for (BatchableStackRenderer.Entry<Fluid, FluidStack> entry : entries) {
            ContentStack<Fluid, FluidStack> stack = entry.stack();
            if (stack.isEmpty()) {
                continue;
            }
            FluidVisual visual = visualFor(stack.value());
            if (visual == null) {
                continue;
            }
            addQuad(buffer, matrix, visual, entry.x(), entry.y(), entry.width(), entry.height());
        }
        batchContext.flush();
    }

    private static @Nullable FluidVisual visualFor(FluidStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        var extension = IClientFluidTypeExtensions.of(stack.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(extension.getStillTexture());
        if (sprite.atlasLocation() == MissingTextureAtlasSprite.getLocation()) {
            return null;
        }
        int color = extension.getTintColor();
        return new FluidVisual(
            sprite,
            (color >> 16 & 0xFF) / 255.0F,
            (color >> 8 & 0xFF) / 255.0F,
            (color & 0xFF) / 255.0F,
            ((color >> 24) & 0xFF) / 255F
        );
    }

    private static void addQuad(
        BufferBuilder buffer,
        Matrix4f matrix,
        FluidVisual visual,
        int x,
        int y,
        int width,
        int height
    ) {
        TextureAtlasSprite sprite = visual.sprite();
        float uMin = sprite.getU0();
        float uMax = sprite.getU1();
        float vMin = sprite.getV0();
        float vMax = sprite.getV1();
        buffer.addVertex(matrix, x, y, 0).setColor(visual.red(), visual.green(), visual.blue(), visual.alpha())
                .setUv(uMin, vMin);
        buffer.addVertex(matrix, x, y + height, 0).setColor(visual.red(), visual.green(), visual.blue(), visual.alpha())
                .setUv(uMin, vMax);
        buffer.addVertex(matrix, x + width, y + height, 0)
                .setColor(visual.red(), visual.green(), visual.blue(), visual.alpha()).setUv(uMax, vMax);
        buffer.addVertex(matrix, x + width, y, 0).setColor(visual.red(), visual.green(), visual.blue(), visual.alpha())
                .setUv(uMax, vMin);
    }

    private record FluidVisual(TextureAtlasSprite sprite, float red, float green, float blue, float alpha) {}

    private void decorate(
        GuiGraphics graphics,
        FluidStack stack,
        long amount,
        int width,
        int height,
        float delta,
        LongFunction<String> formatter
    ) {}

    @Override
    public void decorate(GuiGraphics graphics, FluidStack stack, int width, int height, float delta) {
        decorate(graphics, stack, stack.getAmount(), width, height, delta, NumberUtils::toSuffix);
    }

    @Override
    public void decorate(
        GuiGraphics graphics,
        ContentStack<Fluid, FluidStack> stack,
        int width,
        int height,
        float delta
    ) {
        decorate(graphics, stack.value(), stack.amount(), width, height, delta, NumberUtils::toSuffix);
    }

    @Override
    public void decorateAt(GuiGraphics graphics, FluidStack stack, int x, int y, int width, int height, float delta) {}

    @Override
    public void decorateBatch(
        GuiGraphics graphics,
        List<BatchableStackRenderer.Entry<Fluid, FluidStack>> entries,
        float delta
    ) {
        decorateBatch(graphics, entries, delta, null);
    }

    @Override
    public void decorateBatch(
        GuiGraphics graphics,
        List<BatchableStackRenderer.Entry<Fluid, FluidStack>> entries,
        float delta,
        @Nullable Object context
    ) {}

    private static final class BatchContext {
        private @Nullable BufferBuilder buffer;
        private @Nullable Matrix4f matrix;

        private void begin(GuiGraphics graphics) {
            buffer = null;
            matrix = null;
            RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            matrix = graphics.pose().last().pose();
            buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        }

        private @Nullable BufferBuilder buffer() {
            return buffer;
        }

        private @Nullable Matrix4f matrix() {
            return matrix;
        }

        private void flush() {
            if (buffer != null) {
                BufferUploader.drawWithShader(buffer.buildOrThrow());
                buffer = null;
                matrix = null;
            }
        }
    }

    @Override
    public Font getFont(FluidStack stack, Minecraft minecraft) {
        return minecraft.font;
    }

    @Override
    public Tooltip getTooltip(FluidStack stack, TooltipContext context) {
        Tooltip tooltip = Tooltip.create();
        Fluid fluid = stack.getFluid();
        if (fluid.isSame(Fluids.EMPTY)) {
            return tooltip;
        }
        tooltip.add(stack.getHoverName());
        if (context.flag().isAdvanced()) {
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

    @Override
    public Component getName(FluidStack stack) {
        return stack.getHoverName();
    }

}
