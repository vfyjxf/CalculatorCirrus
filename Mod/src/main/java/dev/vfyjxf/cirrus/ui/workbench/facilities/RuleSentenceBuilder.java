package dev.vfyjxf.cirrus.ui.workbench.facilities;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.human.CostOverrideRule;
import dev.vfyjxf.cirrus.api.planning.human.CycleBreakRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.ModNamespaceRule;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.SystemRule;
import dev.vfyjxf.cirrus.api.planning.human.TagDefaultRule;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Natural-language sentence builder for the eight guidance rule kinds: pure data, no rendering dependency.
 */
public final class RuleSentenceBuilder {

    public static final String fieldScope = "scope";
    public static final String fieldStrength = "strength";
    public static final String fieldResource = "resource";
    public static final String fieldRecipe = "recipe";
    public static final String fieldMachine = "machine";
    public static final String fieldSystem = "system";
    public static final String fieldTag = "tag";
    public static final String fieldMember = "member";
    public static final String fieldMod = "mod";
    public static final String fieldOptIn = "opt_in";
    public static final String fieldAllowBuild = "allow_build";
    public static final String fieldFreeBase = "free_base";
    public static final String fieldTargetResource = "target_resource";
    public static final String fieldBannedRecipe = "banned_recipe";
    public static final String fieldH = "h";
    public static final String fieldP = "p";
    public static final String fieldK = "k";
    public static final String fieldS = "s";
    public static final String fieldC = "c";

    public static final String scopePrefix = "cirrus.ui.workbench.facilities.scope.";

    public enum FieldType {
        strength, resource, recipe, machine, system, tag, mod, number, bool
    }

    public record Field(String key, FieldType type, boolean required) {
        public Field {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(type, "type");
        }

        public LangEntry labelKey() {
            return LangEntry.of("cirrus.ui.workbench.facilities.rule_field." + key);
        }
    }

    public enum RuleKind {
        recipe("recipe"), system("system"), machine("machine"), resource("resource"), tagDefault("tag"), cycleBreak(
            "cycle"
        ), costOverride("cost"), modNamespace("mod");

        private final String id;

        RuleKind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public LangEntry labelKey() {
            return LangEntry.of("cirrus.ui.workbench.facilities.rule_kind." + id);
        }

        public LangEntry sentenceKey() {
            return LangEntry.of("cirrus.ui.workbench.facilities.rule_sentence." + id);
        }

        public List<Field> fields() {
            return switch (this) {
                case recipe -> List.of(
                    new Field(fieldStrength, FieldType.strength, true),
                    new Field(fieldRecipe, FieldType.recipe, true)
                );
                case system -> List.of(
                    new Field(fieldStrength, FieldType.strength, true),
                    new Field(fieldSystem, FieldType.system, true),
                    new Field(fieldOptIn, FieldType.bool, false)
                );
                case machine -> List.of(
                    new Field(fieldStrength, FieldType.strength, true),
                    new Field(fieldMachine, FieldType.machine, true),
                    new Field(fieldAllowBuild, FieldType.bool, false)
                );
                case resource -> List.of(
                    new Field(fieldStrength, FieldType.strength, true),
                    new Field(fieldResource, FieldType.resource, true),
                    new Field(fieldFreeBase, FieldType.bool, false)
                );
                case tagDefault -> List.of(
                    new Field(fieldTag, FieldType.tag, true),
                    new Field(fieldMember, FieldType.resource, true),
                    new Field(fieldStrength, FieldType.strength, true)
                );
                case cycleBreak -> List.of(
                    new Field(fieldTargetResource, FieldType.resource, true),
                    new Field(fieldBannedRecipe, FieldType.recipe, true),
                    new Field(fieldStrength, FieldType.strength, false)
                );
                case costOverride -> List.of(
                    new Field(fieldResource, FieldType.resource, true),
                    new Field(fieldH, FieldType.number, true),
                    new Field(fieldP, FieldType.number, true),
                    new Field(fieldK, FieldType.number, true),
                    new Field(fieldS, FieldType.number, true),
                    new Field(fieldC, FieldType.number, true),
                    new Field(fieldStrength, FieldType.strength, false)
                );
                case modNamespace -> List.of(
                    new Field(fieldStrength, FieldType.strength, true),
                    new Field(fieldMod, FieldType.mod, true)
                );
            };
        }

