package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ResolvedPrompt(
    DecisionPrompt raw,
    @Nullable ResourceKey primaryResource,
    @Nullable String primaryMachineId,
    @Nullable String tagId,
    @Nullable CompiledRecipeKey recipeKey,
    @Nullable String systemId,
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
