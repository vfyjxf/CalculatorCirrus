package dev.vfyjxf.cirrus.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CirrusCalculatorTextureContractTest {

    @Test
    void calculatorActionButtonTextureIsRegistered() {
        assertNotNull(CirrusTextures.Button.Calc.primary);
    }

    @Test
    void executionHorizontalScrollbarNineSliceHasPositiveCenter() {
        var texture = CirrusTextures.Icon.scrollBarHorizontal;
        assertTrue(
            texture.width() - texture.left() - texture.right() > 0,
            "horizontal scrollbar nine-slice center width must be > 0"
        );
        assertTrue(
            texture.height() - texture.top() - texture.bottom() > 0,
            "horizontal scrollbar nine-slice center height must be > 0"
        );
    }
}
