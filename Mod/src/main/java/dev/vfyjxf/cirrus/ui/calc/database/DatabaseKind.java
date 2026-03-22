package dev.vfyjxf.cirrus.ui.calc.database;

public enum DatabaseKind {
    plan,
    profile,
    custom,
    system,
    jei;

    public int defaultRank() {
        return switch (this) {
            case plan -> 0;
            case profile -> 1;
            case custom -> 2;
            case system -> 3;
            case jei -> 4;
        };
    }
}
