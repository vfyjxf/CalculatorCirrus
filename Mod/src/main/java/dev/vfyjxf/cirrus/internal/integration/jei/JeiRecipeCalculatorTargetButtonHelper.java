package dev.vfyjxf.cirrus.internal.integration.jei;

import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorScreen;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorTargetRecipeState;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

public final class JeiRecipeCalculatorTargetButtonHelper {

    private JeiRecipeCalculatorTargetButtonHelper() {
    }

    public static IRecipeButtonControllerFactory buttonFactory() {
        return new IRecipeButtonControllerFactory() {
            @Override
            public @Nullable <T> IIconButtonController createButtonController(IRecipeLayoutDrawable<T> recipeLayoutDrawable) {
                return JeiTargetRecipeBridge.resolveCalculator(recipeLayoutDrawable) == null
                    ? null
                    : new CalculatorTargetButtonController(recipeLayoutDrawable);
            }
        };
    }

    private record CalculatorTargetButtonController(IRecipeLayoutDrawable<?> recipeLayout) implements IIconButtonController {
        @Override
        public void initState(IButtonState state) {
            state.setIcon(JeiTargetButtonHelper.drawable(CirrusTextures.Icon.calculatorTarget));
            updateState(state);
        }

        @Override
        public void updateState(IButtonState state) {
            boolean supported = JeiTargetRecipeBridge.resolveCalculator(recipeLayout) != null;
            state.setVisible(supported);
            state.setActive(supported);
        }

        @Override
        public boolean onPress(IJeiUserInput input) {
            var entry = JeiTargetRecipeBridge.resolveCalculator(recipeLayout);
            if (entry == null) {
                return false;
            }
            if (!input.isSimulate()) {
                CalculatorTargetRecipeState.set(entry, 1);
                Minecraft.getInstance().setScreen(new CalculatorScreen());
            }
            return true;
        }

        @Override
        public void getTooltips(ITooltipBuilder tooltip) {
            tooltip.add(CirrusLangs.Calculator.Jei.Target.tooltip.component());
        }
    }
}
