package dev.vfyjxf.cirrus.api.planning.graph;

public enum RecipeAdvisory {
    NONE(0),
    DISCOURAGED(1),
    LAST_RESORT(10);

    private final int weight;

    RecipeAdvisory(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }

    public static RecipeAdvisory max(RecipeAdvisory a, RecipeAdvisory b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
