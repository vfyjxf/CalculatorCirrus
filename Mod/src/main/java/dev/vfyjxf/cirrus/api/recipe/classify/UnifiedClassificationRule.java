package dev.vfyjxf.cirrus.api.recipe.classify;

import dev.vfyjxf.cirrus.api.recipe.rule.CatalogRuleInput;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleEngine;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleSpec;
import dev.vfyjxf.cirrus.api.recipe.screening.RecipeUsePolicy;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The unified catalog-stage rule set as one {@link ClassificationRule}.
 *
 * <p>Wraps a compiled {@link RuleEngine} over the v3 rule document's
 * catalog-stage rules: conditions are structured (ids, tags, amounts, slot
 * shapes, chances, machines) with regex available only as an explicit
 * {@code op}; actions attach tags and routing hints. This replaces the
 * regex-only {@code PatternClassificationRule}.
 *
 * <p>The most recent verdict is kept per thread so the hint extraction after
 * {@link #classify} reuses the evaluation instead of re-running the engine
 * (graph building classifies in parallel; verdicts are deterministic).
 */
public final class UnifiedClassificationRule implements ClassificationRule {

    public static final String id = "cirrus:unified/patterns";

    private final RuleEngine engine;
    private final ThreadLocal<RecipeClassifyView> lastView = new ThreadLocal<>();
    private final ThreadLocal<RuleEngine.CatalogVerdict> lastVerdict = new ThreadLocal<>();

    private UnifiedClassificationRule(RuleEngine engine) {
        this.engine = engine;
    }

    /** Compiles catalog-stage rule specs; score actions are invalid here. */
    public static UnifiedClassificationRule of(List<RuleSpec> specs) {
        return new UnifiedClassificationRule(RuleEngine.compileCatalogOnly(specs));
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void classify(RecipeClassifyView recipe, ClassificationContext context, ClassificationSink sink) {
        RuleEngine.CatalogVerdict verdict = verdict(recipe);
        for (String tag : verdict.tags()) {
            sink.tag(tag);
        }
    }

    /** The full catalog verdict (tags, hint) for one recipe. */
    public RuleEngine.CatalogVerdict verdict(RecipeClassifyView recipe) {
        if (lastView.get() == recipe) {
            RuleEngine.CatalogVerdict cached = lastVerdict.get();
            if (cached != null) {
                return cached;
            }
        }
        RuleEngine.CatalogVerdict verdict = engine.evaluateCatalog(new CatalogRuleInput(recipe));
        lastView.set(recipe);
        lastVerdict.set(verdict);
        return verdict;
    }

    /** The routing hint policy declared by matching rules, or {@code null}. */
    public @Nullable RecipeUsePolicy hintOf(RecipeClassifyView recipe) {
        return verdict(recipe).hint();
    }

    /** Whether a whole recipe system can be skipped (system-only exclusion rules). */
    public boolean isExcludedSystem(String systemId) {
        return engine.isExcludedSystem(systemId);
    }
}
