package dev.vfyjxf.cirrus.api.recipe.projection;

/**
 * Channel of a slot inside a {@link RecipeProjection}.
 */
public enum SlotChannel {
    input,
    output;

    public boolean isInput() {
        return this == input;
    }

    public boolean isOutput() {
        return this == output;
    }
}
