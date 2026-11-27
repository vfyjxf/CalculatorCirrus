package dev.vfyjxf.cirrus.ui.widget.slider;

import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;

public final class CirrusSliderSkins {

    private CirrusSliderSkins() {}

    public static LongSliderWidget.Skin scroll() {
        return new LongSliderWidget.Skin(
            new LongSliderWidget.Metrics(
                Background.Outlined.inset,
                CirrusTextures.Icon.scrollTrackFill,
                CirrusTextures.Icon.scrollBarHorizontal,
                CirrusTextures.Icon.scrollBarHorizontal,
                2,
                13,
                3,
                1
            ),
            new LongSliderWidget.Metrics(
                Background.Outlined.inset,
                CirrusTextures.Icon.scrollTrackFill,
                CirrusTextures.Icon.scrollBarVertical,
                CirrusTextures.Icon.scrollBarVertical,
                2,
                12,
                3,
                1
            )
        );
    }
}
