package dev.vfyjxf.cirrus.api.recipe.classify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.ExchangeStructuralRule;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.GenerationStructuralRule;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.MassSinkStructuralRule;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.RecolorStructuralRule;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.RecyclingStructuralRule;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The classification pipeline. Runs an ordered list of {@link ClassificationRule}s
 * over a catalog of {@link RecipeClassifyView}s and produces a
 * {@link ClassificationResult}.
 *
 * <p>The pipeline has two passes:
 * <ol>
 *     <li><b>Local pass</b> — every non-global rule runs per recipe.</li>
 *     <li><b>Global pass</b> — context indexes are built, then global rules run
 *     per recipe; group rules (e.g. {@link MassSinkStructuralRule}) additionally
 *     run once over the whole context.</li>
 * </ol>
 *
 * <p>The classifier is stateless and safe to reuse across catalogs.
 */
public final class RecipeClassifier {

    /**
     * Classpath location of the built-in pattern rules derived from the
     * fifteen exported modpack datasets.
     */
    public static final String BUILTIN_RULES_RESOURCE = "assets/calculator_cirrus/classification/builtin_rules.json";

    private static final ClassificationContext EMPTY_CONTEXT = ClassificationContext.of(List.of());

    /**
     * Per-recipe memo of local-pass tags, keyed by {@code systemId + '\n' +
     * recipeId}. The local rules are all context-free (only the global rules
     * read {@link ClassificationContext}), so a recipe's local tags are stable
     * for the lifetime of this classifier instance. The same recipe is
     * classified during graph-build hint derivation and again during the
     * catalog populate pass; the memo makes the second pass a lookup. Entries
     * with an empty recipe id are never cached (distinct recipes share that
     * key). Cleared on client unload via {@link #clearLocalTagsCache()}.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.Set<String>> localTagsCache = new java.util.concurrent.ConcurrentHashMap<>();

    private final List<ClassificationRule> rules;
    private final List<ClassificationRule> localRules;

    private RecipeClassifier(List<ClassificationRule> rules) {
        this.rules = List.copyOf(rules);
        this.localRules = rules.stream().filter(rule -> !rule.global()).toList();
    }

    /**
     * Creates the default classifier: built-in pattern rules (loaded from the
     * classpath resource) followed by the built-in structural rules.
     */
    public static RecipeClassifier createDefault() {
        Builder builder = builder();
        builder.addAll(loadBuiltinPatternRules());
        builder.add(ExchangeStructuralRule.instance);
        builder.add(RecolorStructuralRule.instance);
        builder.add(RecyclingStructuralRule.instance);
        builder.add(GenerationStructuralRule.instance);
        builder.add(MassSinkStructuralRule.create());
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Loads the built-in pattern rules from the classpath. Returns an empty
     * list when the resource is missing or malformed (classification then
     * relies on structural rules only).
     */
    public static List<PatternClassificationRule> loadBuiltinPatternRules() {
        return loadPatternRules(BUILTIN_RULES_RESOURCE);
    }

    /** Loads pattern rules from an arbitrary classpath resource. */
    public static List<PatternClassificationRule> loadPatternRules(String resourcePath) {
        ClassLoader loader = RecipeClassifier.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return List.of();
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject document = JsonParser.parseReader(reader).getAsJsonObject();
                return PatternClassificationRule.listFromJson(document);
            }
        } catch (IOException | RuntimeException ignored) {
            return List.of();
        }
    }

    /** All rules in evaluation order. */
    public List<ClassificationRule> rules() {
        return rules;
    }

    /**
     * Runs only the local (per-recipe) rules against a single view. Global
     * rules are skipped, so two-way/group classifications are absent; intended
     * for graph-build-time hint derivation where only one recipe is resolved.
     */
    public java.util.Set<String> classifyLocal(RecipeClassifyView view) {
        if (!view.recipeId().isEmpty()) {
            return localTagsCache.computeIfAbsent(localTagsKey(view), key -> classifyLocalUncached(view));
        }
        return classifyLocalUncached(view);
    }

    private java.util.Set<String> classifyLocalUncached(RecipeClassifyView view) {
        java.util.Set<String> tags = new java.util.LinkedHashSet<>();
        ClassificationSink sink = tags::add;
        for (ClassificationRule rule : localRules) {
            rule.classify(view, EMPTY_CONTEXT, sink);
        }
        return tags;
    }

    /** Drops the per-recipe local-tag memo (world unload / datapack reload). */
    public void clearLocalTagsCache() {
        localTagsCache.clear();
    }

    private static String localTagsKey(RecipeClassifyView view) {
        return view.systemId() + '\n' + view.recipeId();
    }

    /**
     * Returns {@code true} when the given system id is matched by at least one
     * pattern rule that only attaches exclusion-level tags
     * ({@link ClassificationTags#isExclusionTag}). Such systems are skipped
     * entirely when building the builtin routing catalog.
     */
    public boolean isExcludedSystem(String systemId) {
        for (ClassificationRule rule : rules) {
            if (!(rule instanceof PatternClassificationRule pattern)) {
                continue;
            }
            if (!pattern.matchesSystem(systemId)) {
                continue;
            }
            boolean allExclusion = !pattern.tags().isEmpty()
                && pattern.tags().stream().allMatch(ClassificationTags::isExclusionTag);
            if (allExclusion) {
                return true;
            }
        }
        return false;
    }

    /**
     * Classifies the given catalog of recipe views.
     *
     * @param recipes views indexed by their catalog position
     * @return tag -&gt; recipe-index bitsets
     */
    public ClassificationResult classifyAll(List<? extends RecipeClassifyView> recipes) {
        Map<String, BitSet> tags = new LinkedHashMap<>();
        List<ClassificationRule> local = new ArrayList<>();
        List<ClassificationRule> global = new ArrayList<>();
        List<MassSinkStructuralRule> groupRules = new ArrayList<>();
        for (ClassificationRule rule : rules) {
            (rule.global() ? global : local).add(rule);
            if (rule instanceof MassSinkStructuralRule massSink) {
                groupRules.add(massSink);
            }
        }

        ClassificationContext context = ClassificationContext.of(List.copyOf(recipes));
        for (RecipeClassifyView view : recipes) {
            int index = view.index();
            ClassificationSink sink = tag -> tags.computeIfAbsent(tag, k -> new BitSet(recipes.size())).set(index);
            if (!view.recipeId().isEmpty()) {
                // local rules are context-free, so the memo shared with
                // classifyLocal yields identical tags
                for (String tag : localTagsCache.computeIfAbsent(localTagsKey(view), key -> collectLocalTags(view, context))) {
                    sink.tag(tag);
                }
                continue;
            }
            for (ClassificationRule rule : local) {
                rule.classify(view, context, sink);
            }
        }

        for (RecipeClassifyView view : recipes) {
            int index = view.index();
            ClassificationSink sink = tag -> tags.computeIfAbsent(tag, k -> new BitSet(recipes.size())).set(index);
            for (ClassificationRule rule : global) {
                rule.classify(view, context, sink);
            }
        }
        for (MassSinkStructuralRule groupRule : groupRules) {
            groupRule.classifyAll(context, (index, tag) ->
                tags.computeIfAbsent(tag, k -> new BitSet(recipes.size())).set(index));
        }

        return new ClassificationResult(recipes.size(), tags);
    }

    private java.util.Set<String> collectLocalTags(RecipeClassifyView view, ClassificationContext context) {
        java.util.Set<String> tags = new java.util.LinkedHashSet<>();
        ClassificationSink sink = tags::add;
        for (ClassificationRule rule : localRules) {
            rule.classify(view, context, sink);
        }
        return tags;
    }

    public static final class Builder {
        private final List<ClassificationRule> rules = new ArrayList<>();

        public Builder add(ClassificationRule rule) {
            rules.add(rule);
            return this;
        }

        public Builder addAll(List<? extends ClassificationRule> toAdd) {
            rules.addAll(toAdd);
            return this;
        }

        public RecipeClassifier build() {
            return new RecipeClassifier(rules);
        }
    }
}
