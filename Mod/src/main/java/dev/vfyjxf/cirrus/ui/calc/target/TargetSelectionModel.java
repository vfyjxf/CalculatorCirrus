package dev.vfyjxf.cirrus.ui.calc.target;

import dev.vfyjxf.cirrus.ui.calc.search.SearchExpression;
import dev.vfyjxf.cirrus.ui.calc.search.SearchMatcher;
import dev.vfyjxf.cirrus.ui.calc.search.SearchParser;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public final class TargetSelectionModel {
    private final SearchParser parser = new SearchParser();
    private String search = "";
    private String systemFilter = "";

    public void setSearch(String search) {
        this.search = Objects.requireNonNullElse(search, "");
    }

    public void setSystemFilter(String systemId) {
        this.systemFilter = Objects.requireNonNullElse(systemId, "");
    }

    public List<RecipeCandidate> filter(List<RecipeCandidate> input) {
        SearchExpression expr = parser.parse(search);
        String system = systemFilter.toLowerCase(Locale.ROOT);
        return input.stream()
            .filter(candidate -> system.isEmpty() || candidate.systemId().toLowerCase(Locale.ROOT).equals(system))
            .filter(candidate -> SearchMatcher.matches(expr, candidate))
            .collect(Collectors.toList());
    }
}
