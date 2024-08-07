package dev.vfyjxf.calculatorcirrus.api.content;

public enum MatchContext {
    EXACT(true, false, true),
    FUZZY(false, false, false),
    EXACT_WITH_COUNT(true, true, true),
    FUZZY_WITH_COUNT(false, true, false),
    RECIPE(true, true, false);

    private final boolean exact;
    private final boolean withCount;
    private final boolean withNbt;

    MatchContext(boolean exact, boolean withCount, boolean withNbt) {
        this.exact = exact;
        this.withCount = withCount;
        this.withNbt = withNbt;
    }

    public boolean exact() {
        return exact;
    }

    public boolean fuzzy() {
        return !exact;
    }

    public boolean withCount() {
        return withCount;
    }

    public boolean withoutCount() {
        return !withCount;
    }

    public boolean withNbt() {
        return withNbt;
    }

    public boolean withoutNbt() {
        return !withNbt;
    }
}
