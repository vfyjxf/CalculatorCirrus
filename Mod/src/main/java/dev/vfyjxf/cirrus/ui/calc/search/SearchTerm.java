package dev.vfyjxf.cirrus.ui.calc.search;

public record SearchTerm(
    SearchField field,
    SearchOperator operator,
    String raw
) {
}
