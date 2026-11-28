package dev.vfyjxf.cirrus.api.planning.human;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;

import java.util.Objects;

public record PinResourceRecipeFeedback(ComponentStackKey resource, CompiledRecipeKey recipeKey, boolean persistToWorld)
        implements
            HumanFeedback {

    public PinResourceRecipeFeedback {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(recipeKey, "recipeKey");
    }
}
