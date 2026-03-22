package dev.vfyjxf.cirrus.ui.widget.calculator.calc;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectedRecipe;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.util.NumberUtils;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.effect.UIEffects;
import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollDirection;
import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState;
import dev.vfyjxf.cloudlib.ui.widget.ColumnWidget;
import dev.vfyjxf.cloudlib.ui.widget.DividerWidget;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import dev.vfyjxf.cloudlib.ui.widget.RowWidget;
import dev.vfyjxf.taffy.style.Overflow;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.eclipse.collections.api.list.ImmutableList;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;

/**
 * Resource summary card for a resolved recipe target.
 * <p>
 * Layout (Minecraft slot-style):
 * <pre>
 * ┌─────────────────────────────────────────┐
 * │  Resource Summary                       │  ← header
 * ├────────────────────┬────────────────────┤
 * │  ◆ 外部原材料       │  ◎ 预期产出配额     │
 * │  [slot] ×N         │  [slot] ×N         │  ← icon + amount (tooltip shows name)
 * │  [slot] ×N         │  [slot] 40%        │
 * │  ...               │  ...               │
 * │                    ├────────────────────┤
 * │  ✦ 催化剂与工具     │  ⚙ 运行参数        │
 * │  [slot]            │  耗时     12800s   │
 * │                    │  功耗   16.0M EU/t  │
 * └────────────────────┴────────────────────┘
 * </pre>
 */
public class TargetSummaryCard extends CompositeWidget<Widget> {

    //region constants

    private static final int SLOT_SIZE = 18;
    private static final int ROW_HEIGHT = 20;
    private static final int SECTION_GAP = 4;
    private static final int INNER_PAD = 4;
    private static final int LABEL_COLOR = 0x55FFFF;    // cyan header
    private static final int CHANCE_COLOR = 0x55FF55;    // green chance text
    private static final int VALUE_COLOR = 0xFF5555;     // red value highlight

    //endregion

    public TargetSummaryCard(ProjectedRecipe view) {
        useStyle(
            flexColumn(),
            background(Background.flat),
            padding(4),
            margin(6),
            overflow(Overflow.HIDDEN)
        );

        //region header

        var header = addWidget(RowWidget.create());
        header.useStyle(
            flexRow(),
            alignItemsCenter(),
            marginBottom(4)
        );
        header.addWidget(
            LabelWidget.of(Component.translatable("cirrus.ui.summary.title"))
                       .setColor(LABEL_COLOR)
                       .setShadow(true)
        );

        //endregion

        addWidget(
            DividerWidget.horizontal()
                         .setColor(0xFF555555)
                         .setThickness(1)
        );

        //region body

        addWidget(new ResourceSummaryWidget(view));

        //endregion
    }

    //region ResourceSummaryWidget — scrollable two-column body

    static class ResourceSummaryWidget extends CompositeWidget<Widget> {

        private final ScrollState scrollState;

        public ResourceSummaryWidget(ProjectedRecipe view) {
            this.scrollState = ScrollState.create(ScrollDirection.vertical)
                                          .scrollSpeed(12)
                                          .smooth(true)
                                          .smoothSpeed(0.35f)
                                          .trackTexture(CirrusTextures.Background.inset)
                                          .thumbTexture(CirrusTextures.Icon.scrollBar)
                                          .scrollbarWidth(6);
            useEffect(
                UIEffects.scrollable(scrollState)
            );
            useStyle(
                flexRow(),
                marginTop(SECTION_GAP),
                columnGap(4),
                paddingRight(10),
                flexGrow(1),
                alignItemsFlexStart()
            );

            //region left column — inputs + catalysts/tools

            var left = addWidget(ColumnWidget.create());
            left.useStyle(
                flex(1),
                minWidth(0),
                rowGap(SECTION_GAP)
            );
            left.addWidget(createInputSection(view.inputs()));
            left.addWidget(createCatalystSection(view.inputs()));

            //endregion

            //region right column — outputs + operational specs

            var right = addWidget(ColumnWidget.create());
            right.useStyle(
                flex(1),
                minWidth(0),
                rowGap(SECTION_GAP)
            );
            right.addWidget(createOutputSection(view.outputs()));
            right.addWidget(createOperationalSpecs(view.outputs()));

            //endregion

        }

