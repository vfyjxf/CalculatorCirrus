package dev.vfyjxf.cirrus.api.recipe.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cirrus.api.recipe.screening.RecipeUsePolicy;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The unified rule condition tree. One tree shape serves both evaluation
 * domains (catalog and candidate; see {@link RuleInput}); leaves that a domain
 * cannot supply evaluate to {@code false}.
 *
 * <p>Leaves, with their {@code "kind"} dispatch keys:
 * <ul>
 *     <li>{@code all_of} / {@code any_of} / {@code not} — boolean combinators.</li>
 *     <li>{@code system}, {@code recipe_id}, {@code recipe_mod}, {@code machine} —
 *         id matchers over {@link TextMatchOp} value lists.</li>
 *     <li>{@code material} — matches slots by concrete id or declared tag id,
 *         with optional per-slot amount comparison and any/all quantifier.</li>
 *     <li>{@code slot_count} — input/output/total slot count comparison.</li>
 *     <li>{@code id_overlap} — some output id also appears among the input ids
 *         (chisel swaps, self-repair loops).</li>
 *     <li>{@code chance} — slot chance comparison (probabilistic outputs).</li>
 *     <li>{@code has_concrete_input} — whether any input slot carries a
 *         concrete id (free-resource detection when {@code false}).</li>
 *     <li>{@code classification_tag} — the recipe carries a classification tag.</li>
 *     <li>{@code flag} / {@code scalar} / {@code hint} — candidate-domain leaves.</li>
 * </ul>
 *
 * <p>Material match tokens: {@code "item:<id>"} and {@code "fluid:<id>"} (or a
 * bare id) match concrete slot ids; {@code "#<tag>"} or {@code "tag:<tag>"}
 * matches the slot's declared tag id. Matching is registry-free: it compares
 * declared content, not registry tag membership.
 */
