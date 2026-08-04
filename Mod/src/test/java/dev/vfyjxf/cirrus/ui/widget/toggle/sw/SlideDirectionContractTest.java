package dev.vfyjxf.cirrus.ui.widget.toggle.sw;

import org.junit.jupiter.api.Test;

import static dev.vfyjxf.cirrus.ui.CirrusTextures.Toggle.Switch.Slide.off;
import static dev.vfyjxf.cirrus.ui.CirrusTextures.Toggle.Switch.Slide.offHover;
import static dev.vfyjxf.cirrus.ui.CirrusTextures.Toggle.Switch.Slide.on;
import static dev.vfyjxf.cirrus.ui.CirrusTextures.Toggle.Switch.Slide.onHover;
import static org.junit.jupiter.api.Assertions.assertSame;

class SlideDirectionContractTest {

    @Test
    void enabledStateUsesRightSideVisualTexture() {
        assertSame(off, Slide.textureFor(true, false));
        assertSame(offHover, Slide.textureFor(true, true));
    }

    @Test
    void disabledStateUsesLeftSideVisualTexture() {
        assertSame(on, Slide.textureFor(false, false));
        assertSame(onHover, Slide.textureFor(false, true));
    }
}
