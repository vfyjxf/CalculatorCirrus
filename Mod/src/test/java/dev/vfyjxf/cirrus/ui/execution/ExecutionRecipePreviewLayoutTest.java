package dev.vfyjxf.cirrus.ui.execution;

import dev.vfyjxf.cirrus.ui.execution.support.ExecutionRecipePreviewLayout;
import dev.vfyjxf.cirrus.ui.process.MaterialCategories;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MaterialSlot;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionRecipePreviewLayoutTest {

    @Test
    void fallbackPreviewKeepsCraftingGridAsSingleInputSection() {
        List<MaterialSlot> inputs = Collections.nCopies(9, null);
        ExecutionRecipePreviewLayout.Layout layout = ExecutionRecipePreviewLayout
                .build(inputs, List.of(new MaterialSlot("Output", 1, MaterialCategories.item)));

        assertEquals(1, layout.inputSections().size());
        assertEquals(1, layout.outputSections().size());
        assertEquals(9, layout.inputSlotCount());
        assertEquals(1, layout.outputSlotCount());
    }

    @Test
    void fallbackPreviewPartitionsInputsCatalystsAndOutputsByRole() {
        ExecutionRecipePreviewLayout.Layout layout = ExecutionRecipePreviewLayout.build(
            List.of(
                new MaterialSlot("Iron", 1, MaterialCategories.item),
                new MaterialSlot("Water", 1000, MaterialCategories.fluid),
                new MaterialSlot("Mold", 1, MaterialCategories.catalyst, true)
            ),
            List.of(
                new MaterialSlot("Plate", 1, MaterialCategories.item),
                new MaterialSlot("Steam", 1000, MaterialCategories.fluid)
            )
        );

        assertEquals(3, layout.inputSections().size());
        assertEquals(2, layout.outputSections().size());
        assertEquals(5, layout.sectionCount());
        assertTrue(layout.hasTypedSections());
        assertTrue(layout.inputSections().stream().anyMatch(ExecutionRecipePreviewLayout.Section::catalyst));
    }
}
