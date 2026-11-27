package dev.vfyjxf.cirrus.ui.workbench.facilities;

import com.google.gson.JsonElement;
import dev.vfyjxf.cirrus.api.planning.context.AutomatedLine;
import dev.vfyjxf.cirrus.api.planning.context.AutomatedRecipeCapability;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.OwnedMachineGroup;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.planning.bridge.WorldFacilityRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI-TC-30: CRUD of the four facility modules goes through WorldFacilityRegistry and survives a JSON round trip.
 */
class FacilitiesCrudTest {

    private static final MachineCapabilityId smelting = new MachineCapabilityId("minecraft:smelting");
    private static final MachineCapabilityId crafting = new MachineCapabilityId("minecraft:crafting");
    private static final ResourceKey ironIngot = ResourceKey.item("minecraft:iron_ingot");
    private static final ResourceKey ironBlock = ResourceKey.item("minecraft:iron_block");
    private static final CompiledRecipeKey packIron = new CompiledRecipeKey(
        "minecraft:crafting",
        Optional.of("minecraft:iron_block"),
        11L
    );
    private static final CompiledRecipeKey smeltIron = new CompiledRecipeKey(
        "minecraft:smelting",
        Optional.of("minecraft:iron_ingot_from_smelting"),
        23L
    );

    @Test
    void ownedMachinesSupportCreateUpdateAndDelete() {
        WorldFacilityRegistry registry = WorldFacilityRegistry.createDetached();

        registry.declareOwnedMachine("minecraft:furnace", smelting, 2, 1.0, false);
        FacilitySnapshot created = registry.facilitySnapshot();
        assertEquals(1, created.ownedMachines().size());
        assertEquals(2, created.ownedCountOf("minecraft:furnace"));
        assertEquals(1, created.ownedMachinesFor(smelting).size());

        registry.declareOwnedMachine("minecraft:furnace", smelting, 5, 2.5, true);
        OwnedMachineGroup updated = registry.facilitySnapshot().ownedMachines().getFirst();
        assertEquals(5, updated.count());
        assertEquals(2.5, updated.passiveSpeedMultiplier(), 1e-9);
        assertTrue(updated.automatedFeeding());
        assertEquals(1, registry.facilitySnapshot().ownedMachines().size());

        registry.declareOwnedMachine("minecraft:crafting_table", crafting, 1, 1.0, false);
        assertEquals(1, registry.facilitySnapshot().ownedMachinesFor(crafting).size());
        assertEquals(2, registry.toPersistedState().ownedMachines().size());

        registry.declareOwnedMachine("minecraft:furnace", smelting, 0, 1.0, false);
        assertTrue(registry.facilitySnapshot().ownedMachinesFor(smelting).isEmpty());
        assertEquals(1, registry.toPersistedState().ownedMachines().size());
    }

    @Test
    void automatedLinesSupportPutUpdateAndRemove() {
        WorldFacilityRegistry registry = WorldFacilityRegistry.createDetached();
        AutomatedLine line = new AutomatedLine("line:iron", ironIngot, 60.0, 2.0, 128L, true);

        registry.putAutomatedLine(line);
        assertEquals(1, registry.facilitySnapshot().automatedLinesFor(ironIngot).size());
        assertEquals(1, registry.toPersistedState().automatedLines().size());

        AutomatedLine rescaled = new AutomatedLine("line:iron", ironIngot, 120.0, 1.0, 256L, false);
        registry.putAutomatedLine(rescaled);
        assertEquals(1, registry.facilitySnapshot().automatedLines().size());
        assertEquals(rescaled, registry.facilitySnapshot().automatedLines().getFirst());

        registry.removeAutomatedLine("line:iron");
        assertTrue(registry.facilitySnapshot().automatedLinesFor(ironIngot).isEmpty());
        assertTrue(registry.toPersistedState().automatedLines().isEmpty());
    }

