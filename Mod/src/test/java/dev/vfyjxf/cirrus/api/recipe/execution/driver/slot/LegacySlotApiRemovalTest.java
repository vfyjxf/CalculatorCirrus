package dev.vfyjxf.cirrus.api.recipe.execution.driver.slot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacySlotApiRemovalTest {

    @Test
    void legacySlotApiTypesAreRemoved() {
        assertMissing("dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotMap");
        assertMissing("dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotIndex");
        assertMissing("dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotRole");
        assertMissing("dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotKey");
        assertMissing("dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotRef");
        assertMissing("dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotLink");
        assertMissing("dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotLinkState");
        assertMissing("dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotClick");
        assertMissing("dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotHandler");
        assertMissing("dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotExecutionContext");
        assertMissing("dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotStepResult");
        assertMissing("dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.ValidationResult");
        assertMissing("dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotFailure");
        assertMissing("dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotTracking");
        assertMissing("dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.StandardSlotMap");
        assertMissing("dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.StandardSlotHandler");
        assertMissing("dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotDriverProfile");
    }

    private static void assertMissing(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
    }
}
