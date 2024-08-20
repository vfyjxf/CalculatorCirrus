package dev.vfyjxf.calculatorcirrus.api.content;

public class MatchContext {
    public static final MatchContext EXACT = create(true, false);
    public static final MatchContext FUZZY = create(false, false);

    public static MatchContext create(boolean exact, boolean withNbt) {
        return new MatchContext(exact, withNbt);
    }

    private final boolean exact;
    private final boolean withNbt;

    private MatchContext(boolean exact, boolean withNbt) {
        this.exact = exact;
        this.withNbt = withNbt;
    }

    public boolean exact() {
        return exact;
    }

    public boolean fuzzy() {
        return !exact;
    }

    public boolean withNbt() {
        return withNbt;
    }

    public boolean withoutNbt() {
        return !withNbt;
    }
}
