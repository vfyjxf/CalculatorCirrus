package dev.vfyjxf.cirrus.api.planning.human;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.List;
import java.util.Optional;

public interface HumanGuidanceView {

    List<GuidanceRule> allRules();

    boolean isRecipeBanned(CompiledRecipeKey recipeKey);

    boolean isRecipeBannedForTarget(ResourceKey target, CompiledRecipeKey recipeKey);

    boolean isRecipePinned(CompiledRecipeKey recipeKey);

    boolean isSystemEnabled(String systemId, SystemAdmission admission, boolean unlocked);

    boolean isResourceTreatedAsBase(ResourceKey key);

    boolean isResourceBanned(ResourceKey key);

    boolean isWillingToBuild(String machineId);

    Optional<ResourceKey> preferredTagMember(String tagId);

    Optional<CostVector> overriddenUnitCost(ResourceKey key);

    double softCostMultiplier(CompiledRecipeKey recipeKey, Optional<String> machineId);

    List<GuidanceRule> hardBanRules();

    default double resourceSoftMultiplier(ResourceKey key) {
        return 1.0;
    }

    default double tagSoftMultiplier(String tagId) {
        return 1.0;
    }

    default boolean isTagHardPinned(String tagId) {
        return false;
    }
}
