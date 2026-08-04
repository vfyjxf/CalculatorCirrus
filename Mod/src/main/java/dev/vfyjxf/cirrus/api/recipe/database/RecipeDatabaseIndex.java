package dev.vfyjxf.cirrus.api.recipe.database;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.database.search.RecipeDatabaseSearchDocument.ContentKind;
import dev.vfyjxf.cirrus.api.recipe.database.search.RecipeDatabaseSearchDocument;
import dev.vfyjxf.cirrus.api.recipe.database.search.RecipeDatabaseSearchEngine;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClausedContent;
import dev.vfyjxf.cirrus.api.recipe.search.SearchExpression;
import dev.vfyjxf.cirrus.api.recipe.search.SearchField;
import dev.vfyjxf.cirrus.api.recipe.search.SearchMatch;
import dev.vfyjxf.cirrus.api.recipe.search.SearchOperator;
import dev.vfyjxf.cirrus.api.recipe.search.SearchParser;
import dev.vfyjxf.cirrus.api.recipe.search.SearchTerm;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveError;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;

public final class RecipeDatabaseIndex {
    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Database");
    private static final int parallelFlushBatch = 256;
    private static final int recordBuildChunk = 64;
    private static final int stackSearchCacheLimit = 8192;
    private static final int statsTopLimit = 8;
    private static final RecipeDatabaseIndex empty = new RecipeDatabaseIndex(List.of(), RecipeDatabaseSearchEngine.empty(), null, new int[0], Map.of());
    private final List<RecipeDatabaseRecord> records;
    private final RecipeDatabaseSearchEngine searchEngine;
    private final @Nullable RecipeDatabaseIndex baseIndex;
    private final int[] derivedBaseRecipeIds;
    private final Map<DatabaseRecipeRef, RecipeDatabaseRecord> derivedRecordsByRef;

    private RecipeDatabaseIndex(List<RecipeDatabaseRecord> records) {
        this(records, BuildProgress.none());
    }

    private RecipeDatabaseIndex(List<RecipeDatabaseRecord> records, BuildProgress progress) {
        this.records = List.copyOf(records);
        progress.update(BuildPhase.BUILDING_SEARCH_INDEX, 0, this.records.size());
        this.searchEngine = RecipeDatabaseSearchEngine.build(this.records);
        progress.update(BuildPhase.BUILDING_SEARCH_INDEX, this.records.size(), this.records.size());
        this.baseIndex = null;
        this.derivedBaseRecipeIds = new int[0];
        this.derivedRecordsByRef = Map.of();
    }

    private RecipeDatabaseIndex(
        List<RecipeDatabaseRecord> records,
        RecipeDatabaseSearchEngine searchEngine,
        @Nullable RecipeDatabaseIndex baseIndex,
        int[] derivedBaseRecipeIds,
        Map<DatabaseRecipeRef, RecipeDatabaseRecord> derivedRecordsByRef
    ) {
        this.records = List.copyOf(records);
        this.searchEngine = searchEngine;
        this.baseIndex = baseIndex;
        this.derivedBaseRecipeIds = derivedBaseRecipeIds == null ? new int[0] : derivedBaseRecipeIds.clone();
        this.derivedRecordsByRef = Map.copyOf(derivedRecordsByRef);
    }

    public static RecipeDatabaseIndex ofRecords(List<RecipeDatabaseRecord> records) {
        return records == null || records.isEmpty() ? empty : new RecipeDatabaseIndex(records);
    }

    public static RecipeDatabaseIndex derive(
        RecipeDatabaseIndex catalog,
        DatabaseDefinition database,
        List<Namespace> sources,
        List<DatabaseRecipeRef> recipeRefs
    ) {
        if (catalog == null || database == null) {
            return ofRecords(List.of());
        }
        long startNanos = System.nanoTime();
        Set<Namespace> enabledSources = new LinkedHashSet<>(sources == null ? List.of() : sources);
        Set<DatabaseRecipeRef> explicitRefs = new LinkedHashSet<>(recipeRefs == null ? List.of() : recipeRefs);
        LinkedHashMap<DatabaseRecipeRef, RecipeDatabaseRecord> recordsByRef = new LinkedHashMap<>();
        IntScratch baseRecipeIds = new IntScratch();
        List<RecipeDatabaseRecord> catalogRecords = catalog.records();
        for (int recipeId = 0; recipeId < catalogRecords.size(); recipeId++) {
            RecipeDatabaseRecord record = catalogRecords.get(recipeId);
            DatabaseRecipeRef ref = DatabaseRecipeRef.fromEntry(record.recipe());
            if (ref == null) {
                continue;
            }
            if (!enabledSources.contains(ref.system()) && !explicitRefs.contains(ref)) {
                continue;
            }
            if (!recordsByRef.containsKey(ref)) {
                recordsByRef.put(ref, withDatabase(record, database));
                baseRecipeIds.add(recipeId);
            }
        }
        RecipeDatabaseIndex derived = new RecipeDatabaseIndex(
            List.copyOf(recordsByRef.values()),
            catalog.searchEngine,
            catalog,
            baseRecipeIds.toArray(),
            recordsByRef
        );
        logger.info(
            "Derived recipe database index: database={} catalogRecords={} records={} liveSet={} sources={} recipes={} reusedSearchIndex=true totalMs={}",
            database.id(),
            catalog.records().size(),
            derived.records().size(),
            derived.derivedBaseRecipeIds.length,
            enabledSources.size(),
            explicitRefs.size(),
            nanosToMillis(System.nanoTime() - startNanos)
        );
        return derived;
    }

    public static RecipeDatabaseIndex build(
        List<DatabaseDefinition> databases,
        RichIterable<? extends RecipeSystem<?>> systems,
        Map<Namespace, List<Namespace>> systemTags
    ) {
        return build(databases, systems, systemTags, Map.of());
    }

    public static RecipeDatabaseIndex build(
        List<DatabaseDefinition> databases,
        RichIterable<? extends RecipeSystem<?>> systems,
        Map<Namespace, List<Namespace>> systemTags,
        Map<String, List<RecipeEntry<?>>> explicitEntries
    ) {
        return build(databases, systems, systemTags, explicitEntries, BuildProgress.none());
    }

    public static RecipeDatabaseIndex build(
        List<DatabaseDefinition> databases,
        RichIterable<? extends RecipeSystem<?>> systems,
        Map<Namespace, List<Namespace>> systemTags,
        Map<String, List<RecipeEntry<?>>> explicitEntries,
        BuildProgress progress
    ) {
        return build(databases, systems, systemTags, explicitEntries, progress, null);
    }

