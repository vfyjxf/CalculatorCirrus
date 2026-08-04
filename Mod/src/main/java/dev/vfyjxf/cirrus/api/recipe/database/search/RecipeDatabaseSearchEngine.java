package dev.vfyjxf.cirrus.api.recipe.database.search;

import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseRecord;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseResult;
import dev.vfyjxf.cirrus.api.recipe.search.*;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public final class RecipeDatabaseSearchEngine {
    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Database");
    private static final int[] emptyPosting = new int[0];
    private static final int parallelThreshold = 4096;
    private static final int textCandidateLimit = 2048;
    private static final int linearUniqueThreshold = 64;

    private final List<RecipeDatabaseRecord> records;
    private final RecipeDatabaseSearchDocument[] documents;
    private final int[] allRecipeIds;
    private final EnumMap<SearchField, FieldIndex> fields;
    private final Map<Namespace, int[]> systems;
    private @Nullable List<RecipeDatabaseResult> allResults;

    private RecipeDatabaseSearchEngine(
            List<RecipeDatabaseRecord> records,
            RecipeDatabaseSearchDocument[] documents,
            int[] allRecipeIds,
            EnumMap<SearchField, FieldIndex> fields,
            Map<Namespace, int[]> systems
    ) {
        this.records = records;
        this.documents = documents;
        this.allRecipeIds = allRecipeIds;
        this.fields = fields;
        this.systems = systems;
    }

    public static RecipeDatabaseSearchEngine empty() {
        SearchField[] searchFields = SearchField.values();
        EnumMap<SearchField, FieldIndex> fields = new EnumMap<>(SearchField.class);
        for (SearchField field : searchFields) {
            fields.put(field, FieldIndex.empty());
        }
        return new RecipeDatabaseSearchEngine(List.of(), new RecipeDatabaseSearchDocument[0], emptyPosting, fields, Map.of());
    }

    public static RecipeDatabaseSearchEngine build(List<RecipeDatabaseRecord> records) {
        if (records == null || records.isEmpty()) {
            return empty();
        }
        long startNanos = System.nanoTime();
        RecipeDatabaseSearchDocument[] documents = new RecipeDatabaseSearchDocument[records.size()];
        int[] all = new int[records.size()];
        for (int recipeId = 0; recipeId < records.size(); recipeId++) {
            all[recipeId] = recipeId;
            documents[recipeId] = records.get(recipeId).searchDocument();
        }
        long documentsNanos = System.nanoTime();

        SearchField[] searchFields = SearchField.values();
        FieldIndex[] built = new FieldIndex[searchFields.length];
        ExecutorService sharedExecutor = null;
        int sharedWorkers = 0;
        if (documents.length >= parallelThreshold) {
            sharedWorkers = Math.min(documents.length, Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
            sharedExecutor = Executors.newFixedThreadPool(sharedWorkers, daemonThreadFactory("cirrus-db-search-"));
            try {
                for (int index = 0; index < searchFields.length; index++) {
                    SearchField field = searchFields[index];
                    if (FieldIndex.shouldBuildPostingIndex(field) || field == SearchField.contains) {
                        long fieldStartNanos = System.nanoTime();
                        built[index] = FieldIndex.build(documents, field, sharedExecutor, sharedWorkers);
                        logFieldBuild(field, built[index], documents.length, fieldStartNanos);
                    }
                }
                buildScanOnlyFields(documents, searchFields, built, sharedExecutor, sharedWorkers);
            } finally {
                sharedExecutor.shutdown();
            }
        } else {
            parallelRanges(searchFields.length, "cirrus-db-search-field-", (from, to) -> {
                for (int index = from; index < to; index++) {
                    SearchField field = searchFields[index];
                    if (FieldIndex.shouldBuildPostingIndex(field) || field == SearchField.contains) {
                        long fieldStartNanos = System.nanoTime();
                        built[index] = FieldIndex.build(documents, field);
                        logFieldBuild(field, built[index], documents.length, fieldStartNanos);
                    }
                }
            });
            buildScanOnlyFields(documents, searchFields, built, null, 0);
        }
        EnumMap<SearchField, FieldIndex> fields = new EnumMap<>(SearchField.class);
        for (SearchField field : searchFields) {
            fields.put(field, built[field.ordinal()]);
        }
        Map<Namespace, int[]> systems = buildSystemPostings(records);
        logBuildStats(records.size(), built, documentsNanos - startNanos, System.nanoTime() - documentsNanos, System.nanoTime() - startNanos);
        return new RecipeDatabaseSearchEngine(records, documents, all, fields, systems);
    }

    private static void buildScanOnlyFields(
            RecipeDatabaseSearchDocument[] documents,
            SearchField[] searchFields,
            FieldIndex[] built,
            @Nullable ExecutorService sharedExecutor,
            int sharedWorkers
    ) {
        int scanFieldCount = 0;
        for (SearchField field : searchFields) {
            if (built[field.ordinal()] == null && !FieldIndex.shouldBuildPostingIndex(field) && field != SearchField.contains) {
                scanFieldCount++;
            }
        }
        if (scanFieldCount == 0) {
            return;
        }
        SearchField[] scanFields = new SearchField[scanFieldCount];
        int scanCursor = 0;
        for (SearchField field : searchFields) {
            if (built[field.ordinal()] == null && !FieldIndex.shouldBuildPostingIndex(field) && field != SearchField.contains) {
                scanFields[scanCursor++] = field;
            }
        }
        if (sharedExecutor != null && documents.length >= parallelThreshold && sharedWorkers > 1) {
            buildScanOnlyFieldsParallel(documents, searchFields, scanFields, built, sharedExecutor, sharedWorkers);
            return;
        }
        IntScratch[] recordsByField = new IntScratch[searchFields.length];
        for (int recipeId = 0; recipeId < documents.length; recipeId++) {
            RecipeDatabaseSearchDocument document = documents[recipeId];
            for (SearchField field : scanFields) {
                int ordinal = field.ordinal();
                if (document.present(field)) {
                    IntScratch records = recordsByField[ordinal];
                    if (records == null) {
                        records = new IntScratch();
                        recordsByField[ordinal] = records;
                    }
                    records.add(recipeId);
                }
            }
        }
        for (SearchField field : searchFields) {
            int ordinal = field.ordinal();
            if (built[ordinal] == null) {
                IntScratch records = recordsByField[ordinal];
                built[ordinal] = FieldIndex.scanOnly(records == null ? emptyPosting : records.toArray());
            }
        }
    }

    private static void buildScanOnlyFieldsParallel(
            RecipeDatabaseSearchDocument[] documents,
            SearchField[] searchFields,
            SearchField[] scanFields,
            FieldIndex[] built,
            ExecutorService executor,
            int workers
    ) {
        int total = documents.length;
        int chunkSize = Math.max(1024, Math.min(8192, (total + workers * 8 - 1) / (workers * 8)));
        int chunkCount = (total + chunkSize - 1) / chunkSize;
        IntScratch[][] locals = new IntScratch[chunkCount][];
        AtomicInteger nextChunk = new AtomicInteger();
        try {
            ArrayList<Future<?>> futures = new ArrayList<>(workers);
            for (int worker = 0; worker < workers; worker++) {
                futures.add(executor.submit(() -> {
                    while (true) {
                        int chunk = nextChunk.getAndIncrement();
                        if (chunk >= chunkCount) {
                            return;
                        }
                        int from = chunk * chunkSize;
                        int to = Math.min(total, from + chunkSize);
                        IntScratch[] recordsByField = new IntScratch[searchFields.length];
                        for (int recipeId = from; recipeId < to; recipeId++) {
                            RecipeDatabaseSearchDocument document = documents[recipeId];
                            for (SearchField field : scanFields) {
                                int ordinal = field.ordinal();
                                if (document.present(field)) {
                                    IntScratch records = recordsByField[ordinal];
                                    if (records == null) {
                                        records = new IntScratch();
                                        recordsByField[ordinal] = records;
                                    }
                                    records.add(recipeId);
                                }
                            }
                        }
                        locals[chunk] = recordsByField;
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while building recipe database scan fields", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Failed to build recipe database scan fields", exception.getCause());
        }

        IntScratch[] mergedByField = new IntScratch[searchFields.length];
        for (int chunk = 0; chunk < locals.length; chunk++) {
            IntScratch[] local = locals[chunk];
            if (local == null) {
                continue;
            }
            for (SearchField field : scanFields) {
                int ordinal = field.ordinal();
                IntScratch records = local[ordinal];
                if (records == null) {
                    continue;
                }
                IntScratch merged = mergedByField[ordinal];
                if (merged == null) {
                    merged = new IntScratch();
                    mergedByField[ordinal] = merged;
                }
                merged.addAll(records);
            }
        }
        for (SearchField field : searchFields) {
            int ordinal = field.ordinal();
            if (built[ordinal] == null) {
                IntScratch records = mergedByField[ordinal];
                built[ordinal] = FieldIndex.scanOnly(records == null ? emptyPosting : records.toArray());
            }
        }
    }

    public List<RecipeDatabaseRecord> filter(SearchExpression expression) {
        if (expression == null || expression instanceof SearchExpression.All) {
            return records;
        }
        int[] candidates = candidates(expression);
        ArrayList<RecipeDatabaseRecord> filtered = new ArrayList<>(candidateSize(candidates));
        if (candidates == null) {
            for (int recipeId = 0; recipeId < records.size(); recipeId++) {
                if (matches(expression, recipeId).matched()) {
                    filtered.add(records.get(recipeId));
                }
            }
        } else {
            for (int recipeId : candidates) {
                if (matches(expression, recipeId).matched()) {
                    filtered.add(records.get(recipeId));
                }
            }
        }
        return List.copyOf(filtered);
    }

    public SearchMatch match(SearchExpression expression, int recipeId) {
        if (recipeId < 0 || recipeId >= records.size()) {
            return SearchMatch.NO;
        }
        return expression == null ? SearchMatch.YES : matches(expression, recipeId);
    }

    public List<String> textCandidates(SearchField field, int limit) {
        if (field == SearchField.contains) {
            LinkedHashSet<String> merged = new LinkedHashSet<>();
            for (SearchField sourceField : containsSourceFields()) {
                for (String candidate : field(sourceField).textCandidates()) {
                    merged.add(candidate);
                    if (merged.size() >= limit) {
                        return List.copyOf(merged);
                    }
                }
            }
            return List.copyOf(merged);
        }
        List<String> candidates = field(field).textCandidates();
        return candidates.size() <= limit ? candidates : List.copyOf(candidates.subList(0, limit));
    }

    public List<RecipeDatabaseResult> search(SearchExpression expression, @Nullable Namespace system) {
        if (expression == null || expression instanceof SearchExpression.All) {
            return system == null ? allResults() : searchAll(system);
        }
        int[] candidates = candidates(expression);
        if (system != null) {
            candidates = candidates == null ? systemCandidates(system) : intersect(candidates, systemCandidates(system));
        }
        if (candidates != null && candidates.length == 0) {
            return List.of();
        }
        ArrayList<RecipeDatabaseResult> results = matchedResults(expression, system, candidates);
        sortResults(results);
        return List.copyOf(results);
    }

    private ArrayList<RecipeDatabaseResult> matchedResults(
            SearchExpression expression,
            @Nullable Namespace system,
            int @Nullable [] candidates
    ) {
        int size = candidateSize(candidates);
        if (size >= parallelThreshold) {
            RecipeDatabaseResult[] matches = new RecipeDatabaseResult[candidates == null ? records.size() : candidates.length];
            parallelRanges(matches.length, "cirrus-db-search-", (from, to) -> {
                for (int cursor = from; cursor < to; cursor++) {
                    int recipeId = candidates == null ? cursor : candidates[cursor];
                    matches[cursor] = matchedResult(expression, system, recipeId);
                }
            });
            ArrayList<RecipeDatabaseResult> results = new ArrayList<>(Math.min(size, 256));
            for (int cursor = 0; cursor < matches.length; cursor++) {
                RecipeDatabaseResult result = matches[cursor];
                if (result != null) {
                    results.add(result);
                }
            }
            return results;
        }

        ArrayList<RecipeDatabaseResult> results = new ArrayList<>(Math.min(size, 256));
        if (candidates == null) {
            for (int recipeId = 0; recipeId < records.size(); recipeId++) {
                RecipeDatabaseResult result = matchedResult(expression, system, recipeId);
                if (result != null) {
                    results.add(result);
                }
            }
        } else {
            for (int recipeId : candidates) {
                RecipeDatabaseResult result = matchedResult(expression, system, recipeId);
                if (result != null) {
                    results.add(result);
                }
            }
        }
        return results;
    }

    private @Nullable RecipeDatabaseResult matchedResult(SearchExpression expression, @Nullable Namespace system, int recipeId) {
        RecipeDatabaseRecord record = records.get(recipeId);
        if (system != null && !system.equals(record.recipe().systemId())) {
            return null;
        }
        SearchMatch match = matches(expression, recipeId);
        return match.matched() ? new RecipeDatabaseResult(record, match.score(), record.database().priority(), recipeId) : null;
    }

    private List<RecipeDatabaseResult> allResults() {
        List<RecipeDatabaseResult> cached = allResults;
        if (cached != null) {
            return cached;
        }
        ArrayList<RecipeDatabaseResult> results = new ArrayList<>(records.size());
        for (int recipeId = 0; recipeId < records.size(); recipeId++) {
            RecipeDatabaseRecord record = records.get(recipeId);
            results.add(new RecipeDatabaseResult(record, 0, record.database().priority(), recipeId));
        }
        sortResults(results);
        cached = List.copyOf(results);
        allResults = cached;
        return cached;
    }

    private List<RecipeDatabaseResult> searchAll(Namespace system) {
        int[] candidates = systemCandidates(system);
        if (candidates.length == 0) {
            return List.of();
        }
        ArrayList<RecipeDatabaseResult> results = new ArrayList<>(candidates.length);
        for (int recipeId : candidates) {
            RecipeDatabaseRecord record = records.get(recipeId);
            results.add(new RecipeDatabaseResult(record, 0, record.database().priority(), recipeId));
        }
        sortResults(results);
        return List.copyOf(results);
    }

    private int @Nullable [] candidates(SearchExpression expression) {
        return switch (expression) {
            case null -> allRecipeIds;
            case SearchExpression.All ignored -> allRecipeIds;
            case SearchExpression.And and -> andCandidates(and);
            case SearchExpression.Or or -> orCandidates(or);
            case SearchExpression.Not ignored -> null;
            case SearchExpression.Term term -> termCandidates(term.term());
        };
    }

    private int @Nullable [] andCandidates(SearchExpression.And and) {
        ArrayList<int[]> positive = new ArrayList<>();
        ArrayList<int[]> negative = new ArrayList<>();
        for (SearchExpression child : and.terms()) {
            if (child instanceof SearchExpression.Not not) {
                int[] posting = candidates(not.term());
                if (posting != null && posting.length > 0) {
                    negative.add(posting);
                }
            } else {
                int[] posting = candidates(child);
                if (posting != null) {
                    positive.add(posting);
                }
            }
        }
        if (positive.isEmpty()) {
            return null;
        }
        positive.sort(Comparator.comparingInt(posting -> posting.length));
        int[] current = positive.getFirst();
        for (int index = 1; index < positive.size() && current.length > 0; index++) {
            current = intersect(current, positive.get(index));
        }
        negative.sort(Comparator.comparingInt(posting -> posting.length));
        for (int[] excluded : negative) {
            current = andNot(current, excluded);
            if (current.length == 0) {
                break;
            }
        }
        return current;
    }

    private int @Nullable [] orCandidates(SearchExpression.Or or) {
        int[] current = emptyPosting;
        for (SearchExpression child : or.terms()) {
            int[] posting = candidates(child);
            if (posting == null) {
                return null;
            }
            current = union(current, posting);
        }
        return current;
    }

    private int @Nullable [] termCandidates(SearchTerm term) {
        if (term.field() == SearchField.contains) {
            return containsTermCandidates(term);
        }
        FieldIndex field = field(term.field());
        if (term.field().numeric()) {
            return term.operator() == SearchOperator.exists ? field.fieldRecords() : null;
        }
        if (term.hasAmount()) {
            return contentTermCandidates(field, term);
        }
        if (isContentField(term.field())) {
            return contentTextCandidates(field, term);
        }
        if (!field.indexed()) {
            if (term.operator() == SearchOperator.exists || "*".equals(term.value())) {
                return field.fieldRecords();
            }
            return null;
        }
        return switch (term.operator()) {
            case exists -> field.fieldRecords();
            case contains -> withScanFallback(field, field.contains(normalize(term.value())));
            case exact -> withScanFallback(field, field.exact(normalize(term.value())));
            case wildcard -> {
                String literal = longestWildcardLiteral(term.value());
                yield literal.isBlank() ? field.fieldRecords() : withScanFallback(field, field.contains(normalize(literal)));
            }
            case range, greaterThan, greaterThanOrEqual, lessThan, lessThanOrEqual -> null;
        };
    }

    private int @Nullable [] containsTermCandidates(SearchTerm term) {
        SearchOperator operator = term.operator();
        if (operator == SearchOperator.exists || "*".equals(term.value())) {
            PostingUnion postings = new PostingUnion();
            for (SearchField field : containsSourceFields()) {
                postings.add(field(field).fieldRecords());
            }
            return postings.toArray();
        }
        String needle = normalize(term.value());
        PostingUnion postings = new PostingUnion();
        for (SearchField sourceField : containsSourceFields()) {
            FieldIndex field = field(sourceField);
            int @Nullable [] posting = switch (operator) {
                case contains -> withScanFallback(field, field.contains(needle));
                case exact -> withScanFallback(field, field.exact(needle));
                case wildcard -> {
                    String literal = longestWildcardLiteral(term.value());
                    yield literal.isBlank()
                            ? field.fieldRecords()
                            : field.indexed() ? withScanFallback(field, field.contains(normalize(literal))) : null;
                }
                default -> emptyPosting;
            };
            if (posting == null) {
                return null;
            }
            postings.add(posting);
        }
        return postings.toArray();
    }

    private int @Nullable [] contentTextCandidates(FieldIndex field, SearchTerm term) {
        if (term.operator() == SearchOperator.exists || "*".equals(term.value())) {
            return field.fieldRecords();
        }
        if (!field.indexed()) {
            return null;
        }
        return switch (term.operator()) {
            case contains -> withScanFallback(field, field.contains(normalize(term.value())));
            case exact -> withScanFallback(field, field.exact(normalize(term.value())));
            case wildcard -> {
                String literal = longestWildcardLiteral(term.value());
                yield literal.isBlank()
                        ? field.fieldRecords()
                        : field.indexed() ? withScanFallback(field, field.contains(normalize(literal))) : null;
            }
            default -> emptyPosting;
        };
    }

    private int @Nullable [] contentTermCandidates(FieldIndex field, SearchTerm term) {
        if (term.operator() == SearchOperator.exists || "*".equals(term.value())) {
            return field.fieldRecords();
        }
        if (!field.indexed()) {
            return null;
        }
        return switch (term.operator()) {
            case contains -> withScanFallback(field, field.contains(normalize(term.value())));
            case exact -> withScanFallback(field, field.exact(normalize(term.value())));
            case wildcard -> {
                String literal = longestWildcardLiteral(term.value());
                yield literal.isBlank()
                        ? field.fieldRecords()
                        : field.indexed() ? withScanFallback(field, field.contains(normalize(literal))) : null;
            }
            default -> emptyPosting;
        };
    }

    private int[] withScanFallback(FieldIndex field, @Nullable int[] indexedPosting) {
        if (indexedPosting == null) {
            return null;
        }
        return field.scanFallbackRecords().length == 0
                ? indexedPosting
                : union(indexedPosting, field.scanFallbackRecords());
    }

    private SearchMatch matches(SearchExpression expression, int recipeId) {
        return switch (expression) {
            case SearchExpression.All ignored -> SearchMatch.YES;
            case SearchExpression.And and -> matchAnd(and, recipeId);
            case SearchExpression.Or or -> matchOr(or, recipeId);
            case SearchExpression.Not not -> matches(not.term(), recipeId).matched() ? SearchMatch.NO : SearchMatch.YES;
            case SearchExpression.Term term -> matchTerm(term.term(), recipeId);
        };
    }

    private SearchMatch matchAnd(SearchExpression.And and, int recipeId) {
        int score = 0;
        for (SearchExpression child : and.terms()) {
            SearchMatch match = matches(child, recipeId);
            if (!match.matched()) {
                return SearchMatch.NO;
            }
            score += match.score();
        }
        return new SearchMatch(true, score);
    }

    private SearchMatch matchOr(SearchExpression.Or or, int recipeId) {
        int best = 0;
        boolean matched = false;
        for (SearchExpression child : or.terms()) {
            SearchMatch match = matches(child, recipeId);
            if (match.matched()) {
                matched = true;
                best = Math.max(best, match.score());
            }
        }
        return matched ? new SearchMatch(true, best) : SearchMatch.NO;
    }

    private SearchMatch matchTerm(SearchTerm term, int recipeId) {
        RecipeDatabaseSearchDocument document = documents[recipeId];
        if (term.field() == SearchField.contains) {
            return matchContainsVirtual(document, term);
        }
        if (term.hasAmount()) {
            return matchContentAmount(document, term);
        }
        if (isContentField(term.field())) {
            SearchMatch content = matchContentText(document, term);
            if (content.matched()) {
                return content;
            }
            return switch (term.operator()) {
                case exists -> document.present(term.field()) ? SearchMatch.YES : SearchMatch.NO;
                case contains -> matchText(document, term.field(), term.value(), term.strict(), TextMode.contains);
                case exact -> matchExact(document, term);
                case wildcard -> matchWildcard(document, term.field(), term.value(), term.strict());
                default -> SearchMatch.NO;
            };
        }
        return switch (term.operator()) {
            case exists -> document.present(term.field()) ? SearchMatch.YES : SearchMatch.NO;
            case contains -> matchText(document, term.field(), term.value(), term.strict(), TextMode.contains);
            case exact -> matchExact(document, term);
            case wildcard -> matchWildcard(document, term.field(), term.value(), term.strict());
            case range -> matchRange(document, term.field(), term.value(), term.upperBound());
            case greaterThan -> matchCompare(document, term.field(), term.value(), value -> value > 0);
            case greaterThanOrEqual -> matchCompare(document, term.field(), term.value(), value -> value >= 0);
            case lessThan -> matchCompare(document, term.field(), term.value(), value -> value < 0);
            case lessThanOrEqual -> matchCompare(document, term.field(), term.value(), value -> value <= 0);
        };
    }

    private SearchMatch matchContainsVirtual(RecipeDatabaseSearchDocument document, SearchTerm term) {
        int best = 0;
        for (SearchField field : containsSourceFields()) {
            SearchTerm sourceTerm = new SearchTerm(
                    field,
                    term.operator(),
                    term.value(),
                    term.upperBound(),
                    term.amountOperator(),
                    term.amount(),
                    term.amountUpperBound(),
                    term.strict()
            );
            SearchMatch match = sourceTerm.hasAmount()
                    ? matchContentAmount(document, sourceTerm)
                    : isContentField(field)
                      ? matchContentText(document, sourceTerm)
                      : matchText(document, field, term.value(), term.strict(), TextMode.contains);
            if (match.matched()) {
                best = Math.max(best, match.score());
            }
        }
        return best == 0 ? SearchMatch.NO : new SearchMatch(true, best);
    }

    private SearchMatch matchContentText(RecipeDatabaseSearchDocument document, SearchTerm term) {
        if (term.operator() == SearchOperator.exists) {
            return document.present(term.field()) ? SearchMatch.YES : SearchMatch.NO;
        }
        String needle = term.strict() ? term.value() : normalize(term.value());
        if (needle.isBlank()) {
            return SearchMatch.YES;
        }
        int best = 0;
        for (RecipeDatabaseSearchDocument.ContentTerm entry : document.contentTerms(term.field())) {
            String value = term.strict() ? entry.value() : entry.normalizedValue();
            if (!matchesText(term.operator(), term.value(), value, needle, term.strict())) {
                continue;
            }
            best = Math.max(best, score(term.field(), value, needle));
        }
        return best == 0 ? SearchMatch.NO : new SearchMatch(true, best);
    }

    private SearchMatch matchContentAmount(RecipeDatabaseSearchDocument document, SearchTerm term) {
        String needle = term.strict() ? term.value() : normalize(term.value());
        int best = 0;
        for (RecipeDatabaseSearchDocument.ContentTerm entry : document.contentTerms(term.field())) {
            String value = term.strict() ? entry.value() : entry.normalizedValue();
            if (!matchesText(term.operator(), term.value(), value, needle, term.strict())) {
                continue;
            }
            if (!matchesNumber(term.field(), entry.amount(), term.amountOperator(), term.amount(), term.amountUpperBound())) {
                continue;
            }
            best = Math.max(best, score(term.field(), value, needle) + 40);
        }
        return best == 0 ? SearchMatch.NO : new SearchMatch(true, best);
    }

    private SearchMatch matchText(RecipeDatabaseSearchDocument document, SearchField field, String rawNeedle, boolean strict, TextMode mode) {
        String needle = strict ? rawNeedle : normalize(rawNeedle);
        if (needle.isBlank()) {
            return SearchMatch.YES;
        }
        String[] values = strict ? document.rawText(field) : document.normalizedText(field);
        SearchField[] sourceFields = document.textSourceFields(field);
        int best = 0;
        for (int index = 0; index < values.length; index++) {
            String value = values[index];
            boolean matched = mode == TextMode.exact ? value.equals(needle) : value.contains(needle);
            if (matched) {
                SearchField scoreField = index < sourceFields.length ? sourceFields[index] : field;
                best = Math.max(best, score(scoreField, value, needle));
            }
        }
        return best == 0 ? SearchMatch.NO : new SearchMatch(true, best);
    }

    private SearchMatch matchExact(RecipeDatabaseSearchDocument document, SearchTerm term) {
        Double numeric = tryParseNumber(term.field(), term.value());
        if (numeric != null) {
            for (double value : document.numbers(term.field())) {
                if (Double.compare(value, numeric) == 0) {
                    return new SearchMatch(true, term.field().weight() + 80);
                }
            }
        }
        return matchText(document, term.field(), term.value(), term.strict(), TextMode.exact);
    }

    private SearchMatch matchWildcard(RecipeDatabaseSearchDocument document, SearchField field, String pattern, boolean strict) {
        Pattern compiled = wildcardPattern(pattern, strict);
        String[] values = document.rawText(field);
        int best = 0;
        for (String value : values) {
            if (compiled.matcher(value).find()) {
                best = Math.max(best, field.weight() + 40);
            }
        }
        return best == 0 ? SearchMatch.NO : new SearchMatch(true, best);
    }

    private SearchMatch matchRange(RecipeDatabaseSearchDocument document, SearchField field, String left, String right) {
        for (double value : document.numbers(field)) {
            if (matchesRange(field, value, left, right)) {
                return new SearchMatch(true, field.weight() + 40);
            }
        }
        return SearchMatch.NO;
    }

    private SearchMatch matchCompare(RecipeDatabaseSearchDocument document, SearchField field, String raw, Comparison comparison) {
        double expected = parseNumber(field, raw);
        for (double value : document.numbers(field)) {
            if (comparison.matches(Double.compare(value, expected))) {
                return new SearchMatch(true, field.weight() + 40);
            }
        }
        return SearchMatch.NO;
    }

    private static boolean matchesText(SearchOperator operator, String rawPattern, String value, String needle, boolean strict) {
        return switch (operator) {
            case exists -> true;
            case contains -> value.contains(needle);
            case exact -> value.equals(needle);
            case wildcard -> wildcardPattern(rawPattern, strict).matcher(value).find();
            default -> false;
        };
    }

    private static boolean matchesNumber(SearchField field, double actual, SearchOperator operator, String value, String upperBound) {
        return switch (operator) {
            case exact -> Double.compare(actual, parseNumber(field, value)) == 0;
            case range -> matchesRange(field, actual, value, upperBound);
            case greaterThan -> Double.compare(actual, parseNumber(field, value)) > 0;
            case greaterThanOrEqual -> Double.compare(actual, parseNumber(field, value)) >= 0;
            case lessThan -> Double.compare(actual, parseNumber(field, value)) < 0;
            case lessThanOrEqual -> Double.compare(actual, parseNumber(field, value)) <= 0;
            default -> false;
        };
    }

    private static boolean matchesRange(SearchField field, double value, String left, String right) {
        Double min = left.isBlank() ? null : parseNumber(field, left);
        Double max = right.isBlank() ? null : parseNumber(field, right);
        return (min == null || value >= min) && (max == null || value <= max);
    }

    private static double parseNumber(SearchField field, String raw) {
        Double value = tryParseNumber(field, raw);
        if (value == null) {
            throw new SearchParseException("Invalid numeric value " + raw, 0);
        }
        return value;
    }

    private static @Nullable Double tryParseNumber(SearchField field, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return switch (field) {
                case time -> (double) UnitParser.parseTime(raw);
                case energy -> (double) UnitParser.parseEnergy(raw);
                case chance -> UnitParser.parseChance(raw);
                default -> Double.parseDouble(raw.trim());
            };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int score(SearchField field, String value, String needle) {
        int score = field.weight();
        if (value.equals(needle)) {
            return score + 80;
        }
        if (value.startsWith(needle)) {
            return score + 50;
        }
        if (value.contains(":" + needle) || value.contains("_" + needle) || value.contains(" " + needle)) {
            return score + 30;
        }
        return score + 10;
    }

    private FieldIndex field(SearchField field) {
        return fields.get(field);
    }

    private static boolean isContentField(SearchField field) {
        return switch (field) {
            case contains, in, in_id, out, out_id, cat, cat_id, byproduct, byproduct_id -> true;
            default -> false;
        };
    }

    private static SearchField[] containsSourceFields() {
        return new SearchField[]{
                SearchField.in,
                SearchField.in_id,
                SearchField.out,
                SearchField.out_id,
                SearchField.cat,
                SearchField.cat_id,
                SearchField.byproduct,
                SearchField.byproduct_id
        };
    }

    private int[] systemCandidates(Namespace system) {
        return systems.getOrDefault(system, emptyPosting);
    }

    private int candidateSize(@Nullable int[] candidates) {
        return candidates == null ? records.size() : candidates.length;
    }

    private static void logBuildStats(
            int records,
            FieldIndex[] fields,
            long documentsNanos,
            long fieldsNanos,
            long totalNanos
    ) {
        int indexedFields = 0;
        int scanOnlyFields = 0;
        int termCount = 0;
        int postingCount = 0;
        int fieldRecordCount = 0;
        int scanFallbackRecordCount = 0;
        for (FieldIndex field : fields) {
            if (field == null) {
                continue;
            }
            fieldRecordCount += field.fieldRecords().length;
            scanFallbackRecordCount += field.scanFallbackRecords().length;
            if (field.indexed()) {
                indexedFields++;
                termCount += field.termCount();
                postingCount += field.postingCount();
            } else {
                scanOnlyFields++;
            }
        }
        logger.info(
                "Built recipe database search index: records={}, indexedFields={}, scanOnlyFields={}, terms={}, postings={}, fieldRecords={}, scanFallbackRecords={}, postingExpansion={}, termExpansion={}, fieldRecordExpansion={}, documentsMs={}, fieldsMs={}, totalMs={}",
                records,
                indexedFields,
                scanOnlyFields,
                termCount,
                postingCount,
                fieldRecordCount,
                scanFallbackRecordCount,
                ratio(postingCount, records),
                ratio(termCount, records),
                ratio(fieldRecordCount, records),
                nanosToMillis(documentsNanos),
                nanosToMillis(fieldsNanos),
                nanosToMillis(totalNanos)
        );
        if (logger.isInfoEnabled()) {
            SearchField[] searchFields = SearchField.values();
            StringBuilder detail = new StringBuilder("Recipe database search field stats:");
            for (SearchField searchField : searchFields) {
                FieldIndex field = fields[searchField.ordinal()];
                if (field == null || field.fieldRecords().length == 0 && field.termCount() == 0 && field.postingCount() == 0) {
                    continue;
                }
                detail.append(' ')
                      .append(searchField)
                      .append('[')
                      .append(field.indexed() ? "idx" : "scan")
                      .append(" records=")
                      .append(field.fieldRecords().length)
                      .append(" terms=")
                      .append(field.termCount())
                      .append(" postings=")
                      .append(field.postingCount())
                      .append(" fallback=")
                      .append(field.scanFallbackRecords().length)
                      .append(" exp=")
                      .append(ratio(field.postingCount(), Math.max(1, field.fieldRecords().length)))
                      .append(']');
            }
            logger.info("{}", detail);
        }
    }

    private static void logFieldBuild(SearchField searchField, @Nullable FieldIndex field, int records, long startNanos) {
        if (!logger.isDebugEnabled() || field == null) {
            return;
        }
        Runtime runtime = Runtime.getRuntime();
        logger.debug(
                "Recipe database search field built: field={}, records={}, indexed={}, fieldRecords={}, terms={}, postings={}, scanFallbackRecords={}, elapsedMs={}, heapUsedMiB={}, heapCommittedMiB={}",
                searchField,
                records,
                field.indexed(),
                field.fieldRecords().length,
                field.termCount(),
                field.postingCount(),
                field.scanFallbackRecords().length,
                nanosToMillis(System.nanoTime() - startNanos),
                (runtime.totalMemory() - runtime.freeMemory()) >> 20,
                runtime.totalMemory() >> 20
        );
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    private static String ratio(int numerator, int denominator) {
        if (denominator <= 0) {
            return "0.00x";
        }
        double value = (double) numerator / (double) denominator;
        return String.format(Locale.ROOT, "%.2fx", value);
    }

    private static Map<Namespace, int[]> buildSystemPostings(List<RecipeDatabaseRecord> records) {
        if (records.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<Namespace, Integer> ids = new LinkedHashMap<>();
        for (RecipeDatabaseRecord record : records) {
            Namespace systemId = record.recipe().systemId();
            if (!ids.containsKey(systemId)) {
                ids.put(systemId, ids.size());
            }
        }
        DensePostingBuilder builder = new DensePostingBuilder(ids.size());
        for (RecipeDatabaseRecord record : records) {
            builder.increment(ids.get(record.recipe().systemId()));
        }
        builder.allocate();
        for (int recipeId = 0; recipeId < records.size(); recipeId++) {
            builder.add(ids.get(records.get(recipeId).recipe().systemId()), recipeId);
        }
        LinkedHashMap<Namespace, int[]> out = new LinkedHashMap<>();
        for (Map.Entry<Namespace, Integer> entry : ids.entrySet()) {
            out.put(entry.getKey(), builder.slice(entry.getValue()));
        }
        return out;
    }

    private static void sortResults(ArrayList<RecipeDatabaseResult> results) {
        results.sort(Comparator
                .comparingInt(RecipeDatabaseResult::matchScore).reversed()
                .thenComparingInt(RecipeDatabaseResult::databasePriority)
                .thenComparingInt(RecipeDatabaseResult::matchOrder));
    }

    private static int[] intersect(int[] left, int[] right) {
        if (left.length == 0 || right.length == 0) {
            return emptyPosting;
        }
        int[] out = new int[Math.min(left.length, right.length)];
        int i = 0;
        int j = 0;
        int count = 0;
        while (i < left.length && j < right.length) {
            int a = left[i];
            int b = right[j];
            if (a == b) {
                out[count++] = a;
                i++;
                j++;
            } else if (a < b) {
                i++;
            } else {
                j++;
            }
        }
        return count == 0 ? emptyPosting : Arrays.copyOf(out, count);
    }

    private static int[] union(int[] left, int[] right) {
        if (left.length == 0) {
            return right;
        }
        if (right.length == 0) {
            return left;
        }
        int[] out = new int[left.length + right.length];
        int i = 0;
        int j = 0;
        int count = 0;
        while (i < left.length || j < right.length) {
            int next;
            if (j >= right.length || i < left.length && left[i] < right[j]) {
                next = left[i++];
            } else if (i >= left.length || right[j] < left[i]) {
                next = right[j++];
            } else {
                next = left[i];
                i++;
                j++;
            }
            if (count == 0 || out[count - 1] != next) {
                out[count++] = next;
            }
        }
        return count == 0 ? emptyPosting : Arrays.copyOf(out, count);
    }

    private static int[] andNot(int[] left, int[] right) {
        if (left.length == 0 || right.length == 0) {
            return left;
        }
        int[] out = new int[left.length];
        int i = 0;
        int j = 0;
        int count = 0;
        while (i < left.length) {
            int a = left[i++];
            while (j < right.length && right[j] < a) {
                j++;
            }
            if (j >= right.length || right[j] != a) {
                out[count++] = a;
            }
        }
        return count == left.length ? left : count == 0 ? emptyPosting : Arrays.copyOf(out, count);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String longestWildcardLiteral(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return "";
        }
        String best = "";
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < pattern.length(); index++) {
            char c = pattern.charAt(index);
            if (c == '*' || c == '?') {
                if (current.length() > best.length()) {
                    best = current.toString();
                }
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        return current.length() > best.length() ? current.toString() : best;
    }

    private static Pattern wildcardPattern(String pattern, boolean strict) {
        int flags = strict ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        StringBuilder regex = new StringBuilder();
        for (int index = 0; index < pattern.length(); index++) {
            char c = pattern.charAt(index);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> regex.append(Pattern.quote(Character.toString(c)));
            }
        }
        return Pattern.compile(regex.toString(), flags);
    }

    private enum TextMode {
        contains,
        exact
    }

    private interface Comparison {
        boolean matches(int comparison);
    }

    private static final class FieldIndex {
        private final int[] fieldRecords;
        private final int[] scanFallbackRecords;
        private final String[] terms;
        private final Map<String, Integer> termIds;
        private final EncodedPostings postings;
        private final List<String> textCandidates;
        private final boolean indexed;
        private final int postingCount;

        private FieldIndex(
                int[] fieldRecords,
                int[] scanFallbackRecords,
                String[] terms,
                Map<String, Integer> termIds,
                EncodedPostings postings,
                List<String> textCandidates,
                boolean indexed,
                int postingCount
        ) {
            this.fieldRecords = fieldRecords;
            this.scanFallbackRecords = scanFallbackRecords;
            this.terms = terms;
            this.termIds = termIds;
            this.postings = postings;
            this.textCandidates = textCandidates;
            this.indexed = indexed;
            this.postingCount = postingCount;
        }

        private static FieldIndex empty() {
            return new FieldIndex(emptyPosting, emptyPosting, new String[0], Map.of(), EncodedPostings.empty, List.of(), false, 0);
        }

        private static FieldIndex build(RecipeDatabaseSearchDocument[] documents, SearchField field) {
            return build(documents, field, null, 0);
        }

        private static FieldIndex build(
                RecipeDatabaseSearchDocument[] documents,
                SearchField field,
                @Nullable ExecutorService sharedExecutor,
                int sharedWorkers
        ) {
            if (field == SearchField.contains) {
                return empty();
            }
            if (!shouldBuildPostingIndex(field)) {
                return buildScanOnly(documents, field);
            }
            int documentPostingBudget = documentPostingBudget(documents.length);
            if (documents.length >= parallelThreshold) {
                return buildParallel(documents, field, documentPostingBudget, sharedExecutor, sharedWorkers);
            }
            FieldAccumulator accumulator = new FieldAccumulator(documentPostingBudget);
            for (int recipeId = 0; recipeId < documents.length; recipeId++) {
                RecipeDatabaseSearchDocument document = documents[recipeId];
                accumulator.addDocument(recipeId, document, field);
            }
            return accumulator.freeze(documents, field);
        }

        private static FieldIndex buildParallel(
                RecipeDatabaseSearchDocument[] documents,
                SearchField field,
                int documentPostingBudget,
                @Nullable ExecutorService sharedExecutor,
                int sharedWorkers
        ) {
            int total = documents.length;
            int workers = sharedExecutor == null
                    ? Math.min(total, Math.max(1, Runtime.getRuntime().availableProcessors() - 1))
                    : Math.max(1, Math.min(total, sharedWorkers));
            if (workers <= 1 || total < workers * 2) {
                FieldAccumulator accumulator = new FieldAccumulator(documentPostingBudget);
                for (int recipeId = 0; recipeId < documents.length; recipeId++) {
                    accumulator.addDocument(recipeId, documents[recipeId], field);
                }
                return accumulator.freeze(documents, field);
            }

            int chunkSize = Math.max(512, Math.min(4096, (total + workers * 8 - 1) / (workers * 8)));
            int chunkCount = (total + chunkSize - 1) / chunkSize;
            LocalFieldAccumulator[] locals = new LocalFieldAccumulator[chunkCount];
            AtomicInteger nextChunk = new AtomicInteger();
            ExecutorService executor = sharedExecutor == null
                    ? Executors.newFixedThreadPool(workers, daemonThreadFactory("cirrus-db-search-" + field + "-"))
                    : sharedExecutor;
            try {
                ArrayList<Future<?>> futures = new ArrayList<>(workers);
                for (int worker = 0; worker < workers; worker++) {
                    futures.add(executor.submit(() -> {
                        while (true) {
                            int chunk = nextChunk.getAndIncrement();
                            if (chunk >= chunkCount) {
                                return;
                            }
                            int from = chunk * chunkSize;
                            int to = Math.min(total, from + chunkSize);
                            LocalFieldAccumulator local = new LocalFieldAccumulator(documentPostingBudget);
                            for (int recipeId = from; recipeId < to; recipeId++) {
                                local.addDocument(recipeId, documents[recipeId], field);
                            }
                            locals[chunk] = local;
                        }
                    }));
                }
                for (Future<?> future : futures) {
                    future.get();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while building recipe database search field: " + field, exception);
            } catch (ExecutionException exception) {
                throw new IllegalStateException("Failed to build recipe database search field: " + field, exception.getCause());
            } finally {
                if (sharedExecutor == null) {
                    executor.shutdown();
                }
            }
            return mergeLocals(locals, field);
        }

        private static FieldIndex mergeLocals(LocalFieldAccumulator[] locals, SearchField field) {
            FieldDictionary dictionary = new FieldDictionary();
            LinkedHashSet<String> textCandidates = new LinkedHashSet<>();
            IntScratch fieldRecords = new IntScratch();
            IntScratch scanFallbackRecords = new IntScratch();
            DensePostingBuilder postings = new DensePostingBuilder(0);

            for (int chunk = 0; chunk < locals.length; chunk++) {
                LocalFieldAccumulator local = locals[chunk];
                if (local == null) {
                    continue;
                }
                fieldRecords.addAll(local.fieldRecords);
                scanFallbackRecords.addAll(local.scanFallbackRecords);
                for (String candidate : local.textCandidates) {
                    if (textCandidates.size() >= textCandidateLimit) {
                        break;
                    }
                    textCandidates.add(candidate);
                }
                for (Map.Entry<String, LocalPostingList> entry : local.postingsByTerm.entrySet()) {
                    int termId = dictionary.idFor(entry.getKey());
                    postings.incrementBy(termId, entry.getValue().size());
                }
            }

            postings.resize(dictionary.size());
            postings.allocate();
            for (int chunk = 0; chunk < locals.length; chunk++) {
                LocalFieldAccumulator local = locals[chunk];
                if (local == null) {
                    continue;
                }
                for (Map.Entry<String, LocalPostingList> entry : local.postingsByTerm.entrySet()) {
                    int termId = dictionary.existingId(entry.getKey());
                    LocalPostingList recipeIds = entry.getValue();
                    for (int index = 0; index < recipeIds.size(); index++) {
                        postings.add(termId, recipeIds.get(index));
                    }
                }
            }

            return new FieldIndex(
                    fieldRecords.toArray(),
                    scanFallbackRecords.toArray(),
                    dictionary.terms(),
                    dictionary.idMap(),
                    postings.freeze(),
                    List.copyOf(textCandidates),
                    true,
                    postings.postingCount()
            );
        }

        private static FieldIndex buildScanOnly(RecipeDatabaseSearchDocument[] documents, SearchField field) {
            IntScratch fieldRecords = new IntScratch();
            for (int recipeId = 0; recipeId < documents.length; recipeId++) {
                if (documents[recipeId].present(field)) {
                    fieldRecords.add(recipeId);
                }
            }
            return new FieldIndex(fieldRecords.toArray(), emptyPosting, new String[0], Map.of(), EncodedPostings.empty, List.of(), false, 0);
        }

        private static FieldIndex scanOnly(int[] fieldRecords) {
            return new FieldIndex(fieldRecords, emptyPosting, new String[0], Map.of(), EncodedPostings.empty, List.of(), false, 0);
        }

        private static boolean shouldBuildPostingIndex(SearchField field) {
            if (field.numeric()) {
                return false;
            }
            return switch (field) {
                case mod,
                     system,
                     db,
                     machine,
                     in,
                     in_id,
                     out,
                     out_id,
                     cat,
                     cat_id,
                     byproduct,
                     byproduct_id -> true;
                default -> false;
            };
        }

        private int[] fieldRecords() {
            return fieldRecords;
        }

        private int[] scanFallbackRecords() {
            return scanFallbackRecords;
        }

        private boolean indexed() {
            return indexed;
        }

        private int termCount() {
            return terms.length;
        }

        private int postingCount() {
            return postingCount;
        }

        private List<String> textCandidates() {
            return textCandidates;
        }

        private int @Nullable [] exact(String value) {
            if (!indexed) {
                return null;
            }
            Integer termId = termIds.get(value);
            return termId == null ? emptyPosting : postings.slice(termId);
        }

        private int @Nullable [] contains(String needle) {
            if (!indexed) {
                return null;
            }
            PostingUnion matches = new PostingUnion();
            for (int termId = 0; termId < terms.length; termId++) {
                if (terms[termId].contains(needle)) {
                    postings.addToUnion(termId, matches);
                }
            }
            return matches.toArray();
        }

        private static int documentPostingBudget(int documentCount) {
            return Math.max(2048, Math.max(1, documentCount / 16));
        }

    }

    private static final class FieldAccumulator {
        private final FieldDictionary dictionary = new FieldDictionary();
        private final LinkedHashSet<String> textCandidates = new LinkedHashSet<>();
        private final IntScratch fieldRecords = new IntScratch();
        private final IntScratch scanFallbackRecords = new IntScratch();
        private final BitSet scanFallbackDocuments = new BitSet();
        private final DensePostingBuilder postings = new DensePostingBuilder(0);
        private final int documentPostingBudget;

        private FieldAccumulator(int documentPostingBudget) {
            this.documentPostingBudget = Math.max(1, documentPostingBudget);
        }

        private void addDocument(int recipeId, RecipeDatabaseSearchDocument document, SearchField field) {
            String[] normalized = document.normalizedText(field);
            if (normalized.length > 0) {
                String[] raw = document.rawText(field);
                if (usesScanFallback(recipeId, raw, normalized)) {
                    if (document.present(field)) {
                        fieldRecords.add(recipeId);
                    }
                    return;
                }
                IntScratch unique = new IntScratch();
                for (int index = 0; index < normalized.length; index++) {
                    if (textCandidates.size() < textCandidateLimit) {
                        textCandidates.add(raw[index]);
                    }
                    unique.addIfAbsent(dictionary.idFor(normalized[index]));
                }
                for (int index = 0; index < unique.size(); index++) {
                    postings.increment(unique.get(index));
                }
            }
            if (document.present(field)) {
                fieldRecords.add(recipeId);
            }
        }

        private boolean usesScanFallback(int recipeId, String[] raw, String[] normalized) {
            if (normalized.length <= documentPostingBudget) {
                return false;
            }
            for (int index = 0; index < raw.length && textCandidates.size() < textCandidateLimit; index++) {
                textCandidates.add(raw[index]);
            }
            scanFallbackRecords.add(recipeId);
            scanFallbackDocuments.set(recipeId);
            return true;
        }

        private FieldIndex freeze(RecipeDatabaseSearchDocument[] documents, SearchField field) {
            postings.resize(dictionary.size());
            postings.allocate();
            for (int recipeId = 0; recipeId < documents.length; recipeId++) {
                addPostingData(recipeId, documents[recipeId], field);
            }
            return new FieldIndex(
                    fieldRecords.toArray(),
                    scanFallbackRecords.toArray(),
                    dictionary.terms(),
                    dictionary.idMap(),
                    postings.freeze(),
                    List.copyOf(textCandidates),
                    true,
                    postings.postingCount()
            );
        }

        private void addPostingData(int recipeId, RecipeDatabaseSearchDocument document, SearchField field) {
            if (scanFallbackDocuments.get(recipeId)) {
                return;
            }
            String[] normalized = document.normalizedText(field);
            if (normalized.length == 0) {
                return;
            }
            IntScratch unique = new IntScratch();
            for (String term : normalized) {
                unique.addIfAbsent(dictionary.existingId(term));
            }
            for (int index = 0; index < unique.size(); index++) {
                postings.add(unique.get(index), recipeId);
            }
        }
    }

    private static final class LocalFieldAccumulator {
        private final LinkedHashMap<String, LocalPostingList> postingsByTerm = new LinkedHashMap<>();
        private final LinkedHashSet<String> textCandidates = new LinkedHashSet<>();
        private final IntScratch fieldRecords = new IntScratch();
        private final IntScratch scanFallbackRecords = new IntScratch();
        private final int documentPostingBudget;

        private LocalFieldAccumulator(int documentPostingBudget) {
            this.documentPostingBudget = Math.max(1, documentPostingBudget);
        }

        private void addDocument(int recipeId, RecipeDatabaseSearchDocument document, SearchField field) {
            String[] normalized = document.normalizedText(field);
            if (normalized.length > 0) {
                String[] raw = document.rawText(field);
                if (usesScanFallback(recipeId, raw, normalized)) {
                    if (document.present(field)) {
                        fieldRecords.add(recipeId);
                    }
                    return;
                }
                addTerms(recipeId, raw, normalized);
            }
            if (document.present(field)) {
                fieldRecords.add(recipeId);
            }
        }

        private boolean usesScanFallback(int recipeId, String[] raw, String[] normalized) {
            if (normalized.length <= documentPostingBudget) {
                return false;
            }
            for (int index = 0; index < raw.length && textCandidates.size() < textCandidateLimit; index++) {
                textCandidates.add(raw[index]);
            }
            scanFallbackRecords.add(recipeId);
            return true;
        }

        private void addTerms(int recipeId, String[] raw, String[] normalized) {
            if (normalized.length <= linearUniqueThreshold) {
                ArrayList<String> uniqueTerms = new ArrayList<>(Math.min(normalized.length, linearUniqueThreshold));
                for (int index = 0; index < normalized.length; index++) {
                    String term = normalized[index];
                    if (term == null || term.isBlank() || contains(uniqueTerms, term)) {
                        continue;
                    }
                    uniqueTerms.add(term);
                    addCandidate(raw[index]);
                    addPosting(term, recipeId);
                }
                return;
            }

            HashSet<String> uniqueTerms = new HashSet<>(normalized.length * 2);
            for (int index = 0; index < normalized.length; index++) {
                String term = normalized[index];
                if (term == null || term.isBlank() || !uniqueTerms.add(term)) {
                    continue;
                }
                addCandidate(raw[index]);
                addPosting(term, recipeId);
            }
        }

        private void addPosting(String term, int recipeId) {
            LocalPostingList postings = postingsByTerm.get(term);
            if (postings == null) {
                postingsByTerm.put(term, new LocalPostingList(recipeId));
            } else {
                postings.add(recipeId);
            }
        }

        private void addCandidate(String raw) {
            if (textCandidates.size() < textCandidateLimit) {
                textCandidates.add(raw);
            }
        }

        private static boolean contains(ArrayList<String> values, String value) {
            for (String candidate : values) {
                if (candidate.equals(value)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class LocalPostingList {
        private int first;
        private @Nullable IntScratch rest;

        private LocalPostingList(int first) {
            this.first = first;
        }

        private void add(int recipeId) {
            IntScratch target = rest;
            if (target == null) {
                target = new IntScratch();
                rest = target;
            }
            target.add(recipeId);
        }

        private int size() {
            return 1 + (rest == null ? 0 : rest.size());
        }

        private int get(int index) {
            if (index == 0) {
                return first;
            }
            if (rest == null) {
                throw new IndexOutOfBoundsException(index);
            }
            return rest.get(index - 1);
        }
    }

    private static final class FieldDictionary {
        private final Map<String, Integer> ids = new HashMap<>();
        private final ArrayList<String> terms = new ArrayList<>();

        private int idFor(String term) {
            if (term == null || term.isBlank()) {
                return -1;
            }
            Integer existing = ids.get(term);
            if (existing != null) {
                return existing;
            }
            int id = terms.size();
            ids.put(term, id);
            terms.add(term);
            return id;
        }

        private int existingId(String term) {
            if (term == null || term.isBlank()) {
                return -1;
            }
            Integer existing = ids.get(term);
            return existing == null ? -1 : existing;
        }

        private int size() {
            return terms.size();
        }

        private String[] terms() {
            return terms.toArray(new String[0]);
        }

        private Map<String, Integer> idMap() {
            return Map.copyOf(ids);
        }
    }

    private static final class DensePostingBuilder {
        private int[] counts;
        private int[] offsets;
        private int[] cursor;
        private int[] data;

        private DensePostingBuilder(int termCount) {
            this.counts = new int[termCount];
        }

        private void resize(int termCount) {
            if (counts.length != termCount) {
                counts = Arrays.copyOf(counts, termCount);
            }
        }

        private void increment(int termId) {
            ensureTerm(termId + 1);
            counts[termId]++;
        }

        private void incrementBy(int termId, int amount) {
            if (amount <= 0) {
                return;
            }
            ensureTerm(termId + 1);
            counts[termId] += amount;
        }

        private void allocate() {
            offsets = new int[counts.length + 1];
            for (int index = 0; index < counts.length; index++) {
                offsets[index + 1] = offsets[index] + counts[index];
            }
            cursor = Arrays.copyOf(offsets, offsets.length);
            data = new int[offsets[offsets.length - 1]];
        }

        private void add(int termId, int recipeId) {
            data[cursor[termId]++] = recipeId;
        }

        private void ensureTerm(int size) {
            if (size <= counts.length) {
                return;
            }
            int next = counts.length == 0 ? 8 : counts.length;
            while (next < size) {
                next += Math.max(8, next);
            }
            counts = Arrays.copyOf(counts, next);
        }

        private int[] slice(int termId) {
            int start = offsets[termId];
            int end = offsets[termId + 1];
            return start == end ? emptyPosting : Arrays.copyOfRange(data, start, end);
        }

        private EncodedPostings freeze() {
            return data == null
                    ? EncodedPostings.empty
                    : new EncodedPostings(offsets, Arrays.copyOf(data, data.length));
        }

        private int postingCount() {
            return data == null ? 0 : data.length;
        }
    }

    private record EncodedPostings(int[] offsets, int[] data) {
        private static final EncodedPostings empty = new EncodedPostings(emptyPosting, emptyPosting);

        private int[] slice(int termId) {
            if (termId < 0 || termId + 1 >= offsets.length) {
                return emptyPosting;
            }
            int start = offsets[termId];
            int end = offsets[termId + 1];
            return start == end ? emptyPosting : Arrays.copyOfRange(data, start, end);
        }

        private void addToUnion(int termId, PostingUnion union) {
            if (termId < 0 || termId + 1 >= offsets.length) {
                return;
            }
            union.add(data, offsets[termId], offsets[termId + 1]);
        }
    }

    private static final class PostingUnion {
        private int[] values = emptyPosting;
        private int size;

        private void add(int[] posting) {
            if (posting.length == 0) {
                return;
            }
            add(posting, 0, posting.length);
        }

        private void add(int[] posting, int fromInclusive, int toExclusive) {
            int postingLength = toExclusive - fromInclusive;
            if (postingLength <= 0) {
                return;
            }
            int[] merged = new int[size + postingLength];
            int left = 0;
            int right = fromInclusive;
            int count = 0;
            while (left < size || right < toExclusive) {
                int next;
                if (right >= toExclusive || left < size && values[left] < posting[right]) {
                    next = values[left++];
                } else if (left >= size || posting[right] < values[left]) {
                    next = posting[right++];
                } else {
                    next = values[left];
                    left++;
                    right++;
                }
                if (count == 0 || merged[count - 1] != next) {
                    merged[count++] = next;
                }
            }
            values = count == merged.length ? merged : Arrays.copyOf(merged, count);
            size = count;
        }

        private int[] toArray() {
            return size == 0 ? emptyPosting : values;
        }
    }

    private static final class IntScratch {
        private int[] values = new int[8];
        private int size;
        private @Nullable HashSet<Integer> uniqueValues;

        private void addIfAbsent(int value) {
            if (value < 0) {
                return;
            }
            if (uniqueValues != null) {
                if (uniqueValues.add(value)) {
                    add(value);
                }
                return;
            }
            for (int index = 0; index < size; index++) {
                if (values[index] == value) {
                    return;
                }
            }
            if (size >= linearUniqueThreshold) {
                uniqueValues = new HashSet<>(Math.max(128, size * 2));
                for (int index = 0; index < size; index++) {
                    uniqueValues.add(values[index]);
                }
                uniqueValues.add(value);
            }
            add(value);
        }

        private int size() {
            return size;
        }

        private int get(int index) {
            return values[index];
        }

        private void add(int value) {
            if (size >= values.length) {
                values = Arrays.copyOf(values, values.length + Math.max(8, values.length));
            }
            values[size++] = value;
        }

        private void addAll(IntScratch other) {
            if (other == null || other.size == 0) {
                return;
            }
            int required = size + other.size;
            if (required > values.length) {
                int next = values.length;
                while (next < required) {
                    next += Math.max(8, next);
                }
                values = Arrays.copyOf(values, next);
            }
            System.arraycopy(other.values, 0, values, size, other.size);
            size = required;
        }

        private int[] toArray() {
            return size == 0 ? emptyPosting : Arrays.copyOf(values, size);
        }
    }

    @FunctionalInterface
    private interface RangeTask {
        void run(int fromInclusive, int toExclusive);
    }

    private static void parallelRanges(int total, String namePrefix, RangeTask task) {
        if (total <= 0) {
            return;
        }
        int workers = Math.min(total, Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
        if (workers <= 1 || total < workers * 2) {
            task.run(0, total);
            return;
        }
        ExecutorService executor = Executors.newFixedThreadPool(workers, daemonThreadFactory(namePrefix));
        try {
            ArrayList<Future<?>> futures = new ArrayList<>(workers);
            int chunk = (total + workers - 1) / workers;
            for (int from = 0; from < total; from += chunk) {
                int start = from;
                int end = Math.min(total, from + chunk);
                futures.add(executor.submit(() -> task.run(start, end)));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while building recipe database search index", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Failed to build recipe database search index", exception.getCause());
        } finally {
            executor.shutdown();
        }
    }

    private static ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger ids = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + ids.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
