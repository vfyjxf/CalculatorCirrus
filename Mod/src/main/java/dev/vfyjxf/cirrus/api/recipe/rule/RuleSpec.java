package dev.vfyjxf.cirrus.api.recipe.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.Nullable;

/**
 * One unified rule: a condition tree plus the actions to execute on match.
 *
 * <pre>{@code
 * {
 *   "id": "cirrus:storage_compression_ladder",
 *   "label": "9x compression ladder",
 *   "stage": "catalog",
 *   "enabled": true,
 *   "when": { "kind": "all_of", "conditions": [ ... ] },
 *   "then": [ { "do": "tag", "tag": "cirrus:pattern/storage_ladder" } ]
 * }
 * }</pre>
 *
 * @param id        unique rule id
 * @param label     human-readable name (used in UI and traces)
 * @param enabled   disabled rules are skipped entirely
 * @param stage     pipeline stage (must match the actions' stages)
 * @param condition the match condition
 * @param actions   actions executed in order when the condition matches
 */
public record RuleSpec(
    Namespace id,
    String label,
    boolean enabled,
    RuleStage stage,
    RuleCondition condition,
    java.util.List<RuleAction> actions
) {

    public static final Codec<RuleSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Namespace.codec.fieldOf("id").forGetter(RuleSpec::id),
        Codec.STRING.optionalFieldOf("label", "").forGetter(RuleSpec::label),
        Codec.BOOL.optionalFieldOf("enabled", true).forGetter(RuleSpec::enabled),
        RuleStage.CODEC.fieldOf("stage").forGetter(RuleSpec::stage),
        RuleCondition.CODEC.fieldOf("when").forGetter(RuleSpec::condition),
        Codec.list(RuleAction.CODEC).fieldOf("then").forGetter(RuleSpec::actions)
    ).apply(instance, RuleSpec::new));

    public RuleSpec {
        Checks.checkNotNull(id, "id");
        label = label == null ? "" : label;
        Checks.checkNotNull(stage, "stage");
        Checks.checkNotNull(condition, "condition");
        actions = java.util.List.copyOf(actions == null ? java.util.List.of() : actions);
    }

    /** Infers the stage from the actions when the caller has not decided yet. */
    public static @Nullable RuleStage inferStage(java.util.List<RuleAction> actions) {
        boolean catalog = false;
        boolean candidate = false;
        for (RuleAction action : actions) {
            catalog |= action.catalogStage();
            candidate |= action.candidateStage();
        }
        if (catalog && !candidate) {
            return RuleStage.catalog;
        }
        if (candidate && !catalog) {
            return RuleStage.candidate;
        }
        return null;
    }

    /** Validation problems (empty list = valid); cross-rule checks live in {@link RuleEngine}. */
    public java.util.List<String> validate() {
        java.util.List<String> problems = new java.util.ArrayList<>();
        problems.addAll(RuleCondition.validate(condition));
        if (actions.isEmpty()) {
            problems.add("rule has no actions");
        }
        for (RuleAction action : actions) {
            boolean stageOk = stage == RuleStage.catalog ? action.catalogStage() : action.candidateStage();
            if (!stageOk) {
                problems.add("action '" + action.codecKind() + "' is invalid at " + stage + " stage");
            }
        }
        return problems;
    }
}
