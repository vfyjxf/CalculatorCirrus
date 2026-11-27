package dev.vfyjxf.cirrus.planning.core.graph;

import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeAdvisory;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmissionPolicy;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.tag.CanonicalRecipeTag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure tag-driven SystemAdmissionPolicy (Pure JDK 21, ZERO hardcoded mod/item/regex wordlists!).
 * Driven solely by Cirrus RecipeSystemTags, 10 CanonicalRecipeTags, and external/classifier verdicts.
 */
public final class DefaultSystemAdmissionPolicy implements SystemAdmissionPolicy {

    private static final String tagDisplayOnly = "calculator_cirrus:system/display_only";
    private static final String tagMirrorDuplicate = "calculator_cirrus:system/mirror_duplicate";
    private static final String tagOptIn = "calculator_cirrus:system/opt_in";

    private static final DefaultSystemAdmissionPolicy defaults = new DefaultSystemAdmissionPolicy();

    private final Map<String, SystemAdmission> systemOverrides;
    private final Map<Long, SystemAdmission> externalRecipeAdmissions;
    private final Map<Long, RecipeAdvisory> externalRecipeAdvisories;

    public DefaultSystemAdmissionPolicy() {
        this(Map.of(), Map.of(), Map.of());
    }

    private DefaultSystemAdmissionPolicy(
        Map<String, SystemAdmission> systemOverrides,
        Map<Long, SystemAdmission> externalRecipeAdmissions,
        Map<Long, RecipeAdvisory> externalRecipeAdvisories
    ) {
        this.systemOverrides = systemOverrides;
        this.externalRecipeAdmissions = externalRecipeAdmissions;
        this.externalRecipeAdvisories = externalRecipeAdvisories;
    }

    public static DefaultSystemAdmissionPolicy defaults() {
        return defaults;
    }

    public DefaultSystemAdmissionPolicy withSystemOverride(String systemId, SystemAdmission admission) {
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(admission, "admission");
        Map<String, SystemAdmission> copy = new HashMap<>(systemOverrides);
        copy.put(systemId, admission);
        return new DefaultSystemAdmissionPolicy(Map.copyOf(copy), externalRecipeAdmissions, externalRecipeAdvisories);
    }

    public DefaultSystemAdmissionPolicy withExternalVerdicts(
        Map<Long, SystemAdmission> recipeAdmissions,
        Map<Long, RecipeAdvisory> recipeAdvisories
    ) {
        return new DefaultSystemAdmissionPolicy(
            systemOverrides,
            recipeAdmissions == null ? Map.of() : Map.copyOf(recipeAdmissions),
            recipeAdvisories == null ? Map.of() : Map.copyOf(recipeAdvisories)
        );
    }

    @Override
    public SystemAdmission classify(String systemId, String sourceKind) {
        SystemAdmission override = systemOverrides.get(systemId);
        if (override != null) {
            return override;
        }
        return SystemAdmission.enabled;
    }

    @Override
    public SystemAdmission classifyRecipe(RawRecipeRecord record) {
        SystemAdmission sysAdm = classify(record.systemId(), record.sourceKind());
        if (sysAdm == SystemAdmission.excludedDisplay) {
            return SystemAdmission.excludedDisplay;
        }
        Set<String> sysTags = record.systemTags();
        if (sysTags.contains(tagDisplayOnly) || sysTags.contains(tagMirrorDuplicate)) {
            return SystemAdmission.excludedDisplay;
        }
        if (record.canonicalTags().contains(CanonicalRecipeTag.junkOrDuplicate)) {
            return SystemAdmission.excludedDisplay;
        }
        if (sysTags.contains(tagOptIn) && sysAdm == SystemAdmission.enabled) {
            sysAdm = SystemAdmission.optInOnly;
        }
        if (!externalRecipeAdmissions.isEmpty() && record.sourceRef() != 0L) {
            SystemAdmission ext = externalRecipeAdmissions.get(record.sourceRef());
            if (ext == SystemAdmission.excludedDisplay) {
                return SystemAdmission.excludedDisplay;
            }
            if (ext == SystemAdmission.optInOnly && sysAdm == SystemAdmission.enabled) {
                return SystemAdmission.optInOnly;
            }
        }
        return sysAdm;
    }

    @Override
    public RecipeAdvisory advisoryOf(RawRecipeRecord record) {
        RecipeAdvisory adv = RecipeAdvisory.none;
        for (CanonicalRecipeTag tag : record.canonicalTags()) {
            if (tag.isLastResortByDefault()) {
                adv = RecipeAdvisory.max(adv, RecipeAdvisory.lastResort);
            } else if (tag.isDiscouragedByDefault()) {
                adv = RecipeAdvisory.max(adv, RecipeAdvisory.discouraged);
            }
        }
        if (record.structuralFidelity() < 0.60) {
            adv = RecipeAdvisory.max(adv, RecipeAdvisory.discouraged);
        }
        if (!externalRecipeAdvisories.isEmpty() && record.sourceRef() != 0L) {
            RecipeAdvisory ext = externalRecipeAdvisories.get(record.sourceRef());
            if (ext != null) {
                adv = RecipeAdvisory.max(adv, ext);
            }
        }
        return adv;
    }

    @Override
    public MachineCapabilityId capabilityOf(String systemId) {
        return MachineCapabilityGroups.capabilityOf(systemId);
    }

    @Override
    public List<String> orderMachinesForCapability(String systemId, List<String> rawMachines) {
        return MachineCapabilityGroups.orderMachines(systemId, rawMachines);
    }
}
