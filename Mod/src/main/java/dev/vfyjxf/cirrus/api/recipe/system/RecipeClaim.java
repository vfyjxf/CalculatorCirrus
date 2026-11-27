package dev.vfyjxf.cirrus.api.recipe.system;

public enum RecipeClaim {
    none(0), weak(1), normal(2), exact(3);

    private final int rank;

    RecipeClaim(int rank) {
        this.rank = rank;
    }

    public boolean claimed() {
        return this != none;
    }

    public boolean strongerThan(RecipeClaim other) {
        return rank > other.rank;
    }

    public int rank() {
        return rank;
    }
}