    public static RecipeDatabaseIndex build(
        List<DatabaseDefinition> databases,
        RichIterable<? extends RecipeSystem<?>> systems,
        Map<Namespace, List<Namespace>> systemTags,
        Map<String, List<RecipeEntry<?>>> explicitEntries,
        BuildProgress progress,
        @Nullable RecipeDatabaseFailureRegistry failureRegistry
    ) {
        if (databases == null || databases.isEmpty() || systems == null) {
            return ofRecords(List.of());
        }

        BuildProgress reporter = progress == null ? BuildProgress.none() : progress;
        reporter.update(BuildPhase.COLLECTING, 0, 0);
        Map<Namespace, List<Namespace>> normalizedTags = normalizeSystemTags(systemTags);
        Map<String, List<RecipeEntry<?>>> normalizedExplicitEntries = explicitEntries == null ? Map.of() : explicitEntries;
        ArrayList<DatabaseDefinition> enabled = new ArrayList<>(databases.size());
        for (DatabaseDefinition database : databases) {
            if (database != null && database.enabled()) {
                enabled.add(database);
            }
        }
        enabled.sort(databaseOrder());

        ArrayList<WorkItem> items = new ArrayList<>();
        List<RecipeSystem<?>> systemList = new ArrayList<>();
        for (RecipeSystem<?> system : systems) {
            if (system != null) {
                systemList.add(system);
            }
        }

        int totalSources = enabled.size() * Math.max(1, systemList.size());
        int collectedSources = 0;
        for (DatabaseDefinition database : enabled) {
            reporter.update(BuildPhase.COLLECTING, collectedSources, totalSources);
            for (RecipeEntry<?> entry : normalizedExplicitEntries.getOrDefault(database.id(), List.of())) {
                if (entry != null) {
                    items.add(new WorkItem(database, entry, normalizedTags.getOrDefault(entry.systemId(), List.of())));
                }
            }
            for (RecipeSystem<?> system : systemList) {
                if (!database.sources().contains(system.systemId())) {
                    collectedSources++;
                    continue;
                }
                List<Namespace> tags = normalizedTags.getOrDefault(system.systemId(), List.of());
                @SuppressWarnings("rawtypes")
                RecipeSystem raw = system;
                for (Object recipe : raw.allRecipes()) {
                    items.add(new WorkItem(database, RecipeEntry.of(raw, recipe), tags));
                }
                collectedSources++;
                if (shouldReportProgress(collectedSources, totalSources)) {
                    reporter.update(BuildPhase.COLLECTING, collectedSources, totalSources);
                }
            }
            if (systemList.isEmpty()) {
                collectedSources++;
            }
        }
        reporter.update(BuildPhase.COLLECTING, totalSources, totalSources);
        Set<DatabaseRecipeRef> knownFailedRecipes = failureRegistry == null ? Set.of() : failureRegistry.failedRecipeRefs();
        Set<DatabaseRecipeRef> knownUnsupportedRecipes = failureRegistry == null ? Set.of() : failureRegistry.unsupportedRecipeRefs();
        int skippedKnownFailures = 0;
        int skippedKnownUnsupported = 0;
        if ((!knownFailedRecipes.isEmpty() || !knownUnsupportedRecipes.isEmpty()) && !items.isEmpty()) {
            ArrayList<WorkItem> filteredItems = new ArrayList<>(items.size());
            for (WorkItem item : items) {
                if (item.ref() != null && knownFailedRecipes.contains(item.ref())) {
                    skippedKnownFailures++;
                    continue;
                }
                if (item.ref() != null && knownUnsupportedRecipes.contains(item.ref())) {
                    skippedKnownUnsupported++;
                    continue;
                }
                filteredItems.add(item);
            }
            items = filteredItems;
            if (skippedKnownFailures > 0) {
                logger.info(
                    "Skipped known failed recipe database records: workItems={}, knownFailedRecipes={}",
                    skippedKnownFailures,
                    knownFailedRecipes.size()
                );
            }
            if (skippedKnownUnsupported > 0) {
                logger.info(
                    "Skipped known unsupported recipe database records: workItems={}, knownUnsupportedRecipes={}",
                    skippedKnownUnsupported,
                    knownUnsupportedRecipes.size()
                );
            }
        }
        int duplicateWorkItems = 0;
        if (!items.isEmpty()) {
            WorkItemDedupResult deduped = dedupeWorkItems(items);
            items = deduped.items();
            duplicateWorkItems = deduped.duplicates();
            if (duplicateWorkItems > 0) {
                logger.info(
                    "Skipped duplicate recipe database work items before resolve: duplicates={}, remaining={}",
                    duplicateWorkItems,
                    items.size()
                );
            }
        }
        long recordsStartNanos = System.nanoTime();
        ConcurrentLinkedQueue<RecipeDatabaseFailureRegistry.Failure> failures = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<RecipeDatabaseFailureRegistry.Unsupported> unsupported = new ConcurrentLinkedQueue<>();
        List<RecipeDatabaseRecord> records = buildRecords(
            items,
            reporter,
            knownFailedRecipes,
            knownUnsupportedRecipes,
            failures,
            unsupported
        );
        if (failureRegistry != null && !failures.isEmpty()) {
            failureRegistry.recordFailures(failures);
        }
        if (failureRegistry != null && !unsupported.isEmpty()) {
            failureRegistry.recordUnsupported(unsupported);
        }
        long searchStartNanos = System.nanoTime();
        RecipeDatabaseIndex index = new RecipeDatabaseIndex(records, reporter);
        long endNanos = System.nanoTime();
        logger.info(
            "Built recipe database index: databases={}, systems={}, workItems={}, records={}, skippedKnownFailures={}, skippedKnownUnsupported={}, skippedDuplicates={}, newFailures={}, newUnsupported={}, recordMs={}, searchMs={}, totalMs={}",
            enabled.size(),
            systemList.size(),
            items.size() + skippedKnownFailures + skippedKnownUnsupported + duplicateWorkItems,
            records.size(),
            skippedKnownFailures,
            skippedKnownUnsupported,
            duplicateWorkItems,
            failures.size(),
            unsupported.size(),
            nanosToMillis(searchStartNanos - recordsStartNanos),
            nanosToMillis(endNanos - searchStartNanos),
            nanosToMillis(endNanos - recordsStartNanos)
        );
        return index;
    }

    public enum BuildPhase {
        QUEUED,
        RESOLVING_EXPLICIT,
        BUILDING_CATALOG,
        BUILDING_ACTIVE,
        COLLECTING,
        RESOLVING_RECORDS,
        DEDUPING_RECORDS,
        BUILDING_SEARCH_INDEX,
        DONE
    }

    @FunctionalInterface
    public interface BuildProgress {
        void update(BuildPhase phase, int done, int total);

        static BuildProgress none() {
            return (phase, done, total) -> {
            };
        }
    }

    public List<RecipeDatabaseRecord> records() {
        return records;
    }

    public RecipeDatabaseIndex filtered(SearchExpression expression) {
        if (expression == null || expression instanceof SearchExpression.All) {
            return this;
        }
        if (baseIndex != null) {
            return filteredDerived(expression);
        }
        return ofRecords(searchEngine.filter(expression));
    }

