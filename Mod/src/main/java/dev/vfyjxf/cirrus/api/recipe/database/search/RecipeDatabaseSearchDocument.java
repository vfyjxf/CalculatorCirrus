package dev.vfyjxf.cirrus.api.recipe.database.search;

import dev.vfyjxf.cirrus.api.recipe.search.CirrusSearchFields;
import dev.vfyjxf.cloudlib.api.search.SearchContentValue;
import dev.vfyjxf.cloudlib.api.search.SearchDocument;
import dev.vfyjxf.cloudlib.api.search.SearchField;
import dev.vfyjxf.cloudlib.api.search.SearchFieldSet;
import dev.vfyjxf.cloudlib.api.search.SearchTexts;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RecipeDatabaseSearchDocument {
    private static final String[] emptyStrings = new String[0];
    private static final ContentTerm[] emptyContent = new ContentTerm[0];
    private static final double[] emptyNumbers = new double[0];

    private final String[][] rawText;
    private final String[][] normalizedText;
    private final SearchField[][] textSourceFields;
    private final ContentTerm[][] contentTerms;
    private final double[][] numbers;
    private final byte[] present;
    private final SearchFieldSet fieldSet;

    private RecipeDatabaseSearchDocument(
        SearchFieldSet fieldSet,
        String[][] rawText,
        String[][] normalizedText,
        SearchField[][] textSourceFields,
        ContentTerm[][] contentTerms,
        double[][] numbers,
        byte[] present
    ) {
        this.fieldSet = fieldSet;
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

    /** The registry snapshot this document's arrays are indexed against. */
    public SearchFieldSet fieldSet() {
        return fieldSet;
    }

    public static RecipeDatabaseSearchDocument fromExisting(SearchDocument document) {
        Builder builder = builder();
        for (SearchField field : CirrusSearchFields.SET.fields()) {
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
        int id = field.id();
        return id < rawText.length && rawText[id] != null ? rawText[id] : emptyStrings;
    }

    public String[] normalizedText(SearchField field) {
        int id = field.id();
        return id < normalizedText.length && normalizedText[id] != null ? normalizedText[id] : emptyStrings;
    }

    public SearchField[] textSourceFields(SearchField field) {
        int id = field.id();
        return id < textSourceFields.length && textSourceFields[id] != null ? textSourceFields[id] : new SearchField[0];
    }

    public ContentTerm[] contentTerms(SearchField field) {
        int id = field.id();
        return id < contentTerms.length && contentTerms[id] != null ? contentTerms[id] : emptyContent;
    }

    public double[] numbers(SearchField field) {
        int id = field.id();
        return id < numbers.length && numbers[id] != null ? numbers[id] : emptyNumbers;
    }

    public boolean present(SearchField field) {
        int id = field.id();
        return id < present.length && present[id] != 0;
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
        private final SearchFieldSet fieldSet = CirrusSearchFields.SET;
        private final ArrayList<String>[] rawText = stringLists();
        private final ArrayList<String>[] normalizedText = stringLists();
        private final ArrayList<SearchField>[] textSourceFields = fieldLists();
        private final HashSet<String>[] textDedupSets = stringSets();
        private final LinkedHashMap<ContentKey, MergedContent>[] content = contentMaps();
        private final ArrayList<Double>[] numbers = numberLists();

        public Builder text(SearchField field, String value) {
            textOnly(field, value, field);
            return this;
        }

        public Builder searchableText(SearchField field, String value) {
            // normalize once for both target fields; textOnly guards stay in
            // textOnlyNormalized so the contains/text field rules are unchanged
            if (value != null && !value.isBlank()) {
                String normalizedValue = normalize(value);
                textOnlyNormalized(field, value, field, normalizedValue);
                if (field != CirrusSearchFields.text && field != CirrusSearchFields.contains) {
                    textOnlyNormalized(CirrusSearchFields.text, value, field, normalizedValue);
                }
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
            // one normalization serves the content merge and both text channels;
            // previously each of the three steps re-normalized the same string
            searchableContentNormalized(field, value, amount, kind, normalize(value));
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
            for (Map.Entry<String, String> alias : uniqueAliases.entrySet()) {
                // the alias dedup key already is the normalized form
                searchableContentNormalized(field, alias.getValue(), amount, kind, alias.getKey());
            }
            return this;
        }

        public Builder number(SearchField field, double value) {
            numberOnly(field, value);
            return this;
        }

        private void searchableContentNormalized(SearchField field, String value, double amount, ContentKind kind, String normalizedValue) {
            mergeContent(field, value, amount, kind, normalizedValue);
            textOnlyNormalized(field, value, field, normalizedValue);
            if (!kind.idChannel()) {
                textOnlyNormalized(CirrusSearchFields.text, value, field, normalizedValue);
            }
        }

        private Builder textOnly(SearchField field, String value, SearchField sourceField) {
            if (field == CirrusSearchFields.contains || value == null || value.isBlank()) {
                return this;
            }
            return textOnlyNormalized(field, value, sourceField, normalize(value));
        }

        private Builder textOnlyNormalized(SearchField field, String value, SearchField sourceField, String normalizedValue) {
            if (field == CirrusSearchFields.contains) {
                return this;
            }
            int index = field.id();
            ArrayList<String> existing = normalizedText[index];
            if (existing != null) {
                HashSet<String> dedup = textDedupSets[index];
                if (dedup == null && existing.size() > SMALL_LIST_DEDUP_LIMIT) {
                    // big list inputs (tag expansions, ore dicts) make the
                    // linear scan quadratic; promote to a set past the limit
                    dedup = new HashSet<>(existing);
                    textDedupSets[index] = dedup;
                }
                if (dedup != null) {
                    if (!dedup.add(normalizedValue)) {
                        return this;
                    }
                } else {
                    for (String candidate : existing) {
                        if (candidate.equals(normalizedValue)) {
                            return this;
                        }
                    }
                }
            }
            list(rawText, index).add(value);
            list(normalizedText, index).add(normalizedValue);
            list(textSourceFields, index).add(sourceField);
            return this;
        }

        private Builder numberOnly(SearchField field, double value) {
            if (field != CirrusSearchFields.contains && field != CirrusSearchFields.text && Double.isFinite(value)) {
                list(numbers, field.id()).add(value);
            }
            return this;
        }

        private void mergeContent(SearchField field, String value, double amount, ContentKind kind, String normalizedValue) {
            ContentKey normalized = new ContentKey(kind, normalizedValue);
            LinkedHashMap<ContentKey, MergedContent> fieldContent = map(content, field.id());
            MergedContent existing = fieldContent.get(normalized);
            fieldContent.put(normalized, existing == null
                ? new MergedContent(value, normalizedValue, amount, kind)
                : existing.plus(amount)
            );
        }

        public RecipeDatabaseSearchDocument build() {
            flushMergedContent();

            String[][] raw = new String[fieldSet.size()][];
            String[][] normalized = new String[fieldSet.size()][];
            SearchField[][] sources = new SearchField[fieldSet.size()][];
            ContentTerm[][] contentArrays = new ContentTerm[fieldSet.size()][];
            double[][] numberArrays = new double[fieldSet.size()][];
            byte[] present = new byte[fieldSet.size()];

            for (SearchField field : fieldSet.fields()) {
                int ordinal = field.id();
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
            return new RecipeDatabaseSearchDocument(fieldSet, raw, normalized, sources, contentArrays, numberArrays, present);
        }

        private void flushMergedContent() {
            for (SearchField field : fieldSet.fields()) {
                LinkedHashMap<ContentKey, MergedContent> fieldContent = content[field.id()];
                if (fieldContent == null) {
                    continue;
                }
                for (MergedContent entry : fieldContent.values()) {
                    numberOnly(field, entry.amount());
                }
            }
        }

        private static boolean validContent(SearchField field, String value, double amount) {
            return field != CirrusSearchFields.contains
                && field != CirrusSearchFields.text
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

        private static ContentTerm[] contentArray(LinkedHashMap<ContentKey, MergedContent> values) {
            if (values == null || values.isEmpty()) {
                return emptyContent;
            }
            ContentTerm[] out = new ContentTerm[values.size()];
            int index = 0;
            for (Map.Entry<ContentKey, MergedContent> entry : values.entrySet()) {
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
            return SearchTexts.normalize(value);
        }

        private record ContentKey(ContentKind kind, String normalizedValue) {
        }

        private static final int SMALL_LIST_DEDUP_LIMIT = 16;

        private static final SearchField[] emptyFields = new SearchField[0];

        @SuppressWarnings("unchecked")
        private ArrayList<String>[] stringLists() {
            return new ArrayList[fieldSet.size()];
        }

        @SuppressWarnings("unchecked")
        private HashSet<String>[] stringSets() {
            return new HashSet[fieldSet.size()];
        }

        @SuppressWarnings("unchecked")
        private ArrayList<SearchField>[] fieldLists() {
            return new ArrayList[fieldSet.size()];
        }

        @SuppressWarnings("unchecked")
        private ArrayList<Double>[] numberLists() {
            return new ArrayList[fieldSet.size()];
        }

        @SuppressWarnings("unchecked")
        private LinkedHashMap<ContentKey, MergedContent>[] contentMaps() {
            return new LinkedHashMap[fieldSet.size()];
        }

        private static <T> ArrayList<T> list(ArrayList<T>[] lists, int index) {
            ArrayList<T> list = lists[index];
            if (list == null) {
                list = new ArrayList<>();
                lists[index] = list;
            }
            return list;
        }

        private static LinkedHashMap<ContentKey, MergedContent> map(LinkedHashMap<ContentKey, MergedContent>[] maps, int index) {
            LinkedHashMap<ContentKey, MergedContent> map = maps[index];
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
