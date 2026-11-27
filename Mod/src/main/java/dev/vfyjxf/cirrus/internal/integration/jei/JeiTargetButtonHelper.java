package dev.vfyjxf.cirrus.internal.integration.jei;

import dev.vfyjxf.cirrus.recipe.execution.ExecutionTargetRecipeState;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.execution.overlay.ExecutionOverlayOpenState;
import dev.vfyjxf.cloudlib.api.ui.texture.SizedTexture;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.textures.Textures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import org.jspecify.annotations.Nullable;

public final class JeiTargetButtonHelper {

    private JeiTargetButtonHelper() {}

    public static IRecipeButtonControllerFactory buttonFactory() {
        return new IRecipeButtonControllerFactory() {
            @Override
            public @Nullable <T> IIconButtonController createButtonController(
                IRecipeLayoutDrawable<T> recipeLayoutDrawable
            ) {
                return JeiTargetRecipeBridge.resolveExecution(recipeLayoutDrawable) == null
                        ? null
                        : new ExecutionTargetButtonController(recipeLayoutDrawable);
            }
        };
    }

    public static IDrawable drawable(SizedTexture texture) {
        return new SizedTextureDrawable(texture);
    }

    static void renderIconButton(
        GuiGraphics guiGraphics,
        Rect2i area,
        boolean hovered,
        boolean pressed,
        SizedTexture icon
    ) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 200.0F);
        Textures textures = Internal.getTextures();
        textures.getButtonForState(pressed, true, hovered)
                .draw(guiGraphics, area.getX(), area.getY(), area.getWidth(), area.getHeight());
        int iconX = paddedIconPosition(area.getX(), area.getWidth(), icon.width());
        int iconY = paddedIconPosition(area.getY(), area.getHeight(), icon.height());
        icon.render(guiGraphics, iconX, iconY);
        guiGraphics.pose().popPose();
    }

    private static int paddedIconPosition(int origin, int size, int iconSize) {
        int contentSize = size - 4;
        if (contentSize >= iconSize) {
            return origin + 2 + (contentSize - iconSize) / 2;
        }
        return origin + Math.max(0, (size - iconSize) / 2);
    }

    private record ExecutionTargetButtonController(IRecipeLayoutDrawable<?> recipeLayout)
            implements
                IIconButtonController {
        @Override
        public void initState(IButtonState state) {
            state.setIcon(drawable(CirrusTextures.Icon.start));
            updateState(state);
        }

        @Override
        public void updateState(IButtonState state) {
            state.setVisible(JeiTargetRecipeBridge.resolveExecution(recipeLayout) != null);
            state.setActive(JeiTargetRecipeBridge.resolveExecution(recipeLayout) != null);
        }

        @Override
        public boolean onPress(IJeiUserInput input) {
            var entry = JeiTargetRecipeBridge.resolveExecution(recipeLayout);
            if (entry == null) {
                return false;
            }
            if (!input.isSimulate()) {
                int times = Screen.hasShiftDown() ? 16 : 1;
                ExecutionTargetRecipeState.setWithAutoPlan(entry, times);
                ExecutionOverlayOpenState.setOpen(true);
                JeiTargetSelectionActions
                        .closeCurrentRecipeScreenIfNeeded(new JeiTargetSelectionResult(true, entry, times));
            }
            return true;
        }

        @Override
        public void getTooltips(ITooltipBuilder tooltip) {
            tooltip.add(CirrusLangs.Execution.Jei.Target.tooltip.component());
        }
    }

    private record SizedTextureDrawable(SizedTexture texture) implements IDrawable {
        @Override
        public int getWidth() {
            return texture.width();
        }

        @Override
        public int getHeight() {
            return texture.height();
        }

        @Override
        public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
            texture.render(guiGraphics, xOffset, yOffset);
        }
    }
}
