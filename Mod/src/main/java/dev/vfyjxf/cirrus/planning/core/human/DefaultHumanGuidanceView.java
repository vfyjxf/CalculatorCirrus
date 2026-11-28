package dev.vfyjxf.cirrus.planning.core.human;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.planning.human.CostOverrideRule;
import dev.vfyjxf.cirrus.api.planning.human.CycleBreakRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.ModNamespaceRule;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.SystemRule;
import dev.vfyjxf.cirrus.api.planning.human.TagDefaultRule;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class DefaultHumanGuidanceView implements HumanGuidanceView {

    public static final double softPreferMult = 0.7;
    public static final double softPenalizeMult = 3.0;
    public static final double tagSoftPreferMult = 0.85;

    private static final DefaultHumanGuidanceView empty = new DefaultHumanGuidanceView(List.of());

    private final List<GuidanceRule> allRules;
    private final List<GuidanceRule> hardBans;
    private final Map<CompiledRecipeKey, RecipeRule> recipeRules = new HashMap<>();
    private final Map<Namespace, SystemRule> systemRules = new HashMap<>();
    private final Map<MachineEntry, MachineRule> machineRules = new HashMap<>();
    private final Map<ComponentStackKey, ResourceRule> resourceRules = new HashMap<>();
    private final Map<TagKey<?>, TagDefaultRule> tagRules = new HashMap<>();
    private final Map<TargetRecipePair, CycleBreakRule> cycleBreakRules = new HashMap<>();
    private final Map<ComponentStackKey, CostOverrideRule> costOverrideRules = new HashMap<>();
    private final Map<String, ModNamespaceRule> modNamespaceRules = new HashMap<>();

    private DefaultHumanGuidanceView(List<GuidanceRule> rules) {
        List<IndexedRule> indexed = new ArrayList<>(rules.size());
        for (int i = 0; i < rules.size(); i++) {
            GuidanceRule r = Objects.requireNonNull(rules.get(i), "rule");
            indexed.add(new IndexedRule(r, i));
        }
        // Higher scope overrides lower scope; within same scope later write wins.
        indexed.sort(
            Comparator.comparingInt((IndexedRule ir) -> ir.rule.scope().ordinal()).thenComparingInt(ir -> ir.order)
        );

        List<GuidanceRule> ordered = new ArrayList<>(indexed.size());
        for (IndexedRule ir : indexed) {
            GuidanceRule r = ir.rule;
            ordered.add(r);
            switch (r) {
                case RecipeRule rr -> recipeRules.put(rr.recipeKey(), rr);
                case SystemRule sr -> systemRules.put(sr.systemId(), sr);
                case MachineRule mr -> machineRules.put(mr.machine(), mr);
                case ResourceRule rr -> resourceRules.put(rr.resource(), rr);
                case TagDefaultRule tr -> tagRules.put(tr.tag(), tr);
                case CycleBreakRule cr ->
                    cycleBreakRules.put(new TargetRecipePair(cr.targetResource(), cr.bannedSourceRecipe()), cr);
                case CostOverrideRule cor -> costOverrideRules.put(cor.resource(), cor);
                case ModNamespaceRule mnr -> modNamespaceRules.put(mnr.modNamespace(), mnr);
            }
        }
        this.allRules = List.copyOf(ordered);

        List<GuidanceRule> bans = new ArrayList<>();
        for (RecipeRule r : recipeRules.values()) {
            if (r.strength() == RuleStrength.hardBan) bans.add(r);
        }
        for (SystemRule r : systemRules.values()) {
            if (r.strength() == RuleStrength.hardBan) bans.add(r);
        }
        for (MachineRule r : machineRules.values()) {
            if (r.strength() == RuleStrength.hardBan || !r.willingToBuildNew()) bans.add(r);
        }
        for (ResourceRule r : resourceRules.values()) {
            if (r.strength() == RuleStrength.hardBan) bans.add(r);
        }
        for (CycleBreakRule r : cycleBreakRules.values()) {
            if (r.strength() == RuleStrength.hardBan) bans.add(r);
        }
        for (ModNamespaceRule r : modNamespaceRules.values()) {
            if (r.strength() == RuleStrength.hardBan) bans.add(r);
        }
        bans.sort(Comparator.comparing(GuidanceRule::ruleId));
        this.hardBans = List.copyOf(bans);
    }

    public static HumanGuidanceView empty() {
        return empty;
    }

    public static HumanGuidanceView of(List<GuidanceRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return empty;
        }
        return new DefaultHumanGuidanceView(rules);
    }

    @Override
    public List<GuidanceRule> allRules() {
        return allRules;
    }

    @Override
    public boolean isRecipeBanned(CompiledRecipeKey recipeKey) {
        RecipeRule rr = recipeRules.get(recipeKey);
        if (rr != null) {
            return rr.strength() == RuleStrength.hardBan;
        }
        ModNamespaceRule modRule = modRuleForRecipe(recipeKey);
        return modRule != null && modRule.strength() == RuleStrength.hardBan;
    }

    @Override
    public boolean isRecipeBannedForTarget(ComponentStackKey target, CompiledRecipeKey recipeKey) {
        if (isRecipeBanned(recipeKey)) {
            return true;
        }
        CycleBreakRule cbr = cycleBreakRules.get(new TargetRecipePair(target, recipeKey));
        return cbr != null && cbr.strength() == RuleStrength.hardBan;
    }

    @Override
    public boolean isRecipePinned(CompiledRecipeKey recipeKey) {
        RecipeRule rr = recipeRules.get(recipeKey);
        return rr != null && rr.strength() == RuleStrength.hardPin;
    }

    @Override
    public boolean isRecipeTrusted(CompiledRecipeKey recipeKey) {
        RecipeRule rr = recipeRules.get(recipeKey);
        return rr != null && (rr.strength() == RuleStrength.softPrefer || rr.strength() == RuleStrength.hardPin);
    }

    @Override
    public boolean isSystemEnabled(Namespace systemId, SystemAdmission admission, boolean unlocked) {
        if (admission == SystemAdmission.excludedDisplay) {
            return false;
        }
        SystemRule sr = systemRules.get(systemId);
        if (sr != null) {
            if (sr.strength() == RuleStrength.hardBan) {
                return false;
            }
            if (admission == SystemAdmission.optInOnly) {
                return sr.optInEnabled() || unlocked;
            }
            return true;
        }
        String sysNs = namespaceOfSystem(systemId);
        if (sysNs != null) {
            ModNamespaceRule mnr = modNamespaceRules.get(sysNs);
            if (mnr != null && mnr.strength() == RuleStrength.hardBan) {
                return false;
            }
        }
        return admission == SystemAdmission.enabled || unlocked;
    }

    @Override
    public boolean isResourceTreatedAsBase(ComponentStackKey key) {
        ResourceRule rr = resourceRules.get(key);
        return rr != null && rr.treatAsFreeBaseSource() && rr.strength() != RuleStrength.hardBan;
    }

    @Override
    public boolean isResourceBanned(ComponentStackKey key) {
        ResourceRule rr = resourceRules.get(key);
        if (rr != null) {
            return rr.strength() == RuleStrength.hardBan;
        }
        return false;
    }

    @Override
    public boolean isWillingToBuild(MachineEntry machine) {
        MachineRule mr = machineRules.get(machine);
        if (mr != null) {
            return mr.willingToBuildNew() && mr.strength() != RuleStrength.hardBan;
        }
        String ns = namespaceOfMachine(machine);
        if (ns != null) {
            ModNamespaceRule mnr = modNamespaceRules.get(ns);
            if (mnr != null && mnr.strength() == RuleStrength.hardBan) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Optional<ComponentStackKey> preferredTagMember(TagKey<?> tag) {
        TagDefaultRule tr = tagRules.get(tag);
        return tr == null ? Optional.empty() : Optional.of(tr.preferredMember());
    }

    @Override
    public boolean isTagHardPinned(TagKey<?> tag) {
        TagDefaultRule tr = tagRules.get(tag);
        return tr != null && tr.strength() == RuleStrength.hardPin;
    }

    @Override
    public Optional<CostVector> overriddenUnitCost(ComponentStackKey key) {
        CostOverrideRule cor = costOverrideRules.get(key);
        return cor == null ? Optional.empty() : Optional.of(cor.overrideUnitCost());
    }

    @Override
    public double softCostMultiplier(CompiledRecipeKey recipeKey, Optional<MachineEntry> machine) {
        double mult = 1.0;
        RecipeRule rr = recipeRules.get(recipeKey);
        if (rr != null) {
            mult *= strengthMultiplier(rr.strength());
        } else {
            ModNamespaceRule mnr = modRuleForRecipe(recipeKey);
            if (mnr != null) {
                mult *= strengthMultiplier(mnr.strength());
            }
        }
        SystemRule sr = systemRules.get(recipeKey.systemId());
        if (sr != null) {
            mult *= strengthMultiplier(sr.strength());
        }
        if (machine != null && machine.isPresent()) {
            MachineEntry m = machine.get();
            MachineRule mr = machineRules.get(m);
            if (mr != null) {
                mult *= strengthMultiplier(mr.strength());
            } else {
                String mns = namespaceOfMachine(m);
                if (mns != null) {
                    ModNamespaceRule mnr = modNamespaceRules.get(mns);
                    if (mnr != null) {
                        mult *= strengthMultiplier(mnr.strength());
                    }
                }
            }
        }
        return mult;
    }

    @Override
    public double resourceSoftMultiplier(ComponentStackKey key) {
        ResourceRule rr = resourceRules.get(key);
        if (rr != null) {
            return strengthMultiplier(rr.strength());
        }
        return 1.0;
    }

    @Override
    public double tagSoftMultiplier(TagKey<?> tag) {
        TagDefaultRule tr = tagRules.get(tag);
        if (tr == null) return 1.0;
        return tr.strength() == RuleStrength.softPrefer ? tagSoftPreferMult : 1.0;
    }

    @Override
    public List<GuidanceRule> hardBanRules() {
        return hardBans;
    }

    public boolean hasMachineRule(MachineEntry machine) {
        return machineRules.containsKey(machine);
    }

    public boolean hasResourceRule(ComponentStackKey key) {
        return resourceRules.containsKey(key);
    }

    public boolean hasRecipeRule(CompiledRecipeKey key) {
        return recipeRules.containsKey(key);
    }

    public boolean hasSystemRule(Namespace systemId) {
        return systemRules.containsKey(systemId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DefaultHumanGuidanceView that)) return false;
        return allRules.equals(that.allRules);
    }

    @Override
    public int hashCode() {
        return allRules.hashCode();
    }

    private @Nullable ModNamespaceRule modRuleForRecipe(CompiledRecipeKey key) {
        if (modNamespaceRules.isEmpty()) return null;
        if (key.explicitRecipeId().isPresent()) {
            String ns = key.explicitRecipeId().get().getNamespace();
            ModNamespaceRule rule = modNamespaceRules.get(ns);
            if (rule != null) return rule;
        }
        String sysNs = namespaceOfSystem(key.systemId());
        return sysNs == null ? null : modNamespaceRules.get(sysNs);
    }

    private static double strengthMultiplier(RuleStrength strength) {
        return switch (strength) {
            case softPrefer -> softPreferMult;
            case softPenalize -> softPenalizeMult;
            default -> 1.0;
        };
    }

    private static @Nullable String namespaceOfMachine(MachineEntry machine) {
        return machine.contentKey().map(ComponentStackKey::namespace).orElse(null);
    }

    private static @Nullable String namespaceOfSystem(Namespace systemId) {
        if (systemId == null) return null;
        if ("jei".equals(systemId.namespace()) && systemId.path().startsWith("category/")) {
            String rest = systemId.path().substring("category/".length());
            int slash = rest.indexOf('/');
            return slash > 0 ? rest.substring(0, slash) : rest;
        }
        return systemId.namespace();
    }

    private record IndexedRule(GuidanceRule rule, int order) {}

    private record TargetRecipePair(ComponentStackKey target, CompiledRecipeKey recipe) {}
}