public sealed interface RuleCondition permits
    RuleCondition.AllOf,
    RuleCondition.AnyOf,
    RuleCondition.Not,
    RuleCondition.SystemId,
    RuleCondition.RecipeId,
    RuleCondition.RecipeMod,
    RuleCondition.Machine,
    RuleCondition.Material,
    RuleCondition.SlotCount,
    RuleCondition.IdOverlap,
    RuleCondition.Chance,
    RuleCondition.HasConcreteInput,
    RuleCondition.ClassificationTag,
    RuleCondition.FlagCondition,
    RuleCondition.ScalarCondition,
    RuleCondition.HintCondition {

    Codec<RuleCondition> CODEC = Codec.recursive(
        "cirrus_rule_condition",
        self -> Codec.STRING.dispatch("kind", RuleCondition::codecKind, type -> codecForType(type, self))
    );

    /** Evaluates this condition against the given input. */
    boolean matches(RuleInput input);

    /** The {@code "kind"} dispatch key for serialization. */
    default String codecKind() {
        return switch (this) {
            case AllOf ignored -> "all_of";
            case AnyOf ignored -> "any_of";
            case Not ignored -> "not";
            case SystemId ignored -> "system";
            case RecipeId ignored -> "recipe_id";
            case RecipeMod ignored -> "recipe_mod";
            case Machine ignored -> "machine";
            case Material ignored -> "material";
            case SlotCount ignored -> "slot_count";
            case IdOverlap ignored -> "id_overlap";
            case Chance ignored -> "chance";
            case HasConcreteInput ignored -> "has_concrete_input";
            case ClassificationTag ignored -> "classification_tag";
            case FlagCondition ignored -> "flag";
            case ScalarCondition ignored -> "scalar";
            case HintCondition ignored -> "hint";
        };
    }

    /**
     * Whether the condition depends only on the system id (enables the
     * system-level fast path: whole systems can be skipped at catalog build).
     */
    default boolean dependsOnlyOnSystem() {
        return false;
    }

    private static MapCodec<? extends RuleCondition> codecForType(String type, Codec<RuleCondition> self) {
        return switch (type) {
            case "all_of" -> Codec.list(self).fieldOf("conditions").xmap(AllOf::new, AllOf::conditions);
            case "any_of" -> Codec.list(self).fieldOf("conditions").xmap(AnyOf::new, AnyOf::conditions);
            case "not" -> self.fieldOf("condition").xmap(Not::new, Not::condition);
            case "system" -> textMatcherCodec(SystemId::new, SystemId::matcher);
            case "recipe_id" -> textMatcherCodec(RecipeId::new, RecipeId::matcher);
            case "recipe_mod" -> textMatcherCodec(RecipeMod::new, RecipeMod::matcher);
            case "machine" -> textMatcherCodec(Machine::new, Machine::matcher);
            case "material" -> Material.CODEC;
            case "slot_count" -> SlotCount.CODEC;
            case "id_overlap" -> Codec.BOOL.optionalFieldOf("match", true).xmap(ignored -> new IdOverlap(), ignored -> true);
            case "chance" -> Chance.CODEC;
            case "has_concrete_input" -> Codec.BOOL.fieldOf("value").xmap(HasConcreteInput::new, HasConcreteInput::expected);
            case "classification_tag" -> Codec.STRING.fieldOf("tag").xmap(ClassificationTag::new, ClassificationTag::tag);
            case "flag" -> RuleFlag.CODEC.fieldOf("flag").xmap(FlagCondition::new, FlagCondition::flag);
            case "scalar" -> ScalarCondition.CODEC;
            case "hint" -> dev.vfyjxf.cloudlib.api.util.Codecs.lowerCaseEnum(RecipeUsePolicy.class).fieldOf("policy").xmap(HintCondition::new, HintCondition::policy);
            default -> throw new IllegalArgumentException("Unknown rule condition kind: " + type);
        };
    }

    /** Shared {@code op + values} shape for the id-matching leaves. */
    private static <T extends RuleCondition> MapCodec<T> textMatcherCodec(
        java.util.function.BiFunction<TextMatchOp, List<String>, T> factory,
        java.util.function.Function<T, TextMatcher> getter
    ) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
            TextMatchOp.CODEC.fieldOf("op").forGetter(condition -> getter.apply(condition).op()),
            Codec.STRING.listOf().fieldOf("values").forGetter(condition -> getter.apply(condition).values())
        ).apply(instance, factory));
    }

    /** Matches when every sub-condition matches. */
    record AllOf(List<RuleCondition> conditions) implements RuleCondition {
        public AllOf {
            Checks.checkNotNull(conditions, "conditions");
            conditions = List.copyOf(conditions);
        }

        @Override
        public boolean matches(RuleInput input) {
            for (RuleCondition condition : conditions) {
                if (!condition.matches(input)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean dependsOnlyOnSystem() {
            return conditions.stream().allMatch(RuleCondition::dependsOnlyOnSystem);
        }
    }

    /** Matches when any sub-condition matches. */
    record AnyOf(List<RuleCondition> conditions) implements RuleCondition {
        public AnyOf {
            Checks.checkNotNull(conditions, "conditions");
            conditions = List.copyOf(conditions);
        }

        @Override
        public boolean matches(RuleInput input) {
            for (RuleCondition condition : conditions) {
                if (condition.matches(input)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean dependsOnlyOnSystem() {
            return conditions.stream().allMatch(RuleCondition::dependsOnlyOnSystem);
        }
    }

    /** Matches when the sub-condition does not match. */
    record Not(RuleCondition condition) implements RuleCondition {
        public Not {
            Checks.checkNotNull(condition, "condition");
        }

        @Override
        public boolean matches(RuleInput input) {
            return !condition.matches(input);
        }

        @Override
        public boolean dependsOnlyOnSystem() {
            return condition.dependsOnlyOnSystem();
        }
    }

    /** Id matcher over the owning system id. */
    final class SystemId implements RuleCondition {

        private final TextMatcher matcher;

        public SystemId(TextMatchOp op, List<String> values) {
            this(TextMatcher.compile(op, values));
        }

        public SystemId(TextMatcher matcher) {
            this.matcher = Checks.checkNotNull(matcher, "matcher");
        }
        public TextMatcher matcher() {
            return matcher;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof SystemId other && matcher.equals(other.matcher);
        }

        @Override
        public int hashCode() {
            return SystemId.class.hashCode() * 31 + matcher.hashCode();
        }

        @Override
        public boolean matches(RuleInput input) {
            return matcher.matches(input.systemId());
        }

        @Override
        public boolean dependsOnlyOnSystem() {
            return true;
        }
    }

    /** Id matcher over the full recipe id. */
    final class RecipeId implements RuleCondition {

        private final TextMatcher matcher;

        public RecipeId(TextMatchOp op, List<String> values) {
            this(TextMatcher.compile(op, values));
        }

        public RecipeId(TextMatcher matcher) {
            this.matcher = Checks.checkNotNull(matcher, "matcher");
        }

        public TextMatcher matcher() {
            return matcher;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof RecipeId other && matcher.equals(other.matcher);
        }

        @Override
        public int hashCode() {
            return RecipeId.class.hashCode() * 31 + matcher.hashCode();
        }

        @Override
        public boolean matches(RuleInput input) {
            return !input.recipeId().isEmpty() && matcher.matches(input.recipeId());
        }
    }

    /** Id matcher over the recipe mod namespace. */
    final class RecipeMod implements RuleCondition {

        private final TextMatcher matcher;

        public RecipeMod(TextMatchOp op, List<String> values) {
            this(TextMatcher.compile(op, values));
        }

        public RecipeMod(TextMatcher matcher) {
            this.matcher = Checks.checkNotNull(matcher, "matcher");
        }

        public TextMatcher matcher() {
            return matcher;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof RecipeMod other && matcher.equals(other.matcher);
        }

        @Override
        public int hashCode() {
            return RecipeMod.class.hashCode() * 31 + matcher.hashCode();
        }

        @Override
        public boolean matches(RuleInput input) {
            return !input.recipeMod().isEmpty() && matcher.matches(input.recipeMod());
        }
    }

    /** Id matcher over the machines able to execute the recipe. */
    final class Machine implements RuleCondition {

        private final TextMatcher matcher;

        public Machine(TextMatchOp op, List<String> values) {
            this(TextMatcher.compile(op, values));
        }

        public Machine(TextMatcher matcher) {
            this.matcher = Checks.checkNotNull(matcher, "matcher");
        }

        public TextMatcher matcher() {
            return matcher;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Machine other && matcher.equals(other.matcher);
        }

        @Override
        public int hashCode() {
            return Machine.class.hashCode() * 31 + matcher.hashCode();
        }

        @Override
        public boolean matches(RuleInput input) {
            for (String machine : input.machines()) {
                if (matcher.matches(machine)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Slot matcher: matches slots whose concrete ids or declared tag id equal
     * one of the tokens, optionally filtered by slot amount.
     *
     * @param channel    which slots to inspect
     * @param ids        concrete id tokens (prefix-stripped)
     * @param tags       declared tag id tokens (prefix-stripped)
     * @param amount     optional amount comparison applied to matched slots
     * @param allSlots   {@code true} = every slot on the channel must match
     *                   ({@code quantifier: "all"}); {@code false} = any
     */
    record Material(
        RuleChannel channel,
        Set<String> ids,
        Set<String> tags,
        Optional<AmountSpec> amount,
        boolean allSlots
    ) implements RuleCondition {

        public record AmountSpec(NumOp op, double value) {
            public static final Codec<AmountSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                NumOp.CODEC.fieldOf("op").forGetter(AmountSpec::op),
                Codec.DOUBLE.fieldOf("value").forGetter(AmountSpec::value)
            ).apply(instance, AmountSpec::new));

            public AmountSpec {
                Checks.checkNotNull(op, "op");
            }

            public boolean matches(double candidate) {
                return op.test(candidate, value);
            }
        }

        public static final MapCodec<Material> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RuleChannel.CODEC.fieldOf("channel").forGetter(Material::channel),
            Codec.STRING.listOf().optionalFieldOf("match", List.of()).forGetter(Material::serializedTokens),
            AmountSpec.CODEC.optionalFieldOf("amount").forGetter(Material::amount),
            Codec.BOOL.optionalFieldOf("all_slots", false).forGetter(Material::allSlots)
        ).apply(instance, (channel, tokens, amount, allSlots) ->
            Material.ofTokens(channel, tokens, amount.orElse(null), allSlots)));
        public Material {
            Checks.checkNotNull(channel, "channel");
            ids = Set.copyOf(ids);
            tags = Set.copyOf(tags);
            amount = amount == null ? Optional.empty() : amount;
        }

        /** Factory from raw tokens ({@code "item:x"}, {@code "fluid:x"}, {@code "#t"}, {@code "tag:t"}). */
        public static Material ofTokens(RuleChannel channel, List<String> tokens, @Nullable AmountSpec amount, boolean allSlots) {
            return new Material(channel, parseIds(tokens), parseTags(tokens), Optional.ofNullable(amount), allSlots);
        }

        private static Set<String> parseIds(List<String> tokens) {
            Set<String> out = new LinkedHashSet<>();
            for (String token : tokens) {
                String clean = token == null ? "" : token.trim();
                if (clean.isEmpty() || clean.startsWith("#") || clean.startsWith("tag:")) {
                    continue;
                }
                out.add(stripContentPrefix(clean));
            }
            return out;
        }

        private static Set<String> parseTags(List<String> tokens) {
            Set<String> out = new LinkedHashSet<>();
            for (String token : tokens) {
                String clean = token == null ? "" : token.trim();
                if (clean.startsWith("#")) {
                    out.add(clean.substring(1));
                } else if (clean.startsWith("tag:")) {
                    out.add(clean.substring("tag:".length()));
                }
            }
            return out;
        }

        private static String stripContentPrefix(String token) {
            if (token.startsWith("item:")) {
                return token.substring("item:".length());
            }
            if (token.startsWith("fluid:")) {
                return token.substring("fluid:".length());
            }
            return token;
        }

        private List<String> serializedTokens() {
            List<String> tokens = new ArrayList<>();
            for (String id : ids) {
                tokens.add("item:" + id);
            }
            for (String tag : tags) {
                tokens.add("#" + tag);
            }
            return tokens;
        }

        @Override
        public boolean matches(RuleInput input) {
            if (allSlots) {
                // every slot on the channel must match; a side with zero slots fails
                if (channel == RuleChannel.input || channel == RuleChannel.any) {
                    if (!allSlotsMatch(input, true)) {
                        return false;
                    }
                }
                if (channel == RuleChannel.output || channel == RuleChannel.any) {
                    return allSlotsMatch(input, false);
                }
                return true;
            }
            if (channel == RuleChannel.input || channel == RuleChannel.any) {
                if (matchesSide(input, true)) {
                    return true;
                }
            }
            return (channel == RuleChannel.output || channel == RuleChannel.any) && matchesSide(input, false);
        }

        private boolean matchesSide(RuleInput input, boolean inputSide) {
            int count = input.slotCount(inputSide);
            for (int i = 0; i < count; i++) {
                if (matchesSlot(input.slot(inputSide, i))) {
                    return true;
                }
            }
            return false;
        }

        private boolean allSlotsMatch(RuleInput input, boolean inputSide) {
            int count = input.slotCount(inputSide);
            if (count == 0) {
                return false;
            }
            for (int i = 0; i < count; i++) {
                if (!matchesSlot(input.slot(inputSide, i))) {
                    return false;
                }
            }
            return true;
        }

        private boolean matchesSlot(RuleInput.Slot slot) {
            boolean tokenless = ids.isEmpty() && tags.isEmpty();
            if (!tokenless) {
                boolean idHit = false;
                for (String id : slot.ids()) {
                    if (ids.contains(id)) {
                        idHit = true;
                        break;
                    }
                }
                boolean tagHit = slot.tagId() != null && tags.contains(slot.tagId());
                if (!idHit && !tagHit) {
                    return false;
                }
            }
            return amount.map(spec -> spec.matches(slot.amount())).orElse(!tokenless);
        }
    }

    /** Compares the slot count of a channel ({@code any} = inputs + outputs). */
    record SlotCount(RuleChannel channel, NumOp op, int value) implements RuleCondition {
        public static final MapCodec<SlotCount> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RuleChannel.CODEC.fieldOf("channel").forGetter(SlotCount::channel),
            NumOp.CODEC.fieldOf("op").forGetter(SlotCount::op),
            Codec.INT.fieldOf("value").forGetter(SlotCount::value)
        ).apply(instance, SlotCount::new));

        public SlotCount {
            Checks.checkNotNull(channel, "channel");
            Checks.checkNotNull(op, "op");
        }

        @Override
        public boolean matches(RuleInput input) {
            int count = switch (channel) {
                case input -> input.slotCount(true);
                case output -> input.slotCount(false);
                case any -> input.slotCount(true) + input.slotCount(false);
            };
            return op.test(count, value);
        }
    }

    /** Matches when some output id also appears among the input ids. */
    final class IdOverlap implements RuleCondition {

        @Override
        public boolean equals(Object o) {
            return o instanceof IdOverlap;
        }

        @Override
        public int hashCode() {
            return IdOverlap.class.hashCode();
        }

        @Override
        public boolean matches(RuleInput input) {
            if (input.inputIds().isEmpty() || input.outputIds().isEmpty()) {
                return false;
            }
            for (String outputId : input.outputIds()) {
                if (input.inputIds().contains(outputId)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Compares slot chance values ({@code any} slot satisfying the comparison matches). */
    record Chance(RuleChannel channel, NumOp op, double value) implements RuleCondition {
        public static final MapCodec<Chance> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RuleChannel.CODEC.fieldOf("channel").forGetter(Chance::channel),
            NumOp.CODEC.fieldOf("op").forGetter(Chance::op),
            Codec.DOUBLE.fieldOf("value").forGetter(Chance::value)
        ).apply(instance, Chance::new));

        public Chance {
            Checks.checkNotNull(channel, "channel");
            Checks.checkNotNull(op, "op");
        }

        @Override
        public boolean matches(RuleInput input) {
            if (channel == RuleChannel.input || channel == RuleChannel.any) {
                if (matchesSide(input, true)) {
                    return true;
                }
            }
            if (channel == RuleChannel.output || channel == RuleChannel.any) {
                return matchesSide(input, false);
            }
            return false;
        }

        private boolean matchesSide(RuleInput input, boolean inputSide) {
            int count = input.slotCount(inputSide);
            for (int i = 0; i < count; i++) {
                if (op.test(input.slot(inputSide, i).chance(), value)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Matches when {@code hasConcreteInput == expected}. */
    record HasConcreteInput(boolean expected) implements RuleCondition {
        @Override
        public boolean matches(RuleInput input) {
            boolean has = false;
            for (String id : input.inputIds()) {
                if (!id.isEmpty()) {
                    has = true;
                    break;
                }
            }
            return has == expected;
        }
    }

    /** Matches when the recipe carries the given classification tag. */
    record ClassificationTag(String tag) implements RuleCondition {
        public ClassificationTag {
            Checks.checkNotNull(tag, "tag");
        }

        @Override
        public boolean matches(RuleInput input) {
            return input.tags().contains(tag);
        }
    }

    /** Candidate-domain boolean flag. */
    record FlagCondition(RuleFlag flag) implements RuleCondition {
        public FlagCondition {
            Checks.checkNotNull(flag, "flag");
        }

        @Override
        public boolean matches(RuleInput input) {
            return input.hasCandidateFields() && input.flag(flag);
        }
    }

    /** Candidate-domain scalar comparison. */
    record ScalarCondition(RuleScalar field, NumOp op, double value) implements RuleCondition {
        public static final MapCodec<ScalarCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RuleScalar.CODEC.fieldOf("field").forGetter(ScalarCondition::field),
            NumOp.CODEC.fieldOf("op").forGetter(ScalarCondition::op),
            Codec.DOUBLE.fieldOf("value").forGetter(ScalarCondition::value)
        ).apply(instance, ScalarCondition::new));

        public ScalarCondition {
            Checks.checkNotNull(field, "field");
            Checks.checkNotNull(op, "op");
        }

        @Override
        public boolean matches(RuleInput input) {
            if (!input.hasCandidateFields()) {
                return false;
            }
            double candidate = input.scalar(field);
            return !Double.isNaN(candidate) && op.test(candidate, value);
        }
    }

    /** Candidate-domain routing-hint policy match. */
    record HintCondition(RecipeUsePolicy policy) implements RuleCondition {
        public HintCondition {
            Checks.checkNotNull(policy, "policy");
        }

        @Override
        public boolean matches(RuleInput input) {
            return input.hasCandidateFields() && input.hintPolicy() == policy;
        }
    }

    static List<String> validate(RuleCondition condition) {
        List<String> problems = new ArrayList<>();
        collectProblems(condition, problems);
        return problems;
    }

    private static void collectProblems(RuleCondition condition, List<String> problems) {
        switch (condition) {
            case AllOf allOf -> allOf.conditions().forEach(child -> collectProblems(child, problems));
            case AnyOf anyOf -> anyOf.conditions().forEach(child -> collectProblems(child, problems));
            case Not not -> collectProblems(not.condition(), problems);
            case SystemId systemId -> {
                if (systemId.matcher().isEmpty()) {
                    problems.add("system condition has no values");
                }
            }
            case RecipeId recipeId -> {
                if (recipeId.matcher().isEmpty()) {
                    problems.add("recipe_id condition has no values");
                }
            }
            case RecipeMod recipeMod -> {
                if (recipeMod.matcher().isEmpty()) {
                    problems.add("recipe_mod condition has no values");
                }
            }
            case Machine machine -> {
                if (machine.matcher().isEmpty()) {
                    problems.add("machine condition has no values");
                }
            }
            case Material material -> {
                if (material.ids().isEmpty() && material.tags().isEmpty() && material.amount().isEmpty()) {
                    problems.add("material condition has no match tokens and no amount filter");
                }
            }
            case SlotCount ignored -> {
            }
            case IdOverlap ignored -> {
            }
            case Chance ignored -> {
            }
            case HasConcreteInput ignored -> {
            }
            case ClassificationTag ignored -> {
            }
            case FlagCondition ignored -> {
            }
            case ScalarCondition ignored -> {
            }
            case HintCondition ignored -> {
            }
        }
    }
}
