package dev.vfyjxf.cirrus.ui.widget.execution;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionCapability;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionExecutionPlan;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.PauseReason;
import dev.vfyjxf.cirrus.api.recipe.execution.core.PauseReasons;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowNode;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowViewModel;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExecutionOverlayWidgetContractTest {

    @Test
    void preferredWidthIsClampedForOverlayUsage() {
        assertEquals(180, ExecutionOverlayWidget.preferredPanelWidth(240));
        assertEquals(180, ExecutionOverlayWidget.preferredPanelWidth(520));
        assertEquals(288, ExecutionOverlayWidget.preferredPanelWidth(960));
        assertEquals(300, ExecutionOverlayWidget.preferredPanelWidth(1200));
    }

    @Test
    void bodyHeightKeepsHeaderAndFooterFixed() {
        assertEquals(
            102,
            ExecutionOverlayWidget.bodyHeight(
                180,
                ExecutionOverlayWidget.headerHeight,
                ExecutionOverlayWidget.footerHeight
            )
        );
    }

    @Test
    void widgetRefreshesProjectedViewModelFromHostSnapshot() {
        var manual = new InteractionStep.ManualCheckpointStep(
            ExecutionSideRequirement.manualPlayerAction,
            ExecutionCapability.manualCheckpoint,
            ExecutionGuidance.simple(net.minecraft.network.chat.Component.literal("Press A"))
        );
        var root = ExecutionFlowNode.pending(
            "target",
            net.minecraft.network.chat.Component.literal("Craft target"),
            Lists.immutable.empty(),
            null,
            Lists.immutable.of(
                ExecutionFlowNode.pending(
                    "manual",
                    net.minecraft.network.chat.Component.literal("Manual checkpoint"),
                    Lists.immutable.of(manual),
                    manual.guidance(),
                    Lists.immutable.empty()
                )
            )
        );
        var session = new ExecutionSession(
            new InteractionExecutionPlan(recipeEntry("craft"), 1, Lists.immutable.of(manual)),
            root,
            0,
            Sets.immutable.empty(),
            Sets.immutable.of(manual),
            null,
            new PauseReason(PauseReasons.awaitingPlayerAction, manual.guidance()),
            null,
            ExecutionFlowViewModel.derive(
                root,
                new ExecutionFlowViewModel.RuntimeState(Sets.immutable.empty(), Sets.immutable.of(manual), null, null)
            )
        );
        var widget = new ExecutionOverlayWidget();

        widget.refreshFromHost(() -> session);

        assertNotNull(widget.viewModel());
        assertEquals("paused", widget.viewModel().statusText());
        assertEquals("Press A", widget.viewModel().footerSummary().getString());
    }

    private static RecipeEntry<TestRecipe> recipeEntry(String id) {
        return RecipeEntry.of(recipeSystem, new TestRecipe(id));
    }

    private static final TestRecipeSystem recipeSystem = new TestRecipeSystem();

    private record TestRecipe(String id) {}

    private static final class TestRecipeSystem implements RecipeSystem<TestRecipe> {
        private final Map<String, TestRecipe> byId = new ConcurrentHashMap<>();

        @Override
        public Namespace systemId() {
            return Namespace.of("test", "overlay_widget");
        }

        @Override
        public Class<?> basicType() {
            return TestRecipe.class;
        }

        @Override
        public ResourceLocation getId(TestRecipe recipe) {
            byId.put(recipe.id(), recipe);
            return ResourceLocation.fromNamespaceAndPath("test", recipe.id());
        }

        @Override
        public TestRecipe findRecipe(String uniqueId) {
            return byId.get(uniqueId);
        }

        @Override
        public TestRecipe findRecipe(ResourceLocation registryName) {
            return byId.get(registryName.getPath());
        }

        @Override
        public int hash(TestRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(TestRecipe r1, TestRecipe r2) {
            return r1.id().equals(r2.id());
        }

        @Override
        public RecipeProjection resolveRecipe(TestRecipe recipe) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RichIterable<TestRecipe> allRecipes() {
            return Lists.immutable.ofAll(byId.values());
        }
    }
}
