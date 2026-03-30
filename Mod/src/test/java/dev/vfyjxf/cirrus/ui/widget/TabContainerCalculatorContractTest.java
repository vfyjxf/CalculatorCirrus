package dev.vfyjxf.cirrus.ui.widget;

import dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator.OmniCalculatorView;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TabContainerCalculatorContractTest {

    @Test
    void calculatorTabBuildsOmniCalcView() {
        TabContainer container = new TabContainer();
        Widget calculatorContent = container.debugCreateRegisteredCalculatorContent();

        assertTrue(calculatorContent instanceof OmniCalculatorView);
    }

    @Test
    void calculatorTabIsSelectedByDefault() {
        TabContainer container = new TabContainer();

        assertTrue(container.debugCurrentContent() instanceof OmniCalculatorView);
    }
}
