package dev.vfyjxf.cirrus.ui.widget.calculator.calc;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.MutableList;

public class RecipeSelectorWidget extends CompositeWidget<Widget> {

    public RecipeSelectorWidget() {
        RecipeApi recipeApi = RecipeApi.instance();
        for (RecipeSystem<?> system : recipeApi.allSystems()) {
            var machines = recipeApi.getMachines(system.systemId(), true);

        }
    }

    private static class RecipeCategoryWidget<R> extends CompositeWidget<Widget> {
        private final RecipeSystem<R> system;
        private final RichIterable<R> recipes;
        private final MutableList<MachineEntry> machines;


        RecipeCategoryWidget(RecipeSystem<R> system, RichIterable<R> recipes, MutableList<MachineEntry> machines) {
            this.system = system;
            this.recipes = recipes;
            this.machines = machines;
            RecipeApi recipeApi = RecipeApi.instance();
            var jeiType = recipeApi.getJeiRecipeTypeForSystem(system.systemId());

        }
    }

    private static class RecipeEntry extends CompositeWidget<Widget> {

    }

}