    @Test
    void automatedRecipesSupportPutUpdateAndRemove() {
        WorldFacilityRegistry registry = WorldFacilityRegistry.createDetached();

        registry.putAutomatedRecipe(new AutomatedRecipeCapability(packIron, 3.0, 1));
        assertEquals(1, registry.facilitySnapshot().automatedRecipe(packIron).orElseThrow().parallelCrafters());

        registry.putAutomatedRecipe(new AutomatedRecipeCapability(packIron, 4.5, 3));
        AutomatedRecipeCapability updated = registry.facilitySnapshot().automatedRecipe(packIron).orElseThrow();
        assertEquals(4.5, updated.passiveSecondsPerBatch(), 1e-9);
        assertEquals(3, updated.parallelCrafters());
        assertEquals(1, registry.toPersistedState().automatedRecipes().size());

        registry.removeAutomatedRecipe(packIron);
        assertTrue(registry.facilitySnapshot().automatedRecipe(packIron).isEmpty());
        assertTrue(registry.toPersistedState().automatedRecipes().isEmpty());
    }

    @Test
    void optInUnlocksAreTrackedAndRevoked() {
        WorldFacilityRegistry registry = WorldFacilityRegistry.createDetached();

        registry.unlockSystem("ae2:charger");
        registry.unlockSystem("create:pressing");
        assertEquals(Set.of("ae2:charger", "create:pressing"), registry.facilitySnapshot().unlockedSystems());
        assertEquals(List.of("ae2:charger", "create:pressing"), registry.toPersistedState().unlockedSystems());

        registry.lockSystem("ae2:charger");
        assertEquals(Set.of("create:pressing"), registry.facilitySnapshot().unlockedSystems());
        registry.lockSystem("create:pressing");
        assertTrue(registry.facilitySnapshot().unlockedSystems().isEmpty());
    }

    @Test
    void persistedStateSurvivesJsonRoundTrip() {
        WorldFacilityRegistry registry = WorldFacilityRegistry.createDetached();
        registry.declareOwnedMachine("minecraft:furnace", smelting, 3, 1.75, true);
        registry.declareOwnedMachine("minecraft:crafting_table", crafting, 1, 1.0, false);
        registry.putAutomatedLine(new AutomatedLine("line:iron", ironIngot, 60.0, 2.0, 128L, true));
        registry.putAutomatedRecipe(new AutomatedRecipeCapability(packIron, 3.0, 2));
        registry.putAutomatedRecipe(new AutomatedRecipeCapability(smeltIron, 8.0, 1));
        registry.unlockSystem("ae2:charger");
        registry.putWorldSaveRule(new RecipeRule("rule:pack", GuidanceScope.worldSave, RuleStrength.hardPin, packIron));

        JsonElement encoded = registry.encodeToJson().orElseThrow();
        WorldFacilityRegistry reloaded = WorldFacilityRegistry.loadFromJson(encoded);

        assertNotNull(reloaded);
        assertEquals(registry.toPersistedState(), reloaded.toPersistedState());
        assertEquals(2, reloaded.toPersistedState().ownedMachines().size());
        assertEquals(1, reloaded.toPersistedState().automatedLines().size());
        assertEquals(2, reloaded.toPersistedState().automatedRecipes().size());
        assertEquals(List.of("ae2:charger"), reloaded.toPersistedState().unlockedSystems());
        assertEquals(1, reloaded.worldSaveRules().size());
    }

    @Test
    void nonWorldScopedRulesArePersistedAsWorldSaveRules() {
        WorldFacilityRegistry registry = WorldFacilityRegistry.createDetached();
        registry.putWorldSaveRule(
            new RecipeRule("rule:session", GuidanceScope.session, RuleStrength.hardBan, packIron)
        );

        List<?> persisted = registry.worldSaveRules();
        assertEquals(1, persisted.size());
        assertEquals(GuidanceScope.worldSave, registry.worldSaveRules().getFirst().scope());

        WorldFacilityRegistry reloaded = WorldFacilityRegistry.loadFromJson(registry.encodeToJson().orElseThrow());
        assertEquals(GuidanceScope.worldSave, reloaded.worldSaveRules().getFirst().scope());
        assertFalse(reloaded.worldSaveRules().isEmpty());
    }
}
