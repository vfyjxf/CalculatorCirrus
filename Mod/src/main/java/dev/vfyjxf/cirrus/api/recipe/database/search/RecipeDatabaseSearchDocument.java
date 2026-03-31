package dev.vfyjxf.cirrus.api.recipe.database.search;

import dev.vfyjxf.cirrus.api.recipe.search.SearchContentValue;
import dev.vfyjxf.cirrus.api.recipe.search.SearchDocument;
import dev.vfyjxf.cirrus.api.recipe.search.SearchField;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RecipeDatabaseSearchDocument {
    private static final String[] emptyStrings = new String[0];
    private static final ContentTerm[] emptyContent = new ContentTerm[0];
    private static final double[] emptyNumbers = new double[0];
    private static final SearchField[] fields = SearchField.values();

    private final String[][] rawText;
    private final String[][] normalizedText;
    private final SearchField[][] textSourceFields;
    private final ContentTerm[][] contentTerms;
    private final double[][] numbers;
    private final byte[] present;

    private RecipeDatabaseSearchDocument(
        String[][] rawText,
        String[][] normalizedText,
        SearchField[][] textSourceFields,
        ContentTerm[][] contentTerms,
        double[][] numbers,
        byte[] present
    ) {
        this.rawText = rawText;
        this.normalizedText = normalizedText;
        this.textSourceFields = textSourceFields;
        this.contentTerms = contentTerms;
        this.numbers = numbers;
        this.present = present;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RecipeDatabaseSearchDocument fromExisting(SearchDocument document) {
        Builder builder = builder();
        for (SearchField field : SearchField.values()) {
            for (var entry : document.textEntryArray(field)) {
                builder.textOnly(field, entry.value(), entry.field());
            }
            for (SearchContentValue entry : document.contentEntries(field)) {
                builder.searchableContent(field, entry.value(), entry.amount());
            }
            for (double number : document.numberValues(field)) {
                builder.number(field, number);
            }
        }
        return builder.build();
    }

    public String[] rawText(SearchField field) {
        return rawText[field.ordinal()];
    }

    public String[] normalizedText(SearchField field) {
        return normalizedText[field.ordinal()];
    }

    public SearchField[] textSourceFields(SearchField field) {
        return textSourceFields[field.ordinal()];
    }

    public ContentTerm[] contentTerms(SearchField field) {
        return contentTerms[field.ordinal()];
    }

    public double[] numbers(SearchField field) {
        return numbers[field.ordinal()];
    }

    public boolean present(SearchField field) {
        return present[field.ordinal()] != 0;
    }

    public record ContentTerm(String value, String normalizedValue, double amount, ContentKind kind) {
    }

    public enum ContentKind {
        itemName,
        itemId,
        fluidName,
        fluidId,
        tagId,
        otherName,
        otherId;

        public boolean idChannel() {
            return switch (this) {
                case itemId, fluidId, tagId, otherId -> true;
                default -> false;
            };
        }
    }

    public static final class Builder {
        private final ArrayList<String>[] rawText = stringLists();
        private final ArrayList<String>[] normalizedText = stringLists();
        private final ArrayList<SearchField>[] textSourceFields = fieldLists();
        private final LinkedHashMap<String, MergedContent>[] content = contentMaps();
        private final ArrayList<Double>[] numbers = numberLists();

        public Builder text(SearchField field, String value) {
            textOnly(field, value, field);
            return this;
        }

        public Builder searchableText(SearchField field, String value) {
            textOnly(field, value, field);
            if (field != SearchField.text && field != SearchField.contains) {
                textOnly(SearchField.text, value, field);
            }
            return this;
        }

        public Builder searchableContent(SearchField field, String value, double amount) {
            return searchableContent(field, value, amount, ContentKind.otherName);
        }

        public Builder searchableContent(SearchField field, String value, double amount, ContentKind kind) {
            if (!validContent(field, value, amount)) {
                return this;
            }
            mergeContent(field, value, amount, kind);
            textOnly(field, value, field);
            if (!kind.idChannel()) {
                textOnly(SearchField.text, value, field);
            }
            return this;
        }

        public Builder searchableContentAliases(SearchField field, Iterable<String> values, double amount, ContentKind kind) {
            if (values == null) {
                return this;
            }
            LinkedHashMap<String, String> uniqueAliases = new LinkedHashMap<>();
            for (String value : values) {
                if (validContent(field, value, amount)) {
                    uniqueAliases.putIfAbsent(normalize(value), value);
                }
            }
            for (String value : uniqueAliases.values()) {
                searchableContent(field, value, amount, kind);
            }
            return this;
        }

        public Builder number(SearchField field, double value) {
            numberOnly(field, value);
            return this;
        }

        private Builder textOnly(SearchField field, String value, SearchField sourceField) {
            if (field == SearchField.contains || value == null || value.isBlank()) {
                return this;
            }
            String normalizedValue = normalize(value);
            int index = field.ordinal();
            ArrayList<String> existing = normalizedText[index];
            if (existing != null) {
                for (String candidate : existing) {
                    if (candidate.equals(normalizedValue)) {
                        return this;
                    }
                }
            }
            list(rawText, index).add(value);
            list(normalizedText, index).add(normalizedValue);
            list(textSourceFields, index).add(sourceField);
            return this;
        }

        private Builder numberOnly(SearchField field, double value) {
            if (field != SearchField.contains && field != SearchField.text && Double.isFinite(value)) {
                list(numbers, field.ordinal()).add(value);
            }
            return this;
        }

        private void mergeContent(SearchField field, String value, double amount, ContentKind kind) {
            String normalizedValue = normalize(value);
            String normalized = contentKey(normalizedValue, kind);
            LinkedHashMap<String, MergedContent> fieldContent = map(content, field.ordinal());
            MergedContent existing = fieldContent.get(normalized);
            fieldContent.put(normalized, existing == null
                ? new MergedContent(value, normalizedValue, amount, kind)
                : existing.plus(amount)
            );
        }

        public RecipeDatabaseSearchDocument build() {
            flushMergedContent();

            String[][] raw = new String[fields.length][];
            String[][] normalized = new String[fields.length][];
            SearchField[][] sources = new SearchField[fields.length][];
            ContentTerm[][] contentArrays = new ContentTerm[fields.length][];
            double[][] numberArrays = new double[fields.length][];
            byte[] present = new byte[fields.length];

            for (SearchField field : fields) {
                int ordinal = field.ordinal();
                raw[ordinal] = stringArray(rawText[ordinal]);
                normalized[ordinal] = stringArray(normalizedText[ordinal]);
                sources[ordinal] = sourceFieldArray(textSourceFields[ordinal]);
                contentArrays[ordinal] = contentArray(content[ordinal]);
                numberArrays[ordinal] = numberArray(numbers[ordinal]);
                if (raw[ordinal].length > 0
                    || contentArrays[ordinal].length > 0
                    || numberArrays[ordinal].length > 0) {
                    present[ordinal] = 1;
                }
            }
            return new RecipeDatabaseSearchDocument(raw, normalized, sources, contentArrays, numberArrays, present);
        }

        private void flushMergedContent() {
            for (SearchField field : fields) {
                LinkedHashMap<String, MergedContent> fieldContent = content[field.ordinal()];
                if (fieldContent == null) {
                    continue;
                }
                for (MergedContent entry : fieldContent.values()) {
                    numberOnly(field, entry.amount());
                }
            }
        }

        private static boolean validContent(SearchField field, String value, double amount) {
            return field != SearchField.contains
                && field != SearchField.text
                && value != null
                && !value.isBlank()
                && Double.isFinite(amount);
        }

        private static String[] stringArray(ArrayList<String> values) {
            return values == null || values.isEmpty() ? emptyStrings : values.toArray(new String[0]);
        }

        private static SearchField[] sourceFieldArray(ArrayList<SearchField> values) {
            return values == null || values.isEmpty() ? emptyFields : values.toArray(new SearchField[0]);
        }

        private static ContentTerm[] contentArray(LinkedHashMap<String, MergedContent> values) {
            if (values == null || values.isEmpty()) {
                return emptyContent;
            }
            ContentTerm[] out = new ContentTerm[values.size()];
            int index = 0;
            for (Map.Entry<String, MergedContent> entry : values.entrySet()) {
                MergedContent content = entry.getValue();
                out[index++] = new ContentTerm(content.value(), content.normalizedValue(), content.amount(), content.kind());
            }
            return out;
        }

        private static double[] numberArray(ArrayList<Double> values) {
            if (values == null || values.isEmpty()) {
                return emptyNumbers;
            }
            double[] out = new double[values.size()];
            for (int index = 0; index < values.size(); index++) {
                out[index] = values.get(index);
            }
            return out;
        }

        private static String normalize(String value) {
            return value.toLowerCase(Locale.ROOT);
        }

        private static String contentKey(String normalizedValue, ContentKind kind) {
            return kind.ordinal() + "\0" + normalizedValue;
        }

        private static final SearchField[] emptyFields = new SearchField[0];

        @SuppressWarnings("unchecked")
        private static ArrayList<String>[] stringLists() {
            return new ArrayList[fields.length];
        }

        @SuppressWarnings("unchecked")
        private static ArrayList<SearchField>[] fieldLists() {
            return new ArrayList[fields.length];
        }

        @SuppressWarnings("unchecked")
        private static ArrayList<Double>[] numberLists() {
            return new ArrayList[fields.length];
        }

        @SuppressWarnings("unchecked")
        private static LinkedHashMap<String, MergedContent>[] contentMaps() {
            return new LinkedHashMap[fields.length];
        }

        private static <T> ArrayList<T> list(ArrayList<T>[] lists, int index) {
            ArrayList<T> list = lists[index];
            if (list == null) {
                list = new ArrayList<>();
                lists[index] = list;
            }
            return list;
        }

        private static LinkedHashMap<String, MergedContent> map(LinkedHashMap<String, MergedContent>[] maps, int index) {
            LinkedHashMap<String, MergedContent> map = maps[index];
            if (map == null) {
                map = new LinkedHashMap<>();
                maps[index] = map;
            }
            return map;
        }

        private record MergedContent(String value, String normalizedValue, double amount, ContentKind kind) {
            private MergedContent plus(double delta) {
                return new MergedContent(value, normalizedValue, amount + delta, kind);
            }
        }
    }
}
