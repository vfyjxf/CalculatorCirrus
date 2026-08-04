package dev.vfyjxf.cirrus.ui.execution.support;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.process.MaterialCategories;
import dev.vfyjxf.cirrus.ui.process.MaterialCategory;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MaterialSlot;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSlotGroupRenderer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExecutionRecipePreviewLayout {
    public static final int slotSize = 18;
    public static final int slotInnerSize = 16;
    public static final int slotStep = 18;
    private static final int craftGridSpan = 54;
    private static final int sectionGap = 4;
    private static final int arrowGapLeft = 7;
    private static final int arrowGapRight = 12;

    private ExecutionRecipePreviewLayout() {
    }

    public static Layout build(List<@Nullable MaterialSlot> inputs, List<MaterialSlot> outputs) {
        SideLayout inputLayout = sideLayout(inputSections(inputs == null ? List.of() : inputs));
        SideLayout outputLayout = sideLayout(outputSections(outputs == null ? List.of() : outputs));
        boolean showArrow = inputLayout.hasSlots() && outputLayout.hasSlots();
        int arrowWidth = showArrow ? CirrusTextures.Icon.Middle.arrowRight.width() : 0;
        int arrowHeight = showArrow ? CirrusTextures.Icon.Middle.arrowRight.height() : 0;
        int middleWidth = showArrow ? arrowGapLeft + arrowWidth + arrowGapRight : 0;
        int height = Math.max(Math.max(inputLayout.height(), outputLayout.height()), arrowHeight);

        SideLayout shiftedInputs = inputLayout.offset(0, centeredOffset(height, inputLayout.height()));
        int outputBaseX = inputLayout.width() + middleWidth;
        SideLayout shiftedOutputs = outputLayout.offset(outputBaseX, centeredOffset(height, outputLayout.height()));

        return new Layout(
            shiftedInputs.sections(),
            shiftedOutputs.sections(),
            showArrow,
            inputLayout.width() + arrowGapLeft,
            centeredOffset(height, arrowHeight),
            inputLayout.width() + middleWidth + outputLayout.width(),
            height
        );
    }

    public static RecipeContent slotContent(@Nullable MaterialSlot slot) {
        if (slot == null) {
            return RecipeContent.empty();
        }
        RecipeContent content = slot.content();
        if (content != null && content.notEmpty()) {
            return content;
        }
        return RecipeContent.empty();
    }

    private static List<Section> inputSections(List<@Nullable MaterialSlot> slots) {
        if (slots.isEmpty()) {
            return List.of();
        }
        if (slots.size() == 9) {
            return List.of(fixedCraftGridSection(slots));
        }
        return groupedSections(Side.INPUT, slots);
    }

    private static List<Section> outputSections(List<MaterialSlot> slots) {
        if (slots.isEmpty()) {
            return List.of();
        }
        return groupedSections(Side.OUTPUT, slots);
    }

        private static Section fixedCraftGridSection(List<@Nullable MaterialSlot> slots) {
            ArrayList<SlotEntry> entries = new ArrayList<>(slots.size());
            for (int index = 0; index < slots.size(); index++) {
                entries.add(new SlotEntry(
                    slots.get(index),
                (index % 3) * slotStep,
                (index / 3) * slotStep
            ));
        }
            return new Section(
                Side.INPUT,
                MaterialCategories.ITEM,
                false,
                0,
                0,
                craftGridSpan,
                craftGridSpan,
                CirrusSlotGroupRenderer.rectCells(3, 3),
                List.copyOf(entries)
            );
        }

    private static List<Section> groupedSections(Side side, List<? extends @Nullable MaterialSlot> slots) {
        LinkedHashMap<SectionKey, ArrayList<MaterialSlot>> grouped = new LinkedHashMap<>();
        for (@Nullable MaterialSlot slot : slots) {
            if (slot == null) {
                continue;
            }
            SectionKey key = SectionKey.of(side, slot);
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(slot);
        }
        ArrayList<Section> sections = new ArrayList<>(grouped.size());
        for (Map.Entry<SectionKey, ArrayList<MaterialSlot>> entry : grouped.entrySet()) {
            SectionKey key = entry.getKey();
            sections.add(gridSection(key.side(), key.category(), key.catalyst(), entry.getValue(), columnsFor(key.side(), entry.getValue().size())));
        }
        return List.copyOf(sections);
    }

    private static int columnsFor(Side side, int count) {
        if (side == Side.OUTPUT) {
            if (count <= 2) return count;
            if (count <= 6) return 3;
            return 4;
        }
        if (count <= 2) return count;
        return 3;
    }

    private static Section gridSection(Side side, MaterialCategory category, boolean catalyst, List<MaterialSlot> slots, int columns) {
        int safeColumns = Math.max(1, columns);
        ArrayList<SlotEntry> entries = new ArrayList<>(slots.size());
        for (int index = 0; index < slots.size(); index++) {
            entries.add(new SlotEntry(
                slots.get(index),
                (index % safeColumns) * slotStep,
                (index / safeColumns) * slotStep
            ));
        }
        int rows = (int) Math.ceil((double) slots.size() / (double) safeColumns);
        return new Section(
            side,
            category,
            catalyst,
            0,
            0,
            safeColumns * slotStep,
            rows * slotStep,
            cellsForSlots(entries),
            List.copyOf(entries)
        );
    }

    private static int centeredOffset(int container, int content) {
        return Math.max(0, (container - content) / 2);
    }

    private record SideLayout(List<Section> sections, int width, int height) {
        private boolean hasSlots() {
            return sections.stream().anyMatch(section -> !section.entries().isEmpty());
        }

        private SideLayout offset(int x, int y) {
            if (sections.isEmpty() || x == 0 && y == 0) {
                return this;
            }
            ArrayList<Section> out = new ArrayList<>(sections.size());
            for (Section section : sections) {
                out.add(section.offset(x, y));
            }
            return new SideLayout(List.copyOf(out), width, height);
        }
    }

    private static SideLayout sideLayout(List<Section> sections) {
        if (sections.isEmpty()) {
            return new SideLayout(List.of(), 0, 0);
        }
        ArrayList<Section> placed = new ArrayList<>(sections.size());
        int y = 0;
        int width = 0;
        for (Section section : sections) {
            Section shifted = section.offset(0, y);
            placed.add(shifted);
            width = Math.max(width, shifted.width());
            y += section.height() + sectionGap;
        }
        int height = Math.max(0, y - sectionGap);
        return new SideLayout(List.copyOf(placed), width, height);
    }

    public enum Side {
        INPUT,
        OUTPUT
    }

    private record SectionKey(Side side, MaterialCategory category, boolean catalyst) {
        private static SectionKey of(Side side, MaterialSlot slot) {
            boolean catalyst = side == Side.INPUT && slot.isCatalyst();
            return new SectionKey(
                side,
                catalyst ? MaterialCategories.CATALYST : slot.category(),
                catalyst
            );
        }
    }

    public record Section(
        Side side,
        MaterialCategory category,
        boolean catalyst,
        int x,
        int y,
        int width,
        int height,
        List<CirrusSlotGroupRenderer.Cell> cells,
        List<SlotEntry> entries
    ) {
        public Section {
            cells = List.copyOf(cells);
            entries = List.copyOf(entries);
        }

        private Section offset(int x, int y) {
            if (x == 0 && y == 0) {
                return this;
            }
            ArrayList<SlotEntry> out = new ArrayList<>(entries.size());
            for (SlotEntry entry : entries) {
                out.add(new SlotEntry(entry.slot(), entry.x() + x, entry.y() + y));
            }
            return new Section(side, category, catalyst, this.x + x, this.y + y, width, height, cells, List.copyOf(out));
        }
    }

    public record SlotEntry(@Nullable MaterialSlot slot, int x, int y) {
    }

    public record Layout(
        List<Section> inputSections,
        List<Section> outputSections,
        boolean showArrow,
        int arrowX,
        int arrowY,
        int width,
        int height
    ) {
        public List<SlotEntry> inputs() {
            return flatten(inputSections);
        }

        public List<SlotEntry> outputs() {
            return flatten(outputSections);
        }

        public int inputSlotCount() {
            return inputSections.stream().mapToInt(section -> section.entries().size()).sum();
        }

        public int outputSlotCount() {
            return outputSections.stream().mapToInt(section -> section.entries().size()).sum();
        }

        public int sectionCount() {
            return inputSections.size() + outputSections.size();
        }

        public boolean hasTypedSections() {
            return sectionCount() > 2
                || inputSections.stream().anyMatch(Section::catalyst)
                || outputSections.stream().map(Section::category).distinct().count() > 1;
        }

        public boolean hasSlots() {
            return inputSlotCount() > 0 || outputSlotCount() > 0;
        }

        private static List<SlotEntry> flatten(List<Section> sections) {
            ArrayList<SlotEntry> out = new ArrayList<>();
            for (Section section : sections) {
                out.addAll(section.entries());
            }
            return List.copyOf(out);
        }
    }

    public static List<CirrusSlotGroupRenderer.Cell> cellsForSlots(List<SlotEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        ArrayList<CirrusSlotGroupRenderer.Cell> cells = new ArrayList<>(entries.size());
        for (SlotEntry entry : entries) {
            cells.add(new CirrusSlotGroupRenderer.Cell(
                Math.max(0, entry.x() / slotStep),
                Math.max(0, entry.y() / slotStep)
            ));
        }
        return List.copyOf(cells);
    }
}
