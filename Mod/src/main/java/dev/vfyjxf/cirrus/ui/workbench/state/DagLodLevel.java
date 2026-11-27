package dev.vfyjxf.cirrus.ui.workbench.state;

public enum DagLodLevel {
    macro, blueprint, microRecipe;

    public static final float blueprintMinZoom = 0.50f;
    public static final float microRecipeMinZoom = 1.20f;

    public static DagLodLevel forZoom(float zoom) {
        if (Float.isNaN(zoom)) {
            return blueprint;
        }
        if (zoom < blueprintMinZoom) {
            return macro;
        }
        if (zoom < microRecipeMinZoom) {
            return blueprint;
        }
        return microRecipe;
    }
}
