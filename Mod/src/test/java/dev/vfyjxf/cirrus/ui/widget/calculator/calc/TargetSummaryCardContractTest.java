package dev.vfyjxf.cirrus.ui.widget.calculator.calc;

import dev.vfyjxf.cirrus.ui.widget.common.CirrusCardWidget;
import dev.vfyjxf.cloudlib.ui.widget.ColumnWidget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetSummaryCardContractTest {

    @Test
    void summarySpecsHideImplicitEnergyValueRows() {
        TargetSummaryCard card = new TargetSummaryCard(DebugRecipeProvider.createDebugView());

        TargetSummaryCard.ResourceSummaryWidget summary = (TargetSummaryCard.ResourceSummaryWidget) card.children().get(1);
        ColumnWidget right = (ColumnWidget) summary.children().get(1);
        CirrusCardWidget specs = (CirrusCardWidget) right.children().get(1);

        assertEquals(1, specs.body().children().size());
        assertTrue(specs.body().children().getFirst() instanceof TargetSummaryCard.ValueRow);
    }

    @Test
    void summarySectionsUseReusableLightCards() {
        TargetSummaryCard card = new TargetSummaryCard(DebugRecipeProvider.createDebugView());

        TargetSummaryCard.ResourceSummaryWidget summary = (TargetSummaryCard.ResourceSummaryWidget) card.children().get(1);
        ColumnWidget left = (ColumnWidget) summary.children().getFirst();
        ColumnWidget right = (ColumnWidget) summary.children().get(1);

        assertTrue(left.children().getFirst() instanceof CirrusCardWidget);
        assertTrue(left.children().get(1) instanceof CirrusCardWidget);
        assertTrue(right.children().getFirst() instanceof CirrusCardWidget);
        assertTrue(right.children().get(1) instanceof CirrusCardWidget);
    }
}
