package dev.vfyjxf.cirrus.ui.tooltip;

import dev.vfyjxf.cirrus.ui.process.MaterialCategories;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MaterialSlot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RichTooltipComponentTest {

    @Test
    void recipeCardLayoutWrapsInputs() {
        var layout = RichTooltipComponent.recipeCardLayout(140, 10, 2);
        assertEquals(7, layout.columns(), "Columns should match width / slot step");
        assertEquals(2, layout.inputRows(), "Inputs should wrap to multiple rows");
        assertEquals(1, layout.outputRows(), "Outputs should fit on one row");
    }

    @Test
    void recipeCardLayoutClampsToSingleColumn() {
        var layout = RichTooltipComponent.recipeCardLayout(10, 3, 2);
        assertEquals(1, layout.columns(), "Columns should clamp to at least one");
        assertEquals(3, layout.inputRows(), "Inputs should stack when only one column fits");
        assertEquals(2, layout.outputRows(), "Outputs should stack when only one column fits");
    }

    @Test
    void recipeCardCatalystsWrapIncreaseHeight() {
        var oneCatalyst = new RichTooltipComponent.Entry.RecipeCard(
            List.of(new MaterialSlot("cat", 1, MaterialCategories.catalyst)),
            List.of(),
            List.of(new MaterialSlot("cat", 1, MaterialCategories.catalyst)),
            null
        );
        var manyCatalysts = new RichTooltipComponent.Entry.RecipeCard(
            List.of(new MaterialSlot("cat", 1, MaterialCategories.catalyst)),
            List.of(),
            List.of(
                new MaterialSlot("c1", 1, MaterialCategories.catalyst),
                new MaterialSlot("c2", 1, MaterialCategories.catalyst),
                new MaterialSlot("c3", 1, MaterialCategories.catalyst),
                new MaterialSlot("c4", 1, MaterialCategories.catalyst),
                new MaterialSlot("c5", 1, MaterialCategories.catalyst),
                new MaterialSlot("c6", 1, MaterialCategories.catalyst),
                new MaterialSlot("c7", 1, MaterialCategories.catalyst),
                new MaterialSlot("c8", 1, MaterialCategories.catalyst),
                new MaterialSlot("c9", 1, MaterialCategories.catalyst),
                new MaterialSlot("c10", 1, MaterialCategories.catalyst),
                new MaterialSlot("c11", 1, MaterialCategories.catalyst),
                new MaterialSlot("c12", 1, MaterialCategories.catalyst),
                new MaterialSlot("c13", 1, MaterialCategories.catalyst),
                new MaterialSlot("c14", 1, MaterialCategories.catalyst)
            ),
            null
        );

        var oneComponent = RichTooltipComponent.builder().recipeCard(oneCatalyst).build();
        var manyComponent = RichTooltipComponent.builder().recipeCard(manyCatalysts).build();
        var oneClient = oneComponent.toClient(oneComponent);
        var manyClient = manyComponent.toClient(manyComponent);

        assertTrue(
            manyClient.getHeight() > oneClient.getHeight(),
            "Catalyst wrapping should increase recipe card height."
        );
        int recipeCardMaxCols = 7; // RichTooltipComponent.Client.RECIPE_CARD_MAX_COLS
        int iconStep = 18; // RichTooltipComponent.Client.ICON_STEP
        int padX = 4; // RichTooltipComponent.Client.PAD_X
        int expectedMaxWidth = recipeCardMaxCols * iconStep + padX * 2;
        assertTrue(
            manyClient.getWidth(null) <= expectedMaxWidth,
            "Catalyst wrapping should cap recipe card width to max columns."
        );
    }

    @Test
    void recipeCardCatalystsAlignWithLayoutColumns() {
        var card = new RichTooltipComponent.Entry.RecipeCard(
            List.of(new MaterialSlot("in", 1, MaterialCategories.item)),
            List.of(new MaterialSlot("out", 1, MaterialCategories.item)),
            List.of(
                new MaterialSlot("c1", 1, MaterialCategories.catalyst),
                new MaterialSlot("c2", 1, MaterialCategories.catalyst),
                new MaterialSlot("c3", 1, MaterialCategories.catalyst),
                new MaterialSlot("c4", 1, MaterialCategories.catalyst),
                new MaterialSlot("c5", 1, MaterialCategories.catalyst),
                new MaterialSlot("c6", 1, MaterialCategories.catalyst),
                new MaterialSlot("c7", 1, MaterialCategories.catalyst),
                new MaterialSlot("c8", 1, MaterialCategories.catalyst)
            ),
            null
        );

        var component = RichTooltipComponent.builder().recipeCard(card).build();
        var client = component.toClient(component);

        int iconStep = 18; // RichTooltipComponent.Client.ICON_STEP
        int padX = 4; // RichTooltipComponent.Client.PAD_X
        int arrowGap = 18; // RichTooltipComponent.Client.RECIPE_ARROW_GAP
        int framePad = 3; // RichTooltipComponent.Client.RECIPE_CARD_FRAME_PAD
        int columns = 1;
        int expectedMainWidth = columns * iconStep * 2 + arrowGap;
        int expectedFrameWidth = expectedMainWidth + framePad * 2;
        int expectedWidth = expectedFrameWidth + padX * 2;

        assertEquals(
            expectedWidth,
            client.getWidth(null),
            "Catalyst columns should align with the recipe card layout columns."
        );
    }
}
