package dev.vfyjxf.cirrus.ui.execution.support;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.process.MaterialCategories;
import dev.vfyjxf.cirrus.ui.process.MaterialCategory;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MaterialSlot;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSlotGroupRenderer;
import dev.vfyjxf.cirrus.ui.workbench.value.ValueTypeGaugeRenderer;
import org.jspecify.annotations.Nullable;

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
    private static final int valueSectionGap = 6;
    private static final int arrowGapLeft = 7;
    private static final int arrowGapRight = 12;

    private ExecutionRecipePreviewLayout() {}

    public static Layout build(List<@Nullable MaterialSlot> inputs, List<MaterialSlot> outputs) {
        List<@Nullable MaterialSlot> rawInputs = inputs == null ? List.of() : inputs;
        List<MaterialSlot> rawOutputs = outputs == null ? List.of() : outputs;

        ArrayList<ValueTypeGaugeRenderer.ValueEntry> valueEntries = new ArrayList<>();
        ArrayList<@Nullable MaterialSlot> slotInputs = new ArrayList<>(rawInputs.size());
        int inputValues = 0;
        for (@Nullable
        MaterialSlot slot : rawInputs) {
            if (slot != null && slot.content() instanceof ValueContent<?> value && value.notEmpty()) {
                valueEntries.add(
                    ValueTypeGaugeRenderer.ValueEntry.fromContent(value, ValueTypeGaugeRenderer.Direction.input)
                );
                inputValues++;
            } else {
                slotInputs.add(slot);
            }
        }

        ArrayList<MaterialSlot> slotOutputs = new ArrayList<>(rawOutputs.size());
        int outputValues = 0;
        for (MaterialSlot slot : rawOutputs) {
            if (slot != null && slot.content() instanceof ValueContent<?> value && value.notEmpty()) {
                valueEntries.add(
                    ValueTypeGaugeRenderer.ValueEntry.fromContent(value, ValueTypeGaugeRenderer.Direction.output)
                );
                outputValues++;
            } else if (slot != null) {
                slotOutputs.add(slot);
            }
        }

        SideLayout inputLayout = sideLayout(inputSections(slotInputs));
        SideLayout outputLayout = sideLayout(outputSections(slotOutputs));
        boolean hasSlots = inputLayout.hasSlots() || outputLayout.hasSlots();
        boolean showArrow = inputLayout.hasSlots() && outputLayout.hasSlots();
        int arrowWidth = showArrow ? CirrusTextures.Icon.Middle.arrowRight.width() : 0;
        int arrowHeight = showArrow ? CirrusTextures.Icon.Middle.arrowRight.height() : 0;
        int middleWidth = showArrow ? arrowGapLeft + arrowWidth + arrowGapRight : 0;
        int slotRowWidth = inputLayout.width() + middleWidth + outputLayout.width();
        int slotRowHeight = hasSlots ? Math.max(Math.max(inputLayout.height(), outputLayout.height()), arrowHeight) : 0;

        int meterCols = valueEntries.size() <= 1 ? 1 : 2;
        int minValueStripW = valueEntries.isEmpty()
                ? 0
                : meterCols * ValueTypeGaugeRenderer.databaseMeterMinWidth
                        + (meterCols - 1) * ValueTypeGaugeRenderer.databaseMeterGap;
        int totalWidth = Math.max(slotRowWidth, minValueStripW);
        int slotOffsetX = Math.max(0, (totalWidth - slotRowWidth) / 2);

        SideLayout shiftedInputs = inputLayout.offset(slotOffsetX, centeredOffset(slotRowHeight, inputLayout.height()));
        int outputBaseX = slotOffsetX + inputLayout.width() + middleWidth;
        SideLayout shiftedOutputs = outputLayout
                .offset(outputBaseX, centeredOffset(slotRowHeight, outputLayout.height()));

        ArrayList<ValueEntryBox> valueBoxes = new ArrayList<>(valueEntries.size());
        int valueDividerY = -1;
        int totalHeight = slotRowHeight;
        if (!valueEntries.isEmpty()) {
            int meterTop = hasSlots ? slotRowHeight + valueSectionGap : 0;
            if (hasSlots) {
                valueDividerY = slotRowHeight + valueSectionGap / 2;
            }
            int gap = ValueTypeGaugeRenderer.databaseMeterGap;
            int meterH = ValueTypeGaugeRenderer.databaseMeterHeight;
            int cellW = meterCols == 1
                    ? totalWidth
                    : Math.max(ValueTypeGaugeRenderer.databaseMeterMinWidth, (totalWidth - gap) / 2);
            for (int i = 0; i < valueEntries.size(); i++) {
                int col = i % meterCols;
                int row = i / meterCols;
                boolean lastOdd = meterCols == 2 && i == valueEntries.size() - 1 && col == 0;
                int w = lastOdd ? totalWidth : cellW;
                int x = col * (cellW + gap);
                int y = meterTop + row * (meterH + gap);
                valueBoxes.add(new ValueEntryBox(valueEntries.get(i), x, y, w, meterH));
            }
            int rows = (int) Math.ceil(valueEntries.size() / (double) meterCols);
            totalHeight = meterTop + rows * meterH + Math.max(0, rows - 1) * gap;
        }

        return new Layout(
            shiftedInputs.sections(),
            shiftedOutputs.sections(),
            List.copyOf(valueBoxes),
            inputValues,
            outputValues,
            valueDividerY,
            showArrow,
            slotOffsetX + inputLayout.width() + arrowGapLeft,
            centeredOffset(slotRowHeight, arrowHeight),
            totalWidth,
            totalHeight
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
        return groupedSections(Side.input, slots);
    }

    private static List<Section> outputSections(List<MaterialSlot> slots) {
        if (slots.isEmpty()) {
            return List.of();
        }
        return groupedSections(Side.output, slots);
    }

    private static Section fixedCraftGridSection(List<@Nullable MaterialSlot> slots) {
        ArrayList<SlotEntry> entries = new ArrayList<>(slots.size());
        for (int index = 0; index < slots.size(); index++) {
            entries.add(new SlotEntry(slots.get(index), (index % 3) * slotStep, (index / 3) * slotStep));
        }
        return new Section(
            Side.input,
            MaterialCategories.item,
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
        for (@Nullable
        MaterialSlot slot : slots) {
            if (slot == null) {
                continue;
            }
            SectionKey key = SectionKey.of(side, slot);
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(slot);
        }
        ArrayList<Section> sections = new ArrayList<>(grouped.size());
        for (Map.Entry<SectionKey, ArrayList<MaterialSlot>> entry : grouped.entrySet()) {
            SectionKey key = entry.getKey();
            sections.add(
                gridSection(
                    key.side(),
                    key.category(),
                    key.catalyst(),
                    entry.getValue(),
                    columnsFor(key.side(), entry.getValue().size())
                )
            );
        }
        return List.copyOf(sections);
    }

    private static int columnsFor(Side side, int count) {
        if (side == Side.output) {
            if (count <= 2) return count;
            if (count <= 6) return 3;
            return 4;
        }
        if (count <= 2) return count;
        return 3;
    }

    private static Section gridSection(
        Side side,
        MaterialCategory category,
        boolean catalyst,
        List<MaterialSlot> slots,
        int columns
    ) {
        int safeColumns = Math.max(1, columns);
        ArrayList<SlotEntry> entries = new ArrayList<>(slots.size());
        for (int index = 0; index < slots.size(); index++) {
            entries.add(
                new SlotEntry(slots.get(index), (index % safeColumns) * slotStep, (index / safeColumns) * slotStep)
            );
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
        input, output
    }

    private record SectionKey(Side side, MaterialCategory category, boolean catalyst) {
        private static SectionKey of(Side side, MaterialSlot slot) {
            boolean catalyst = side == Side.input && slot.isCatalyst();
            return new SectionKey(side, catalyst ? MaterialCategories.catalyst : slot.category(), catalyst);
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
            return new Section(
                side,
                category,
                catalyst,
                this.x + x,
                this.y + y,
                width,
                height,
                cells,
                List.copyOf(out)
            );
        }
    }

    public record SlotEntry(@Nullable MaterialSlot slot, int x, int y) {}

    public record ValueEntryBox(ValueTypeGaugeRenderer.ValueEntry entry, int x, int y, int width, int height) {}

    public record Layout(
        List<Section> inputSections,
        List<Section> outputSections,
        List<ValueEntryBox> valueEntries,
        int inputValueCount,
        int outputValueCount,
        int valueDividerY,
        boolean showArrow,
        int arrowX,
        int arrowY,
        int width,
        int height
    ) {
        public Layout {
            valueEntries = valueEntries == null ? List.of() : List.copyOf(valueEntries);
        }

        public List<SlotEntry> inputs() {
            return flatten(inputSections);
        }

        public List<SlotEntry> outputs() {
            return flatten(outputSections);
        }

        public int inputSlotCount() {
            return inputSections.stream().mapToInt(section -> section.entries().size()).sum() + inputValueCount;
        }

        public int outputSlotCount() {
            return outputSections.stream().mapToInt(section -> section.entries().size()).sum() + outputValueCount;
        }

        public int valueCount() {
            return valueEntries.size();
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
            return inputSlotCount() > 0 || outputSlotCount() > 0 || !valueEntries.isEmpty();
        }

        public @Nullable ValueEntryBox valueAt(double x, double y) {
            for (ValueEntryBox box : valueEntries) {
                if (x >= box.x() && x < box.x() + box.width() && y >= box.y() && y < box.y() + box.height()) {
                    return box;
                }
            }
            return null;
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
            cells.add(
                new CirrusSlotGroupRenderer.Cell(Math.max(0, entry.x() / slotStep), Math.max(0, entry.y() / slotStep))
            );
        }
        return List.copyOf(cells);
    }
}
