package dev.vfyjxf.cirrus.api.recipe.classify;

/**
 * A single classification rule. Rules are matched against one recipe at a
 * time and attach zero or more {@linkplain ClassificationTags tags} through
 * the provided sink.
 *
 * <p>Rules are either <b>local</b> (decidable from the recipe alone, e.g.
 * pattern rules and structural input/output relations) or <b>global</b>
 * (needing catalog-wide indexes from the {@link ClassificationContext}, e.g.
 * two-way swap detection). Global rules run in a second pass.
 */
public interface ClassificationRule {

    /** Unique rule id, used in reports and diagnostics. */
    String id();

    /**
     * Whether this rule requires the global context indexes. Global rules run
     * after all local rules, once per recipe, with a fully built context.
     */
    default boolean global() {
        return false;
    }

    /**
     * Classifies one recipe, attaching tags through the sink.
     *
     * @param recipe  the recipe view (never null)
     * @param context global catalog indexes (never null; may be unused by local rules)
     * @param sink    tag receiver
     */
    void classify(RecipeClassifyView recipe, ClassificationContext context, ClassificationSink sink);
}