        public Map<String, Object> defaults() {
            return switch (this) {
                case system -> Map.of(fieldOptIn, false);
                case machine -> Map.of(fieldAllowBuild, true);
                case resource -> Map.of(fieldFreeBase, false);
                case cycleBreak -> Map.of(fieldStrength, RuleStrength.hardBan);
                case costOverride -> Map.of(fieldStrength, RuleStrength.softPenalize);
                default -> Map.of();
            };
        }
    }

    public record Validation(List<Field> missing, List<Field> invalid) {
        public Validation {
            missing = List.copyOf(missing);
            invalid = List.copyOf(invalid);
        }

        public boolean ok() {
            return missing.isEmpty() && invalid.isEmpty();
        }
    }

    private final RuleKind kind;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private GuidanceScope scope = GuidanceScope.session;

    private RuleSentenceBuilder(RuleKind kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.values.putAll(kind.defaults());
    }

    public static RuleSentenceBuilder of(RuleKind kind) {
        return new RuleSentenceBuilder(kind);
    }

    public RuleKind kind() {
        return kind;
    }

    public List<Field> fields() {
        return kind.fields();
    }

    public GuidanceScope scope() {
        return scope;
    }

    public RuleSentenceBuilder scope(GuidanceScope scope) {
        this.scope = scope == null ? GuidanceScope.session : scope;
        return this;
    }

    public RuleSentenceBuilder put(String key, @Nullable Object value) {
        if (fieldOf(key).isEmpty()) {
            throw new IllegalArgumentException("Unknown field " + key + " for rule kind " + kind.id());
        }
        if (value == null) {
            values.remove(key);
        } else {
            values.put(key, value);
        }
        return this;
    }

    public @Nullable Object valueOf(String key) {
        return values.get(key);
    }

    public RuleSentenceBuilder strength(@Nullable RuleStrength strength) {
        return put(fieldStrength, strength);
    }

    public RuleSentenceBuilder resource(ResourceKey resource) {
        return put(fieldResource, resource);
    }

    public RuleSentenceBuilder targetResource(ResourceKey resource) {
        return put(fieldTargetResource, resource);
    }

    public RuleSentenceBuilder preferredMember(ResourceKey resource) {
        return put(fieldMember, resource);
    }

    public RuleSentenceBuilder recipe(CompiledRecipeKey recipeKey) {
        return put(fieldRecipe, recipeKey);
    }

    public RuleSentenceBuilder bannedSourceRecipe(CompiledRecipeKey recipeKey) {
        return put(fieldBannedRecipe, recipeKey);
    }

    public RuleSentenceBuilder machine(String machineId) {
        return put(fieldMachine, machineId);
    }

    public RuleSentenceBuilder system(String systemId) {
        return put(fieldSystem, systemId);
    }

    public RuleSentenceBuilder tag(String tagId) {
        return put(fieldTag, tagId);
    }

    public RuleSentenceBuilder mod(String modNamespace) {
        return put(fieldMod, modNamespace);
    }

    public RuleSentenceBuilder flag(String key, boolean value) {
        return put(key, value);
    }

    public RuleSentenceBuilder number(String key, double value) {
        return put(key, value);
    }

    public RuleSentenceBuilder unitCost(CostVector cost) {
        Objects.requireNonNull(cost, "cost");
        put(fieldH, cost.humanActiveSeconds());
        put(fieldP, cost.passiveWaitSeconds());
        put(fieldK, cost.machineSetupCapital());
        put(fieldS, cost.materialScarcity());
        return put(fieldC, cost.cognitiveFriction());
    }