        //region section builders

        /**
         * Input materials section — consumed inputs only (chance > 0).
         */
        private Widget createInputSection(ImmutableList<InputContent> inputs) {
            ColumnWidget section = ColumnWidget.create();
            section.useStyle(rowGap(2));

            section.addWidget(createSectionHeader("cirrus.ui.summary.inputs"));
            section.addWidget(DividerWidget.horizontal().setColor(0xFF444444));

            for (InputContent input : inputs) {
                if (input.isEmpty() || input.notConsumed()) continue;
                if (isValueContent(input)) continue;
                section.addWidget(new StackInputWidget(input, input.amount(), 0));
            }

            return section;
        }

        /**
         * Catalysts & tools section — non-consumed inputs (chance == 0).
         */
        private Widget createCatalystSection(ImmutableList<InputContent> inputs) {
            ColumnWidget section = ColumnWidget.create();
            section.useStyle(rowGap(2));

            section.addWidget(createSectionHeader("cirrus.ui.summary.catalysts"));
            section.addWidget(DividerWidget.horizontal().setColor(0xFF444444));

            for (InputContent input : inputs) {
                if (input.isEmpty() || input.consumed()) continue;
                section.addWidget(new StackInputWidget(input, input.amount(), 0));
            }

            return section;
        }

        /**
         * Output products section — stack-type outputs.
         */
        private Widget createOutputSection(ImmutableList<OutputContent> outputs) {
            ColumnWidget section = ColumnWidget.create();
            section.useStyle(
                background(Background.Outlined.inset),
                padding(INNER_PAD),
                rowGap(2)
            );

            section.addWidget(createSectionHeader("cirrus.ui.summary.outputs"));
            section.addWidget(DividerWidget.horizontal().setColor(0xFF444444));

            for (OutputContent output : outputs) {
                if (output.isEmpty()) continue;
                if (isValueContent(output)) continue;
                section.addWidget(createOutputEntry(output));
            }

            return section;
        }

        /**
         * Operational specs section — value-type outputs (time, energy, etc.).
         */
        private Widget createOperationalSpecs(ImmutableList<OutputContent> outputs) {
            ColumnWidget section = ColumnWidget.create();
            section.useStyle(
                background(Background.dark),
                padding(INNER_PAD),
                rowGap(2)
            );

            section.addWidget(createSectionHeader("cirrus.ui.summary.specs"));
            section.addWidget(DividerWidget.horizontal().setColor(0xFF444444));

            for (OutputContent output : outputs) {
                if (output.isEmpty()) continue;
                if (!isValueContent(output)) continue;
                section.addWidget(createValueRow(output));
            }

            return section;
        }

        //endregion

        //region widget factory

        private static LabelWidget createSectionHeader(String langKey) {
            var label = LabelWidget.of(Component.translatable(langKey))
                                   .setColor(LABEL_COLOR)
                                   .setShadow(true);
            label.useStyle(overflow(Overflow.HIDDEN));
            return label;
        }

        /**
         * Creates an output entry row: [slot icon] [amount/chance label].
         */
        private static Widget createOutputEntry(RecipeContent content) {
            if (content.isEmpty()) throw new IllegalArgumentException("Content is empty");
            return new OutputEntryRow(content);
        }

        /**
         * Creates a value spec row: [label]  [formatted value].
         */
        private static Widget createValueRow(OutputContent output) {
            ContentResource resource = output.asResource();
            if (resource instanceof ContentValue<?> value) {
                return new ValueSpecRow(value);
            }
            return LabelWidget.of("???").setColor(0xAAAAAA);
        }

