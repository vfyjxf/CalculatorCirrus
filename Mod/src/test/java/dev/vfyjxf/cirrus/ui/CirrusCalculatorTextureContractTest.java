package dev.vfyjxf.cirrus.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CirrusCalculatorTextureContractTest {

    @Test
    void calculatorActionButtonTextureIsRegistered() {
        assertNotNull(CirrusTextures.Button.Calc.primary);
    }
}
