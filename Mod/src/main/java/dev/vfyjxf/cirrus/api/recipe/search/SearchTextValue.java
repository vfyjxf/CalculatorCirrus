package dev.vfyjxf.cirrus.api.recipe.search;

import java.util.Locale;
import java.util.Objects;

public final class SearchTextValue {
    private final SearchField field;
    private final String value;
    private final String normalizedValue;

    public SearchTextValue(SearchField field, String value) {
        this.field = field;
        this.value = value;
        this.normalizedValue = value.toLowerCase(Locale.ROOT);
    }

    public SearchField field() {
        return field;
    }

    public String value() {
        return value;
    }

    public String normalizedValue() {
        return normalizedValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SearchTextValue that)) return false;
        return Objects.equals(field, that.field) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, value);
    }

    @Override
    public String toString() {
        return "SearchTextValue[field=" + field + ", value=" + value + "]";
    }
}
