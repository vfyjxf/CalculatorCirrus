package dev.vfyjxf.cirrus.internal.integration.jei;

import dev.vfyjxf.cirrus.api.content.ContentApi;
import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.planning.bridge.CirrusContentBridge;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorScreen;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorTargetRecipeState;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public final class JeiRecipeCalculatorTargetButtonHelper {

    private JeiRecipeCalculatorTargetButtonHelper() {}

    public static IRecipeButtonControllerFactory buttonFactory() {
        return new IRecipeButtonControllerFactory() {
            @Override
            public @Nullable <T> IIconButtonController createButtonController(
                IRecipeLayoutDrawable<T> recipeLayoutDrawable
            ) {
                return JeiTargetRecipeBridge.resolveCalculator(recipeLayoutDrawable) == null
                        ? null
                        : new CalculatorTargetButtonController(recipeLayoutDrawable);
            }
        };
    }

    static List<TargetDemand> extractLayoutOutputDemands(IRecipeLayoutDrawable<?> recipeLayout) {
        try {
            List<IRecipeSlotView> outputSlots = recipeLayout.getRecipeSlotsView()
                    .getSlotViews(RecipeIngredientRole.OUTPUT);
            for (IRecipeSlotView slotView : outputSlots) {
                Optional<ITypedIngredient<?>> displayed = slotView.getDisplayedIngredient();
                if (displayed.isPresent()) {
                    TargetDemand demand = toTargetDemand(displayed.get());
                    if (demand != null) {
                        return List.of(demand);
                    }
                }
                for (ITypedIngredient<?> ingredient : slotView.getAllIngredients().toList()) {
                    TargetDemand demand = toTargetDemand(ingredient);
                    if (demand != null) {
                        return List.of(demand);
                    }
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            return List.of();
        }
        return List.of();
    }

    private static <I> @Nullable TargetDemand toTargetDemand(ITypedIngredient<I> typed) {
        I raw = typed.getIngredient();
        if (raw instanceof ItemStack itemStack && !itemStack.isEmpty()) {
            Optional<ComponentStackKey> key = ComponentStackKey.fromItemStack(itemStack);
            if (key.isPresent()) {
                return new TargetDemand(key.get(), Math.max(1L, itemStack.getCount()));
            }
        }
        if (raw instanceof FluidStack fluidStack && !fluidStack.isEmpty()) {
            Optional<ComponentStackKey> key = ComponentStackKey.fromFluidStack(fluidStack);
            if (key.isPresent()) {
                return new TargetDemand(key.get(), Math.max(1L, fluidStack.getAmount()));
            }
        }
        try {
            var stackType = ContentApi.instance().findStackTypeForIngredientType(typed.getType());
            if (stackType != null) {
                long amount = stackType.getAmount(raw);
                ContentStack<?, I> stack = ContentStack
                        .fromStack(stackType, raw, amount > 0L ? amount : stackType.quantity());
                if (stack.notEmpty()) {
                    Optional<ComponentStackKey> key = CirrusContentBridge.toConcreteKey(stack);
                    if (key.isPresent()) {
                        return new TargetDemand(key.get(), Math.max(1L, stack.amount()));
                    }
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
        return null;
    }

    private record CalculatorTargetButtonController(IRecipeLayoutDrawable<?> recipeLayout)
            implements
                IIconButtonController {
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
                List<TargetDemand> layoutDemands = extractLayoutOutputDemands(recipeLayout);
                CalculatorTargetRecipeState.set(entry, 1, layoutDemands);
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
