package dev.vfyjxf.cirrus.api.recipe.search;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;

public record SearchTerm(
    SearchField field,
    SearchOperator operator,
    String value,
    String upperBound,
    SearchOperator amountOperator,
    String amount,
    String amountUpperBound,
    boolean strict
) {
    public static final Codec<SearchTerm> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        SearchField.CODEC.fieldOf("field").forGetter(SearchTerm::field),
        SearchOperator.CODEC.fieldOf("operator").forGetter(SearchTerm::operator),
        Codec.STRING.fieldOf("value").forGetter(SearchTerm::value),
        Codec.STRING.optionalFieldOf("upper_bound", "").forGetter(SearchTerm::upperBound),
        SearchOperator.CODEC.optionalFieldOf("amount_operator", SearchOperator.exact).forGetter(SearchTerm::amountOperator),
        Codec.STRING.optionalFieldOf("amount", "").forGetter(SearchTerm::amount),
        Codec.STRING.optionalFieldOf("amount_upper_bound", "").forGetter(SearchTerm::amountUpperBound),
        Codec.BOOL.fieldOf("strict").forGetter(SearchTerm::strict)
    ).apply(instance, SearchTerm::new));

    public SearchTerm {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(upperBound, "upperBound");
        Objects.requireNonNull(amountOperator, "amountOperator");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(amountUpperBound, "amountUpperBound");
    }

    public SearchTerm(SearchField field, SearchOperator operator, String value) {
        this(field, operator, value, "");
    }

    public SearchTerm(SearchField field, SearchOperator operator, String value, String upperBound) {
        this(field, operator, value, upperBound, false);
    }

    public SearchTerm(SearchField field, SearchOperator operator, String value, String upperBound, boolean strict) {
        this(field, operator, value, upperBound, SearchOperator.exact, "", "", strict);
    }

    public SearchTerm(
        SearchField field,
        SearchOperator operator,
        String value,
        String upperBound,
        SearchOperator amountOperator,
        String amount,
        String amountUpperBound
    ) {
        this(field, operator, value, upperBound, amountOperator, amount, amountUpperBound, false);
    }

    public boolean hasAmount() {
        return !amount.isBlank() || !amountUpperBound.isBlank();
    }
}
