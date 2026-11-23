package dev.vfyjxf.cirrus.api.planning.human;

import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.Objects;

public record PinResourceRecipeFeedback(
        ResourceKey resource,
        CompiledRecipeKey recipeKey,
        boolean persistToWorld
) implements HumanFeedback {

    public PinResourceRecipeFeedback {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(recipeKey, "recipeKey");
    }
}
