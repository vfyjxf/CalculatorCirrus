package dev.vfyjxf.cirrus.ui.widget.calculator.calc;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectedRecipe;
import dev.vfyjxf.cirrus.ui.CirrusScroll;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusCardWidget;
import dev.vfyjxf.cirrus.util.NumberUtils;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.effect.UIEffects;
import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState;
import dev.vfyjxf.cloudlib.ui.widget.ColumnWidget;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import dev.vfyjxf.taffy.style.Overflow;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.eclipse.collections.api.list.ImmutableList;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsFlexStart;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.columnGap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flex;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.justifySpaceBetween;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.margin;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.overflow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingHorizontal;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingRight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.rowGap;

public class TargetSummaryCard extends CompositeWidget<Widget> {
    private static final int rowHeight = 20;
    private static final int headerColor = 0xFFE5E7EB;
    private static final int mutedColor = 0xFF9CA3AF;
    private static final int valueColor = 0xFFFF7777;
    private static final int chanceColor = 0xFF77DD77;

    public TargetSummaryCard(ProjectedRecipe view) {
        useStyle(
            flexColumn(),
            padding(4),
            margin(4),
            rowGap(4),
            overflow(Overflow.HIDDEN)
        );

        addWidget(LabelWidget.of(CirrusLangs.Summary.title)
            .setColor(headerColor)
            .setShadow(false));
        addWidget(new ResourceSummaryWidget(view));
    }

    static class ResourceSummaryWidget extends CompositeWidget<Widget> {
        private final ScrollState scrollState;

        ResourceSummaryWidget(ProjectedRecipe view) {
            scrollState = CirrusScroll.longVerticalState(12)
                .smooth(true)
                .smoothSpeed(0.35f)
                .trackTexture(CirrusTextures.Background.scroll)
                .thumbTexture(CirrusTextures.Icon.scrollBarVertical)
                .scrollbarWidth(6)
                .minThumbSize(14)
                .viewportInset(3, 1, 3, 0);
            useEffect(UIEffects.scrollable(scrollState));
            useStyle(
                flexRow(),
                columnGap(4),
                paddingRight(10),
                flexGrow(1),
                alignItemsFlexStart(),
                minWidth(0)
            );

            ColumnWidget left = addWidget(ColumnWidget.create());
            left.useStyle(flex(1), minWidth(0), rowGap(4));
            left.addWidget(inputSection(view.inputs(), false));
            left.addWidget(inputSection(view.inputs(), true));

            ColumnWidget right = addWidget(ColumnWidget.create());
            right.useStyle(flex(1), minWidth(0), rowGap(4));
            right.addWidget(outputSection(view.outputs()));
            Widget specs = valueSection(view.outputs());
            if (specs != null) {
                right.addWidget(specs);
            }
        }

        private static Widget inputSection(ImmutableList<InputContent> inputs, boolean catalysts) {
            CirrusCardWidget section = section((catalysts ? CirrusLangs.Summary.catalysts : CirrusLangs.Summary.inputs).component());
            for (InputContent input : inputs) {
                if (input.isEmpty() || input instanceof ValueContent<?>) {
                    continue;
                }
                if (catalysts != input.notConsumed()) {
                    continue;
                }
                section.addContent(new ContentAmountRow(input));
            }
            return section;
        }

        private static Widget outputSection(ImmutableList<OutputContent> outputs) {
            CirrusCardWidget section = section(CirrusLangs.Summary.outputs.component());
            for (OutputContent output : outputs) {
                if (output.isEmpty() || output instanceof ValueContent<?>) {
                    continue;
                }
                section.addContent(new ContentAmountRow(output));
            }
            return section;
        }

        private static Widget valueSection(ImmutableList<OutputContent> outputs) {
            CirrusCardWidget section = section(CirrusLangs.Summary.specs.component());
            int added = 0;
            for (OutputContent output : outputs) {
                if (!(output instanceof ValueContent<?>)) {
                    continue;
                }
                ContentResource resource = output.asResource();
                if (resource instanceof ContentValue<?> value) {
                    if (isImplicitEnergySpec(value)) {
                        continue;
                    }
                    section.addContent(new ValueRow(value));
                    added++;
                }
            }
            return added == 0 ? null : section;
        }

        private static CirrusCardWidget section(Component title) {
            CirrusCardWidget card = new CirrusCardWidget(title);
            card.useStyle(rowGap(2), padding(4), minWidth(0));
            return card;
        }

        private static boolean isImplicitEnergySpec(ContentValue<?> value) {
            return value.type() == CirrusTypes.Contents.ENERGY;
        }
    }

    static class ContentAmountRow extends CompositeWidget<Widget> {
        private final RecipeContent content;

        ContentAmountRow(RecipeContent content) {
            this.content = content;
            useStyle(
                flexRow(),
                alignItemsCenter(),
                columnGap(4),
                heightOf(rowHeight),
                minWidth(0),
                overflow(Overflow.HIDDEN)
            );

            RecipeContentSlotWidget slot = addWidget(new RecipeContentSlotWidget(content));
            slot.useStyle(background(Background.Slot.dark), flexShrink(0));

            LabelWidget amount = addWidget(amountLabel());
            amount.useStyle(flexGrow(1), flexShrink(1), minWidth(0), overflow(Overflow.HIDDEN));
        }

        private LabelWidget amountLabel() {
            var amount = Component.literal(formatAmount(content));
            float chance = content.chance();
            if (chance > RecipeContent.MIN_CHANCE && chance < RecipeContent.MAX_CHANCE) {
                amount.append(Component.literal(" " + Math.round(chance * 100) + "%").withStyle(ChatFormatting.GREEN));
                return LabelWidget.of(amount).setColor(chanceColor).setShadow(false);
            }
            return LabelWidget.of(amount).setColor(0xFFFFFFFF).setShadow(false);
        }

        private static String formatAmount(RecipeContent content) {
            String suffix = content instanceof StackContent<?, ?> stack && stack.isFluid() ? " mB" : "";
            return NumberUtils.toSuffix(content.amount()) + suffix;
        }
    }

    static class ValueRow extends CompositeWidget<Widget> {
        ValueRow(ContentValue<?> value) {
            useStyle(
                flexRow(),
                justifySpaceBetween(),
                alignItemsCenter(),
                heightOf(rowHeight),
                paddingHorizontal(2),
                minWidth(0),
                overflow(Overflow.HIDDEN)
            );

            LabelWidget label = addWidget(LabelWidget.of(CirrusLangs.Summary.spec(value.type().typeId().path()))
                .setColor(mutedColor)
                .setShadow(false));
            label.useStyle(flexShrink(1), minWidth(0), overflow(Overflow.HIDDEN));

            LabelWidget amount = addWidget(LabelWidget.of(NumberUtils.toSuffix(value.value()))
                .setColor(valueColor)
                .setShadow(false));
            amount.useStyle(flexShrink(0));
        }
    }
}