        //endregion

        //region utils

        private static boolean isValueContent(RecipeContent content) {
            return content instanceof ValueContent<?>;
        }

        //endregion

    }

    //endregion

    //region OutputEntryRow — slot icon + amount label for outputs

    /**
     * A single row representing an output content entry.
     * <p>
     * Icon is rendered via {@link ContentStackWidget} (no quantity decoration);
     * amount and chance are shown as a separate label.
     * <pre>
     * [18×18 slot] [amount]
     * </pre>
     */
    static class OutputEntryRow extends CompositeWidget<Widget> {

        private final RecipeContent content;

        OutputEntryRow(RecipeContent content) {
            this.content = content;
            useStyle(
                flexRow(),
                alignItemsCenter(),
                columnGap(3),
                heightOf(ROW_HEIGHT),
                overflow(Overflow.HIDDEN)
            );

            var slot = addWidget(ContentStackWidget.fromContent(content));
            slot.useStyle(
                background(Background.Slot.dark)
            );

            addWidget(createAmountLabel());
        }

        private LabelWidget createAmountLabel() {
            float chance = content.chance();
            long amount = content.amount();
            if (chance < RecipeContent.MAX_CHANCE && chance > RecipeContent.MIN_CHANCE) {
                String text = NumberUtils.toSuffix(amount);
                int pct = Math.round(chance * 100);
                return LabelWidget.of(Component.literal(text + " ")
                                               .append(Component.literal(pct + "%")
                                                                .withStyle(ChatFormatting.GREEN)))
                                  .setColor(CHANCE_COLOR)
                                  .setShadow(true);
            }
            String amountText = NumberUtils.toSuffix(amount);
            boolean isFluid = content instanceof StackContent<?, ?> sc && sc.isFluid();
            String suffix = isFluid ? " mB" : "";
            int color = isFluid ? VALUE_COLOR : 0xFFFFFF;
            return LabelWidget.of(amountText + suffix)
                              .setColor(color)
                              .setShadow(true);
        }
    }

    //endregion

    //region ValueSpecRow — label + formatted value

    /**
     * A single row representing a value-type content (time, energy, etc.).
     * <pre>
     * [label]         [formatted value]
     * </pre>
     */
    static class ValueSpecRow extends CompositeWidget<Widget> {

        ValueSpecRow(ContentValue<?> value) {
            useStyle(
                flexRow(),
                justifySpaceBetween(),
                alignItemsCenter(),
                heightOf(ROW_HEIGHT),
                paddingHorizontal(2),
                overflow(Overflow.HIDDEN)
            );

            String typeName = value.type().typeId().path();
            var label = addWidget(
                LabelWidget.of(Component.translatable("cirrus.ui.summary.spec." + typeName))
                           .setColor(0xAAAAAA)
                           .setShadow(true)
            );
            label.useStyle(flexShrink(1), minWidth(0), overflow(Overflow.HIDDEN));

            var valueLabel = addWidget(
                LabelWidget.of(formatValue(value))
                           .setColor(VALUE_COLOR)
                           .setShadow(true)
            );
            valueLabel.useStyle(flexShrink(0));
        }

        private static String formatValue(ContentValue<?> value) {
            return NumberUtils.toSuffix(value.value());
        }
    }

    //endregion

    //region SlotWidget — renders a content stack inside a slot background

    //endregion

    //region ContentEntryWidget / ContentValueWidget — legacy typed wrappers

    static class ContentEntryWidget extends CompositeWidget<Widget> {
        final ContentStack<?, ?> content;

        ContentEntryWidget(ContentStack<?, ?> content) {
            this.content = content;
        }
    }

    static class ContentValueWidget extends CompositeWidget<Widget> {
        final ContentValue<?> value;

        ContentValueWidget(ContentValue<?> value) {
            this.value = value;
        }
    }

    //endregion

}
