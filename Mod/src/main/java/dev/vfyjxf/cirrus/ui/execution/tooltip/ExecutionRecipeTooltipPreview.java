package dev.vfyjxf.cirrus.ui.execution.tooltip;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.execution.InputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.OutputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayRecipePreview;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectedRecipe;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.recipe.execution.driver.slot.CraftingTableExecutionDriver;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.process.ProcessNode;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExecutionRecipeTooltipPreview {
    private ExecutionRecipeTooltipPreview() {}

    public static @Nullable TooltipComponent create(@Nullable ExecutionOverlayRecipePreview preview) {
        return create(preview, false);
    }

    public static @Nullable TooltipComponent createLeftAligned(@Nullable ExecutionOverlayRecipePreview preview) {
        return create(preview, true);
    }

    private static @Nullable TooltipComponent create(
        @Nullable ExecutionOverlayRecipePreview preview,
        boolean leftAligned
    ) {
        if (preview == null) {
            return null;
        }
        TooltipComponent jei = JeiRecipeTooltipComponent.create(preview.recipe(), leftAligned ? 4 : 0);
        if (jei != null) {
            return jei;
        }
        List<ProcessNode.MaterialSlot> inputs = previewInputSlots(preview.recipe(), preview.inputs());
        List<ProcessNode.MaterialSlot> outputs = previewOutputSlots(preview.outputs());
        return createFallback(inputs, outputs, leftAligned);
    }

    public static @Nullable TooltipComponent create(
        @Nullable RecipeEntry<?> recipe,
        @Nullable RecipeProjection projection,
        @Nullable ProjectedRecipe projected
    ) {
        TooltipComponent jei = JeiRecipeTooltipComponent.create(recipe);
        if (jei != null) {
            return jei;
        }
        List<ProcessNode.MaterialSlot> inputs = previewInputSlots(recipe, projection, projected);
        List<ProcessNode.MaterialSlot> outputs = previewOutputSlots(projection, projected);
        return createFallback(inputs, outputs, false);
    }

    public static @Nullable TooltipComponent create(
        @Nullable RecipeEntry<?> recipe,
        List<ProcessNode.MaterialSlot> inputs,
        List<ProcessNode.MaterialSlot> outputs
    ) {
        TooltipComponent jei = JeiRecipeTooltipComponent.create(recipe);
        if (jei != null) {
            return jei;
        }
        return createFallback(inputs, outputs, false);
    }

    private static @Nullable TooltipComponent createFallback(
        List<ProcessNode.MaterialSlot> inputs,
        List<ProcessNode.MaterialSlot> outputs,
        boolean leftAligned
    ) {
        if (inputs.isEmpty() && outputs.isEmpty()) {
            return null;
        }
        if (leftAligned) {
            return ExecutionRecipeLayoutTooltipComponent.leftAligned(new ArrayList<>(inputs), List.copyOf(outputs));
        }
        return new ExecutionRecipeLayoutTooltipComponent(new ArrayList<>(inputs), List.copyOf(outputs));
    }

    public static List<ProcessNode.MaterialSlot> previewInputSlots(
        @Nullable RecipeEntry<?> recipe,
        @Nullable RecipeProjection projection,
        @Nullable ProjectedRecipe projected
    ) {
        List<InputContent> inputs = inputContents(projection, projected);
        if (inputs.isEmpty()) {
            return List.of();
        }
        ArrayList<InputBinding> bindings = new ArrayList<>(inputs.size());
        for (InputContent input : inputs) {
            bindings.add(new InputBinding(input, Math.max(1L, input.amount())));
        }
        ImmutableList<CraftingTableExecutionDriver.RecipePreviewSlot> previewSlots = CraftingTableExecutionDriver
                .resolvePreviewSlots(recipe, Lists.immutable.ofAll(bindings));
        if (previewSlots.notEmpty()) {
            return mapPreviewSlots(previewSlots);
        }
        ArrayList<ProcessNode.MaterialSlot> out = new ArrayList<>(inputs.size());
        for (InputContent input : inputs) {
            out.add(ExecutionOverlayContentHelper.materialSlot(input));
        }
        return out;
    }

    public static List<ProcessNode.MaterialSlot> previewOutputSlots(
        @Nullable RecipeProjection projection,
        @Nullable ProjectedRecipe projected
    ) {
        List<OutputContent> outputs = outputContents(projection, projected);
        if (outputs.isEmpty()) {
            return List.of();
        }
        ArrayList<ProcessNode.MaterialSlot> out = new ArrayList<>(outputs.size());
        for (OutputContent output : outputs) {
            out.add(ExecutionOverlayContentHelper.materialSlot(output));
        }
        return out;
    }

    public static List<ProcessNode.MaterialSlot> previewInputSlots(
        @Nullable RecipeEntry<?> recipe,
        ImmutableList<InputBinding> inputs
    ) {
        if (inputs.isEmpty()) {
            return List.of();
        }
        ImmutableList<CraftingTableExecutionDriver.RecipePreviewSlot> previewSlots = CraftingTableExecutionDriver
                .resolvePreviewSlots(recipe, inputs);
        if (previewSlots.notEmpty()) {
            return mapPreviewSlots(previewSlots);
        }
        ArrayList<ProcessNode.MaterialSlot> out = new ArrayList<>(inputs.size());
        for (InputBinding input : inputs) {
            out.add(ExecutionOverlayContentHelper.materialSlot(input, false));
        }
        return out;
    }

    public static List<ProcessNode.MaterialSlot> previewOutputSlots(ImmutableList<OutputBinding> outputs) {
        if (outputs.isEmpty()) {
            return List.of();
        }
        ArrayList<ProcessNode.MaterialSlot> out = new ArrayList<>(outputs.size());
        for (OutputBinding output : outputs) {
            out.add(ExecutionOverlayContentHelper.materialSlot(output));
        }
        return out;
    }

    private static List<InputContent> inputContents(
        @Nullable RecipeProjection projection,
        @Nullable ProjectedRecipe projected
    ) {
        if (projected != null) {
            return projected.inputs().stream().filter(input -> input != null && input.notEmpty()).toList();
        }
        if (projection == null) {
            return List.of();
        }
        ArrayList<InputContent> inputs = new ArrayList<>();
        for (var input : projection.inputs()) {
            if (input.content().notEmpty()) {
                inputs.add(input.content());
            }
        }
        return List.copyOf(inputs);
    }

    private static List<OutputContent> outputContents(
        @Nullable RecipeProjection projection,
        @Nullable ProjectedRecipe projected
    ) {
        if (projected != null) {
            return projected.outputs().stream().filter(ExecutionRecipeTooltipPreview::isVisiblePreviewContent).toList();
        }
        if (projection == null) {
            return List.of();
        }
        ArrayList<OutputContent> outputs = new ArrayList<>();
        for (var output : projection.outputs()) {
            if (isVisiblePreviewContent(output.content())) {
                outputs.add(output.content());
            }
        }
        return List.copyOf(outputs);
    }

    private static boolean isVisiblePreviewContent(@Nullable RecipeContent content) {
        return content != null && content.notEmpty();
    }

    private static ArrayList<ProcessNode.MaterialSlot> mapPreviewSlots(
        ImmutableList<CraftingTableExecutionDriver.RecipePreviewSlot> previewSlots
    ) {
        ArrayList<ProcessNode.MaterialSlot> out = new ArrayList<>(Collections.nCopies(9, null));
        for (CraftingTableExecutionDriver.RecipePreviewSlot previewSlot : previewSlots) {
            if (previewSlot.gridIndex() < 0 || previewSlot.gridIndex() >= out.size()) {
                continue;
            }
            out.set(previewSlot.gridIndex(), toPreviewMaterialSlot(previewSlot));
        }
        return out;
    }

    private static ProcessNode.MaterialSlot toPreviewMaterialSlot(
        CraftingTableExecutionDriver.RecipePreviewSlot previewSlot
    ) {
        InputContent input = previewSlot.input();
        if (input != null) {
            return ExecutionOverlayContentHelper.materialSlot(input.copyWithAmount(previewSlot.amount()));
        }
        return ExecutionOverlayContentHelper
                .materialSlot(InputContent.fromItemStack(previewSlot.template(), previewSlot.amount()));
    }

    public static RecipeContent firstPreviewContent(
        @Nullable RecipeProjection projection,
        @Nullable ProjectedRecipe projected
    ) {
        List<OutputContent> outputs = outputContents(projection, projected);
        if (!outputs.isEmpty()) {
            return outputs.getFirst().copy();
        }
        List<InputContent> inputs = inputContents(projection, projected);
        if (!inputs.isEmpty()) {
            return inputs.getFirst().copy();
        }
        return RecipeContent.empty();
    }
}
