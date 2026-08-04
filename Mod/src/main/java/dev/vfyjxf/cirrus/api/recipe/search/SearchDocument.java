package dev.vfyjxf.cirrus.api.recipe.search;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public final class SearchDocument implements SearchableDocument {
    private final Map<SearchField, List<String>> textValues;
    private final Map<SearchField, List<Double>> numberValues;
    private final Map<SearchField, List<SearchContentValue>> contentValues;
    private final List<SearchTextValue> defaultText;

    private final List<SearchTextValue>[] textEntriesByField;
    private final SearchTextValue[][] textEntryArraysByField;
    private final TextBucket[] textBuckets;

    private SearchDocument(
        Map<SearchField, List<String>> textValues,
        Map<SearchField, List<Double>> numberValues,
        Map<SearchField, List<SearchContentValue>> contentValues,
        List<SearchTextValue> defaultText
    ) {
        this.textValues = freeze(textValues);
        this.numberValues = freeze(numberValues);
        this.contentValues = freeze(contentValues);
        this.defaultText = List.copyOf(defaultText);

        // Precompute array-backed text entries for every field
        @SuppressWarnings("unchecked")
        List<SearchTextValue>[] entriesByField = new List[SearchField.values().length];
        SearchTextValue[][] arraysByField = new SearchTextValue[SearchField.values().length][];

        BiConsumer<SearchField, List<SearchTextValue>> putField = (field, entries) -> {
            List<SearchTextValue> immutable = List.copyOf(entries);
            entriesByField[field.ordinal()] = immutable;
            arraysByField[field.ordinal()] = immutable.toArray(new SearchTextValue[0]);
        };

        putField.accept(SearchField.text, defaultText);

        for (SearchField field : SearchField.values()) {
            if (field == SearchField.text || field == SearchField.contains) continue;
            ArrayList<SearchTextValue> values = new ArrayList<>();
            addContentText(values, field);
            putField.accept(field, values);
        }

        // contains = union of in, out, cat, byproduct
        ArrayList<SearchTextValue> containsValues = new ArrayList<>();
        containsValues.addAll(entriesByField[SearchField.in.ordinal()]);
        containsValues.addAll(entriesByField[SearchField.in_id.ordinal()]);
        containsValues.addAll(entriesByField[SearchField.out.ordinal()]);
        containsValues.addAll(entriesByField[SearchField.out_id.ordinal()]);
        containsValues.addAll(entriesByField[SearchField.cat.ordinal()]);
        containsValues.addAll(entriesByField[SearchField.cat_id.ordinal()]);
        containsValues.addAll(entriesByField[SearchField.byproduct.ordinal()]);
        containsValues.addAll(entriesByField[SearchField.byproduct_id.ordinal()]);
        putField.accept(SearchField.contains, containsValues);

        this.textEntriesByField = entriesByField;
        this.textEntryArraysByField = arraysByField;

        // Build text buckets for aggregate haystack matching
        TextBucket[] buckets = new TextBucket[SearchField.values().length];
        for (int i = 0; i < SearchField.values().length; i++) {
            SearchTextValue[] array = arraysByField[i];
            if (array.length == 0) {
                buckets[i] = new TextBucket(array, "", "");
                continue;
            }
            StringBuilder raw = new StringBuilder();
            StringBuilder norm = new StringBuilder();
            for (SearchTextValue entry : array) {
                raw.append('\0').append(entry.value());
                norm.append('\0').append(entry.normalizedValue());
            }
            buckets[i] = new TextBucket(array, raw.toString(), norm.toString());
        }
        this.textBuckets = buckets;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<SearchTextValue> textEntries(SearchField field) {
        return textEntriesByField[field.ordinal()];
    }

    @Override
    public SearchTextValue[] textEntryArray(SearchField field) {
        return textEntryArraysByField[field.ordinal()];
    }

    TextBucket textBucket(SearchField field) {
        return textBuckets[field.ordinal()];
    }

    private void addContentText(ArrayList<SearchTextValue> out, SearchField field) {
        for (SearchContentValue entry : contentValues.getOrDefault(field, List.of())) {
            out.add(new SearchTextValue(entry.field(), entry.value()));
        }
        for (String value : textValues.getOrDefault(field, List.of())) {
            out.add(new SearchTextValue(field, value));
        }
    }

    @Override
    public List<Double> numberValues(SearchField field) {
        if (field == SearchField.contains) {
            ArrayList<Double> values = new ArrayList<>();
            for (SearchField contentField : List.of(SearchField.in, SearchField.in_id, SearchField.out, SearchField.out_id, SearchField.cat, SearchField.cat_id, SearchField.byproduct, SearchField.byproduct_id)) {
                values.addAll(numberValues.getOrDefault(contentField, List.of()));
            }
            return List.copyOf(values);
        }
        return numberValues.getOrDefault(field, List.of());
    }

    @Override
    public List<SearchContentValue> contentEntries(SearchField field) {
        if (field == SearchField.contains) {
            ArrayList<SearchContentValue> values = new ArrayList<>();
            addContentEntries(values, SearchField.in);
            addContentEntries(values, SearchField.in_id);
            addContentEntries(values, SearchField.out);
            addContentEntries(values, SearchField.out_id);
            addContentEntries(values, SearchField.cat);
            addContentEntries(values, SearchField.cat_id);
            addContentEntries(values, SearchField.byproduct);
            addContentEntries(values, SearchField.byproduct_id);
            return List.copyOf(values);
        }
        return contentValues.getOrDefault(field, List.of());
    }

    private void addContentEntries(ArrayList<SearchContentValue> out, SearchField field) {
        out.addAll(contentValues.getOrDefault(field, List.of()));
    }

    private static <T> Map<SearchField, List<T>> freeze(Map<SearchField, List<T>> source) {
        EnumMap<SearchField, List<T>> copy = new EnumMap<>(SearchField.class);
        for (Map.Entry<SearchField, List<T>> entry : source.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                copy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        return Map.copyOf(copy);
    }

    record TextBucket(SearchTextValue[] entries, String rawHaystack, String normalizedHaystack) {}

    public static final class Builder {
        private final EnumMap<SearchField, List<String>> textValues = new EnumMap<>(SearchField.class);
        private final EnumMap<SearchField, List<Double>> numberValues = new EnumMap<>(SearchField.class);
        private final EnumMap<SearchField, List<SearchContentValue>> contentValues = new EnumMap<>(SearchField.class);
        private final ArrayList<SearchTextValue> defaultText = new ArrayList<>();

        public Builder text(SearchField field, String value) {
            if (field != SearchField.contains && value != null && !value.isBlank()) {
                if (field == SearchField.text) {
                    defaultText.add(new SearchTextValue(SearchField.text, value));
                } else {
                    textValues.computeIfAbsent(field, ignored -> new ArrayList<>()).add(value);
                }
            }
            return this;
        }

        public Builder searchableText(SearchField field, String value) {
            text(field, value);
            if (field != SearchField.contains && field != SearchField.text && value != null && !value.isBlank()) {
                defaultText.add(new SearchTextValue(field, value));
            }
            return this;
        }

        public Builder searchableContent(SearchField field, String value, double amount) {
            if (field != SearchField.contains
                && field != SearchField.text
                && value != null
                && !value.isBlank()
                && Double.isFinite(amount)) {
                SearchContentValue entry = new SearchContentValue(field, value, amount);
                contentValues.computeIfAbsent(field, ignored -> new ArrayList<>()).add(entry);
                if (!isExplicitIdContentField(field)) {
                    defaultText.add(new SearchTextValue(field, value));
                }
                number(field, amount);
            }
            return this;
        }

        public Builder texts(SearchField field, Iterable<String> values) {
            if (values != null) {
                for (String value : values) {
                    text(field, value);
                }
            }
            return this;
        }

        public Builder searchableTexts(SearchField field, Iterable<String> values) {
            if (values != null) {
                for (String value : values) {
                    searchableText(field, value);
                }
            }
            return this;
        }

        public Builder number(SearchField field, double value) {
            if (field != SearchField.contains && field != SearchField.text && Double.isFinite(value)) {
                numberValues.computeIfAbsent(field, ignored -> new ArrayList<>()).add(value);
            }
            return this;
        }

        public SearchDocument build() {
            return new SearchDocument(textValues, numberValues, contentValues, defaultText);
        }

        private static boolean isExplicitIdContentField(SearchField field) {
            return switch (field) {
                case in_id, out_id, cat_id, byproduct_id -> true;
                default -> false;
            };
        }
    }
}
