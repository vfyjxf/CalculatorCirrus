package dev.vfyjxf.cirrus.api.planning.human;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;

public interface HumanGuidanceView {

    List<GuidanceRule> allRules();

    boolean isRecipeBanned(CompiledRecipeKey recipeKey);

    boolean isRecipeBannedForTarget(ComponentStackKey target, CompiledRecipeKey recipeKey);

    boolean isRecipePinned(CompiledRecipeKey recipeKey);

    default boolean isRecipeTrusted(CompiledRecipeKey recipeKey) {
        return isRecipePinned(recipeKey);
    }

    boolean isSystemEnabled(Namespace systemId, SystemAdmission admission, boolean unlocked);

    boolean isResourceTreatedAsBase(ComponentStackKey key);

    boolean isResourceBanned(ComponentStackKey key);

    boolean isWillingToBuild(MachineEntry machine);

    Optional<ComponentStackKey> preferredTagMember(TagKey<?> tag);

    Optional<CostVector> overriddenUnitCost(ComponentStackKey key);

    double softCostMultiplier(CompiledRecipeKey recipeKey, Optional<MachineEntry> machine);

    List<GuidanceRule> hardBanRules();

    default double resourceSoftMultiplier(ComponentStackKey key) {
        return 1.0;
    }

    default double tagSoftMultiplier(TagKey<?> tag) {
        return 1.0;
    }

    default boolean isTagHardPinned(TagKey<?> tag) {
        return false;
    }
}
