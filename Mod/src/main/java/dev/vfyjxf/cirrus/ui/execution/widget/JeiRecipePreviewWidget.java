package dev.vfyjxf.cirrus.ui.execution.widget;

import com.mojang.datafixers.util.Either;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.ui.execution.tooltip.JeiRecipeTooltipComponent;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipStack;
import dev.vfyjxf.taffy.style.Overflow;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.common.Internal;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.overflow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public final class JeiRecipePreviewWidget extends Widget {
    private final RecipeEntry<?> recipe;
    private final IRecipeLayoutDrawable<?> layout;
    private final int previewWidth;
    private final int previewHeight;
    private final int borderOffsetX;
    private final int borderOffsetY;

    public static @Nullable JeiRecipePreviewWidget create(@Nullable RecipeEntry<?> recipe) {
        IRecipeLayoutDrawable<?> layout = JeiRecipeTooltipComponent.createIndependentLayoutDrawable(recipe);
        return recipe == null || layout == null ? null : new JeiRecipePreviewWidget(recipe, layout);
    }

    private JeiRecipePreviewWidget(RecipeEntry<?> recipe, IRecipeLayoutDrawable<?> layout) {
        this.recipe = recipe;
        this.layout = layout;
        layout.setPosition(0, 0);
        Rect2i rect = layout.getRectWithBorder();
        this.previewWidth = Math.max(1, rect.getWidth());
        this.previewHeight = Math.max(1, rect.getHeight());
        this.borderOffsetX = rect.getX();
        this.borderOffsetY = rect.getY();
        useStyle(
            sizeOf(previewWidth, previewHeight),
            boxSizingBorderBox(),
            overflow(Overflow.HIDDEN)
        );
        setTickable(true);
        onHoverTooltip(this::tooltipAt);
    }

    public boolean matches(@Nullable RecipeEntry<?> recipe) {
        return recipe != null && this.recipe.equals(recipe);
    }

    public int previewWidth() {
        return previewWidth;
    }

    public int previewHeight() {
        return previewHeight;
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
        int availableWidth = Math.max(0, width());
        int availableHeight = Math.max(0, height());
        float scale = contentScale(availableWidth, availableHeight, previewWidth, previewHeight);
        if (scale <= 0.0f) {
            return;
        }

        int scaledWidth = Math.round(previewWidth * scale);
        int scaledHeight = Math.round(previewHeight * scale);
        int x = Math.max(0, (width() - scaledWidth) / 2);
        int y = Math.max(0, (height() - scaledHeight) / 2);
        var graphics = canvas.layeredGraphics();
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        layout.setPosition(-borderOffsetX, -borderOffsetY);
        layout.drawRecipe(graphics, -1, -1);
        graphics.pose().popPose();
    }

    private @Nullable Tooltip tooltipAt(double localMouseX, double localMouseY) {
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

    private @Nullable LayoutMouse layoutMouse(double localMouseX, double localMouseY) {
        int availableWidth = Math.max(0, width());
        int availableHeight = Math.max(0, height());
        float scale = contentScale(availableWidth, availableHeight, previewWidth, previewHeight);
        if (scale <= 0.0f) {
            return null;
        }

        int scaledWidth = Math.round(previewWidth * scale);
        int scaledHeight = Math.round(previewHeight * scale);
        int x = Math.max(0, (width() - scaledWidth) / 2);
        int y = Math.max(0, (height() - scaledHeight) / 2);
        if (localMouseX < x || localMouseY < y || localMouseX >= x + scaledWidth || localMouseY >= y + scaledHeight) {
            return null;
        }
        return new LayoutMouse(
            (localMouseX - x) / scale,
            (localMouseY - y) / scale
        );
    }

    @SuppressWarnings("removal")
    private @Nullable Tooltip slotTooltip(RecipeSlotUnderMouse hoveredSlot) {
        JeiTooltipBridge tooltip = new JeiTooltipBridge();
        hoveredSlot.slot().getTooltip(tooltip);
        return tooltip.toTooltip();
    }

    private record LayoutMouse(double x, double y) {
    }

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
            add(Component.translatable(translationKey, boldKeyMapping)
                .withStyle(ChatFormatting.ITALIC)
                .withStyle(ChatFormatting.GRAY));
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

            ITypedIngredient<?> typedIngredient = ingredient;
            if (typedIngredient == null) {
                return;
            }
            try {
                Internal.getJeiRuntime()
                    .getJeiHelpers()
                    .getModIdHelper()
                    .getModNameForTooltip(typedIngredient)
                    .ifPresent(this::add);
            } catch (RuntimeException | LinkageError ignored) {
            }
        }

        private Optional<ItemStack> itemStack() {
            ITypedIngredient<?> typedIngredient = ingredient;
            return typedIngredient == null ? Optional.empty() : typedIngredient.getItemStack();
        }

        private static Component asComponent(FormattedText text) {
            return text instanceof Component component ? component : Component.literal(text.getString());
        }
    }
}
