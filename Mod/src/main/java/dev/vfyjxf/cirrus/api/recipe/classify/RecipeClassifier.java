package dev.vfyjxf.cirrus.api.recipe.classify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.ColonyViewMirrorRule;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.ExchangeStructuralRule;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.GenerationStructuralRule;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.MassSinkStructuralRule;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.RecolorStructuralRule;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.RecyclingStructuralRule;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeUsePolicy;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleDocument;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleSpec;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The classification pipeline. Runs an ordered list of {@link ClassificationRule}s
 * over a catalog of {@link RecipeClassifyView}s and produces a
 * {@link ClassificationResult}.
 *
 * <p>The default classifier is the unified v3 rule document (structured
 * conditions, loaded from {@link #BUILTIN_RULES_RESOURCE}) followed by the
 * built-in structural rules.
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
     * Classpath location of the built-in unified rule document (v3):
     * structured catalog-stage rules derived from the exported modpack
     * datasets and the multi-pack routing/chain audits.
     */
    public static final String BUILTIN_RULES_RESOURCE = "assets/calculator_cirrus/rules/builtin_rules.json";

    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Classifier");
    private static final ClassificationContext EMPTY_CONTEXT = ClassificationContext.of(List.of());

    /** Local-pass outcome: tags plus the routing hint declared by unified rules. */
    public record LocalOutcome(Set<String> tags, @Nullable RecipeUsePolicy hint) {
    }

    /**
     * Per-recipe memo of local-pass outcomes, keyed by {@code systemId + '\n' +
     * recipeId}. The local rules are all context-free (only the global rules
     * read {@link ClassificationContext}), so a recipe's local outcome is stable
     * for the lifetime of this classifier instance. The same recipe is
     * classified during graph-build hint derivation and again during the
     * catalog populate pass; the memo makes the second pass a lookup. Entries
     * with an empty recipe id are never cached (distinct recipes share that
     * key). Cleared on client unload via {@link #clearLocalTagsCache()}.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, LocalOutcome> localOutcomeCache = new java.util.concurrent.ConcurrentHashMap<>();

    private final List<ClassificationRule> rules;
    private final List<ClassificationRule> localRules;
    private final @Nullable UnifiedClassificationRule unified;

    private RecipeClassifier(List<ClassificationRule> rules, @Nullable UnifiedClassificationRule unified) {
        this.rules = List.copyOf(rules);
        this.localRules = rules.stream().filter(rule -> !rule.global()).toList();
        this.unified = unified;
    }

    /**
     * Creates the default classifier: the built-in unified rules (loaded from
     * the classpath resource) followed by the built-in structural rules.
     */
    public static RecipeClassifier createDefault() {
        Builder builder = builder();
        UnifiedClassificationRule unified = UnifiedClassificationRule.of(loadBuiltinRuleSpecs());
        builder.add(unified);
        builder.add(ExchangeStructuralRule.instance);
        builder.add(RecolorStructuralRule.instance);
        builder.add(RecyclingStructuralRule.instance);
        builder.add(GenerationStructuralRule.instance);
        builder.add(MassSinkStructuralRule.create());
        builder.add(ColonyViewMirrorRule.instance);
        return builder.buildWithUnified(unified);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Loads the built-in unified rule specs from the classpath. Returns an
     * empty list when the resource is missing or malformed — the failure is
     * logged loudly (structural rules still classify) instead of silently
     * degrading.
     */
    public static List<RuleSpec> loadBuiltinRuleSpecs() {
        return loadRuleSpecs(BUILTIN_RULES_RESOURCE);
    }

    /** Loads catalog-stage rule specs from an arbitrary classpath resource. */
    public static List<RuleSpec> loadRuleSpecs(String resourcePath) {
        ClassLoader loader = RecipeClassifier.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                logger.error("rule resource not found: {} (classification falls back to structural rules)", resourcePath);
                return List.of();
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject document = JsonParser.parseReader(reader).getAsJsonObject();
                return RuleDocument.CODEC
                    .parse(JsonOps.INSTANCE, document)
                    .resultOrPartial(error -> logger.error("rule resource {} failed to decode: {}", resourcePath, error))
                    .map(RuleDocument::rules)
                    .orElseGet(List::of);
            }
        } catch (IOException | RuntimeException e) {
            logger.error("rule resource {} failed to load: {}", resourcePath, e.toString());
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
        return classifyLocalOutcome(view).tags();
    }

    /** Memoized local outcome (tags + unified hint) for one recipe. */
    public LocalOutcome classifyLocalOutcome(RecipeClassifyView view) {
        if (!view.recipeId().isEmpty()) {
            return localOutcomeCache.computeIfAbsent(localTagsKey(view), key -> classifyLocalUncached(view));
        }
        return classifyLocalUncached(view);
    }

    /** The routing hint policy for one recipe, or {@code null}. */
    public @Nullable RecipeUsePolicy hintOf(RecipeClassifyView view) {
        return classifyLocalOutcome(view).hint();
    }

    private LocalOutcome classifyLocalUncached(RecipeClassifyView view) {
        java.util.Set<String> tags = new LinkedHashSet<>();
        ClassificationSink sink = tags::add;
        for (ClassificationRule rule : localRules) {
            rule.classify(view, EMPTY_CONTEXT, sink);
        }
        RecipeUsePolicy hint = unified != null ? unified.hintOf(view) : null;
        return new LocalOutcome(Set.copyOf(tags), hint);
    }

    /** Drops the per-recipe local-outcome memo (world unload / datapack reload). */
    public void clearLocalTagsCache() {
        localOutcomeCache.clear();
    }

    private static String localTagsKey(RecipeClassifyView view) {
        return view.systemId() + '\n' + view.recipeId();
    }

    /**
     * Returns {@code true} when the given system id is matched by at least one
     * system-only unified rule with exclusion-level effect. Such systems are
     * skipped entirely when building the builtin routing catalog.
     */
    public boolean isExcludedSystem(String systemId) {
        return unified != null && unified.isExcludedSystem(systemId);
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
                for (String tag : classifyLocalOutcome(view).tags()) {
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
            return new RecipeClassifier(rules, null);
        }

        private RecipeClassifier buildWithUnified(UnifiedClassificationRule unified) {
            return new RecipeClassifier(rules, unified);
        }
    }
}
