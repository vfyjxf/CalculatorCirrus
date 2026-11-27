package dev.vfyjxf.cirrus.ui.execution.widget;

import com.mojang.datafixers.util.Either;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.internal.integration.jei.CirrusJeiPlugin;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.execution.tooltip.JeiRecipeTooltipComponent;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiRecipeGrid;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiSlotAmount;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipStack;
import dev.vfyjxf.taffy.style.Overflow;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiKeyMapping;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.overflow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Renders a JEI recipe layout in our OreUI theme using JEI's live {@link IRecipeLayoutDrawable} slot/category APIs, with a one-click toggle to the native JEI canvas.
 */
public final class JeiRecipePreviewWidget extends Widget {

    private static final int headerH = 20;
    private static final int pad = 6;
    private static final int slotSize = 18;
    private static final int slotStride = slotSize - 1;
    private static final int arrowW = 22;

    private final RecipeEntry<?> recipe;
    private final IRecipeLayoutDrawable<?> layout;
    private final int nativeWidth;
    private final int nativeHeight;
    private final int borderOffsetX;
    private final int borderOffsetY;
    private final int previewWidth;
    private final int previewHeight;
    private final List<IRecipeSlotView> inputSlots;
    private final List<IRecipeSlotView> outputSlots;
    private final List<IRecipeSlotView> catalystSlots;
    private final boolean canRenderOreUi;
    private final List<SlotHit> renderedSlots = new ArrayList<>();
    private @Nullable Rect modeToggleRect;
    private boolean nativeMode;

    private record SlotHit(Rect rect, IRecipeSlotView view) {}

    public static @Nullable JeiRecipePreviewWidget create(@Nullable RecipeEntry<?> recipe) {
        IRecipeLayoutDrawable<?> layout = JeiRecipeTooltipComponent.createIndependentLayoutDrawable(recipe);
        return recipe == null || layout == null ? null : new JeiRecipePreviewWidget(recipe, layout);
    }

