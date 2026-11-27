package dev.vfyjxf.cirrus.ui.workbench;

import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.SessionTabManager;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * State facade shared by shell chrome and the workspaces; later work packages wire against this signature.
 */
public interface WorkbenchShellContext {

    SessionTabManager tabManager();

    @Nullable
    PlannerWorkbenchController activeController();

    WorkbenchUiSnapshot activeSnapshot();

    @Nullable
    CompiledRecipeGraph activeGraph();

    WorkbenchBreakpoint breakpoint();

    WorkbenchThemeMode theme();

    void setTheme(WorkbenchThemeMode theme);

    CirrusPalette palette();

    WorkbenchMode mode();

    void setMode(WorkbenchMode mode);

    boolean zenMode();

    void setZenMode(boolean zen);

    float leftDockWidth();

    void setLeftDockWidth(float width);

    default boolean leftDockCollapsed() {
        return leftDockWidth() <= 0f;
    }

    default void setLeftDockCollapsed(boolean collapsed) {
        setLeftDockWidth(collapsed ? 0f : -1f);
    }

    float rightDockWidth();

    void setRightDockWidth(float width);

    default boolean rightDockCollapsed() {
        return rightDockWidth() <= 0f;
    }

    default void setRightDockCollapsed(boolean collapsed) {
        setRightDockWidth(collapsed ? 0f : -1f);
    }

    float shellWidth();

    float shellHeight();

    float leftRailWidth();

    float topChromeHeight();

    void toast(LangEntry message);

    void toast(Component message);

    void openCommandPalette();

    void closeCommandPalette();

    boolean commandPaletteOpen();

    void requestReplan();

    void setTargets(List<TargetDemand> targets, @Nullable RecipeEntry<?> primaryRecipe);

    void openTab(List<TargetDemand> targets, @Nullable RecipeEntry<?> primaryRecipe);

    void closeActiveTab();

    default void resetActiveTabSession() {
        PlannerWorkbenchController active = activeController();
        if (active != null) {
            active.resetSession();
        }
    }

    void sendActivePlanToExecution();

    void addListener(Runnable listener);

    void removeListener(Runnable listener);

    void shutdown();
}
