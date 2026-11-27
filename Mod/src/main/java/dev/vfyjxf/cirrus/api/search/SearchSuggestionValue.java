package dev.vfyjxf.cirrus.api.search;

/**
 * A value-completion candidate with a relevance count (aggregate postings
 * volume of the dictionary terms behind it), used to rank suggestions.
 */
public record SearchSuggestionValue(String value, int count) {}