    private RecipeDatabaseIndex filteredDerived(SearchExpression expression) {
        HashMap<DatabaseRecipeRef, Integer> baseRecipeIdsByRef = new HashMap<>(derivedBaseRecipeIds.length * 2 + 1);
        for (int recipeId : derivedBaseRecipeIds) {
            RecipeDatabaseRecord baseRecord = baseIndex.records().get(recipeId);
            DatabaseRecipeRef ref = DatabaseRecipeRef.fromEntry(baseRecord.recipe());
            if (ref != null) {
                baseRecipeIdsByRef.putIfAbsent(ref, recipeId);
            }
        }

        List<RecipeDatabaseResult> results = search(expression);
        IntScratch nextRecipeIds = new IntScratch();
        LinkedHashMap<DatabaseRecipeRef, RecipeDatabaseRecord> nextRecordsByRef = new LinkedHashMap<>();
        for (RecipeDatabaseResult result : results) {
            DatabaseRecipeRef ref = DatabaseRecipeRef.fromEntry(result.record().recipe());
            Integer baseRecipeId = ref == null ? null : baseRecipeIdsByRef.get(ref);
            if (ref == null || baseRecipeId == null || nextRecordsByRef.containsKey(ref)) {
                continue;
            }
            nextRecordsByRef.put(ref, result.record());
            nextRecipeIds.add(baseRecipeId);
        }
        return new RecipeDatabaseIndex(
            List.copyOf(nextRecordsByRef.values()),
            searchEngine,
            baseIndex,
            nextRecipeIds.toArray(),
            nextRecordsByRef
        );
    }

    public List<String> textCandidates(SearchField field, int limit) {
        int max = Math.max(0, limit);
        return max == 0 ? List.of() : searchEngine.textCandidates(field, max);
    }

    public List<String> numberCandidates(SearchField field, int limit) {
        int max = Math.max(0, limit);
        if (max == 0) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        if (field == SearchField.contains) {
            for (SearchField sourceField : containsSourceFields()) {
                collectNumberCandidates(values, sourceField, max);
                if (values.size() >= max) {
                    return List.copyOf(values);
                }
            }
            return List.copyOf(values);
        }
        collectNumberCandidates(values, field, max);
        return List.copyOf(values);
    }

    private void collectNumberCandidates(Set<String> values, SearchField field, int limit) {
        for (RecipeDatabaseRecord record : records) {
            for (double value : record.searchDocument().numbers(field)) {
                values.add(formatNumberCandidate(value));
                if (values.size() >= limit) {
                    return;
                }
            }
        }
    }

    public Map<SearchField, List<String>> textCandidateMap(int perFieldLimit) {
        EnumMap<SearchField, List<String>> out = new EnumMap<>(SearchField.class);
        for (SearchField field : SearchField.values()) {
            if (!hasTextCandidates(field)) {
                continue;
            }
            List<String> values = textCandidates(field, perFieldLimit);
            if (!values.isEmpty()) {
                out.put(field, values);
            }
        }
        return Map.copyOf(out);
    }

    private static boolean hasTextCandidates(SearchField field) {
        return switch (field) {
            case text, time, energy, chance, tier, level -> false;
            default -> true;
        };
    }