    private JeiRecipePreviewWidget(RecipeEntry<?> recipe, IRecipeLayoutDrawable<?> layout) {
        this.recipe = recipe;
        this.layout = layout;
        layout.setPosition(0, 0);
        Rect2i rect = layout.getRectWithBorder();
        this.nativeWidth = Math.max(1, rect.getWidth());
        this.nativeHeight = Math.max(1, rect.getHeight());
        this.borderOffsetX = rect.getX();
        this.borderOffsetY = rect.getY();

        List<IRecipeSlotView> ins = new ArrayList<>();
        List<IRecipeSlotView> outs = new ArrayList<>();
        List<IRecipeSlotView> cats = new ArrayList<>();
        try {
            for (IRecipeSlotView slotView : layout.getRecipeSlotsView().getSlotViews()) {
                if (slotView == null || slotView.isEmpty()) {
                    continue;
                }
                RecipeIngredientRole role = slotView.getRole();
                if (role == RecipeIngredientRole.INPUT) {
                    ins.add(slotView);
                } else if (role == RecipeIngredientRole.OUTPUT) {
                    outs.add(slotView);
                } else if (role == RecipeIngredientRole.CATALYST) {
                    cats.add(slotView);
                }
            }
        } catch (RuntimeException | LinkageError ignored) {}
        this.inputSlots = List.copyOf(ins);
        this.outputSlots = List.copyOf(outs);
        this.catalystSlots = List.copyOf(cats);
        this.canRenderOreUi = !this.inputSlots.isEmpty() || !this.outputSlots.isEmpty();
        this.nativeMode = !this.canRenderOreUi;

        int inCols = inputColumns(inputSlots.size());
        int inRows = inCols == 0 ? 0 : (inputSlots.size() + inCols - 1) / inCols;
        int outCols = outputColumns(outputSlots.size());
        int outRows = outCols == 0 ? 0 : (outputSlots.size() + outCols - 1) / outCols;
        int inGridW = inCols == 0 ? 0 : inCols * slotStride + 1;
        int outGridW = outCols == 0 ? 0 : outCols * slotStride + 1;
        int catW = catalystSlots.isEmpty() ? 0 : (Math.min(2, catalystSlots.size()) * slotStride + 1) + 8;
        int styledBodyW = pad * 2 + inGridW + (inCols > 0 && outCols > 0 ? arrowW : 0) + outGridW + catW;
        int styledBodyH = pad * 2
                + Math.max(slotSize, Math.max(inRows, outRows) * slotStride + 1)
                + (hasMetadataStrip() ? 16 : 0);

        this.previewWidth = Math.max(216, Math.max(styledBodyW, nativeWidth + pad * 2));
        this.previewHeight = headerH + Math.max(styledBodyH, nativeHeight + pad * 2);
        useStyle(sizeOf(previewWidth, previewHeight), boxSizingBorderBox(), overflow(Overflow.HIDDEN));
        setInteractive(true);
        setTickable(true);
        onMouseClick((input, clickCount, ctx) -> {
            if (!input.isLeftClick() || !canRenderOreUi) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            if (modeToggleRect != null && modeToggleRect.contains(local.x(), local.y())) {
                nativeMode = !nativeMode;
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
        onHoverTooltip(this::tooltipAt);
    }

    public boolean matches(@Nullable RecipeEntry<?> recipe) {
        return recipe != null && recipe.equals(this.recipe);
    }

    public int previewWidth() {
        return previewWidth;
    }

    public int previewHeight() {
        return previewHeight;
    }

    public boolean nativeMode() {
        return nativeMode;
    }

    public static float contentScale(int availableWidth, int availableHeight, int previewWidth, int previewHeight) {
        if (availableWidth <= 0 || availableHeight <= 0 || previewWidth <= 0 || previewHeight <= 0) {
            return 0.0f;
        }
        return Math.min(1.0f, Math.min(availableWidth / (float) previewWidth, availableHeight / (float) previewHeight));
    }

    @Override
    public void tick() {
        super.tick();
        JeiRecipeTooltipComponent.tickLayoutOncePerClientTick(layout);
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        int w = width();
        int h = height();
        if (w <= 0 || h <= 0) {
            return;
        }
        CirrusPalette palette = CirrusUiTheme.palette(this);
        renderedSlots.clear();
        modeToggleRect = null;

        OreUiPaint.raisedCard(canvas, 0, 0, w, h, palette, false, false);
        OreUiPaint.headerBand(canvas, 0, 0, w, headerH, palette, false);

        IRecipeCategory<?> category = layout.getRecipeCategory();
        int leftX = pad;
        IDrawable catIcon = category == null ? null : category.getIcon();
        if (catIcon != null) {
            var graphics = canvas.layeredGraphics();
            graphics.pose().pushPose();
            graphics.pose().translate(leftX, (headerH - 12) / 2.0f, 0f);
            graphics.pose().scale(0.75f, 0.75f, 1.0f);
            catIcon.draw(graphics, 0, 0);
            graphics.pose().popPose();
            leftX += 16;
        }

        int rightX = w - pad;
        if (canRenderOreUi) {
            Component toggleLbl = Component.literal(nativeMode ? "JEI" : "OreUI");
            int tw = Math.max(32, ChromeText.width(canvas, toggleLbl) + 10);
            modeToggleRect = new Rect(rightX - tw, 3, tw, headerH - 6);
            boolean hov = modeToggleRect.contains(mouseX, mouseY);
            OreUiPaint.raisedButton(
                canvas,
                modeToggleRect.x(),
                modeToggleRect.y(),
                modeToggleRect.width(),
                modeToggleRect.height(),
                palette,
                false,
                hov,
                false,
                false
            );
            ChromeText.draw(
                canvas,
                toggleLbl,
                modeToggleRect.x() + (modeToggleRect.width() - ChromeText.width(canvas, toggleLbl)) / 2,
                ChromeText.centeredButtonY(modeToggleRect.y(), modeToggleRect.height(), canvas),
                hov ? palette.focusRing() : palette.textSecondary()
            );
            rightX = modeToggleRect.x() - 4;
        }
        int badgeW = OreUiPaint.tagPill(
            canvas,
            Math.max(leftX + 36, rightX - 24),
            3,
            OreUiPaint.minPillHeight,
            Component.literal("JEI"),
            palette.focusRing(),
            palette
        );
        rightX = Math.min(rightX, Math.max(leftX + 36, rightX - badgeW)) - 4;

        Component title = category != null ? category.getTitle() : Component.literal("JEI Recipe");
        ChromeText.drawFitted(
            canvas,
            title,
            leftX,
            ChromeText.centeredY(0, headerH, canvas),
            Math.max(16, rightX - leftX),
            palette.textPrimary()
        );

        int bodyY = headerH + 1;
        int bodyH = Math.max(1, h - bodyY - 2);
        if (nativeMode || !canRenderOreUi) {
            renderNativeJeiBody(canvas, bodyY, w, bodyH);
        } else {
            renderOreUiJeiBody(canvas, palette, bodyY, w, bodyH, mouseX, mouseY);
        }
    }

    private void renderOreUiJeiBody(
        SceneCanvas canvas,
        CirrusPalette palette,
        int bodyY,
        int w,
        int bodyH,
        int mouseX,
        int mouseY
    ) {
        int inCols = inputColumns(inputSlots.size());
        int inRows = inCols == 0 ? 0 : (inputSlots.size() + inCols - 1) / inCols;
        int outCols = outputColumns(outputSlots.size());
        int outRows = outCols == 0 ? 0 : (outputSlots.size() + outCols - 1) / outCols;
        int inW = inCols == 0 ? 0 : inCols * slotStride + 1;
        int inH = inRows == 0 ? 0 : inRows * slotStride + 1;
        int outW = outCols == 0 ? 0 : outCols * slotStride + 1;
        int outH = outRows == 0 ? 0 : outRows * slotStride + 1;
        boolean hasArrow = inCols > 0 && outCols > 0;
        int catShown = Math.min(2, catalystSlots.size());
        int catW = catShown == 0 ? 0 : catShown * slotStride + 1;
        int totalFlowW = inW + (hasArrow ? arrowW : 0) + outW + (catW > 0 ? 8 + catW : 0);
        int flowH = Math.max(slotSize, Math.max(inH, outH));
        int metaH = hasMetadataStrip() ? 16 : 0;

        int startX = Math.max(pad, (w - totalFlowW) / 2);
        int flowY = bodyY + Math.max(pad, (bodyH - flowH - metaH) / 2);

        // Input grid
        if (inCols > 0) {
            int inY = flowY + (flowH - inH) / 2;
            renderSlotMatrix(canvas, palette, inputSlots, startX, inY, inCols, inRows, false, mouseX, mouseY);
        }

        // Sleek OreUI recipe arrow
        int outX = startX + inW + (hasArrow ? arrowW : 0);
        if (hasArrow) {
            int midY = flowY + flowH / 2;
            OreUiRecipeGrid.drawRecipeArrow(canvas, startX + inW + 4, outX - 4, midY, palette, false);
        }

        // Output grid
        if (outCols > 0) {
            int outY = flowY + (flowH - outH) / 2;
            renderSlotMatrix(canvas, palette, outputSlots, outX, outY, outCols, outRows, true, mouseX, mouseY);
        }

        // Optional catalyst slots
        if (catShown > 0) {
            int catX = outX + outW + 8;
            int catY = flowY + (flowH - slotSize) / 2;
            renderSlotMatrix(
                canvas,
                palette,
                catalystSlots.subList(0, catShown),
                catX,
                catY,
                catShown,
                1,
                false,
                mouseX,
                mouseY
            );
        }

        // Optional metadata pills (e.g. Anvil repair, Furnace cook time/XP)
        if (metaH > 0) {
            renderMetadataStrip(canvas, palette, pad, flowY + flowH + 4, w - pad * 2);
        }
    }

    private void renderSlotMatrix(
        SceneCanvas canvas,
        CirrusPalette palette,
        List<IRecipeSlotView> slots,
        int x,
        int y,
        int cols,
        int rows,
        boolean outputSide,
        int mouseX,
        int mouseY
    ) {
        int totalCells = cols * rows;
        for (int i = 0; i < totalCells; i++) {
            int col = i % cols;
            int row = i / cols;
            int sx = x + col * slotStride;
            int sy = y + row * slotStride;
            OreUiPaint.slot(canvas, sx, sy, slotSize, palette);
        }
        if (outputSide && !slots.isEmpty()) {
            canvas.strokeRect(
                x + 1,
                y + 1,
                slotSize - 2,
                slotSize - 2,
                OreUiPaint.withAlpha(palette.costS(), 0.48f),
                1
            );
        }
        IIngredientManager manager = CirrusJeiPlugin.ingredientManagerOrNull();
        GuiGraphics graphics = canvas.layeredGraphics();
        for (int i = 0; i < slots.size() && i < totalCells; i++) {
            int col = i % cols;
            int row = i / cols;
            int sx = x + col * slotStride;
            int sy = y + row * slotStride;
            IRecipeSlotView view = slots.get(i);
            Rect rect = new Rect(sx, sy, slotSize, slotSize);
            renderedSlots.add(new SlotHit(rect, view));
            Optional<ITypedIngredient<?>> displayed = view.getDisplayedIngredient();
            if (displayed.isPresent()) {
                renderTypedIngredient(canvas, graphics, manager, displayed.get(), sx, sy, palette);
            }
            if (rect.contains(mouseX, mouseY)) {
                canvas.strokeRect(sx, sy, slotSize, slotSize, palette.focusRing(), 1);
            }
        }
    }

    private static <T> void renderTypedIngredient(
        SceneCanvas canvas,
        GuiGraphics graphics,
        @Nullable IIngredientManager manager,
        ITypedIngredient<T> typed,
        int sx,
        int sy,
        CirrusPalette palette
    ) {
        if (typed.getType().equals(VanillaTypes.ITEM_STACK)) {
            Optional<ItemStack> opt = typed.getIngredient(VanillaTypes.ITEM_STACK);
            if (opt.isPresent() && !opt.get().isEmpty()) {
                ItemStack stack = opt.get();
                ItemStack single = stack.getCount() > 1 ? stack.copyWithCount(1) : stack;
                graphics.renderFakeItem(single, sx + 1, sy + 1);
                if (stack.isBarVisible()) {
                    graphics.renderItemDecorations(canvas.font(), single, sx + 1, sy + 1);
                }
                if (stack.getCount() > 1) {
                    OreUiSlotAmount.drawInSlot(canvas, sx, sy, slotSize, stack.getCount(), palette);
                }
                return;
            }
        }
        if (manager == null) {
            return;
        }
        try {
            IIngredientType<T> type = typed.getType();
            IIngredientRenderer<T> renderer = manager.getIngredientRenderer(type);
            renderer.render(graphics, typed.getIngredient(), sx + 1, sy + 1);
        } catch (RuntimeException | LinkageError ignored) {}
    }

    private boolean hasMetadataStrip() {
        Object raw = unwrapRecipe(layout.getRecipe());
        return raw instanceof AbstractCookingRecipe || raw instanceof IJeiAnvilRecipe;
    }

    private void renderMetadataStrip(SceneCanvas canvas, CirrusPalette palette, int x, int y, int maxW) {
        Object raw = unwrapRecipe(layout.getRecipe());
        int cx = x;
        if (raw instanceof AbstractCookingRecipe cooking) {
            int ticks = cooking.getCookingTime();
            if (ticks > 0) {
                double sec = ticks / 20.0;
                String lbl = sec == Math.floor(sec) ? ((int) sec) + "s" : String.format(Locale.ROOT, "%.1fs", sec);
                cx += OreUiPaint.tagPill(
                    canvas,
                    cx,
                    y,
                    OreUiPaint.minPillHeight,
                    Component.literal("Time " + lbl),
                    palette.costP(),
                    palette
                ) + 4;
            }
            float xp = cooking.getExperience();
            if (xp > 0f && cx + 40 <= x + maxW) {
                String xpLbl = String.format(Locale.ROOT, "%.1f XP", xp);
                OreUiPaint.tagPill(
                    canvas,
                    cx,
                    y,
                    OreUiPaint.minPillHeight,
                    Component.literal(xpLbl),
                    palette.costS(),
                    palette
                );
            }
        } else if (raw instanceof IJeiAnvilRecipe) {
            OreUiPaint.tagPill(
                canvas,
                cx,
                y,
                OreUiPaint.minPillHeight,
                Component.literal("Anvil Combine / Repair"),
                palette.costH(),
                palette
            );
        }
    }

    private static @Nullable Object unwrapRecipe(@Nullable Object raw) {
        return raw instanceof RecipeHolder<?> holder ? holder.value() : raw;
    }

    private void renderNativeJeiBody(SceneCanvas canvas, int bodyY, int w, int bodyH) {
        int availableWidth = Math.max(0, w - pad * 2);
        int availableHeight = Math.max(0, bodyH - pad * 2);
        float scale = contentScale(availableWidth, availableHeight, nativeWidth, nativeHeight);
        if (scale <= 0.0f) {
            return;
        }
        int scaledWidth = Math.round(nativeWidth * scale);
        int scaledHeight = Math.round(nativeHeight * scale);
        int x = Math.max(pad, (w - scaledWidth) / 2);
        int y = bodyY + Math.max(pad, (bodyH - scaledHeight) / 2);
        var graphics = canvas.layeredGraphics();
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        layout.setPosition(-borderOffsetX, -borderOffsetY);
        layout.drawRecipe(graphics, -1, -1);
        graphics.pose().popPose();
    }

    private static int inputColumns(int count) {
        if (count <= 0) {
            return 0;
        }
        if (count <= 3) {
            return count;
        }
        if (count == 4) {
            return 2;
        }
        return 3;
    }

    private static int outputColumns(int count) {
        if (count <= 0) {
            return 0;
        }
        return Math.min(3, count);
    }

    private @Nullable Tooltip tooltipAt(double localMouseX, double localMouseY) {
        if (modeToggleRect != null && modeToggleRect.contains(localMouseX, localMouseY)) {
            Tooltip tip = Tooltip.create();
            tip.add(
                Component.literal(
                    nativeMode ? "Switch to OreUI styled recipe view" : "Switch to native JEI recipe canvas"
                )
            );
            return tip;
        }
        if (!nativeMode && canRenderOreUi) {
            for (SlotHit hit : renderedSlots) {
                if (hit.rect().contains(localMouseX, localMouseY)) {
                    return slotViewTooltip(hit.view());
                }
            }
            return null;
        }
        LayoutMouse mouse = layoutMouse(localMouseX, localMouseY);
        if (mouse == null) {
            return null;
        }
        layout.setPosition(-borderOffsetX, -borderOffsetY);
        Optional<RecipeSlotUnderMouse> hoveredSlot = layout.getSlotUnderMouse(mouse.x(), mouse.y());
        if (hoveredSlot.isEmpty()) {
            return null;
        }
        try {
            return slotTooltip(hoveredSlot.get());
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @SuppressWarnings("removal")
    private @Nullable Tooltip slotViewTooltip(IRecipeSlotView view) {
        if (view instanceof IRecipeSlotDrawable drawable) {
            try {
                JeiTooltipBridge bridge = new JeiTooltipBridge();
                drawable.getTooltip(bridge);
                Tooltip tip = bridge.toTooltip();
                if (tip != null) {
                    return tip;
                }
            } catch (RuntimeException | LinkageError ignored) {}
        }
        Optional<ITypedIngredient<?>> displayed = view.getDisplayedIngredient();
        if (displayed.isEmpty()) {
            return null;
        }
        return typedIngredientTooltip(displayed.get());
    }

    private static <T> @Nullable Tooltip typedIngredientTooltip(ITypedIngredient<T> typed) {
        IIngredientManager manager = CirrusJeiPlugin.ingredientManagerOrNull();
        if (manager == null) {
            return null;
        }
        try {
            IIngredientRenderer<T> renderer = manager.getIngredientRenderer(typed.getType());
            JeiTooltipBridge bridge = new JeiTooltipBridge();
            bridge.setIngredient(typed);
            TooltipFlag flag = Minecraft.getInstance().options.advancedItemTooltips
                    ? TooltipFlag.Default.ADVANCED
                    : TooltipFlag.Default.NORMAL;
            renderer.getTooltip(bridge, typed.getIngredient(), flag);
            return bridge.toTooltip();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private @Nullable LayoutMouse layoutMouse(double localMouseX, double localMouseY) {
        int bodyY = headerH + 1;
        int bodyH = Math.max(1, height() - bodyY - 2);
        int availableWidth = Math.max(0, width() - pad * 2);
        int availableHeight = Math.max(0, bodyH - pad * 2);
        float scale = contentScale(availableWidth, availableHeight, nativeWidth, nativeHeight);
        if (scale <= 0.0f) {
            return null;
        }
        int scaledWidth = Math.round(nativeWidth * scale);
        int scaledHeight = Math.round(nativeHeight * scale);
        int x = Math.max(pad, (width() - scaledWidth) / 2);
        int y = bodyY + Math.max(pad, (bodyH - scaledHeight) / 2);
        if (localMouseX < x || localMouseY < y || localMouseX >= x + scaledWidth || localMouseY >= y + scaledHeight) {
            return null;
        }
        return new LayoutMouse((localMouseX - x) / scale, (localMouseY - y) / scale);
    }

    @SuppressWarnings("removal")
    private @Nullable Tooltip slotTooltip(RecipeSlotUnderMouse hoveredSlot) {
        JeiTooltipBridge tooltip = new JeiTooltipBridge();
        hoveredSlot.slot().getTooltip(tooltip);
        return tooltip.toTooltip();
    }

    private record LayoutMouse(double x, double y) {}

    private static final class JeiTooltipBridge implements ITooltipBuilder {
        private final List<Either<FormattedText, TooltipComponent>> lines = new ArrayList<>();
        private @Nullable ITypedIngredient<?> ingredient;

        @Override
        public void add(FormattedText component) {
            if (component != null) {
                lines.add(Either.left(component));
            }
        }

        @Override
        public void addAll(Collection<? extends FormattedText> components) {
            for (FormattedText component : components) {
                add(component);
            }
        }

        @Override
        public void add(TooltipComponent component) {
            if (component != null) {
                lines.add(Either.right(component));
            }
        }

        @Override
        public void addKeyUsageComponent(String translationKey, IJeiKeyMapping keyMapping) {
            MutableComponent translatedKeyMessage = keyMapping.getTranslatedKeyMessage().copy();
            Component boldKeyMapping = translatedKeyMessage.withStyle(ChatFormatting.BOLD);
            add(
                Component.translatable(translationKey, boldKeyMapping).withStyle(ChatFormatting.ITALIC)
                        .withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public void setIngredient(ITypedIngredient<?> typedIngredient) {
            this.ingredient = typedIngredient;
        }

        @Override
        public void clearIngredient() {
            this.ingredient = null;
        }

        @Override
        public List<Either<FormattedText, TooltipComponent>> getLines() {
            return lines;
        }

        @SuppressWarnings("removal")
        @Override
        public List<Component> toLegacyToComponents() {
            ArrayList<Component> components = new ArrayList<>();
            for (Either<FormattedText, TooltipComponent> line : lines) {
                line.left().ifPresent(text -> {
                    if (text instanceof Component component) {
                        components.add(component);
                    }
                });
            }
            return components;
        }

        @SuppressWarnings("removal")
        @Override
        public void removeAll(List<Component> components) {
            for (Component component : components) {
                lines.remove(Either.left(component));
            }
        }

        private @Nullable Tooltip toTooltip() {
            addJeiIngredientRenderExtras();
            if (lines.isEmpty()) {
                return null;
            }
            Tooltip tooltip = Tooltip.create();
            for (Either<FormattedText, TooltipComponent> line : lines) {
                line.ifLeft(text -> tooltip.add(asComponent(text)));
                line.ifRight(tooltip::add);
            }
            itemStack().ifPresent(stack -> tooltip.setStack(TooltipStack.of(stack)));
            return tooltip.notEmpty() ? tooltip : null;
        }

        private void addJeiIngredientRenderExtras() {
            Optional<ItemStack> itemStack = itemStack();
            itemStack.flatMap(ItemStack::getTooltipImage)
                    .ifPresent(component -> lines.add(Math.min(1, lines.size()), Either.right(component)));
        }

        private Optional<ItemStack> itemStack() {
            return ingredient == null ? Optional.empty() : ingredient.getItemStack();
        }

        private static Component asComponent(FormattedText text) {
            return text instanceof Component component ? component : Component.literal(text.getString());
        }
    }
}