    public Validation validate() {
        List<Field> missing = new ArrayList<>();
        List<Field> invalid = new ArrayList<>();
        for (Field field : kind.fields()) {
            Object value = values.get(field.key());
            if (value == null) {
                if (field.required()) {
                    missing.add(field);
                }
                continue;
            }
            if (!matches(field.type(), value)) {
                invalid.add(field);
            }
        }
        return new Validation(missing, invalid);
    }

    public Optional<GuidanceRule> tryBuild(String ruleId) {
        if (!validate().ok()) {
            return Optional.empty();
        }
        return Optional.of(build(ruleId));
    }

    public GuidanceRule build(String ruleId) {
        Objects.requireNonNull(ruleId, "ruleId");
        if (ruleId.isBlank()) {
            throw new IllegalArgumentException("ruleId must not be blank");
        }
        Validation validation = validate();
        if (!validation.ok()) {
            throw new IllegalArgumentException(
                "Rule draft is incomplete: missing=" + keysOf(validation.missing()) + " invalid="
                        + keysOf(validation.invalid())
            );
        }
        RuleStrength strength = (RuleStrength) requiredValue(fieldStrength);
        return switch (kind) {
            case recipe -> new RecipeRule(ruleId, scope, strength, (CompiledRecipeKey) requiredValue(fieldRecipe));
            case system -> new SystemRule(
                ruleId,
                scope,
                strength,
                (String) requiredValue(fieldSystem),
                Boolean.TRUE.equals(values.get(fieldOptIn))
            );
            case machine -> new MachineRule(
                ruleId,
                scope,
                strength,
                (String) requiredValue(fieldMachine),
                Boolean.TRUE.equals(values.get(fieldAllowBuild))
            );
            case resource -> new ResourceRule(
                ruleId,
                scope,
                strength,
                (ResourceKey) requiredValue(fieldResource),
                Boolean.TRUE.equals(values.get(fieldFreeBase))
            );
            case tagDefault -> new TagDefaultRule(
                ruleId,
                scope,
                strength,
                (String) requiredValue(fieldTag),
                (ResourceKey) requiredValue(fieldMember)
            );
            case cycleBreak -> new CycleBreakRule(
                ruleId,
                scope,
                strength,
                (ResourceKey) requiredValue(fieldTargetResource),
                (CompiledRecipeKey) requiredValue(fieldBannedRecipe)
            );
            case costOverride -> new CostOverrideRule(
                ruleId,
                scope,
                strength,
                (ResourceKey) requiredValue(fieldResource),
                new CostVector(numberOf(fieldH), numberOf(fieldP), numberOf(fieldK), numberOf(fieldS), numberOf(fieldC))
            );
            case modNamespace -> new ModNamespaceRule(ruleId, scope, strength, (String) requiredValue(fieldMod));
        };
    }

    /** A field the validation pass proved present; a missing one is a builder bug. */
    private Object requiredValue(String key) {
        return Objects.requireNonNull(values.get(key), "missing rule field " + key);
    }

    public Component previewSentence() {
        return previewSentence(this::defaultDisplay);
    }

    public Component previewSentence(Function<Field, Component> fieldRenderer) {
        Objects.requireNonNull(fieldRenderer, "fieldRenderer");
        List<Component> arguments = new ArrayList<>();
        for (Field field : kind.fields()) {
            arguments.add(fieldRenderer.apply(field));
        }
        return Component.translatable("cirrus.ui.workbench.facilities.rule_sentence." + kind.id(), arguments.toArray());
    }

    public Component defaultDisplay(Field field) {
        Object value = values.get(field.key());
        if (value == null) {
            return Component.literal("-");
        }
        if (value instanceof RuleStrength strength) {
            return strengthLabel(strength);
        }
        if (value instanceof ResourceKey resource) {
            return Component.literal(resource.shortString());
        }
        if (value instanceof CompiledRecipeKey recipeKey) {
            return Component.literal(recipeKey.shortString());
        }
        if (value instanceof Boolean flag) {
            return flag
                    ? Component.translatable("cirrus.ui.workbench.facilities.rule_field.on")
                    : Component.translatable("cirrus.ui.workbench.facilities.rule_field.off");
        }
        return Component.literal(String.valueOf(value));
    }

