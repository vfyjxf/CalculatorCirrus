package dev.vfyjxf.calculatorcirrus.api.recipe.content;

/**
 * Forge Energy Content
 */
public record EnergyValue(long energy) {

    public static EnergyValue from(long energy) {
        return new EnergyValue(energy);
    }

}
