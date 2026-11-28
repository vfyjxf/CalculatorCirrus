package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ResolvedPrompt(
    DecisionPrompt raw,
    @Nullable ComponentStackKey primaryResource,
    @Nullable MachineEntry primaryMachine,
    @Nullable TagKey<?> tag,
    @Nullable CompiledRecipeKey recipeKey,
    @Nullable Namespace systemId,
    Set<String> anchoredStepIds,
    List<ResolvedPromptOption> options
) {

    public ResolvedPrompt {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(anchoredStepIds, "anchoredStepIds");
        Objects.requireNonNull(options, "options");
        anchoredStepIds = Set.copyOf(anchoredStepIds);
        options = List.copyOf(options);
    }
}
