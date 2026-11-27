package dev.vfyjxf.cirrus.api.search;

public record SearchMatch(boolean matched, int score) {
    public static final SearchMatch no = new SearchMatch(false, 0);
    public static final SearchMatch yes = new SearchMatch(true, 0);

    public SearchMatch {
        if (!matched) {
            score = 0;
        }
    }
}
