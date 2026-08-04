package dev.vfyjxf.cirrus.api.recipe.value;

/**
 * Forge Energy Content
 */
public record EnergyValue(long energy) {

    public static EnergyValue from(long energy) {
        return new EnergyValue(energy);
    }

}
