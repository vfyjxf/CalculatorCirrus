package dev.vfyjxf.cirrus.internal.integration.jei;

import dev.vfyjxf.cirrus.api.recipe.database.CustomRecipeDatabase;
import dev.vfyjxf.cirrus.api.recipe.database.FileRecipeDatabaseRepository;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseWorkspace;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import net.neoforged.fml.loading.FMLPaths;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

public final class JeiRecipeDatabaseButtonHelper {

    private JeiRecipeDatabaseButtonHelper() {}

    public static IRecipeButtonControllerFactory buttonFactory() {
        return new IRecipeButtonControllerFactory() {
            @Override
            public @Nullable <T> IIconButtonController createButtonController(
                IRecipeLayoutDrawable<T> recipeLayoutDrawable
            ) {
                return JeiTargetRecipeBridge.resolveRecipeDatabase(recipeLayoutDrawable) == null
                        ? null
                        : new DatabaseButtonController(recipeLayoutDrawable);
            }
        };
    }

    public static Path defaultRepositoryRoot() {
        return FMLPaths.CONFIGDIR.get().resolve("cirrus").resolve("databases");
    }

    private static String activeDatabaseName() {
        try {
            RecipeDatabaseWorkspace workspace = activeWorkspace();
            CustomRecipeDatabase database = workspace.activeDatabase();
            return database.builtin()
                    ? CirrusLangs.Calculator.Database.builtin.component().getString()
                    : database.name();
        } catch (RuntimeException ignored) {
            return CirrusLangs.Calculator.Database.builtin.component().getString();
        }
    }

    private static boolean activeDatabaseEditable() {
        return !activeWorkspace().activeDatabase().builtin();
    }

    private static RecipeDatabaseWorkspace activeWorkspace() {
        return new FileRecipeDatabaseRepository(defaultRepositoryRoot()).loadWorkspace();
    }

    private record DatabaseButtonController(IRecipeLayoutDrawable<?> recipeLayout) implements IIconButtonController {
        @Override
        public void initState(IButtonState state) {
            state.setIcon(JeiTargetButtonHelper.drawable(CirrusTextures.Icon.Small.plus));
            updateState(state);
        }

        @Override
        public void updateState(IButtonState state) {
            boolean supported = JeiTargetRecipeBridge.resolveRecipeDatabase(recipeLayout) != null;
            state.setVisible(supported);
            state.setActive(supported && activeDatabaseEditable());
        }

        @Override
        public boolean onPress(IJeiUserInput input) {
            var entry = JeiTargetRecipeBridge.resolveRecipeDatabase(recipeLayout);
            if (entry == null) {
                return false;
            }
            if (!activeDatabaseEditable()) {
                return true;
            }
            if (!input.isSimulate()) {
                new FileRecipeDatabaseRepository(defaultRepositoryRoot()).addToActiveDatabase(entry);
            }
            return true;
        }

        @Override
        public void getTooltips(ITooltipBuilder tooltip) {
            tooltip.add(CirrusLangs.Calculator.Jei.Database.tooltip.component());
            tooltip.add(CirrusLangs.Calculator.Jei.Database.tooltipCurrent.component(activeDatabaseName()));
            if (!activeDatabaseEditable()) {
                tooltip.add(CirrusLangs.Calculator.Database.readOnlyBuiltinDesc.component());
            }
        }
    }
}
