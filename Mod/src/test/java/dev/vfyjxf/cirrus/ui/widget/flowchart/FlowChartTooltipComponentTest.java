package dev.vfyjxf.cirrus.ui.widget.flowchart;

import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode.MaterialSlot;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode.MaterialType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FlowChartTooltipComponentTest {

    @Test
    void recipeCardLayoutWrapsInputs() {
        var layout = FlowChartTooltipComponent.recipeCardLayout(140, 10, 2);
        assertEquals(7, layout.columns(), "Columns should match width / slot step");
        assertEquals(2, layout.inputRows(), "Inputs should wrap to multiple rows");
        assertEquals(1, layout.outputRows(), "Outputs should fit on one row");
    }

    @Test
    void recipeCardLayoutClampsToSingleColumn() {
        var layout = FlowChartTooltipComponent.recipeCardLayout(10, 3, 2);
        assertEquals(1, layout.columns(), "Columns should clamp to at least one");
        assertEquals(3, layout.inputRows(), "Inputs should stack when only one column fits");
        assertEquals(2, layout.outputRows(), "Outputs should stack when only one column fits");
    }

    @Test
    void recipeCardEntryReportsSize() {
        var entry = FlowChartTooltipComponent.Entry.RecipeCard.empty();
        var component = FlowChartTooltipComponent.builder().recipeCard(entry).build();
        var client = component.toClient(component);
        assertTrue(client.getWidth(null) > 0);
        assertTrue(client.getHeight() > 0);
    }

    @Test
    void recipeCardCatalystsWrapIncreaseHeight() {
        var oneCatalyst = new FlowChartTooltipComponent.Entry.RecipeCard(
                List.of(new MaterialSlot("cat", 1, MaterialType.CATALYST)),
                List.of(),
                List.of(new MaterialSlot("cat", 1, MaterialType.CATALYST)),
                null
        );
        var manyCatalysts = new FlowChartTooltipComponent.Entry.RecipeCard(
                List.of(new MaterialSlot("cat", 1, MaterialType.CATALYST)),
                List.of(),
                List.of(
                        new MaterialSlot("c1", 1, MaterialType.CATALYST),
                        new MaterialSlot("c2", 1, MaterialType.CATALYST),
                        new MaterialSlot("c3", 1, MaterialType.CATALYST),
                        new MaterialSlot("c4", 1, MaterialType.CATALYST),
                        new MaterialSlot("c5", 1, MaterialType.CATALYST),
                        new MaterialSlot("c6", 1, MaterialType.CATALYST),
                        new MaterialSlot("c7", 1, MaterialType.CATALYST),
                        new MaterialSlot("c8", 1, MaterialType.CATALYST),
                        new MaterialSlot("c9", 1, MaterialType.CATALYST),
                        new MaterialSlot("c10", 1, MaterialType.CATALYST),
                        new MaterialSlot("c11", 1, MaterialType.CATALYST),
                        new MaterialSlot("c12", 1, MaterialType.CATALYST),
                        new MaterialSlot("c13", 1, MaterialType.CATALYST),
                        new MaterialSlot("c14", 1, MaterialType.CATALYST)
                ),
                null
        );

        var oneComponent = FlowChartTooltipComponent.builder().recipeCard(oneCatalyst).build();
        var manyComponent = FlowChartTooltipComponent.builder().recipeCard(manyCatalysts).build();
        var oneClient = oneComponent.toClient(oneComponent);
        var manyClient = manyComponent.toClient(manyComponent);

        assertTrue(manyClient.getHeight() > oneClient.getHeight(),
                "Catalyst wrapping should increase recipe card height.");
        int recipeCardMaxCols = 7; // FlowChartTooltipComponent.Client.RECIPE_CARD_MAX_COLS
        int iconStep = 18; // FlowChartTooltipComponent.Client.ICON_STEP
        int padX = 4; // FlowChartTooltipComponent.Client.PAD_X
        int expectedMaxWidth = recipeCardMaxCols * iconStep + padX * 2;
        assertTrue(manyClient.getWidth(null) <= expectedMaxWidth,
                "Catalyst wrapping should cap recipe card width to max columns.");
    }

    @Test
    void recipeCardCatalystsAlignWithLayoutColumns() {
        var card = new FlowChartTooltipComponent.Entry.RecipeCard(
                List.of(new MaterialSlot("in", 1, MaterialType.ITEM)),
                List.of(new MaterialSlot("out", 1, MaterialType.ITEM)),
                List.of(
                        new MaterialSlot("c1", 1, MaterialType.CATALYST),
                        new MaterialSlot("c2", 1, MaterialType.CATALYST),
                        new MaterialSlot("c3", 1, MaterialType.CATALYST),
                        new MaterialSlot("c4", 1, MaterialType.CATALYST),
                        new MaterialSlot("c5", 1, MaterialType.CATALYST),
                        new MaterialSlot("c6", 1, MaterialType.CATALYST),
                        new MaterialSlot("c7", 1, MaterialType.CATALYST),
                        new MaterialSlot("c8", 1, MaterialType.CATALYST)
                ),
                null
        );

        var component = FlowChartTooltipComponent.builder().recipeCard(card).build();
        var client = component.toClient(component);

        int iconStep = 18; // FlowChartTooltipComponent.Client.ICON_STEP
        int padX = 4; // FlowChartTooltipComponent.Client.PAD_X
        int arrowGap = 18; // FlowChartTooltipComponent.Client.RECIPE_ARROW_GAP
        int maxSlots = 1;
        int maxWidth = Math.min(iconStep * Math.max(1, maxSlots), iconStep * 7);
        var layout = FlowChartTooltipComponent.recipeCardLayout(maxWidth, 1, 1);
        int columns = Math.max(1, layout.columns());
        int expectedMainWidth = columns * iconStep * 2 + arrowGap;
        int expectedWidth = expectedMainWidth + padX * 2;

        assertEquals(expectedWidth, client.getWidth(null),
                "Catalyst columns should align with the recipe card layout columns.");
    }
}
