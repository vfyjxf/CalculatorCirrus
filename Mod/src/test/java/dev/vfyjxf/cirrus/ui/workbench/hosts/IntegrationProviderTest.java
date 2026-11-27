package dev.vfyjxf.cirrus.ui.workbench.hosts;

import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseIndex;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.DatabasePage;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchIntegrationProvider;
import dev.vfyjxf.cirrus.ui.workbench.database.DatabaseExplorerWorkspace;
import dev.vfyjxf.cirrus.ui.workbench.facilities.FacilitiesAndRulesWorkspace;
import dev.vfyjxf.cirrus.ui.workbench.facilities.FacilitiesHost;
import dev.vfyjxf.cirrus.ui.workbench.flow.FlowStageWidget;
import dev.vfyjxf.cirrus.ui.workbench.settings.SettingsAndDiagnosticsWorkspace;
import dev.vfyjxf.cirrus.ui.workbench.state.FlowViewMode;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Workspace wiring: all four entry points construct, the planner injects the three views plus the built-in DAG, and a throwing panel falls back to a placeholder.
 */
class IntegrationProviderTest {

    private static WorkbenchIntegrationProvider provider() {
        return new WorkbenchIntegrationProvider(() -> new DatabasePage(RecipeDatabaseIndex.ofRecords(List.of())));
    }

    @Test
    void plannerWorkspaceCarriesTheDagAndTwoInjectedViews() {
        HostsTestFixtures.FakeShellContext context = HostsTestFixtures.fixture().context();

        Widget planner = provider().plannerWorkspace(context);

        assertTrue(planner instanceof FlowStageWidget);
        FlowStageWidget stage = (FlowStageWidget) planner;
        assertNotNull(stage.dag());
        assertEquals(FlowViewMode.spatialDag, stage.currentView());
        assertEquals(
            Set.of(FlowViewMode.preparationLedger, FlowViewMode.executionChecklist),
            stage.injectedViews().keySet()
        );
        assertTrue(context.componentToasts().isEmpty());
    }

    @Test
    void allFourWorkspacesConstructWithoutMounting() {
        HostsTestFixtures.FakeShellContext context = HostsTestFixtures.fixture().context();
        WorkbenchIntegrationProvider provider = provider();

        assertTrue(provider.plannerWorkspace(context) instanceof FlowStageWidget);
        assertTrue(provider.facilitiesWorkspace(context) instanceof FacilitiesAndRulesWorkspace);

        Widget settings = provider.settingsWorkspace(context);
        assertTrue(settings instanceof SettingsAndDiagnosticsWorkspace);

        Widget database = provider.databaseWorkspace(context);
        assertTrue(database instanceof DatabaseExplorerWorkspace);
        DatabaseExplorerWorkspace explorer = (DatabaseExplorerWorkspace) database;
        assertNotNull(explorer.databasePage());
        assertTrue(context.componentToasts().isEmpty());
    }

    @Test
    void facilitiesWorkspaceImportsSessionRecipesFromTheActiveSnapshot() {
        HostsTestFixtures.FakeShellContext context = HostsTestFixtures.fixture().context();

        Widget facilities = provider().facilitiesWorkspace(context);

        assertTrue(facilities instanceof FacilitiesAndRulesWorkspace);
        FacilitiesHost host = Objects.requireNonNull((FacilitiesAndRulesWorkspace) facilities).host();
        assertTrue(host instanceof ShellFacilitiesHost);
        CompiledRecipeKey expected = HostsTestFixtures.recipeKey("minecraft:crafting", "minecraft:iron_ingot");
        assertEquals(List.of(expected), host.sessionRecipeKeys());
    }

    @Test
    void brokenPanelFallsBackToAPlaceholderInsteadOfThrowing() {
        HostsTestFixtures.FakeShellContext context = HostsTestFixtures.fixture().context();
        WorkbenchIntegrationProvider provider = new WorkbenchIntegrationProvider(() -> {
            throw new IllegalStateException("database page exploded");
        });

        Widget database = assertDoesNotThrow(() -> provider.databaseWorkspace(context));

        assertNotNull(database);
        assertFalse(database instanceof DatabasePage);
        assertTrue(database instanceof WidgetGroup);
        assertTrue(((WidgetGroup<?>) database).children().isEmpty());
        assertEquals(1, context.componentToasts().size());
    }

    @Test
    void plannerHostsSurviveAContextWithoutController() {
        HostsTestFixtures.FakeShellContext context = new HostsTestFixtures.FakeShellContext(null);
        WorkbenchIntegrationProvider provider = provider();

        FlowStageWidget stage = (FlowStageWidget) assertDoesNotThrow(() -> provider.plannerWorkspace(context));

        assertNotNull(stage.dag());
        assertEquals(2, stage.injectedViews().size());
    }
}
