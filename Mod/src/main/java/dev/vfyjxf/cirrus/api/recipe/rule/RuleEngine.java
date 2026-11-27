package dev.vfyjxf.cirrus.api.recipe.rule;

import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags;
import dev.vfyjxf.cirrus.api.recipe.screening.RecipeUsePolicy;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Compiles and evaluates a unified rule set. One engine instance serves both
 * stages:
 *
 * <ul>
 *     <li>{@link #evaluateCatalog(RuleInput)} — tags, exclusion verdict
 *         (last-wins over {@code exclude}/{@code include} actions in document
 *         order) and hint policy for one recipe at catalog-build time;</li>
 *     <li>{@link #evaluateCandidate(RuleInput)} — reject reason and score
 *         adjustments for one planner candidate.</li>
 * </ul>
 *
 * <p>Compilation validates everything cross-referential up front — condition
 * shape, action/stage consistency, and score-action component names against
 * the active score model — so evaluation never fails and never silently drops
 * an unknown component.
 */
public final class RuleEngine {

    private final List<RuleSpec> catalogRules;
    private final List<RuleSpec> candidateRules;

    private RuleEngine(List<RuleSpec> catalogRules, List<RuleSpec> candidateRules) {
        this.catalogRules = catalogRules;
        this.candidateRules = candidateRules;
    }

    /**
     * Compiles rule specs into an engine.
     *
     * @param rules               rules in document order
     * @param knownScoreComponents component names the candidate-stage score
     *                             actions may reference (from the active
     *                             {@link ScoreModel})
     * @throws IllegalArgumentException on the first validation problem,
     *                                  with every problem listed in the cause chain message
     */
    public static RuleEngine compile(List<RuleSpec> rules, Set<String> knownScoreComponents) {
        Checks.checkNotNull(rules, "rules");
        Checks.checkNotNull(knownScoreComponents, "knownScoreComponents");
        List<String> problems = new ArrayList<>();
        List<RuleSpec> catalog = new ArrayList<>();
        List<RuleSpec> candidate = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        for (RuleSpec rule : rules) {
            if (!seenIds.add(rule.id().toString())) {
                problems.add("duplicate rule id '" + rule.id() + "'");
            }
            problems.addAll(rule.validate().stream().map(problem -> "rule " + rule.id() + ": " + problem).toList());
            for (RuleAction action : rule.actions()) {
                if (action instanceof RuleAction.ScoreAdjust adjust
                        && !knownScoreComponents.contains(adjust.component())) {
                    problems.add(
                        "rule " + rule.id() + ": score action references undeclared component '" + adjust.component()
                                + "'"
                    );
                }
            }
            (rule.stage() == RuleStage.catalog ? catalog : candidate).add(rule);
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("invalid rule set: " + String.join("; ", problems));
        }
        return new RuleEngine(List.copyOf(catalog), List.copyOf(candidate));
    }

    /** Compiles with no score model (candidate score actions are then invalid). */
    public static RuleEngine compileCatalogOnly(List<RuleSpec> rules) {
        return compile(rules, Set.of());
    }

    public List<RuleSpec> catalogRules() {
        return catalogRules;
    }

    public List<RuleSpec> candidateRules() {
        return candidateRules;
    }

    /** Catalog-stage verdict for one recipe. */
    public CatalogVerdict evaluateCatalog(RuleInput input) {
        Set<String> tags = new LinkedHashSet<>();
        boolean excluded = false;
        @Nullable
        RecipeUsePolicy hint = null;
        // relation rules can build on tags attached earlier in the same pass
        RuleInput accumulated = new AccumulatedTagsInput(input, tags);
        for (RuleSpec rule : catalogRules) {
            if (!rule.enabled() || !rule.condition().matches(accumulated)) {
                continue;
            }
            for (RuleAction action : rule.actions()) {
                switch (action) {
                    case RuleAction.Tag tag -> tags.add(tag.tag());
                    case RuleAction.Exclude ignored -> excluded = true;
                    case RuleAction.Include ignored -> excluded = false;
                    case RuleAction.Hint hintAction -> hint = strictest(hint, hintAction.policy());
                    default -> {
                        // candidate-stage actions cannot reach here (validated at compile)
                    }
                }
            }
        }
        return new CatalogVerdict(tags, excluded, hint);
    }

    /** Delegating wrapper exposing the tags accumulated so far in this pass. */
    private static final class AccumulatedTagsInput implements RuleInput {
        private final RuleInput delegate;
        private final Set<String> tags;

        private AccumulatedTagsInput(RuleInput delegate, Set<String> tags) {
            this.delegate = delegate;
            this.tags = tags;
        }

        @Override
        public String systemId() {
            return delegate.systemId();
        }

        @Override
        public String recipeId() {
            return delegate.recipeId();
        }

        @Override
        public List<String> machines() {
            return delegate.machines();
        }

        @Override
        public int slotCount(boolean inputSide) {
            return delegate.slotCount(inputSide);
        }

        @Override
        public Slot slot(boolean inputSide, int index) {
            return delegate.slot(inputSide, index);
        }

        @Override
        public Set<String> inputIds() {
            return delegate.inputIds();
        }

        @Override
        public Set<String> outputIds() {
            return delegate.outputIds();
        }

        @Override
        public Set<String> tags() {
            return tags;
        }
    }

    /**
     * Whole-system exclusion fast path: whether some system-only catalog rule
     * (condition depends on nothing but the system id) with an exclusion-effect
     * action matches this system id. Descriptive tag actions may accompany the
     * exclusion (layered rule files attach {@code cirrus:feat/*} tags first);
     * their loss on skipped systems is harmless because those recipes are
     * excluded from routing anyway. Hint actions disqualify the rule (hints on
     * skipped systems would be silently lost).
     */
    public boolean isExcludedSystem(String systemId) {
        RuleInput systemOnly = new SystemOnlyInput(systemId);
        for (RuleSpec rule : catalogRules) {
            if (!rule.enabled() || !rule.condition().dependsOnlyOnSystem()) {
                continue;
            }
            boolean hasExclusionEffect = false;
            boolean eligible = !rule.actions().isEmpty();
            for (RuleAction action : rule.actions()) {
                if (isExclusionEffect(action)) {
                    hasExclusionEffect = true;
                } else if (!(action instanceof RuleAction.Tag)) {
                    eligible = false;
                    break;
                }
            }
            if (eligible && hasExclusionEffect && rule.condition().matches(systemOnly)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExclusionEffect(RuleAction action) {
        if (action instanceof RuleAction.Exclude) {
            return true;
        }
        return action instanceof RuleAction.Tag tag && ClassificationTags.isExclusionTag(tag.tag());
    }

    private static @Nullable RecipeUsePolicy strictest(@Nullable RecipeUsePolicy current, RecipeUsePolicy next) {
        if (current == null) {
            return next;
        }
        return severity(current) >= severity(next) ? current : next;
    }

    private static int severity(RecipeUsePolicy policy) {
        return switch (policy) {
            case normal -> 0;
            case discouraged -> 1;
            case lastResort -> 2;
            case disabledByDefault -> 3;
            case targetOnly -> 4;
        };
    }

    /** Candidate-stage verdict for one planner candidate. */
    public CandidateVerdict evaluateCandidate(RuleInput input) {
        @Nullable
        String rejectReason = null;
        List<RuleAction.ScoreAdjust> adjustments = List.of();
        List<RuleAction.ScoreAdjust> collected = null;
        for (RuleSpec rule : candidateRules) {
            if (!rule.enabled() || !rule.condition().matches(input)) {
                continue;
            }
            for (RuleAction action : rule.actions()) {
                switch (action) {
                    case RuleAction.Reject reject -> {
                        if (rejectReason == null) {
                            rejectReason = reject.reasonOrNull() != null
                                    ? reject.reasonOrNull()
                                    : ("rejected by rule " + rule.id());
                        }
                    }
                    case RuleAction.ScoreAdjust adjust -> {
                        if (collected == null) {
                            collected = new ArrayList<>();
                        }
                        collected.add(adjust);
                    }
                    default -> {
                        // catalog-stage actions cannot reach here (validated at compile)
                    }
                }
            }
        }
        if (collected != null) {
            adjustments = List.copyOf(collected);
        }
        return new CandidateVerdict(rejectReason, adjustments);
    }

    /** Catalog-stage outcome for one recipe. */
    public record CatalogVerdict(Set<String> tags, boolean excluded, @Nullable RecipeUsePolicy hint) {}

    /** Candidate-stage outcome for one planner candidate. */
    public record CandidateVerdict(@Nullable String rejectReason, List<RuleAction.ScoreAdjust> adjustments) {

        public boolean rejected() {
            return rejectReason != null;
        }
    }

    /** Minimal input carrying only a system id, for the system-level fast path. */
    private record SystemOnlyInput(String systemId) implements RuleInput {

        @Override
        public String systemId() {
            return systemId;
        }

        @Override
        public String recipeId() {
            return "";
        }

        @Override
        public List<String> machines() {
            return List.of();
        }

        @Override
        public int slotCount(boolean inputSide) {
            return 0;
        }

        @Override
        public Slot slot(boolean inputSide, int index) {
            throw new IndexOutOfBoundsException("system-only input has no slots");
        }

        @Override
        public Set<String> inputIds() {
            return Set.of();
        }

        @Override
        public Set<String> outputIds() {
            return Set.of();
        }
    }
}