    public static Component strengthLabel(RuleStrength strength) {
        return switch (Objects.requireNonNull(strength, "strength")) {
            case hardBan -> Component.translatable("cirrus.ui.workbench.facilities.strength.hard_ban");
            case hardPin -> Component.translatable("cirrus.ui.workbench.facilities.strength.hard_pin");
            case softPrefer -> Component.translatable("cirrus.ui.workbench.facilities.strength.soft_prefer");
            case softPenalize -> Component.translatable("cirrus.ui.workbench.facilities.strength.soft_penalize");
        };
    }

    public static Component scopeLabel(GuidanceScope scope) {
        return switch (Objects.requireNonNull(scope, "scope")) {
            case session -> Component.translatable(scopePrefix + "session");
            case worldSave -> Component.translatable(scopePrefix + "world");
            case modpackDefault -> Component.translatable(scopePrefix + "default");
            case queryLocal -> Component.translatable(scopePrefix + "query");
        };
    }

    public static Component sentenceOf(GuidanceRule rule) {
        Objects.requireNonNull(rule, "rule");
        RuleSentenceBuilder draft = of(kindOf(rule).orElseThrow()).scope(rule.scope());
        draft.strength(rule.strength());
        switch (rule) {
            case RecipeRule r -> draft.recipe(r.recipeKey());
            case SystemRule r -> draft.system(r.systemId()).flag(fieldOptIn, r.optInEnabled());
            case MachineRule r -> draft.machine(r.machineId()).flag(fieldAllowBuild, r.willingToBuildNew());
            case ResourceRule r -> draft.resource(r.resource()).flag(fieldFreeBase, r.treatAsFreeBaseSource());
            case TagDefaultRule r -> draft.tag(r.tagId()).preferredMember(r.preferredMember());
            case CycleBreakRule r ->
                draft.targetResource(r.targetResource()).bannedSourceRecipe(r.bannedSourceRecipe());
            case CostOverrideRule r -> draft.resource(r.resource()).unitCost(r.overrideUnitCost());
            case ModNamespaceRule r -> draft.mod(r.modNamespace());
        }
        return draft.previewSentence();
    }

    public static Optional<RuleKind> kindOf(GuidanceRule rule) {
        Objects.requireNonNull(rule, "rule");
        return Optional.of(switch (rule) {
            case RecipeRule ignored -> RuleKind.recipe;
            case SystemRule ignored -> RuleKind.system;
            case MachineRule ignored -> RuleKind.machine;
            case ResourceRule ignored -> RuleKind.resource;
            case TagDefaultRule ignored -> RuleKind.tagDefault;
            case CycleBreakRule ignored -> RuleKind.cycleBreak;
            case CostOverrideRule ignored -> RuleKind.costOverride;
            case ModNamespaceRule ignored -> RuleKind.modNamespace;
        });
    }

    public static int kindCount() {
        return RuleKind.values().length;
    }

    private Optional<Field> fieldOf(String key) {
        for (Field field : kind.fields()) {
            if (field.key().equals(key)) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }

    private double numberOf(String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    private static boolean matches(FieldType type, Object value) {
        return switch (type) {
            case strength -> value instanceof RuleStrength;
            case resource -> value instanceof ResourceKey;
            case recipe -> value instanceof CompiledRecipeKey;
            case machine, system, tag, mod -> value instanceof String text && !text.isBlank();
            case number ->
                value instanceof Number number && Double.isFinite(number.doubleValue()) && number.doubleValue() >= 0.0;
            case bool -> value instanceof Boolean;
        };
    }

    private static List<String> keysOf(List<Field> fields) {
        return fields.stream().map(Field::key).toList();
    }
}