    private static SearchField[] containsSourceFields() {
        return new SearchField[] {
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

    public List<RecipeDatabaseResult> search(String query) {
        return search(new SearchParser().parse(query));
    }

    public List<RecipeDatabaseResult> search(String query, Namespace system) {
        return search(new SearchParser().parse(query), system);
    }

    public List<RecipeDatabaseResult> search(SearchExpression expression) {
        return search(expression, null);
    }

    public List<RecipeDatabaseResult> search(SearchExpression expression, @Nullable Namespace system) {
        if (baseIndex == null) {
            return searchEngine.search(expression, system);
        }
        SearchExpression baseExpression = withoutDerivedOnlyFields(expression);
        if (baseExpression == null) {
            return searchDerivedLiveSet(expression, system);
        }
        List<RecipeDatabaseResult> baseResults = searchEngine.search(baseExpression, system);
        if (baseResults.isEmpty()) {
            return List.of();
        }
        ArrayList<RecipeDatabaseResult> out = new ArrayList<>(Math.min(baseResults.size(), records.size()));
        int order = 0;
        for (RecipeDatabaseResult result : baseResults) {
            DatabaseRecipeRef ref = DatabaseRecipeRef.fromEntry(result.record().recipe());
            RecipeDatabaseRecord activeRecord = ref == null ? null : derivedRecordsByRef.get(ref);
            if (activeRecord != null) {
                SearchMatch match = matchDerivedExpression(expression, result.matchOrder(), activeRecord);
                if (match.matched()) {
                    out.add(new RecipeDatabaseResult(activeRecord, match.score(), activeRecord.database().priority(), order++));
                }
            }
        }
        return List.copyOf(out);
    }

    private List<RecipeDatabaseResult> searchDerivedLiveSet(SearchExpression expression, @Nullable Namespace system) {
        if (derivedBaseRecipeIds.length == 0) {
            return List.of();
        }
        ArrayList<RecipeDatabaseResult> out = new ArrayList<>(records.size());
        int order = 0;
        for (int recipeId : derivedBaseRecipeIds) {
            RecipeDatabaseRecord baseRecord = baseIndex.records().get(recipeId);
            if (system != null && !system.equals(baseRecord.recipe().systemId())) {
                continue;
            }
            DatabaseRecipeRef ref = DatabaseRecipeRef.fromEntry(baseRecord.recipe());
            RecipeDatabaseRecord activeRecord = ref == null ? null : derivedRecordsByRef.get(ref);
            if (activeRecord != null) {
                SearchMatch match = matchDerivedExpression(expression, recipeId, activeRecord);
                if (match.matched()) {
                    out.add(new RecipeDatabaseResult(activeRecord, match.score(), activeRecord.database().priority(), order++));
                }
            }
        }
        return List.copyOf(out);
    }

    private static @Nullable SearchExpression withoutDerivedOnlyFields(SearchExpression expression) {
        return switch (expression) {
            case null -> new SearchExpression.All();
            case SearchExpression.All all -> all;
            case SearchExpression.Term term -> isDerivedOnlyField(term.term().field()) ? null : term;
            case SearchExpression.Not not -> {
                SearchExpression child = withoutDerivedOnlyFields(not.term());
                yield child == null ? new SearchExpression.All() : new SearchExpression.Not(child);
            }
            case SearchExpression.And and -> {
                ArrayList<SearchExpression> children = new ArrayList<>(and.terms().size());
                for (SearchExpression child : and.terms()) {
                    SearchExpression baseChild = withoutDerivedOnlyFields(child);
                    if (baseChild != null && !(baseChild instanceof SearchExpression.All)) {
                        children.add(baseChild);
                    }
                }
                yield children.isEmpty()
                    ? null
                    : children.size() == 1 ? children.getFirst() : new SearchExpression.And(List.copyOf(children));
            }
            case SearchExpression.Or or -> {
                ArrayList<SearchExpression> children = new ArrayList<>(or.terms().size());
                boolean droppedDerivedOnlyChild = false;
                for (SearchExpression child : or.terms()) {
                    SearchExpression baseChild = withoutDerivedOnlyFields(child);
                    if (baseChild == null) {
                        droppedDerivedOnlyChild = true;
                    } else {
                        children.add(baseChild);
                    }
                }
                yield droppedDerivedOnlyChild
                    ? null
                    : children.isEmpty()
                    ? null
                    : children.size() == 1 ? children.getFirst() : new SearchExpression.Or(List.copyOf(children));
            }
        };
    }

    private SearchMatch matchDerivedExpression(SearchExpression expression, int baseRecipeId, RecipeDatabaseRecord record) {
        return switch (expression) {
            case null -> SearchMatch.YES;
            case SearchExpression.All ignored -> SearchMatch.YES;
            case SearchExpression.And and -> {
                int score = 0;
                for (SearchExpression child : and.terms()) {
                    SearchMatch match = matchDerivedExpression(child, baseRecipeId, record);
                    if (!match.matched()) {
                        yield SearchMatch.NO;
                    }
                    score += match.score();
                }
                yield new SearchMatch(true, score);
            }
            case SearchExpression.Or or -> {
                SearchMatch best = SearchMatch.NO;
                for (SearchExpression child : or.terms()) {
                    SearchMatch match = matchDerivedExpression(child, baseRecipeId, record);
                    if (match.matched() && (!best.matched() || match.score() > best.score())) {
                        best = match;
                    }
                }
                yield best;
            }
            case SearchExpression.Not not -> matchDerivedExpression(not.term(), baseRecipeId, record).matched()
                ? SearchMatch.NO
                : SearchMatch.YES;
            case SearchExpression.Term term -> isDerivedOnlyField(term.term().field())
                ? matchDatabaseTerm(term.term(), record.database())
                : searchEngine.match(expression, baseRecipeId);
        };
    }

    private static boolean isDerivedOnlyField(SearchField field) {
        return field == SearchField.db || field == SearchField.profile;
    }

    private static SearchMatch matchDatabaseTerm(SearchTerm term, DatabaseDefinition database) {
        ArrayList<String> values = new ArrayList<>(3);
        if (term.field() == SearchField.profile) {
            values.add(database.kind().name());
        } else {
            values.add(database.id());
            values.add(database.name());
        }
        String needle = term.strict() ? term.value() : normalize(term.value());
        int best = 0;
        for (String rawValue : values) {
            String value = term.strict() ? rawValue : normalize(rawValue);
            boolean matched = switch (term.operator()) {
                case exists -> true;
                case contains -> value.contains(needle);
                case exact -> value.equals(needle);
                case wildcard -> wildcardMatches(term.value(), rawValue, term.strict());
                default -> false;
            };
            if (matched) {
                if (term.operator() == SearchOperator.exists) {
                    best = Math.max(best, term.field().weight());
                } else if (value.equals(needle)) {
                    best = Math.max(best, term.field().weight() + 80);
                } else if (value.startsWith(needle)) {
                    best = Math.max(best, term.field().weight() + 50);
                } else {
                    best = Math.max(best, term.field().weight() + 10);
                }
            }
        }
        return best == 0 ? SearchMatch.NO : new SearchMatch(true, best);
    }

    private static boolean wildcardMatches(String pattern, String value, boolean strict) {
        if (pattern == null || value == null) {
            return false;
        }
        String needle = strict ? pattern : normalize(pattern);
        String haystack = strict ? value : normalize(value);
        return wildcardMatchesNormalized(needle, haystack);
    }

    private static boolean wildcardMatchesNormalized(String pattern, String value) {
        int patternIndex = 0;
        int valueIndex = 0;
        int starIndex = -1;
        int starValueIndex = 0;
        while (valueIndex < value.length()) {
            if (patternIndex < pattern.length()
                && (pattern.charAt(patternIndex) == '?' || pattern.charAt(patternIndex) == value.charAt(valueIndex))) {
                patternIndex++;
                valueIndex++;
            } else if (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
                starIndex = patternIndex++;
                starValueIndex = valueIndex;
            } else if (starIndex >= 0) {
                patternIndex = starIndex + 1;
                valueIndex = ++starValueIndex;
            } else {
                return false;
            }
        }
        while (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
            patternIndex++;
        }
        return patternIndex == pattern.length();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }

    private static RecipeDatabaseRecord withDatabase(RecipeDatabaseRecord record, DatabaseDefinition database) {
        if (record.database().equals(database)) {
            return record;
        }
        return new RecipeDatabaseRecord(
            record.recipe(),
            database,
            record.searchDocument(),
            record.projection(),
            record.systemTags()
        );
    }

    private static List<RecipeDatabaseRecord> buildRecords(
        List<WorkItem> items,
        BuildProgress progress,
        Set<DatabaseRecipeRef> knownFailedRecipes,
        Set<DatabaseRecipeRef> knownUnsupportedRecipes,
        Queue<RecipeDatabaseFailureRegistry.Failure> failures,
        Queue<RecipeDatabaseFailureRegistry.Unsupported> unsupported
    ) {
        if (items == null || items.isEmpty()) {
            progress.update(BuildPhase.RESOLVING_RECORDS, 0, 0);
            progress.update(BuildPhase.DEDUPING_RECORDS, 0, 0);
            return List.of();
        }

        Set<DatabaseRecipeRef> failedRecipes = ConcurrentHashMap.newKeySet();
        if (knownFailedRecipes != null && !knownFailedRecipes.isEmpty()) {
            failedRecipes.addAll(knownFailedRecipes);
        }
        Set<DatabaseRecipeRef> unsupportedRecipes = ConcurrentHashMap.newKeySet();
        if (knownUnsupportedRecipes != null && !knownUnsupportedRecipes.isEmpty()) {
            unsupportedRecipes.addAll(knownUnsupportedRecipes);
        }
        RecipeDatabaseRecord[] resolved = new RecipeDatabaseRecord[items.size()];
        AtomicInteger resolvedCount = new AtomicInteger();
        AtomicInteger failedCount = new AtomicInteger();
        AtomicInteger unsupportedCount = new AtomicInteger();
        AtomicInteger resolveDebugBucket = logger.isDebugEnabled() ? new AtomicInteger(-1) : null;
        ResolveStats resolveStats = logger.isDebugEnabled() ? new ResolveStats() : null;
        int[] resolveOrder = interleavedResolveOrder(items);
        long resolveStartNanos = System.nanoTime();
        progress.update(BuildPhase.RESOLVING_RECORDS, 0, items.size());
        parallelChunks(items.size(), recordBuildChunk, "cirrus-database-record-", (from, to) -> {
            ArrayList<RecipeDatabaseFailureRegistry.Failure> capturedFailures = new ArrayList<>(1);
            StackSearchCache stackSearchCache = new StackSearchCache(stackSearchCacheLimit);
            int pendingProgress = 0;
            for (int cursor = from; cursor < to; cursor++) {
                int index = resolveOrder == null ? cursor : resolveOrder[cursor];
                WorkItem item = items.get(index);
                DatabaseRecipeRef ref = item.ref();
                if (ref != null && (failedRecipes.contains(ref) || unsupportedRecipes.contains(ref))) {
                    pendingProgress++;
                    if (pendingProgress >= parallelFlushBatch) {
                        int done = resolvedCount.addAndGet(pendingProgress);
                        pendingProgress = 0;
                        if (shouldReportProgress(done, items.size())) {
                            progress.update(BuildPhase.RESOLVING_RECORDS, done, items.size());
                        }
                        logBuildProgress("resolve", done, items.size(), resolveStartNanos, resolveDebugBucket);
                    }
                    continue;
                }
                try {
                    capturedFailures.clear();
                    RecordBuildResult result = RecipeDatabaseFailureCapture.capture(
                        item.database().id(),
                        ref,
                        item.recipeClass(),
                        capturedFailures::add,
                        () -> recordForEntry(item.database(), item.entry(), item.tags(), stackSearchCache)
                    );
                    if (!capturedFailures.isEmpty()) {
                        failedCount.incrementAndGet();
                        if (resolveStats != null) {
                            resolveStats.failed.increment();
                        }
                        addCapturedFailures(capturedFailures, failedRecipes, failures);
                        logger.warn(
                            "Skipped recipe database record after resolver failure: database={}, system={}, recipe={}",
                            item.database().id(),
                            item.entry() == null ? "<null>" : item.entry().systemId(),
                            ref == null ? "<unknown>" : ref.recipe()
                        );
                    } else if (result.record() != null) {
                        resolved[index] = result.record();
                        if (resolveStats != null) {
                            resolveStats.resolved.increment();
                        }
                    } else {
                        ResolveError error = result.error();
                        recordEmpty(resolveStats, item, error);
                        if (cacheableUnsupported(error)) {
                            unsupportedCount.incrementAndGet();
                        }
                        if (cacheableUnsupported(error) && ref != null && unsupportedRecipes.add(ref)) {
                            if (unsupported != null) {
                                unsupported.add(new RecipeDatabaseFailureRegistry.Unsupported(
                                    ref,
                                    item.database().id(),
                                    item.recipeClass(),
                                    error == null ? "" : error.name()
                                ));
                            }
                        }
                    }
                } catch (RuntimeException | LinkageError exception) {
                    failedCount.incrementAndGet();
                    if (resolveStats != null) {
                        resolveStats.failed.increment();
                    }
                    if (ref != null && failedRecipes.add(ref) && failures != null) {
                        failures.add(RecipeDatabaseFailureRegistry.Failure.from(
                            ref,
                            item.database().id(),
                            item.recipeClass(),
                            exception
                        ));
                    }
                    logger.warn(
                        "Failed to resolve recipe database record: database={}, system={}, recipe={}, recipeClass={}, exception={}, message={}",
                        item.database().id(),
                        item.entry() == null ? "<null>" : item.entry().systemId(),
                        ref == null ? "<unknown>" : ref.recipe(),
                        item.recipeClass(),
                        exception.getClass().getName(),
                        exception.getMessage(),
                        exception
                    );
                }
                pendingProgress++;
                if (pendingProgress >= parallelFlushBatch) {
                    int done = resolvedCount.addAndGet(pendingProgress);
                    pendingProgress = 0;
                    if (shouldReportProgress(done, items.size())) {
                        progress.update(BuildPhase.RESOLVING_RECORDS, done, items.size());
                    }
                    logBuildProgress("resolve", done, items.size(), resolveStartNanos, resolveDebugBucket);
                }
            }
            if (pendingProgress > 0) {
                int done = resolvedCount.addAndGet(pendingProgress);
                if (shouldReportProgress(done, items.size())) {
                    progress.update(BuildPhase.RESOLVING_RECORDS, done, items.size());
                }
                logBuildProgress("resolve", done, items.size(), resolveStartNanos, resolveDebugBucket);
            }
        });
        progress.update(BuildPhase.RESOLVING_RECORDS, items.size(), items.size());
        logBuildProgress("resolve", items.size(), items.size(), resolveStartNanos, resolveDebugBucket);

        ArrayList<RecipeDatabaseRecord> records = new ArrayList<>();
        Set<DatabaseRecipeRef> seenRefs = new HashSet<>();
        Set<RecipeEntry<?>> seenEntriesWithoutRef = new HashSet<>();
        int duplicateCount = 0;
        progress.update(BuildPhase.DEDUPING_RECORDS, 0, items.size());
        for (int index = 0; index < items.size(); index++) {
            RecipeDatabaseRecord record = resolved[index];
            if (record != null) {
                DatabaseRecipeRef ref = recipeRef(record.recipe());
                boolean first = ref == null
                    ? seenEntriesWithoutRef.add(record.recipe())
                    : seenRefs.add(ref);
                if (first) {
                    records.add(record);
                } else {
                    duplicateCount++;
                }
            }
            int done = index + 1;
            if (shouldReportProgress(done, items.size())) {
                progress.update(BuildPhase.DEDUPING_RECORDS, done, items.size());
            }
        }
        progress.update(BuildPhase.DEDUPING_RECORDS, items.size(), items.size());
        if (failedCount.get() > 0 || unsupportedCount.get() > 0 || duplicateCount > 0 || records.size() != items.size()) {
            logger.info(
                "Resolved recipe database records: workItems={}, records={}, failed={}, unsupported={}, duplicates={}, empty={}",
                items.size(),
                records.size(),
                failedCount.get(),
                unsupportedCount.get(),
                duplicateCount,
                items.size() - records.size() - failedCount.get() - unsupportedCount.get() - duplicateCount
            );
        }
        logResolveStats(resolveStats, items.size());
        return records;
    }

    private static @Nullable int[] interleavedResolveOrder(List<WorkItem> items) {
        if (items == null || items.size() <= recordBuildChunk * 4) {
            return null;
        }
        LinkedHashMap<Namespace, IntScratch> bySystem = new LinkedHashMap<>();
        for (int index = 0; index < items.size(); index++) {
            WorkItem item = items.get(index);
            Namespace systemId = item.entry() == null ? Namespace.of("cirrus", "unknown") : item.entry().systemId();
            bySystem.computeIfAbsent(systemId, ignored -> new IntScratch()).add(index);
        }
        if (bySystem.size() <= 1) {
            return null;
        }

        IntScratch[] groups = bySystem.values().toArray(new IntScratch[0]);
        int[] order = new int[items.size()];
        int[] cursors = new int[groups.length];
        int written = 0;
        boolean advanced;
        do {
            advanced = false;
            for (int group = 0; group < groups.length; group++) {
                IntScratch indexes = groups[group];
                int cursor = cursors[group];
                if (cursor >= indexes.size()) {
                    continue;
                }
                int burst = Math.min(recordBuildChunk, indexes.size() - cursor);
                for (int i = 0; i < burst; i++) {
                    order[written++] = indexes.get(cursor + i);
                }
                cursors[group] = cursor + burst;
                advanced = true;
            }
        } while (advanced);

        return written == items.size() ? order : null;
    }

    private static void addCapturedFailures(
        Iterable<RecipeDatabaseFailureRegistry.Failure> capturedFailures,
        Set<DatabaseRecipeRef> failedRecipes,
        Queue<RecipeDatabaseFailureRegistry.Failure> failures
    ) {
        if (capturedFailures == null || failures == null) {
            return;
        }
        for (RecipeDatabaseFailureRegistry.Failure failure : capturedFailures) {
            if (failure == null) {
                continue;
            }
            logger.warn(
                "Captured recipe database resolver failure: database={}, system={}, recipe={}, recipeClass={}, exception={}, message={}",
                failure.database(),
                failure.ref().system(),
                failure.ref().recipe(),
                failure.recipeClass(),
                failure.exception(),
                failure.message(),
                failure.cause()
            );
            if (failure != null && failedRecipes.add(failure.ref())) {
                failures.add(failure);
            }
        }
    }

    private static @Nullable DatabaseRecipeRef recipeRef(@Nullable RecipeEntry<?> entry) {
        if (entry == null) {
            return null;
        }
        try {
            return DatabaseRecipeRef.fromEntry(entry);
        } catch (RuntimeException | LinkageError exception) {
            logger.warn(
                "Failed to read recipe database ref: system={}",
                entry.systemId(),
                exception
            );
            return null;
        }
    }

    private static String recipeClass(@Nullable RecipeEntry<?> entry) {
        if (entry == null || entry.recipe() == null) {
            return "";
        }
        Object recipe = entry.recipe();
        if (recipe instanceof RecipeHolder<?> holder) {
            return "RecipeHolder(" + holder.value().getClass().getName() + ")";
        }
        return recipe.getClass().getName();
    }

    private static boolean cacheableUnsupported(@Nullable ResolveError error) {
        return error == ResolveError.unsupported || error == ResolveError.invalidRecipe;
    }

    private static final class ResolveStats {
        private final LongAdder resolved = new LongAdder();
        private final LongAdder failed = new LongAdder();
        private final LongAdder unsupported = new LongAdder();
        private final LongAdder empty = new LongAdder();
        private final Map<Namespace, LongAdder> unsupportedBySystem = new ConcurrentHashMap<>();
        private final Map<String, LongAdder> unsupportedByClass = new ConcurrentHashMap<>();
        private final Map<ResolveError, LongAdder> unsupportedByError = new ConcurrentHashMap<>();
        private final Map<Namespace, LongAdder> emptyBySystem = new ConcurrentHashMap<>();
        private final Map<String, LongAdder> emptyByClass = new ConcurrentHashMap<>();
        private final Map<ResolveError, LongAdder> emptyByError = new ConcurrentHashMap<>();
    }

    private record RecordBuildResult(
        @Nullable RecipeDatabaseRecord record,
        @Nullable ResolveError error
    ) {
        private static RecordBuildResult resolved(RecipeDatabaseRecord record) {
            return new RecordBuildResult(record, null);
        }

        private static RecordBuildResult empty(@Nullable ResolveError error) {
            return new RecordBuildResult(null, error);
        }
    }

    private static final class StackSearchCache {
        private final int limit;
        private final HashMap<StackKey, String> displayNames = new HashMap<>();
        private final HashMap<StackKey, String> contentIds = new HashMap<>();

        private StackSearchCache(int limit) {
            this.limit = Math.max(1, limit);
        }

        private <T, S> String displayName(ContentStack<T, S> stack) {
            StackKey key = StackKey.of(stack);
            String cached = displayNames.get(key);
            if (cached != null) {
                return cached;
            }
            String value = RecipeDatabaseIndex.displayName(stack);
            if (displayNames.size() < limit) {
                displayNames.putIfAbsent(key, value);
            }
            return value;
        }

        private <T, S> String contentId(ContentStack<T, S> stack) {
            StackKey key = StackKey.of(stack);
            String cached = contentIds.get(key);
            if (cached != null) {
                return cached;
            }
            String value = RecipeDatabaseIndex.contentId(stack);
            if (contentIds.size() < limit) {
                contentIds.putIfAbsent(key, value);
            }
            return value;
        }
    }

    private record StackKey(
        Object type,
        Object content,
        Object components
    ) {
        private static StackKey of(ContentStack<?, ?> stack) {
            return new StackKey(stack.type(), stack.content(), stack.componentsPatch());
        }
    }

    private static RecordBuildResult recordForEntry(
        DatabaseDefinition database,
        RecipeEntry<?> entry,
        List<Namespace> systemTags,
        StackSearchCache cache
    ) {
        if (entry == null) {
            return RecordBuildResult.empty(ResolveError.unsupported);
        }
        ResolveResult result = entry.resolveResult(ResolveContext.empty());
        if (!result.resolved()) {
            return RecordBuildResult.empty(result.error());
        }
        RecipeProjection projection = snapshotProjection(result.projection());
        RecipeDatabaseSearchDocument searchDocument = document(database, entry, systemTags, projection, cache);
        return RecordBuildResult.resolved(new RecipeDatabaseRecord(
            entry,
            database,
            searchDocument,
            projection,
            systemTags
        ));
    }

    private static RecipeProjection snapshotProjection(RecipeProjection projection) {
        ArrayList<ClausedContent<InputContent>> inputs = new ArrayList<>(projection.inputs().size());
        for (ClausedContent<InputContent> input : projection.inputs()) {
            inputs.add(snapshotInput(input));
        }
        ArrayList<ClausedContent<OutputContent>> outputs = new ArrayList<>(projection.outputs().size());
        for (ClausedContent<OutputContent> output : projection.outputs()) {
            outputs.add(snapshotOutput(output));
        }
        return new RecipeProjection(
            projection.recipe(),
            Lists.immutable.ofAll(inputs),
            Lists.immutable.ofAll(outputs),
            projection.schema(),
            projection.clauses().merge()
        );
    }

    private static void recordEmpty(
        @Nullable ResolveStats stats,
        WorkItem item,
        @Nullable ResolveError error
    ) {
        if (stats == null) {
            return;
        }
        ResolveError effectiveError = error == null ? ResolveError.unsupported : error;
        Namespace systemId = item.entry() == null ? Namespace.of("cirrus", "unknown") : item.entry().systemId();
        String recipeClass = item.recipeClass();
        if (cacheableUnsupported(effectiveError)) {
            stats.unsupported.increment();
            stats.unsupportedByError.computeIfAbsent(effectiveError, ignored -> new LongAdder()).increment();
            stats.unsupportedBySystem.computeIfAbsent(systemId, ignored -> new LongAdder()).increment();
            stats.unsupportedByClass.computeIfAbsent(recipeClass, ignored -> new LongAdder()).increment();
            return;
        }
        stats.empty.increment();
        stats.emptyByError.computeIfAbsent(effectiveError, ignored -> new LongAdder()).increment();
        stats.emptyBySystem.computeIfAbsent(systemId, ignored -> new LongAdder()).increment();
        stats.emptyByClass.computeIfAbsent(recipeClass, ignored -> new LongAdder()).increment();
    }

    private static void logResolveStats(@Nullable ResolveStats stats, int total) {
        if (stats == null) {
            return;
        }
        long failed = stats.failed.sum();
        long unsupported = stats.unsupported.sum();
        long empty = stats.empty.sum();
        if (failed <= 0 && unsupported <= 0 && empty <= 0) {
            return;
        }
        logger.info(
            "Recipe database resolve summary: workItems={}, resolved={}, failed={}, unsupported={}, empty={}",
            total,
            stats.resolved.sum(),
            failed,
            unsupported,
            empty
        );
        logger.info(
            "Recipe database resolve unsupported top systems: {}",
            topCounts(stats.unsupportedBySystem, stats.unsupportedBySystem.size(), stats.unsupportedBySystem.size())
        );
        logger.info(
            "Recipe database resolve unsupported top classes: {}",
            topCounts(stats.unsupportedByClass, stats.unsupportedByClass.size(), stats.unsupportedByClass.size())
        );
        logger.info(
            "Recipe database resolve empty top systems: {}",
            topCounts(stats.emptyBySystem, stats.emptyBySystem.size(), stats.emptyBySystem.size())
        );
        logger.info(
            "Recipe database resolve empty top classes: {}",
            topCounts(stats.emptyByClass, stats.emptyByClass.size(), stats.emptyByClass.size())
        );
        if (logger.isDebugEnabled()) {
            logger.debug(
                "Recipe database resolve empty by error: {}",
                topCounts(stats.emptyByError, stats.emptyByError.size(), stats.emptyByError.size())
            );
            logger.debug(
                "Recipe database resolve unsupported by error: {}",
                topCounts(stats.unsupportedByError, stats.unsupportedByError.size(), stats.unsupportedByError.size())
            );
        }
    }

    private static <T> List<String> topCounts(Map<T, LongAdder> counts, int limit, int debugLimit) {
        if (counts == null || counts.isEmpty() || limit <= 0) {
            return List.of();
        }
        int max = logger.isDebugEnabled() ? Math.max(limit, debugLimit) : Math.min(limit, statsTopLimit);
        ArrayList<Map.Entry<T, LongAdder>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((left, right) -> {
            int cmp = Long.compare(right.getValue().sum(), left.getValue().sum());
            if (cmp != 0) {
                return cmp;
            }
            return String.valueOf(left.getKey()).compareTo(String.valueOf(right.getKey()));
        });
        ArrayList<String> out = new ArrayList<>(Math.min(max, entries.size()));
        int index = 0;
        for (Map.Entry<T, LongAdder> entry : entries) {
            if (index++ >= max) {
                break;
            }
            out.add(entry.getKey() + "=" + entry.getValue().sum());
        }
        return out;
    }

    private static ClausedContent<InputContent> snapshotInput(ClausedContent<InputContent> input) {
        return new ClausedContent<>(snapshotContent(input.content()), input.schema(), input.clauses().merge());
    }

    private static ClausedContent<OutputContent> snapshotOutput(ClausedContent<OutputContent> output) {
        return new ClausedContent<>(snapshotContent(output.content()), output.schema(), output.clauses().merge());
    }

    @SuppressWarnings("unchecked")
    private static <T extends RecipeContent> T snapshotContent(T content) {
        return (T) content.copyWithAmountAndChance(content.amount(), content.chance());
    }

    private static RecipeDatabaseSearchDocument document(
        DatabaseDefinition database,
        RecipeEntry<?> entry,
        List<Namespace> systemTags,
        @Nullable RecipeProjection projection,
        StackSearchCache cache
    ) {
        RecipeDatabaseSearchDocument.Builder builder = RecipeDatabaseSearchDocument.builder();
        ResourceLocation id = entry.id();
        builder.searchableText(SearchField.id, id == null ? entry.toString() : id.toString());
        if (id != null) {
            builder.searchableText(SearchField.name, id.getPath());
            builder.text(SearchField.mod, id.getNamespace());
        }
        builder.searchableText(SearchField.system, entry.systemId().toString());
        builder.text(SearchField.db, database.id());
        builder.searchableText(SearchField.db, database.name());
        builder.text(SearchField.profile, database.kind().name());
        builder.text(SearchField.mod, entry.systemId().root());
        builder.text(SearchField.context, entry.systemId().toString());
        builder.text(SearchField.context, database.kind().name());
        for (Namespace tag : systemTags) {
            builder.searchableText(SearchField.system, tag.toString());
            builder.text(SearchField.tag, tag.toString());
            builder.text(SearchField.profile, tag.toString());
            builder.text(SearchField.context, tag.toString());
        }
        if (projection != null) {
            addProjection(builder, projection, cache);
        }
        return builder.build();
    }

    private static void addProjection(RecipeDatabaseSearchDocument.Builder builder, RecipeProjection projection, StackSearchCache cache) {
        for (ClausedContent<InputContent> input : projection.inputs()) {
            addContent(builder, SearchField.in, input.content(), cache);
            if (input.content().notConsumed()) {
                addContent(builder, SearchField.cat, input.content(), cache);
            }
        }
        for (ClausedContent<OutputContent> output : projection.outputs()) {
            addContent(builder, SearchField.out, output.content(), cache);
            if (output.content().chance() < RecipeContent.MAX_CHANCE) {
                addContent(builder, SearchField.byproduct, output.content(), cache);
            }
        }
    }

    private static void addContent(RecipeDatabaseSearchDocument.Builder builder, SearchField field, RecipeContent content, StackSearchCache cache) {
        if (content == null || content.isEmpty()) {
            return;
        }
        builder.number(field, content.amount());
        builder.number(SearchField.chance, content.chance());
        switch (content) {
            case StackContent<?, ?> stack -> addStack(builder, field, stack.content(), content.amount(), cache);
            case StackListContent list -> {
                for (var stack : list.stacks()) {
                    addStack(builder, field, stack, content.amount(), cache);
                }
            }
            case TypedStackListContent<?, ?> list -> {
                for (var stack : list.stacks()) {
                    addStack(builder, field, stack, content.amount(), cache);
                }
            }
            case TagStackContent<?> tag -> {
                builder.searchableContent(idField(field), tag.tagKey().location().toString(), content.amount(), ContentKind.tagId);
                builder.text(SearchField.tag, tag.tagKey().location().toString());
            }
            case ValueContent<?> value -> addValue(builder, field, value.content(), content.amount());
            default -> {
            }
        }
    }

    private static <T, S> void addStack(
        RecipeDatabaseSearchDocument.Builder builder,
        SearchField field,
        ContentStack<T, S> stack,
        double amount,
        StackSearchCache cache
    ) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ContentKind nameKind = stack.isFluid() ? ContentKind.fluidName : stack.isItem() ? ContentKind.itemName : ContentKind.otherName;
        ContentKind idKind = stack.isFluid() ? ContentKind.fluidId : stack.isItem() ? ContentKind.itemId : ContentKind.otherId;
        if (stack.isItem() || stack.isFluid()) {
            builder.searchableContent(field, cache.displayName(stack), amount, nameKind);
        } else {
            builder.searchableContent(field, stack.type().toString(stack.value()), amount, nameKind);
        }
        builder.searchableContent(idField(field), cache.contentId(stack), amount, idKind);
        builder.searchableContent(idField(field), stack.type().toString(stack.value()), amount, idKind);
    }

    private static <T> void addValue(
        RecipeDatabaseSearchDocument.Builder builder,
        SearchField field,
        ContentValue<T> value,
        double amount
    ) {
        if (value == null) {
            return;
        }
        builder.searchableContent(field, value.type().toString(value.content()), amount, ContentKind.otherName);
        builder.searchableContent(idField(field), value.type().typeId().toString(), amount, ContentKind.otherId);
        builder.number(field, value.value());
        if (value.type().typeId().path().contains("time")) {
            builder.number(SearchField.time, value.value());
        }
        if (value.type().typeId().path().contains("energy")) {
            builder.number(SearchField.energy, value.value());
        }
    }

    private static SearchField idField(SearchField field) {
        return switch (field) {
            case in -> SearchField.in_id;
            case out -> SearchField.out_id;
            case cat -> SearchField.cat_id;
            case byproduct -> SearchField.byproduct_id;
            default -> field;
        };
    }

    private static <T, S> String displayName(ContentStack<T, S> stack) {
        return stack.type().toItemStack(stack).getHoverName().getString();
    }

    private static <T, S> String contentId(ContentStack<T, S> stack) {
        ResourceLocation location = stack.contentType().getLocation(stack.content());
        return location == null ? stack.type().toString(stack.value()) : location.toString();
    }

    private static Map<Namespace, List<Namespace>> normalizeSystemTags(Map<Namespace, List<Namespace>> systemTags) {
        if (systemTags == null || systemTags.isEmpty()) {
            return Map.of();
        }
        Map<Namespace, List<Namespace>> out = new LinkedHashMap<>();
        for (Map.Entry<Namespace, List<Namespace>> entry : systemTags.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            ArrayList<Namespace> tags = new ArrayList<>(entry.getValue().size());
            for (Namespace tag : entry.getValue()) {
                if (tag != null && !tags.contains(tag)) {
                    tags.add(tag);
                }
            }
            tags.sort(Comparator.naturalOrder());
            if (!tags.isEmpty()) {
                out.put(entry.getKey(), List.copyOf(tags));
            }
        }
        return Map.copyOf(out);
    }

    private static String formatNumberCandidate(double value) {
        if (Math.rint(value) == value) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static Comparator<DatabaseDefinition> databaseOrder() {
        return Comparator
            .comparingInt(DatabaseDefinition::priority)
            .thenComparingInt(definition -> definition.kind().defaultRank())
            .thenComparing(DatabaseDefinition::id);
    }

    private record WorkItem(
        DatabaseDefinition database,
        RecipeEntry<?> entry,
        List<Namespace> tags,
        @Nullable DatabaseRecipeRef ref,
        String recipeClass
    ) {
        private WorkItem(DatabaseDefinition database, RecipeEntry<?> entry, List<Namespace> tags) {
            this(database, entry, tags, recipeRef(entry), RecipeDatabaseIndex.recipeClass(entry));
        }
    }

    private static WorkItemDedupResult dedupeWorkItems(List<WorkItem> items) {
        if (items.size() <= 1) {
            return new WorkItemDedupResult(new ArrayList<>(items), 0);
        }
        ArrayList<WorkItem> deduped = new ArrayList<>(items.size());
        Set<DatabaseRecipeRef> seenRefs = new HashSet<>();
        Set<RecipeEntry<?>> seenEntriesWithoutRef = new HashSet<>();
        int duplicates = 0;
        for (WorkItem item : items) {
            DatabaseRecipeRef ref = item.ref();
            boolean first = ref == null
                ? seenEntriesWithoutRef.add(item.entry())
                : seenRefs.add(ref);
            if (first) {
                deduped.add(item);
            } else {
                duplicates++;
            }
        }
        return new WorkItemDedupResult(deduped, duplicates);
    }

    private record WorkItemDedupResult(
        ArrayList<WorkItem> items,
        int duplicates
    ) {
    }

    @FunctionalInterface
    private interface RangeTask {
        void run(int fromInclusive, int toExclusive);
    }

    private static void parallelChunks(int total, int chunkSize, String threadNamePrefix, RangeTask task) {
        if (total <= 0) {
            return;
        }
        int workers = Math.min(total, Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
        if (workers <= 1 || total < workers * 2) {
            task.run(0, total);
            return;
        }
        int chunk = Math.max(1, chunkSize);
        AtomicInteger next = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(workers, daemonThreadFactory(threadNamePrefix));
        try {
            ArrayList<Future<?>> futures = new ArrayList<>(workers);
            for (int worker = 0; worker < workers; worker++) {
                futures.add(executor.submit(() -> {
                    while (true) {
                        int from = next.getAndAdd(chunk);
                        if (from >= total) {
                            return;
                        }
                        task.run(from, Math.min(total, from + chunk));
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while building recipe database records", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Failed to build recipe database records", exception.getCause());
        } finally {
            executor.shutdown();
        }
    }

    private static void parallelRanges(int total, String threadNamePrefix, RangeTask task) {
        if (total <= 0) {
            return;
        }
        int workers = Math.min(total, Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
        if (workers <= 1 || total < workers * 2) {
            task.run(0, total);
            return;
        }
        ExecutorService executor = Executors.newFixedThreadPool(workers, daemonThreadFactory(threadNamePrefix));
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
            throw new IllegalStateException("Interrupted while building recipe database records", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Failed to build recipe database records", exception.getCause());
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

    private static boolean shouldReportProgress(int done, int total) {
        if (total <= 0 || done == total) {
            return true;
        }
        if (done < 1024) {
            return (done & 63) == 0;
        }
        return (done & 1023) == 0;
    }

    private static void logBuildProgress(
        String stage,
        int done,
        int total,
        long startNanos,
        @Nullable AtomicInteger lastBucket
    ) {
        if (!logger.isDebugEnabled() || lastBucket == null || total <= 0) {
            return;
        }
        int bucket = done >= total ? Integer.MAX_VALUE : done >>> 15;
        while (true) {
            int previous = lastBucket.get();
            if (bucket <= previous) {
                return;
            }
            if (lastBucket.compareAndSet(previous, bucket)) {
                break;
            }
        }
        long elapsedNanos = Math.max(1L, System.nanoTime() - startNanos);
        long elapsedMs = nanosToMillis(elapsedNanos);
        long ratePerSecond = elapsedNanos <= 0 ? 0L : done * 1_000_000_000L / elapsedNanos;
        Runtime runtime = Runtime.getRuntime();
        long usedMiB = (runtime.totalMemory() - runtime.freeMemory()) >> 20;
        long committedMiB = runtime.totalMemory() >> 20;
        logger.debug(
            "Recipe database build progress: stage={}, done={}, total={}, percent={}%, elapsedMs={}, ratePerSecond={}, heapUsedMiB={}, heapCommittedMiB={}",
            stage,
            done,
            total,
            total == 0 ? 100 : done * 100L / total,
            elapsedMs,
            ratePerSecond,
            usedMiB,
            committedMiB
        );
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    private static final class IntScratch {
        private int[] values = new int[8];
        private int size;

        private void add(int value) {
            if (size >= values.length) {
                values = java.util.Arrays.copyOf(values, values.length + Math.max(8, values.length));
            }
            values[size++] = value;
        }

        private int get(int index) {
            return values[index];
        }

        private int size() {
            return size;
        }

        private int[] toArray() {
            return size == 0 ? new int[0] : java.util.Arrays.copyOf(values, size);
        }
    }
}
