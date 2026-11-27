package dev.vfyjxf.cirrus.content.renderer;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.vfyjxf.cirrus.api.content.render.BatchableStackRenderer;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.mixin.ItemDecoratorHandlerAccessor;
import dev.vfyjxf.cirrus.util.NumberUtils;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipEntry;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipLocator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ItemDecoratorHandler;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class ItemStackRenderer implements BatchableStackRenderer<Item, ItemStack> {

    public static final ItemStackRenderer instance = new ItemStackRenderer();
    private static final Map<BakedModel, SouthFaceOnlyModel> southFaceModelCache = new IdentityHashMap<>();

    private ItemStackRenderer() {}

    public static String countDecorationText(long amount) {
        return amount <= 1L ? "" : NumberUtils.toSuffix(amount);
    }

    @Override
    public void render(GuiGraphics graphics, ItemStack stack, int width, int height, float delta) {
        renderBase(graphics, stack, width, height, delta);
        decorate(graphics, stack, width, height, delta);
    }

    @Override
    public void renderAt(GuiGraphics graphics, ItemStack stack, int x, int y, int width, int height, float delta) {
        renderBaseAt(graphics, stack, x, y, width, height, delta);
        decorateAt(graphics, stack, x, y, width, height, delta);
    }

    @Override
    public void render(GuiGraphics graphics, ContentStack<Item, ItemStack> stack, int width, int height, float delta) {
        renderBase(graphics, stack.value(), width, height, delta);
        decorate(graphics, stack, width, height, delta);
    }

    @Override
    public void renderAt(
        GuiGraphics graphics,
        ContentStack<Item, ItemStack> stack,
        int x,
        int y,
        int width,
        int height,
        float delta
    ) {
        renderBaseAt(graphics, stack.value(), x, y, width, height, delta);
        decorateAt(graphics, stack, x, y, width, height, delta);
    }

    @Override
    public void renderBase(GuiGraphics graphics, ItemStack stack, int width, int height, float delta) {
        if (stack.isEmpty()) return;
        graphics.pose().pushPose();
        RenderSystem.enableDepthTest();
        graphics.pose().scale(width / 16f, height / 16f, 1f);
        graphics.pose().last().normal().identity();
        graphics.renderFakeItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    @Override
    public void renderBaseAt(GuiGraphics graphics, ItemStack stack, int x, int y, int width, int height, float delta) {
        if (stack.isEmpty()) return;
        RenderSystem.enableDepthTest();
        graphics.pose().pushPose();
        try {
            if (width == 16 && height == 16) {
                graphics.pose().last().normal().identity();
                graphics.renderFakeItem(stack, x, y);
                return;
            }
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(width / 16f, height / 16f, 1f);
            graphics.pose().last().normal().identity();
            graphics.renderFakeItem(stack, 0, 0);
        } finally {
            graphics.pose().popPose();
        }
    }

    @Override
    public void renderBase(
        GuiGraphics graphics,
        ContentStack<Item, ItemStack> stack,
        int width,
        int height,
        float delta
    ) {
        if (stack.isEmpty()) return;
        renderBase(graphics, stack.value(), width, height, delta);
    }

    @Override
    public void renderBaseAt(
        GuiGraphics graphics,
        ContentStack<Item, ItemStack> stack,
        int x,
        int y,
        int width,
        int height,
        float delta
    ) {
        if (stack.isEmpty()) return;
        renderBaseAt(graphics, stack.value(), x, y, width, height, delta);
    }

    @Override
    public void renderBaseBatch(
        GuiGraphics graphics,
        List<BatchableStackRenderer.Entry<Item, ItemStack>> entries,
        float delta
    ) {
        renderBaseBatch(graphics, entries, delta, null);
    }

    @Override
    public void renderBaseBatch(
        GuiGraphics graphics,
        List<BatchableStackRenderer.Entry<Item, ItemStack>> entries,
        float delta,
        @Nullable Object context
    ) {
        if (entries.isEmpty()) {
            return;
        }
        BatchContext batchContext = context instanceof BatchContext typed ? typed : new BatchContext();
        batchContext.resetForBase();
        Lighting.setupFor3DItems();
        try {
            for (BatchableStackRenderer.Entry<Item, ItemStack> entry : entries) {
                ItemStack stack = entry.stack().value();
                if (stack.isEmpty()) {
                    continue;
                }
                batchContext.renderBase(graphics, stack, entry.x(), entry.y(), entry.width(), entry.height(), delta);
            }
        } finally {
            Lighting.setupForFlatItems();
        }
    }

    @Override
    public @Nullable Object createBatchContext() {
        return new BatchContext();
    }

    @Override
    public void decorateBatch(
        GuiGraphics graphics,
        List<BatchableStackRenderer.Entry<Item, ItemStack>> entries,
        float delta,
        @Nullable Object context
    ) {
        BatchContext batchContext = context instanceof BatchContext typed ? typed : new BatchContext();
        batchContext.resetForDecorate();
        Minecraft minecraft = batchContext.minecraft();
        LocalPlayer player = batchContext.player();
        float partialTick = batchContext.partialTick();
        ArrayList<DecorationEntry> batched = batchContext.batched();
        HashMap<Item, DecoratorState> decorators = batchContext.decorators();
        HashMap<Item, Font> fonts = batchContext.fonts();
        boolean hasCountText = false;
        boolean hasOverlay = false;
        boolean hasCustomDecorators = false;
        for (BatchableStackRenderer.Entry<Item, ItemStack> entry : entries) {
            ContentStack<Item, ItemStack> stack = entry.stack();
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack value = stack.value();
            if (value.isEmpty()) {
                continue;
            }
            if (entry.width() == 16 && entry.height() == 16) {
                String countText = countDecorationText(stack.amount());
                float cooldown = player == null
                        ? 0.0F
                        : player.getCooldowns().getCooldownPercent(value.getItem(), partialTick);
                boolean overlay = value.isBarVisible() || cooldown > 0.0F;
                DecoratorState decorator = decorators.computeIfAbsent(value.getItem(), ignored -> {
                    ItemDecoratorHandler handler = ItemDecoratorHandler.of(value); // NOPMD - NeoForge offers no public replacement for the internal decorator handler
                    return new DecoratorState(handler, hasItemDecorators(handler));
                });
                boolean customDecorators = decorator.hasCustomDecorators();
                if (countText.isEmpty() && !overlay && !customDecorators) {
                    continue;
                }
                hasCountText |= !countText.isEmpty();
                hasOverlay |= overlay;
                hasCustomDecorators |= customDecorators;
                Font font = fonts.computeIfAbsent(value.getItem(), ignored -> instance.getFont(value, minecraft));
                batched.add(
                    new DecorationEntry(
                        value,
                        entry.x(),
                        entry.y(),
                        countText,
                        customDecorators,
                        font,
                        decorator.handler(),
                        cooldown
                    )
                );
            } else {
                decorateAt(graphics, stack, entry.x(), entry.y(), entry.width(), entry.height(), delta);
            }
        }
        if (!batched.isEmpty()) {
            if (hasCountText) {
                drawCountTextBatch(graphics, batched);
            }
            if (hasOverlay) {
                drawOverlayBatch(graphics, minecraft, batched);
            }
            if (hasCountText || hasOverlay) {
                graphics.flush();
            }
            if (hasCustomDecorators) {
                for (DecorationEntry entry : batched) {
                    if (entry.customDecorators()) {
                        entry.handler().render(graphics, entry.font(), entry.stack(), entry.x(), entry.y()); // NOPMD - same internal decorator handler
                    }
                }
            }
        }
    }

    private static final class SouthFaceOnlyModel implements BakedModel {
        private final BakedModel delegate;
        private @Nullable List<BakedQuad> quads;

        private SouthFaceOnlyModel(BakedModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction direction,
            RandomSource random
        ) {
            if (direction != null) {
                return Collections.emptyList();
            }
            List<BakedQuad> cached = quads;
            if (cached == null) {
                cached = delegate.getQuads(state, null, random).stream()
                        .filter(quad -> quad.getDirection() == Direction.SOUTH).toList();
                quads = cached;
            }
            return cached;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return delegate.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return delegate.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return delegate.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return delegate.isCustomRenderer();
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return delegate.getParticleIcon();
        }

        @Override
        public ItemTransforms getTransforms() {
            return delegate.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return delegate.getOverrides();
        }
    }

    @Override
    public void decorate(GuiGraphics graphics, ItemStack stack, int width, int height, float delta) {
        if (stack.isEmpty()) return;
        //TODO:support custom number formater,also see FluidStackRenderer
        graphics.pose().pushPose();
        graphics.pose().scale(width / 16f, height / 16f, 1f);
        graphics.renderItemDecorations(
            getFont(stack, Minecraft.getInstance()),
            stack,
            0,
            0,
            countDecorationText(stack.getCount())
        );
        graphics.pose().popPose();
    }

    @Override
    public void decorateAt(GuiGraphics graphics, ItemStack stack, int x, int y, int width, int height, float delta) {
        if (stack.isEmpty()) return;
        if (width == 16 && height == 16) {
            graphics.renderItemDecorations(
                getFont(stack, Minecraft.getInstance()),
                stack,
                x,
                y,
                countDecorationText(stack.getCount())
            );
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(width / 16f, height / 16f, 1f);
        graphics.renderItemDecorations(
            getFont(stack, Minecraft.getInstance()),
            stack,
            0,
            0,
            countDecorationText(stack.getCount())
        );
        graphics.pose().popPose();
    }

    @Override
    public void decorate(
        GuiGraphics graphics,
        ContentStack<Item, ItemStack> stack,
        int width,
        int height,
        float delta
    ) {
        if (stack.isEmpty()) return;
        graphics.pose().pushPose();
        graphics.pose().scale(width / 16f, height / 16f, 1f);
        var content = stack.value();
        graphics.renderItemDecorations(
            getFont(content, Minecraft.getInstance()),
            content,
            0,
            0,
            countDecorationText(stack.amount())
        );
        graphics.pose().popPose();
    }

    @Override
    public void decorateAt(
        GuiGraphics graphics,
        ContentStack<Item, ItemStack> stack,
        int x,
        int y,
        int width,
        int height,
        float delta
    ) {
        if (stack.isEmpty()) return;
        var content = stack.value();
        if (width == 16 && height == 16) {
            graphics.renderItemDecorations(
                getFont(content, Minecraft.getInstance()),
                content,
                x,
                y,
                countDecorationText(stack.amount())
            );
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(width / 16f, height / 16f, 1f);
        graphics.renderItemDecorations(
            getFont(content, Minecraft.getInstance()),
            content,
            0,
            0,
            countDecorationText(stack.amount())
        );
        graphics.pose().popPose();
    }

    private static boolean hasItemDecorators(ItemDecoratorHandler handler) {
        return !((ItemDecoratorHandlerAccessor) (Object) handler).cirrusGetItemDecorators().isEmpty();
    }

    private static void drawCountTextBatch(GuiGraphics graphics, List<DecorationEntry> entries) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        for (DecorationEntry entry : entries) {
            if (!entry.text().isEmpty()) {
                Font font = entry.font();
                font.drawInBatch(
                    entry.text(),
                    entry.x() + 19 - 2 - font.width(entry.text()),
                    entry.y() + 6 + 3,
                    0xFFFFFF,
                    true,
                    graphics.pose().last().pose(),
                    graphics.bufferSource(),
                    Font.DisplayMode.NORMAL,
                    0,
                    15728880
                );
            }
        }
        graphics.pose().popPose();
    }

    private static void drawOverlayBatch(GuiGraphics graphics, Minecraft minecraft, List<DecorationEntry> entries) {
        LocalPlayer player = minecraft.player;
        for (DecorationEntry entry : entries) {
            ItemStack stack = entry.stack();
            int x = entry.x();
            int y = entry.y();
            if (stack.isBarVisible()) {
                int width = stack.getBarWidth();
                int color = stack.getBarColor();
                int barX = x + 2;
                int barY = y + 13;
                graphics.fill(RenderType.guiOverlay(), barX, barY, barX + 13, barY + 2, 0xFF000000);
                graphics.fill(RenderType.guiOverlay(), barX, barY, barX + width, barY + 1, color | 0xFF000000);
            }
            float cooldown = player == null ? 0.0F : entry.cooldown();
            if (cooldown > 0.0F) {
                int top = y + Mth.floor(16.0F * (1.0F - cooldown));
                int bottom = top + Mth.ceil(16.0F * cooldown);
                graphics.fill(RenderType.guiOverlay(), x, top, x + 16, bottom, Integer.MAX_VALUE);
            }
        }
    }

    private static final class BatchContext {
        private final Minecraft minecraft = Minecraft.getInstance();
        private final ArrayList<DecorationEntry> batched = new ArrayList<>();
        private final HashMap<Item, DecoratorState> decorators = new HashMap<>();
        private final HashMap<Item, Font> fonts = new HashMap<>();
        private final LocalPlayer player = minecraft.player;
        private final float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(true);

        private void resetForBase() {
            batched.clear();
            decorators.clear();
            fonts.clear();
        }

        private void resetForDecorate() {
            batched.clear();
            decorators.clear();
            fonts.clear();
        }

        private void renderBase(
            GuiGraphics graphics,
            ItemStack stack,
            int x,
            int y,
            int width,
            int height,
            float delta
        ) {
            if (stack.isEmpty()) {
                return;
            }
            graphics.pose().pushPose();
            try {
                RenderSystem.enableDepthTest();
                if (width == 16 && height == 16) {
                    graphics.pose().last().normal().identity();
                    graphics.renderFakeItem(stack, x, y);
                    return;
                }
                graphics.pose().translate(x, y, 0);
                graphics.pose().scale(width / 16f, height / 16f, 1f);
                graphics.pose().last().normal().identity();
                graphics.renderFakeItem(stack, 0, 0);
            } finally {
                graphics.pose().popPose();
            }
        }

        private Minecraft minecraft() {
            return minecraft;
        }

        private LocalPlayer player() {
            return player;
        }

        private float partialTick() {
            return partialTick;
        }

        private ArrayList<DecorationEntry> batched() {
            return batched;
        }

        private HashMap<Item, DecoratorState> decorators() {
            return decorators;
        }

        private HashMap<Item, Font> fonts() {
            return fonts;
        }
    }

    private record DecoratorState(ItemDecoratorHandler handler, boolean hasCustomDecorators) {}

    private record DecorationEntry(
        ItemStack stack,
        int x,
        int y,
        String text,
        boolean customDecorators,
        Font font,
        ItemDecoratorHandler handler,
        float cooldown
    ) {}

    @Override
    public Font getFont(ItemStack stack, Minecraft minecraft) {
        IClientItemExtensions extensions = IClientItemExtensions.of(stack);
        Font font = extensions.getFont(stack, IClientItemExtensions.FontContext.TOOLTIP);
        return font == null ? minecraft.font : font;
    }

    @Override
    public Tooltip getTooltip(ItemStack stack, TooltipContext context) {
        if (stack.isEmpty()) return new Tooltip();
        var tooltip = new Tooltip();
        var lines = stack.getTooltipLines(context, context.player(), context.flag());
        for (var line : lines) tooltip.add(line);
        var tooltipImage = stack.getTooltipImage();
        tooltipImage.ifPresent(
            tooltipComponent -> tooltip
                    .insert(TooltipEntry.component(tooltipComponent), Tooltip.body, TooltipLocator.after(Tooltip.title))
        );
        return tooltip;
    }

    @Override
    public Component getName(ItemStack stack) {
        return stack.getDisplayName();
    }

}
