package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.network.chat.Component;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textMuted;
import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textPrimary;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.widthOf;

public final class CirrusKeyValueRowWidget extends CirrusRowWidget {
    private static final int defaultKeyWidth = 58;

    public CirrusKeyValueRowWidget(Component key, Component value) {
        this(key, value, defaultKeyWidth);
    }

    public CirrusKeyValueRowWidget(Component key, Component value, int keyWidth) {
        super(18, 4, Chrome.flat);

        CirrusFittedTextWidget keyWidget = new CirrusFittedTextWidget(key, textMuted);
        keyWidget.useStyle(widthOf(Math.max(34, keyWidth)), flexShrink(0));
        addWidget(keyWidget);

        CirrusFittedTextWidget valueWidget = new CirrusFittedTextWidget(value, textPrimary);
        valueWidget.useStyle(flexGrow(1), minWidth(0));
        addWidget(valueWidget);
    }

    public CirrusKeyValueRowWidget(LangEntry key, Component value) {
        this(key == null ? Component.empty() : key.component(), value);
    }

    public static CirrusKeyValueRowWidget create(Component key, Component value) {
        return new CirrusKeyValueRowWidget(key, value);
    }

    public static CirrusKeyValueRowWidget create(LangEntry key, Component value) {
        return new CirrusKeyValueRowWidget(key, value);
    }
}
