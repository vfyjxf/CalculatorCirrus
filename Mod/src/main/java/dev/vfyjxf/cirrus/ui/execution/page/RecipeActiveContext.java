package dev.vfyjxf.cirrus.ui.execution.page;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.InputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.OutputBinding;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectedRecipe;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectionEngine;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContexts;
import dev.vfyjxf.cirrus.recipe.execution.driver.slot.CraftingTableExecutionDriver;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.process.ProcessNode;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public record RecipeActiveContext(
    RecipeEntry<?> recipe,
    ImmutableList<InputBinding> inputs,
    ImmutableList<OutputBinding> outputs,
    boolean current,
    LangEntry phase,
    @Nullable ProjectedRecipe projected,
    String label,
    RecipeContent content,
    List<ProcessNode.MaterialSlot> displayInputs,
    List<ProcessNode.MaterialSlot> displayInputGrid,
    List<ProcessNode.MaterialSlot> displayOutputs,
    List<RecipeContent> outputContents
) {
    public static @Nullable RecipeActiveContext fromSession(@Nullable ExecutionSession session) {
        if (session == null) {
            return null;
        }
        ImmutableList<InteractionStep> steps = session.plan().steps();
        if (steps.isEmpty()) {
            return null;
        }
        int baseIndex = Math.max(0, Math.min(session.nextStepIndex(), steps.size() - 1));
        RecipeActiveContext current = recipeContextAt(steps.get(baseIndex), true);
        if (current != null) {
            return current;
        }
        for (int offset = 1; offset < steps.size(); offset++) {
            int previousIndex = baseIndex - offset;
            if (previousIndex >= 0) {
                RecipeActiveContext candidate = recipeContextAt(steps.get(previousIndex), false);
                if (candidate != null) {
                    return candidate;
                }
            }
            int nextIndex = baseIndex + offset;
            if (nextIndex < steps.size()) {
                RecipeActiveContext candidate = recipeContextAt(steps.get(nextIndex), false);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static @Nullable RecipeActiveContext recipeContextAt(InteractionStep step, boolean current) {
        return switch (step) {
            case InteractionStep.OpenInteractionStep open -> build(
                open.recipe(),
                Lists.immutable.empty(),
                Lists.immutable.empty(),
                current,
                CirrusLangs.Execution.Overlay.Trace.phaseOpen
            );
            case InteractionStep.PrepareInputsStep prepare -> build(
                prepare.recipe(),
                prepare.inputs(),
                Lists.immutable.empty(),
                current,
                CirrusLangs.Execution.Overlay.Trace.phasePrepare
            );
            case InteractionStep.ExecuteRecipeStep execute -> build(
                execute.recipe(),
                execute.inputs(),
                execute.expectedOutputs(),
                current,
                CirrusLangs.Execution.Overlay.Trace.phaseExecute
            );
            case InteractionStep.CollectOutputsStep collect -> build(
                collect.recipe(),
                Lists.immutable.empty(),
                collect.expectedOutputs(),
                current,
                CirrusLangs.Execution.Overlay.Trace.phaseCollect
            );
            default -> null;
        };
    }

    private static RecipeActiveContext build(
        RecipeEntry<?> recipe,
        ImmutableList<InputBinding> inputs,
        ImmutableList<OutputBinding> outputs,
        boolean current,
        LangEntry phase
    ) {
        ProjectedRecipe projected = projectedRecipe(recipe);
        RecipeContent preview = previewContent(projected);
        List<RecipeContent> outputContents = outputContents(outputs, projected);
        List<ProcessNode.MaterialSlot> displayInputs = inputs.notEmpty()
                ? mergeMaterialSlots(inputBindingSlots(inputs))
                : projected == null ? List.of() : mergeMaterialSlots(inputContentSlots(projected.inputs()));
        ImmutableList<InputBinding> previewBindings = previewBindings(inputs, projected);
        List<ProcessNode.MaterialSlot> displayInputGrid = previewBindings.notEmpty()
                ? mapPreviewSlots(recipe, previewBindings)
                : new ArrayList<>(displayInputs);
        List<ProcessNode.MaterialSlot> displayOutputs = outputs.notEmpty()
                ? outputBindingSlots(outputs)
                : projected == null ? List.of() : outputContentSlots(projected.outputs());
        String label = label(recipe, preview);
        RecipeContent content = preview.notEmpty()
                ? preview
                : firstContent(displayOutputs.isEmpty() ? displayInputs : displayOutputs);
        if (content.isEmpty()) {
            content = firstContent(displayInputs);
        }
        return new RecipeActiveContext(
            recipe,
            inputs,
            outputs,
            current,
            phase,
            projected,
            label,
            content,
            List.copyOf(displayInputs),
            List.copyOf(displayInputGrid),
            List.copyOf(displayOutputs),
            List.copyOf(outputContents)
        );
    }

    public String label() {
        return label;
    }

    public RecipeContent content() {
        return content;
    }

    public List<ProcessNode.MaterialSlot> displayInputs() {
        return displayInputs;
    }

    public List<ProcessNode.MaterialSlot> displayInputGrid() {
        return displayInputGrid;
    }

    public List<ProcessNode.MaterialSlot> displayOutputs() {
        return displayOutputs;
    }

    public List<RecipeContent> outputContents() {
        return outputContents;
    }

    public @Nullable ProjectedRecipe projected() {
        return projected;
    }

    private static @Nullable ProjectedRecipe projectedRecipe(RecipeEntry<?> recipe) {
        RecipeProjection projection = recipe.resolve();
        if (projection == null) {
            return null;
        }
        return new ProjectionEngine().project(ProjectionContexts.global(), projection);
    }

    private static RecipeContent previewContent(@Nullable ProjectedRecipe projected) {
        if (projected == null) {
            return RecipeContent.empty();
        }
        for (OutputContent output : projected.outputs()) {
            if (output != null && output.notEmpty()) {
                return output.copyWithAmount(Math.max(1L, output.amount()));
            }
        }
        return RecipeContent.empty();
    }

    private static List<RecipeContent> outputContents(
        ImmutableList<OutputBinding> outputs,
        @Nullable ProjectedRecipe projected
    ) {
        if (outputs.notEmpty()) {
            ArrayList<RecipeContent> out = new ArrayList<>(outputs.size());
            for (OutputBinding output : outputs) {
                RecipeContent content = ExecutionOverlayContentHelper
                        .recipeContentFromResource(output.resource(), Math.max(1L, output.amount()), 1.0d);
                if (content.notEmpty()) {
                    out.add(content);
                }
            }
            return out;
        }
        if (projected == null || projected.outputs().isEmpty()) {
            return List.of();
        }
        ArrayList<RecipeContent> out = new ArrayList<>(projected.outputs().size());
        for (OutputContent output : projected.outputs()) {
            if (output != null && output.notEmpty()) {
                out.add(output.copyWithAmount(Math.max(1L, output.amount())));
            }
        }
        return out;
    }

    private static List<ProcessNode.MaterialSlot> inputBindingSlots(ImmutableList<InputBinding> inputs) {
        ArrayList<ProcessNode.MaterialSlot> out = new ArrayList<>(inputs.size());
        for (InputBinding binding : inputs) {
            out.add(ExecutionOverlayContentHelper.materialSlot(binding, false));
        }
        return out;
    }

    private static List<ProcessNode.MaterialSlot> outputBindingSlots(ImmutableList<OutputBinding> outputs) {
        ArrayList<ProcessNode.MaterialSlot> out = new ArrayList<>(outputs.size());
        for (OutputBinding binding : outputs) {
            out.add(ExecutionOverlayContentHelper.materialSlot(binding));
        }
        return out;
    }

    private static List<ProcessNode.MaterialSlot> inputContentSlots(ImmutableList<InputContent> inputs) {
        ArrayList<ProcessNode.MaterialSlot> out = new ArrayList<>(inputs.size());
        for (InputContent input : inputs) {
            out.add(ExecutionOverlayContentHelper.materialSlot(input));
        }
        return out;
    }

    private static List<ProcessNode.MaterialSlot> outputContentSlots(ImmutableList<OutputContent> outputs) {
        ArrayList<ProcessNode.MaterialSlot> out = new ArrayList<>(outputs.size());
        for (OutputContent output : outputs) {
            out.add(ExecutionOverlayContentHelper.materialSlot(output));
        }
        return out;
    }

    private static ImmutableList<InputBinding> previewBindings(
        ImmutableList<InputBinding> inputs,
        @Nullable ProjectedRecipe projected
    ) {
        if (inputs.notEmpty()) {
            return inputs;
        }
        if (projected == null || projected.inputs().isEmpty()) {
            return Lists.immutable.empty();
        }
        ArrayList<InputBinding> bindings = new ArrayList<>(projected.inputs().size());
        for (InputContent input : projected.inputs()) {
            bindings.add(new InputBinding(input, input.amount()));
        }
        return Lists.immutable.ofAll(bindings);
    }

    private static ArrayList<ProcessNode.MaterialSlot> mapPreviewSlots(
        RecipeEntry<?> recipe,
        ImmutableList<InputBinding> bindings
    ) {
        ImmutableList<CraftingTableExecutionDriver.RecipePreviewSlot> previewSlots = CraftingTableExecutionDriver
                .resolvePreviewSlots(recipe, bindings);
        if (previewSlots.isEmpty()) {
            return new ArrayList<>(inputBindingSlots(bindings));
        }
        ArrayList<ProcessNode.MaterialSlot> out = new ArrayList<>(Collections.nCopies(9, null));
        for (CraftingTableExecutionDriver.RecipePreviewSlot previewSlot : previewSlots) {
            if (previewSlot.gridIndex() < 0 || previewSlot.gridIndex() >= out.size()) {
                continue;
            }
            InputContent input = previewSlot.input();
            ProcessNode.MaterialSlot slot = input != null
                    ? ExecutionOverlayContentHelper.materialSlot(input.copyWithAmount(previewSlot.amount()))
                    : ExecutionOverlayContentHelper
                            .materialSlot(InputContent.fromItemStack(previewSlot.template(), previewSlot.amount()));
            out.set(previewSlot.gridIndex(), slot);
        }
        return out;
    }

    private static List<ProcessNode.MaterialSlot> mergeMaterialSlots(List<ProcessNode.MaterialSlot> slots) {
        LinkedHashMap<String, ProcessNode.MaterialSlot> merged = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> amounts = new LinkedHashMap<>();
        for (ProcessNode.MaterialSlot slot : slots) {
            String key = materialSlotKey(slot);
            ProcessNode.MaterialSlot existing = merged.get(key);
            int nextAmount = amounts.getOrDefault(key, 0) + Math.max(1, slot.amount());
            amounts.put(key, nextAmount);
            if (existing == null) {
                merged.put(key, slot);
            }
        }

        ArrayList<ProcessNode.MaterialSlot> out = new ArrayList<>(merged.size());
        for (var entry : merged.entrySet()) {
            ProcessNode.MaterialSlot slot = entry.getValue();
            int amount = amounts.getOrDefault(entry.getKey(), Math.max(1, slot.amount()));
            out.add(copyWithAmount(slot, amount));
        }
        return out;
    }

    private static String materialSlotKey(ProcessNode.MaterialSlot slot) {
        if (slot.content() != null && slot.content().notEmpty()) {
            if (slot.content() instanceof InputContent input && input.preserveOriginalShortageSemantics()) {
                return ExecutionOverlayContentHelper.label(slot.content()) + "|" + slot.type() + "|"
                        + slot.isCatalyst();
            }
            return ExecutionOverlayContentHelper.recipeContentKey(slot.content()) + "|" + slot.type() + "|"
                    + slot.isCatalyst();
        }
        return slot.materialName() + "|" + slot.type() + "|" + slot.isCatalyst();
    }

    private static ProcessNode.MaterialSlot copyWithAmount(ProcessNode.MaterialSlot slot, int amount) {
        RecipeContent content = slot.content() == null || slot.content().isEmpty()
                ? RecipeContent.empty()
                : slot.content().copyWithAmount(Math.max(1, amount));
        String label = ExecutionOverlayContentHelper.label(content);
        return new ProcessNode.MaterialSlot(
            label == null || label.isBlank() || "-".equals(label) ? slot.materialName() : label,
            Math.max(1, amount),
            slot.type(),
            slot.chance(),
            slot.isCatalyst(),
            content
        );
    }

    private static RecipeContent firstContent(List<ProcessNode.MaterialSlot> slots) {
        if (slots.isEmpty()) {
            return RecipeContent.empty();
        }
        ProcessNode.MaterialSlot slot = slots.getFirst();
        return slot.content() == null ? RecipeContent.empty() : slot.content().copy();
    }

    private static String label(RecipeEntry<?> recipe, RecipeContent content) {
        String label = ExecutionOverlayContentHelper.label(content);
        if (label != null && !label.isBlank() && !"-".equals(label)) {
            return label;
        }
        if (recipe.id() != null) {
            return recipe.id().toString();
        }
        return String.valueOf(recipe);
    }
}
